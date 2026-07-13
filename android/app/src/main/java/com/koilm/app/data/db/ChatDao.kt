package com.koilm.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChatDao {
    @Insert
    suspend fun insertTurn(turn: ConversationTurnEntity)

    @Query("SELECT * FROM conversation_turns WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getTurns(sessionId: String): List<ConversationTurnEntity>

    @Query("SELECT COUNT(*) FROM conversation_turns WHERE sessionId = :sessionId")
    suspend fun getTurnCount(sessionId: String): Int

    @Query("UPDATE conversation_turns SET readAt = :readAt WHERE id = :id")
    suspend fun markRead(id: Long, readAt: Long)

    @Query("DELETE FROM conversation_turns WHERE sessionId = :sessionId AND id NOT IN (:keepIds)")
    suspend fun deleteTurnsExcept(sessionId: String, keepIds: List<Long>)

    @Upsert
    suspend fun upsertSummary(summary: MemorySummaryEntity)

    @Query("SELECT * FROM memory_summaries WHERE sessionId = :sessionId")
    suspend fun getSummary(sessionId: String): MemorySummaryEntity?

    @Upsert
    suspend fun upsertProfile(profile: ProfileMemoryEntity)

    @Query("SELECT * FROM user_profile_memory WHERE sessionId = :sessionId")
    suspend fun getAllProfile(sessionId: String): List<ProfileMemoryEntity>

    @Insert
    suspend fun insertPendingProactiveMessage(message: PendingProactiveMessageEntity): Long

    @Query("SELECT * FROM pending_proactive_messages WHERE consumed = 0 ORDER BY id DESC LIMIT 1")
    suspend fun getLatestUnconsumedProactiveMessage(): PendingProactiveMessageEntity?

    @Query("UPDATE pending_proactive_messages SET consumed = 1 WHERE id = :id")
    suspend fun markProactiveMessageConsumed(id: Long)

    // --- キャラクター削除("別れる")時のクリーンアップ用 ---

    @Query("DELETE FROM conversation_turns WHERE sessionId = :sessionId")
    suspend fun deleteAllTurns(sessionId: String)

    @Query("DELETE FROM memory_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummary(sessionId: String)

    @Query("DELETE FROM user_profile_memory WHERE sessionId = :sessionId")
    suspend fun deleteAllProfile(sessionId: String)

    @Query("DELETE FROM pending_proactive_messages WHERE sessionId = :sessionId")
    suspend fun deleteAllPendingProactiveMessages(sessionId: String)
}
