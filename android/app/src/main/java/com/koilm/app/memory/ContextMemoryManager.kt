package com.koilm.app.memory

import com.koilm.app.data.db.ChatDao
import com.koilm.app.data.db.ConversationTurnEntity
import com.koilm.app.data.db.MemorySummaryEntity
import com.koilm.app.data.db.ProfileMemoryEntity
import com.koilm.app.llm.LlamaCppEngine

/**
 * ローカル・コンテキスト管理マネージャー(Android/Kotlin版)。
 *
 * local_memory/context_manager.py (Pythonモック) と同じロジックを、
 * 永続化層をRoom(SQLite)、要約実行を端末内LlamaCppEngineに置き換えて実装したもの。
 * サーバー通信は一切行わない。
 */
class ContextMemoryManager(
    private val dao: ChatDao,
    private val engine: LlamaCppEngine,
    private val summarizeThresholdTurns: Int = 20,
    private val keepRecentTurns: Int = 6,
    private val maxContextChars: Int = 1500,
) {
    private val profilePatterns = mapOf(
        "呼び方" to Regex("(私|僕|俺)のことは(.+?)って呼んで"),
        "好きな話題" to Regex("(.+?)(の話|について話す)のが好き"),
        "地雷" to Regex("(.+?)(の話|の話題)はやめて|苦手"),
    )

    /** ユーザー/アシスタントの1ターンを記録し、必要なら長期記憶へ圧縮する。 */
    suspend fun addTurn(sessionId: String, role: String, content: String) {
        dao.insertTurn(ConversationTurnEntity(sessionId = sessionId, role = role, content = content))
        extractProfile(sessionId, content)

        val count = dao.getTurnCount(sessionId)
        if (count > summarizeThresholdTurns) {
            compress(sessionId)
        }
    }

    private suspend fun extractProfile(sessionId: String, content: String) {
        for ((key, pattern) in profilePatterns) {
            val match = pattern.find(content) ?: continue
            dao.upsertProfile(ProfileMemoryEntity(sessionId = sessionId, key = key, value = match.value.trim()))
        }
    }

    private suspend fun compress(sessionId: String) {
        val allTurns = dao.getTurns(sessionId)
        if (allTurns.size <= keepRecentTurns) return

        val oldTurns = allTurns.dropLast(keepRecentTurns)
        val recentTurns = allTurns.takeLast(keepRecentTurns)

        val oldLog = oldTurns.joinToString("\n") { "${it.role}: ${it.content}" }
        val existingSummary = dao.getSummary(sessionId)?.summaryText.orEmpty()

        val newSummary = engine.summarize(oldLog, existingSummary).trim()
        dao.upsertSummary(MemorySummaryEntity(sessionId = sessionId, summaryText = newSummary))

        // 古いターンを削除し、直近分だけ生ログとして残す
        dao.deleteTurnsExcept(sessionId, recentTurns.map { it.id })
    }

    /**
     * キャラ設定 + プロフィールメモリ + 長期記憶サマリ を合成してSystem Promptを構築する。
     * トークン(文字数)予算を超える場合はキャラ設定 > プロフィール > サマリ の優先度で削る。
     */
    suspend fun buildSystemPrompt(sessionId: String, basePersonaPrompt: String): String {
        val profile = dao.getAllProfile(sessionId)
        val profileText = if (profile.isNotEmpty()) {
            "【ユーザー設定】\n" + profile.joinToString("\n") { "- ${it.key}: ${it.value}" }
        } else ""

        val summary = dao.getSummary(sessionId)?.summaryText.orEmpty()
        var summaryText = if (summary.isNotBlank()) "【これまでの記憶】\n$summary" else ""

        var combined = listOf(basePersonaPrompt, profileText, summaryText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        if (combined.length > maxContextChars) {
            val budget = maxContextChars - basePersonaPrompt.length - profileText.length - 20
            summaryText = if (budget > 0) summaryText.take(budget) + "…" else ""
            combined = listOf(basePersonaPrompt, profileText, summaryText)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        }

        return combined
    }

    suspend fun recentDialogueText(sessionId: String): String =
        dao.getTurns(sessionId).joinToString("\n") { "${it.role}: ${it.content}" }
}
