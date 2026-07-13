package com.koilm.app.memory

/**
 * AIの返信内容から好感度スコアの増減を見積もる、キーワードベースの軽量スコアラー。
 * サーバー通信・追加モデルなしで完結させるため、単純な語彙マッチングのみで判定する。
 * (会話内容そのものには影響を与えず、既読〜返信間隔などの演出にのみ使う)
 */
object AffectionScorer {
    private val POSITIVE_WORDS = listOf(
        "好き", "大好き", "愛し", "嬉し", "楽しい", "会いたい", "幸せ", "ありがとう", "可愛い", "抱きしめ", "♡", "❤",
    )
    private val NEGATIVE_WORDS = listOf(
        "嫌い", "うざい", "面倒", "疲れた", "無理", "怒", "冷た", "興味ない",
    )

    private const val MAX_DELTA_PER_REPLY = 3

    /** 1回の返信あたりの好感度増減量(-MAX_DELTA_PER_REPLY 〜 +MAX_DELTA_PER_REPLY)。 */
    fun score(replyText: String): Int {
        val positiveHits = POSITIVE_WORDS.count { replyText.contains(it) }
        val negativeHits = NEGATIVE_WORDS.count { replyText.contains(it) }
        val delta = positiveHits - negativeHits
        return delta.coerceIn(-MAX_DELTA_PER_REPLY, MAX_DELTA_PER_REPLY)
    }
}
