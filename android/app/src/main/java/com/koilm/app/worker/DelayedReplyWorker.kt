package com.koilm.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.koilm.app.data.ChatRepository
import com.koilm.app.data.db.AppDatabase
import com.koilm.app.data.db.PendingProactiveMessageEntity
import com.koilm.app.llm.LlmEngineProvider
import com.koilm.app.memory.ContextMemoryManager
import com.koilm.app.notification.NotificationHelper
import com.koilm.app.settings.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 「タイミング」返信モード(ReplyMode.TIMED)用のワーカー。
 * ユーザーがメッセージを送った直後ではなく、最大15分以内のランダムなタイミングで
 * キャラクターからの返信を生成し、通知で知らせる(即レスしすぎない「間」を演出する)。
 *
 * AIBackgroundWorkerと違い、これは特定のsessionId(=キャラクター)に対して1回だけ実行される
 * one-shotなワーカーで、自己再スケジュールは行わない。
 */
class DelayedReplyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val KEY_SESSION_ID = "sessionId"

        private const val BASE_MAX_DELAY_MINUTES = 15

        /**
         * ユーザーがメッセージを送った直後に呼ぶ。0秒〜15分のランダムな遅延で返信をスケジュールするが、
         * 好感度システムが有効な場合は好感度が高いほど最大遅延を短くし(好感度1につき最大1分短縮)、
         * さらに「仕事中」時間帯に該当する場合はその時間帯が終わるまで押し出す
         * (この場合は既読すら付かない=ChatRepository側の既読マークもまだ行われない)。
         */
        suspend fun schedule(context: Context, sessionId: String) {
            val prefs = AppPreferences(context)
            val affectionEnabled = prefs.isAffectionSystemEnabled.first()

            var maxDelayMinutes = BASE_MAX_DELAY_MINUTES
            if (affectionEnabled) {
                val character = AppDatabase.getInstance(context).characterDao().getById(sessionId)
                val affection = character?.affectionScore ?: 0
                maxDelayMinutes = (BASE_MAX_DELAY_MINUTES - affection).coerceIn(1, BASE_MAX_DELAY_MINUTES)
            }

            val now = LocalDateTime.now()
            var candidate = now.plusSeconds(Random.nextInt(maxDelayMinutes * 60).toLong())

            if (affectionEnabled) {
                val busyStart = prefs.busyHoursStart.first()
                val busyEnd = prefs.busyHoursEnd.first()
                candidate = pushOutsideBusyHours(candidate, busyStart, busyEnd)
            }

            val delaySeconds = Duration.between(now, candidate).seconds.coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<DelayedReplyWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** candidate が「仕事中」時間帯(busyStart時〜busyEnd時、日またぎ可)に入っていれば、その終了時刻まで押し出す。 */
        private fun pushOutsideBusyHours(candidate: LocalDateTime, busyStart: Int, busyEnd: Int): LocalDateTime {
            if (busyStart == busyEnd) return candidate

            val hour = candidate.hour
            val inBusyHours = if (busyStart < busyEnd) {
                hour in busyStart until busyEnd
            } else {
                hour >= busyStart || hour < busyEnd
            }
            if (!inBusyHours) return candidate

            var pushed = candidate.toLocalDate().atTime(busyEnd, 0)
            if (!pushed.isAfter(candidate)) {
                pushed = pushed.plusDays(1)
            }
            return pushed
        }
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val context = applicationContext
        val database = AppDatabase.getInstance(context)
        val dao = database.chatDao()
        val characterDao = database.characterDao()
        val prefs = AppPreferences(context)

        val character = characterDao.getById(sessionId) ?: return Result.success()

        // 既に返信済み(最後のターンがuserでない)なら二重生成しない。
        val lastTurn = dao.getTurns(sessionId).lastOrNull()
        if (lastTurn == null || lastTurn.role != "user") {
            return Result.success()
        }

        val engine = LlmEngineProvider.getOrLoad(context)
        val memoryManager = ContextMemoryManager(dao, engine)
        val chatRepository = ChatRepository(dao, characterDao, engine, memoryManager, prefs)

        val contentLevel = prefs.contentLevel.first()
        val message = chatRepository.generateReply(sessionId, character.systemPrompt, contentLevel).trim()
        if (message.isBlank()) return Result.success()

        val pendingId = dao.insertPendingProactiveMessage(
            PendingProactiveMessageEntity(
                sessionId = sessionId,
                text = message,
            ),
        )

        runCatching {
            NotificationHelper.showProactiveMessage(
                context = context,
                characterName = character.name,
                text = message,
                pendingMessageId = pendingId,
            )
        }

        return Result.success()
    }
}
