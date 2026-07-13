package com.koilm.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 会話の生ログ(1ターン分)。完全に端末内のSQLiteにのみ保存される。 */
@Entity(tableName = "conversation_turns")
data class ConversationTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** userターンが「既読」になった時刻(=キャラ側の返信生成が開始された時刻)。null=未読。 */
    val readAt: Long? = null,
)

/** キャラ×セッションごとの長期記憶サマリ(再帰的に圧縮更新される)。 */
@Entity(tableName = "memory_summaries")
data class MemorySummaryEntity(
    @PrimaryKey val sessionId: String,
    val summaryText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * ユーザーの好み・設定(呼び方、好きな話題、地雷ワード等)のプロフィールメモリ。
 * sessionId(=キャラクターid)ごとに分離して保存する。これが無いと、
 * 別のキャラクターとの会話で得た「呼び方」等が他のキャラクターにも漏れてしまう。
 */
@Entity(tableName = "user_profile_memory", primaryKeys = ["sessionId", "key"])
data class ProfileMemoryEntity(
    val sessionId: String,
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
