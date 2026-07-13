package com.koilm.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.koilm.app.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * AIBackgroundWorkerを「次にいつ起こすか」決めて予約する。
 *
 * WorkManagerのPeriodicWorkRequestは固定間隔(最短15分)しか表現できないため、
 * OneTimeWorkRequestを実行のたびに次回分を自分で積み直す(自己再スケジュール)方式にしている。
 *
 * タイミングは AppPreferences の「静かな時間帯(quiet hours)」と「送信間隔(min/max)」設定に従う。
 * 設定画面(SettingsScreen)からユーザーが自由に変更できる。
 */
object ProactiveMessageScheduler {
    private const val UNIQUE_WORK_NAME = "koilm_ai_background_worker"

    /** アプリ起動時(MainActivity.onCreate等)に一度だけ呼び、初回の予約を行う。非suspendコンテキスト用。 */
    fun scheduleInitial(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { scheduleNext(context) }
    }

    /** AIBackgroundWorker.doWork() の最後、または設定変更時に呼び、次回分を積み直す。 */
    suspend fun scheduleNext(context: Context) {
        val prefs = AppPreferences(context)
        val delay = computeNextDelay(
            quietHoursStart = prefs.quietHoursStart.first(),
            quietHoursEnd = prefs.quietHoursEnd.first(),
            minIntervalHours = prefs.minIntervalHours.first(),
            maxIntervalHours = prefs.maxIntervalHours.first(),
        )
        val request = OneTimeWorkRequestBuilder<AIBackgroundWorker>()
            .setInitialDelay(delay)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * [minIntervalHours, maxIntervalHours] の範囲でランダムな間隔を選び、
     * その結果が「静かな時間帯」に入っていたら、静かな時間帯が終わる時刻まで押し出す。
     */
    internal fun computeNextDelay(
        quietHoursStart: Int,
        quietHoursEnd: Int,
        minIntervalHours: Int,
        maxIntervalHours: Int,
    ): Duration {
        val now = LocalDateTime.now()

        val safeMin = minIntervalHours.coerceAtLeast(1)
        val safeMax = maxIntervalHours.coerceAtLeast(safeMin)
        val randomHours = safeMin + Random.nextInt(safeMax - safeMin + 1)
        val randomMinutes = Random.nextInt(60)

        var candidate = now.plusHours(randomHours.toLong()).plusMinutes(randomMinutes.toLong())
        candidate = pushOutsideQuietHours(candidate, quietHoursStart, quietHoursEnd)

        return Duration.between(now, candidate)
    }

    /** candidate が静かな時間帯(quietStart時〜quietEnd時、日またぎ可)に入っていれば、その終了時刻まで押し出す。 */
    private fun pushOutsideQuietHours(candidate: LocalDateTime, quietStart: Int, quietEnd: Int): LocalDateTime {
        if (quietStart == quietEnd) return candidate // 24時間指定(実質、常に静か)は今回のスコープでは非対応として無視

        val hour = candidate.hour
        val inQuietHours = if (quietStart < quietEnd) {
            hour in quietStart until quietEnd
        } else {
            // 日をまたぐケース(例: 23時〜7時)
            hour >= quietStart || hour < quietEnd
        }
        if (!inQuietHours) return candidate

        var pushed = candidate.toLocalDate().atTime(quietEnd, 0)
        if (!pushed.isAfter(candidate)) {
            pushed = pushed.plusDays(1)
        }
        return pushed
    }
}
