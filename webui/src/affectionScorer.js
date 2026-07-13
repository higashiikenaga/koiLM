// Android版 memory/AffectionScorer.kt と同じ語彙・ロジックをJSで再現したもの。
const POSITIVE_WORDS = [
  "好き", "大好き", "愛し", "嬉し", "楽しい", "会いたい", "幸せ", "ありがとう", "可愛い", "抱きしめ", "♡", "❤",
];
const NEGATIVE_WORDS = ["嫌い", "うざい", "面倒", "疲れた", "無理", "怒", "冷た", "興味ない"];
const MAX_DELTA_PER_REPLY = 3;

function score(replyText) {
  const positiveHits = POSITIVE_WORDS.filter((w) => replyText.includes(w)).length;
  const negativeHits = NEGATIVE_WORDS.filter((w) => replyText.includes(w)).length;
  const delta = positiveHits - negativeHits;
  return Math.max(-MAX_DELTA_PER_REPLY, Math.min(MAX_DELTA_PER_REPLY, delta));
}

module.exports = { score };
