const express = require("express");
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

const config = require("./src/config");
const store = require("./src/store");
const llmEngine = require("./src/llmEngine");
const chatRepository = require("./src/chatRepository");
const characterRepository = require("./src/characterRepository");
const delayedReply = require("./src/delayedReply");
const contentLevel = require("./src/contentLevel");
const imageGen = require("./src/imageGen");

const app = express();
app.use(express.json());

// --- 簡易PIN認証 ---
// 同一LAN内の他の端末からもアクセスできてしまうため、config.pin(環境変数 KOILM_PIN)が
// 設定されている場合のみ、Cookie不要な素朴なBearerトークン方式で保護する。
// (本格的なセッション管理は「低コスト」の範囲を超えるため見送り。あくまで家庭内LAN用途)
const sessions = new Set();

function requireAuth(req, res, next) {
  if (!config.pin) return next(); // PIN未設定なら無認証(自己責任)
  const token = req.headers["x-koilm-token"];
  if (token && sessions.has(token)) return next();
  res.status(401).json({ error: "unauthorized" });
}

app.post("/api/login", (req, res) => {
  if (!config.pin) return res.json({ token: null });
  if (req.body?.pin !== config.pin) return res.status(401).json({ error: "PINが違います" });
  const token = crypto.randomBytes(16).toString("hex");
  sessions.add(token);
  res.json({ token });
});

app.get("/api/needs-pin", (_req, res) => res.json({ needsPin: !!config.pin }));

app.use("/api", (req, res, next) => {
  if (req.path === "/login" || req.path === "/needs-pin") return next();
  requireAuth(req, res, next);
});

app.use(express.static(path.join(__dirname, "public")));

// --- 設定 ---
app.get("/api/settings", (_req, res) => res.json(store.load().settings));

app.post("/api/settings/age-verified", (req, res) => {
  const data = store.load();
  data.settings.ageVerified = !!req.body.isAdult;
  if (!req.body.isAdult) data.settings.contentLevel = "NORMAL";
  store.save();
  res.json(data.settings);
});

app.post("/api/settings/content-level", (req, res) => {
  const data = store.load();
  const level = req.body.level;
  const safeLevel =
    contentLevel.requiresAgeVerification(level) && !data.settings.ageVerified ? "SFW" : level;
  data.settings.contentLevel = safeLevel;
  store.save();
  res.json(data.settings);
});

app.post("/api/settings/romantic-target", (req, res) => {
  const data = store.load();
  data.settings.romanticTarget = req.body.target;
  data.settings.romanticTargetSelected = true;
  store.save();
  res.json(data.settings);
});

app.post("/api/settings/affection-system", (req, res) => {
  const data = store.load();
  data.settings.affectionSystemEnabled = !!req.body.enabled;
  store.save();
  res.json(data.settings);
});

app.post("/api/settings/busy-hours", (req, res) => {
  const data = store.load();
  data.settings.busyHoursStart = Math.min(23, Math.max(0, req.body.start));
  data.settings.busyHoursEnd = Math.min(23, Math.max(0, req.body.end));
  store.save();
  res.json(data.settings);
});

/** 言語切り替え(itch.io向け英語対応)。モデルが異なるためllama-serverを再起動する。 */
app.post("/api/settings/language", async (req, res) => {
  const data = store.load();
  data.settings.language = req.body.language;
  store.save();
  await llmEngine.switchModel(config.resolveModelPath(data.settings)).catch((err) => {
    console.error("[llmEngine] モデル切り替え失敗:", err);
  });
  res.json(data.settings);
});

/** 日本語モードのモデルサイズ切り替え(1B=軽量・高速 / 7B=高品質・低速)。 */
app.post("/api/settings/model-size", async (req, res) => {
  const data = store.load();
  data.settings.modelSize = req.body.modelSize;
  store.save();
  await llmEngine.switchModel(config.resolveModelPath(data.settings)).catch((err) => {
    console.error("[llmEngine] モデル切り替え失敗:", err);
  });
  res.json(data.settings);
});

// --- キャラクター管理 ---
app.get("/api/characters", (_req, res) => res.json(characterRepository.listCharacters()));

app.post("/api/characters", async (req, res) => {
  const { name, systemPrompt, appearance } = req.body;
  const character = characterRepository.createCharacter(name, systemPrompt);
  const portraitPath = path.join(store.portraitDir(), `${character.id}.png`);
  await imageGen.generatePortrait({ appearance, outputPath: portraitPath }).catch((err) => {
    console.error("[imageGen] 立ち絵生成失敗:", err);
    return null;
  });
  res.json({ character, hasPortrait: fs.existsSync(portraitPath) });
});

app.delete("/api/characters/:id", (req, res) => {
  characterRepository.deleteCharacter(req.params.id);
  res.json({ ok: true });
});

app.get("/api/characters/:id/portrait", (req, res) => {
  const portraitPath = path.join(store.portraitDir(), `${req.params.id}.png`);
  if (!fs.existsSync(portraitPath)) return res.status(404).end();
  res.sendFile(portraitPath);
});

// --- チャット ---
app.get("/api/chat/:sessionId/history", (req, res) => {
  const data = store.load();
  res.json(data.turns[req.params.sessionId] || []);
});

app.post("/api/chat/:sessionId/send", async (req, res) => {
  const { personaPrompt, message } = req.body;
  const level = store.load().settings.contentLevel;
  try {
    const reply = await chatRepository.sendMessage(req.params.sessionId, personaPrompt, message, level);
    res.json({ reply });
  } catch (err) {
    res.status(500).json({ error: String(err) });
  }
});

// 「タイミング」モード: 即座に返し、実際の返信はポーリングで取りに来てもらう簡易実装。
const pendingTimedReplies = new Map(); // sessionId -> reply text (1件のみ保持)

app.post("/api/chat/:sessionId/send-timed", async (req, res) => {
  const { personaPrompt, message } = req.body;
  const sessionId = req.params.sessionId;
  await chatRepository.recordUserMessageOnly(sessionId, message);
  const level = store.load().settings.contentLevel;
  delayedReply.schedule(sessionId, personaPrompt, level, (reply) => {
    pendingTimedReplies.set(sessionId, reply);
  });
  res.json({ ok: true });
});

app.get("/api/chat/:sessionId/timed-reply", (req, res) => {
  const sessionId = req.params.sessionId;
  const reply = pendingTimedReplies.get(sessionId);
  if (reply !== undefined) pendingTimedReplies.delete(sessionId);
  res.json({ reply: reply ?? null });
});

app.listen(config.webPort, () => {
  console.log(`[koilm-webui] http://localhost:${config.webPort} で起動しました`);
  if (!config.pin) {
    console.warn(
      "[koilm-webui] 警告: PIN未設定です。同じネットワーク上の誰でもアクセスできます。" +
        " KOILM_PIN環境変数で保護することを推奨します。"
    );
  }
});

llmEngine.start(config.resolveModelPath(store.load().settings)).catch((err) =>
  console.error("[llmEngine] 起動失敗:", err)
);

process.on("SIGINT", () => {
  llmEngine.stop();
  process.exit(0);
});
