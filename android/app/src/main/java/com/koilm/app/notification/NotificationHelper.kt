package com.koilm.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.koilm.app.MainActivity

/**
 * キャラからの自発的な話しかけをLocal Notificationとして表示するためのヘルパー。
 * サーバー経由のPush通知(FCM等)は使わず、端末内で完結するLocal Notificationのみを使用する。
 */
object NotificationHelper {
    const val CHANNEL_ID = "koilm_proactive_messages"
    const val EXTRA_PENDING_MESSAGE_ID = "extra_pending_message_id"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "キャラからのメッセージ",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "キャラクターからの自発的な話しかけを通知します"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * @param pendingMessageId PendingProactiveMessageEntityのID。
     *   タップ時にMainActivityへ渡し、該当メッセージの音声を再生するために使う。
     */
    fun showProactiveMessage(context: Context, characterName: String, text: String, pendingMessageId: Long) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PENDING_MESSAGE_ID, pendingMessageId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            pendingMessageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // TODO: 専用アイコンに差し替え
            .setContentTitle(characterName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // POST_NOTIFICATIONS (API33+) が未許可の場合は例外になるため呼び出し元でハンドリングする。
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
