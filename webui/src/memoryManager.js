const store = require("./store");
const llmEngine = require("./llmEngine");

// Android版 memory/ContextMemoryManager.kt と同じロジックをJSで再現したもの。
const SUMMARIZE_THRESHOLD_TURNS = 20;
const KEEP_RECENT_TURNS = 6;
const MAX_CONTEXT_CHARS = 1500;

const PROFILE_PATTERNS = {
  呼び方: /(私|僕|俺)のことは(.+?)って呼んで/,
  好きな話題: /(.+?)(の話|について話す)のが好き/,
  地雷: /(.+?)(の話|の話題)はやめて|苦手/,
};

function turnsFor(sessionId) {
  const data = store.load();
  if (!data.turns[sessionId]) data.turns[sessionId] = [];
  return data.turns[sessionId];
}

/** ユーザー/アシスタントの1ターンを記録し、必要なら長期記憶へ圧縮する。 */
async function addTurn(sessionId, role, content) {
  const data = store.load();
  const turns = turnsFor(sessionId);
  turns.push({ id: data.nextTurnId++, role, content, timestamp: Date.now(), readAt: null });
  store.save();

  extractProfile(sessionId, content);

  if (turns.length > SUMMARIZE_THRESHOLD_TURNS) {
    await compress(sessionId);
  }
}

/** シーン画像を1件、会話ログに記録する(LLMの応答文脈には含めない。チャットUI表示専用)。 */
function addImageTurn(sessionId, imageUrl) {
  const data = store.load();
  const turns = turnsFor(sessionId);
  turns.push({ id: data.nextTurnId++, role: "assistant", content: "", imageUrl, timestamp: Date.now(), readAt: null });
  store.save();
}

function extractProfile(sessionId, content) {
  const data = store.load();
  if (!data.profileMemory[sessionId]) data.profileMemory[sessionId] = {};
  for (const [key, pattern] of Object.entries(PROFILE_PATTERNS)) {
    const match = pattern.exec(content);
    if (!match) continue;
    data.profileMemory[sessionId][key] = { value: match[0].trim(), updatedAt: Date.now() };
  }
  store.save();
}

async function compress(sessionId) {
  const data = store.load();
  const turns = turnsFor(sessionId);
  if (turns.length <= KEEP_RECENT_TURNS) return;

  const oldTurns = turns.slice(0, turns.length - KEEP_RECENT_TURNS);
  const recentTurns = turns.slice(turns.length - KEEP_RECENT_TURNS);

  const oldLog = oldTurns.map((t) => `${t.role}: ${t.content}`).join("\n");
  const existingSummary = (data.memorySummaries[sessionId] || {}).summaryText || "";

  const newSummary = (await llmEngine.summarize(oldLog, existingSummary)).trim();
  data.memorySummaries[sessionId] = { summaryText: newSummary, updatedAt: Date.now() };
  data.turns[sessionId] = recentTurns;
  store.save();
}

/** キャラ設定 + プロフィールメモリ + 長期記憶サマリ を合成してSystem Promptを構築する。 */
function buildSystemPrompt(sessionId, basePersonaPrompt) {
  const data = store.load();
  const profile = data.profileMemory[sessionId] || {};
  const profileEntries = Object.entries(profile);
  const profileText = profileEntries.length
    ? "【ユーザー設定】\n" + profileEntries.map(([k, v]) => `- ${k}: ${v.value}`).join("\n")
    : "";

  const summary = (data.memorySummaries[sessionId] || {}).summaryText || "";
  let summaryText = summary.trim() ? `【これまでの記憶】\n${summary}` : "";

  let combined = [basePersonaPrompt, profileText, summaryText].filter(Boolean).join("\n\n");

  if (combined.length > MAX_CONTEXT_CHARS) {
    const budget = MAX_CONTEXT_CHARS - basePersonaPrompt.length - profileText.length - 20;
    summaryText = budget > 0 ? summaryText.slice(0, budget) + "…" : "";
    combined = [basePersonaPrompt, profileText, summaryText].filter(Boolean).join("\n\n");
  }

  return combined;
}

module.exports = { addTurn, addImageTurn, buildSystemPrompt, turnsFor };
