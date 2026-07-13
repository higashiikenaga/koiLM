package com.koilm.app.llm

/**
 * sarashina2.2-1b-instruct-v0.1 の chat_template (学習後に output/koilm-lora-1b/chat_template.jinja
 * として保存されたもの)と完全に一致させたテンプレート。
 *
 * 実際の学習フォーマットは `タグ + 本文 + eos_token` の直接連結であり、タグと本文の間や
 * ターンの区切りに改行は入らない(以前は誤って改行区切りにしていたため、モデルが
 * 学習時と異なる入力分布に晒され、英語混じりの出力など劣化の原因になっていた)。
 */
object PromptTemplate {
    private const val SYSTEM_TAG = "<|system|>"
    private const val USER_TAG = "<|user|>"
    private const val ASSISTANT_TAG = "<|assistant|>"
    private const val EOS_TOKEN = "</s>"

    data class Turn(val role: String, val content: String) // role: "system" | "user" | "assistant"

    /** 会話ターン列から学習時と同一フォーマットのプロンプト文字列を組み立てる。 */
    fun build(turns: List<Turn>, addGenerationPrompt: Boolean = true): String = buildString {
        for (turn in turns) {
            val tag = when (turn.role) {
                "system" -> SYSTEM_TAG
                "user" -> USER_TAG
                "assistant" -> ASSISTANT_TAG
                else -> continue
            }
            append(tag).append(turn.content).append(EOS_TOKEN)
        }
        if (addGenerationPrompt) {
            append(ASSISTANT_TAG)
        }
    }
}
