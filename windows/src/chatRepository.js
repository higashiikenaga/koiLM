const store = require("./store");
const llmEngine = require("./llmEngine");
const memoryManager = require("./memoryManager");
const promptTemplate = require("./promptTemplate");
const contentLevel = require("./contentLevel");
const romanticTarget = require("./romanticTarget");
const affectionScorer = require("./affectionScorer");

// Android版 data/ChatRepository.kt と同じロジックをJSで再現したもの。

/** 「即返信」: ユーザー発言を記録し、その場で返信を生成する。 */
async function sendMessage(sessionId, personaPrompt, userInput, requestedLevel) {
  await memoryManager.addTurn(sessionId, "user", userInput);
  return generateReply(sessionId, personaPrompt, requestedLevel);
}

async function recordUserMessageOnly(sessionId, userInput) {
  await memoryManager.addTurn(sessionId, "user", userInput);
}

/** 「タイミング」モード等、既に記録済みのユーザー発言に対して返信だけを生成する。 */
async function generateReply(sessionId, personaPrompt, requestedLevel) {
  const data = store.load();

  // 防御的チェック: 年齢確認未完了ならここで必ずSFWまで強制降格する。
  const ageVerified = data.settings.ageVerified;
  const effectiveLevel =
    contentLevel.requiresAgeVerification(requestedLevel) && !ageVerified ? "SFW" : requestedLevel;

  const target = data.settings.romanticTarget;
  const language = data.settings.language || "ja";
  const basePrompt = memoryManager.buildSystemPrompt(sessionId, personaPrompt);
  // 日本語モードはsarashina2.2(バイリンガルだが日本語RPデータのみでLoRA学習)を使うため、
  // 明示しないと英語が混ざることがある。英語モードはQwen2.5-1.5B(LoRA無し)を使う。
  const languageDirective =
    language === "en"
      ? "[LANGUAGE] Always respond in English only. Do not mix in Japanese or other languages."
      : "【応答言語】必ず日本語のみで応答してください。英語や他言語を混ぜないでください。";
  const systemPrompt =
    `${basePrompt}\n\n${contentLevel.directiveFor(effectiveLevel)}\n\n${romanticTarget.directiveFor(target)}\n\n${languageDirective}`;

  const turns = memoryManager.turnsFor(sessionId);
  const recentTurns = turns.map((t) => ({
    role: t.role === "assistant" ? "assistant" : "user",
    content: t.content,
  }));
  const promptTurns = [{ role: "system", content: systemPrompt }, ...recentTurns];
  const fullPrompt = promptTemplate.build(promptTurns, true);

  const affectionEnabled = data.settings.affectionSystemEnabled;
  if (affectionEnabled) {
    // 「既読」は生成開始のタイミングで付ける(=返信が届くより前に相手が読んだ体験を演出する)。
    for (let i = turns.length - 1; i >= 0; i--) {
      if (turns[i].role === "user" && turns[i].readAt == null) {
        turns[i].readAt = Date.now();
        store.save();
        break;
      }
    }
  }

  let reply = await llmEngine.completeRaw(fullPrompt);
  if (language !== "en" && containsEnglishWord(reply)) {
    // 小型モデルゆえ稀に英単語が紛れ込むため、検知した場合は1回だけ再生成する
    // (英語モード自体では当然英語が正常なので対象外)。
    reply = await llmEngine.completeRaw(fullPrompt);
  }
  await memoryManager.addTurn(sessionId, "assistant", reply);

  if (affectionEnabled && reply) {
    const delta = affectionScorer.score(reply);
    if (delta !== 0) {
      const character = data.characters.find((c) => c.id === sessionId);
      if (character) {
        character.affectionScore = (character.affectionScore || 0) + delta;
        store.save();
      }
    }
  }

  return reply;
}

/** 3文字以上連続する英字(≒英単語)が含まれるかどうか。 */
function containsEnglishWord(text) {
  return /[A-Za-z]{3,}/.test(text);
}

module.exports = { sendMessage, recordUserMessageOnly, generateReply };
