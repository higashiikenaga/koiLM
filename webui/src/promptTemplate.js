// sarashina2.2-1b-instruct-v0.1 の chat_template (output/koilm-lora-1b/chat_template.jinja として
// 学習後に保存されたもの)と完全に一致させたテンプレート。
// 実際の学習フォーマットは「タグ + 本文 + eos_token」の直接連結であり、タグと本文の間や
// ターンの区切りに改行は入らない(以前は誤って改行区切りにしており、モデルが学習時と異なる
// 入力分布に晒され、英語混じりの出力など劣化の原因になっていた。Android版と同時に修正)。
const SYSTEM_TAG = "<|system|>";
const USER_TAG = "<|user|>";
const ASSISTANT_TAG = "<|assistant|>";
const EOS_TOKEN = "</s>";

function tagFor(role) {
  if (role === "system") return SYSTEM_TAG;
  if (role === "user") return USER_TAG;
  if (role === "assistant") return ASSISTANT_TAG;
  return null;
}

/** turns: [{role, content}] -> 学習時と同一フォーマットのプロンプト文字列 */
function build(turns, addGenerationPrompt = true) {
  let out = "";
  for (const turn of turns) {
    const tag = tagFor(turn.role);
    if (!tag) continue;
    out += `${tag}${turn.content}${EOS_TOKEN}`;
  }
  if (addGenerationPrompt) out += ASSISTANT_TAG;
  return out;
}

const STOP_SEQUENCES = [USER_TAG, SYSTEM_TAG, ASSISTANT_TAG, EOS_TOKEN];

module.exports = { build, STOP_SEQUENCES };
