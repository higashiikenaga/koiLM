package com.koilm.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LlamaBridge(JNI)を使いやすく包んだKotlin層。
 * サーバー通信は一切行わず、端末内のGGUFモデルファイルのみを参照する。
 */
class LlamaCppEngine(
    private val nCtx: Int = 4096,
    private val nThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
) {
    private var handle: Long = 0
    private var loaded = false

    suspend fun load(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        require(modelFile.exists()) { "モデルファイルが見つかりません: ${modelFile.absolutePath}" }
        handle = LlamaBridge.loadModel(modelFile.absolutePath, nCtx, nThreads)
        loaded = handle != 0L
        loaded
    }

    /**
     * System Prompt(キャラ設定+メモリ)と直近履歴を組み立てたプロンプトから応答を生成する。
     * ChatRepository / ContextMemoryManager から呼ばれる想定。
     */
    suspend fun generate(
        prompt: String,
        maxNewTokens: Int = 256,
        temperature: Float = 0.9f,
        topP: Float = 0.9f,
    ): String = withContext(Dispatchers.Default) {
        check(loaded) { "モデルが未ロードです。先に load() を呼んでください" }
        LlamaBridge.generate(handle, prompt, maxNewTokens, temperature, topP)
    }

    /**
     * 長期記憶の要約生成にも同じエンジンを使い回す
     * (要約専用の別モデルを持たず、通信も発生させないため)。
     */
    suspend fun summarize(logText: String, existingSummary: String): String {
        val prompt = buildString {
            appendLine("以下はキャラクターとユーザーの過去の会話ログです。")
            appendLine("今後の会話で覚えておくべき重要な事実・感情の変化・約束事だけを、")
            appendLine("簡潔な日本語の箇条書きで200字以内に要約してください。")
            appendLine()
            appendLine("[既存の長期記憶]")
            appendLine(existingSummary)
            appendLine()
            appendLine("[新しい会話ログ]")
            append(logText)
        }
        return generate(prompt, maxNewTokens = 200, temperature = 0.4f, topP = 0.9f)
    }

    fun close() {
        if (loaded) {
            LlamaBridge.freeModel(handle)
            loaded = false
        }
    }
}
