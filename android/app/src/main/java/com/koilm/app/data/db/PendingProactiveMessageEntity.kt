package com.koilm.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AIBackgroundWorkerが生成した「自発的な話しかけメッセージ」の受け渡し用エントリ。
 *
 * 生成 → 通知発火 の時点ではアプリがフォアグラウンドにいないため即座に画面表示できない。
 * ユーザーが通知をタップしてアプリを開いた瞬間に、この未消費(consumed=false)のレコードを
 * 取り出して該当キャラのチャット画面を開くことで「裏で生成 → 通知 → 開いた瞬間に表示」を実現する。
 */
@Entity(tableName = "pending_proactive_messages")
data class PendingProactiveMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val consumed: Boolean = false,
)
