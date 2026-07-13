package com.koilm.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.koilm.app.data.db.AppDatabase
import com.koilm.app.data.db.ConversationTurnEntity
import com.koilm.app.data.db.PendingProactiveMessageEntity
import com.koilm.app.llm.LlmEngineProvider
import com.koilm.app.llm.PromptTemplate
import com.koilm.app.notification.NotificationHelper
import java.time.LocalTime
import kotlin.random.Random

/**
 * アプリが閉じられていても裏で動作し、キャラからの自発的な話しかけを生成する CoroutineWorker。
 *
 * 処理の骨子:
 *   1. Room DBから直近の会話履歴(直近セッション)を取得
 *   2. 現在時刻から「朝/昼/夜」を判定
 *   3. ローカルSLM(LlamaCppEngine, LlmEngineProviderで使い回す)に
 *      「時間帯 + 直近の文脈」を渡して自発的な一言を生成させる
 *   4. 生成結果をDBに保存(会話履歴にも残す) + PendingProactiveMessageとして保留登録
 *   5. Local Notificationを発火
 *   6. 次回の実行をProactiveMessageSchedulerで再予約する
 *
 * 実運用上の注意: GGUFモデルのロード・推論は数百ms〜数秒かかることがあるため、
 * 頻繁な起動や長時間処理が必要な場合はWorkManagerの制約(実行時間の上限)に注意し、
 * 必要に応じて Expedited Work や ForegroundService への切り替えを検討すること。
 */
class AIBackgroundWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val context = applicationContext
            val database = AppDatabase.getInstance(context)
            val dao = database.chatDao()
            val characterDao = database.characterDao()

            // 通知が有効(notificationsEnabled=true)、かつ一度でも会話したことがあるキャラの中から
            // ランダムに1体選ぶ。全キャラに毎回送るのではなく、偏りなく自発的な話しかけが起こるようにする。
            val eligibleCharacters = characterDao.getNotificationEnabled()
                .filter { dao.getTurnCount(it.id) > 0 }
            if (eligibleCharacters.isEmpty()) {
                return Result.success()
            }
            val character = eligibleCharacters[Random.nextInt(eligibleCharacters.size)]
            val sessionId = character.id

            val engine = LlmEngineProvider.getOrLoad(context)
            val recentTurns = dao.getTurns(sessionId).takeLast(10)

            val prompt = buildProactivePrompt(character.systemPrompt, recentTurns)
            val message = engine.generate(prompt, maxNewTokens = 120, temperature = 1.0f).trim()
            if (message.isBlank()) {
                return Result.success()
            }

            dao.insertTurn(
                ConversationTurnEntity(sessionId = sessionId, role = "assistant", content = message),
            )

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
            } // POST_NOTIFICATIONS未許可等で失敗しても、Workerとしては成功扱いにする

            return Result.success()
        } finally {
            // 成功・失敗いずれの場合も次回実行を必ず再予約する(自己再スケジュール)。
            ProactiveMessageScheduler.scheduleNext(applicationContext)
        }
    }

    /**
     * 学習時(train.py/prompt_template.py)と同一の<|system|>/<|user|>/<|assistant|>タグ形式で
     * プロンプトを組み立てる。フォアグラウンドのChatRepositoryと同じテンプレートを使うことで、
     * 自発メッセージもLoRAで学習した口調・地の文表現を再現できるようにしている。
     */
    private fun buildProactivePrompt(personaPrompt: String, recentTurns: List<ConversationTurnEntity>): String {
        val timeOfDay = currentTimeOfDayLabel()
        val systemContent = buildString {
            appendLine(personaPrompt)
            appendLine()
            appendLine("今は「$timeOfDay」の時間帯です。ユーザーはまだ何も話しかけていません。")
            appendLine("以下の直近の会話の文脈を踏まえて、あなたからユーザーに自発的に話しかける短い一言を生成してください。")
            append("地の文と台詞を含め、2〜3文程度にまとめてください。")
        }

        val turns = listOf(PromptTemplate.Turn(role = "system", content = systemContent)) +
            recentTurns.map {
                PromptTemplate.Turn(role = if (it.role == "assistant") "assistant" else "user", content = it.content)
            }
        return PromptTemplate.build(turns, addGenerationPrompt = true)
    }

    private fun currentTimeOfDayLabel(): String {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..10 -> "朝"
            in 11..16 -> "昼"
            in 17..22 -> "夜"
            else -> "深夜"
        }
    }
}
