package com.koilm.app.data

import com.koilm.app.data.db.CharacterDao
import com.koilm.app.data.db.ChatDao
import com.koilm.app.llm.LlamaCppEngine
import com.koilm.app.llm.PromptTemplate
import com.koilm.app.memory.AffectionScorer
import com.koilm.app.memory.ContextMemoryManager
import com.koilm.app.settings.AppPreferences
import com.koilm.app.settings.ContentLevel
import kotlinx.coroutines.flow.first

/**
 * チャット送受信のユースケース層。UI(ViewModel)からはこのクラスのみを呼び出す。
 * DBアクセス・メモリ管理・LLM呼び出しをすべて集約し、通信は一切発生させない。
 */
class ChatRepository(
    private val dao: ChatDao,
    private val characterDao: CharacterDao,
    private val engine: LlamaCppEngine,
    private val memoryManager: ContextMemoryManager,
    private val prefs: AppPreferences,
) {
    /** 「即返信」モード: ユーザー発言を記録し、その場で返信を生成する。 */
    suspend fun sendMessage(
        sessionId: String,
        personaPrompt: String,
        userInput: String,
        requestedLevel: ContentLevel,
    ): String {
        memoryManager.addTurn(sessionId, role = "user", content = userInput)
        return generateReply(sessionId, personaPrompt, requestedLevel)
    }

    /**
     * 「タイミング」モード: 既に記録済みのユーザー発言に対して、返信だけを生成する
     * (DelayedReplyWorkerから呼ばれる。ユーザーターンの追加は行わない)。
     */
    suspend fun generateReply(
        sessionId: String,
        personaPrompt: String,
        requestedLevel: ContentLevel,
    ): String {
        // 防御的チェック: UI側でNSFWを隠していても、年齢確認未完了ならここで必ずSFWまで強制降格する。
        val ageVerified = prefs.isAgeVerified.first()
        val effectiveLevel = if (requestedLevel.requiresAgeVerification && !ageVerified) {
            ContentLevel.SFW
        } else {
            requestedLevel
        }

        val romanticTarget = prefs.romanticTarget.first()
        val basePrompt = memoryManager.buildSystemPrompt(sessionId, personaPrompt)
        // sarashina2.2はバイリンガル(日英)モデルのため、明示しないと英語が混ざることがある。
        val languageDirective = "【応答言語】必ず日本語のみで応答してください。英語や他言語を混ぜないでください。"
        val systemPrompt =
            "$basePrompt\n\n${effectiveLevel.promptDirective()}\n\n${romanticTarget.promptDirective()}\n\n$languageDirective"

        // 学習時(train.py/prompt_template.py)と同一の<|system|>/<|user|>/<|assistant|>タグ形式で
        // プロンプトを組み立てる。ここがズレるとLoRAで学習した口調・地の文表現が再現されない。
        val turns = dao.getTurns(sessionId)
        val recentTurns = turns.map {
            PromptTemplate.Turn(role = if (it.role == "assistant") "assistant" else "user", content = it.content)
        }
        val promptTurns = listOf(PromptTemplate.Turn(role = "system", content = systemPrompt)) + recentTurns
        val fullPrompt = PromptTemplate.build(promptTurns, addGenerationPrompt = true)

        val affectionEnabled = prefs.isAffectionSystemEnabled.first()
        if (affectionEnabled) {
            // 「既読」は生成開始のタイミングで付ける(=返信が届くより前に相手が読んだ体験を演出する)。
            turns.lastOrNull { it.role == "user" && it.readAt == null }?.let {
                dao.markRead(it.id, System.currentTimeMillis())
            }
        }

        var reply = engine.generate(fullPrompt)
        if (containsEnglishWord(reply)) {
            // 小型モデルゆえ稀に英単語が紛れ込むため、検知した場合は1回だけ再生成する
            // (無限リトライはコストがかかるため1回のみ。それでも混ざる場合はそのまま返す)。
            reply = engine.generate(fullPrompt)
        }
        memoryManager.addTurn(sessionId, role = "assistant", content = reply)

        if (affectionEnabled) {
            val delta = AffectionScorer.score(reply)
            if (delta != 0) {
                characterDao.getById(sessionId)?.let { character ->
                    characterDao.update(character.copy(affectionScore = character.affectionScore + delta))
                }
            }
        }

        return reply
    }

    suspend fun recordUserMessageOnly(sessionId: String, userInput: String) {
        memoryManager.addTurn(sessionId, role = "user", content = userInput)
    }

    /** 3文字以上連続する英字(≒英単語)が含まれるかどうか。顔文字の "w" 連打等は対象外。 */
    private fun containsEnglishWord(text: String): Boolean =
        Regex("[A-Za-z]{3,}").containsMatchIn(text)

    suspend fun history(sessionId: String) = dao.getTurns(sessionId)
}
