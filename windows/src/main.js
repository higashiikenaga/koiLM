const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");
const fs = require("fs");

const llmEngine = require("./llmEngine");
const imageGen = require("./imageGen");
const store = require("./store");
const chatRepository = require("./chatRepository");
const characterRepository = require("./characterRepository");
const delayedReply = require("./delayedReply");
const contentLevel = require("./contentLevel");
const config = require("./config");

let mainWindow;

function charactersDir() {
  const dir = path.join(app.getPath("userData"), "characters");
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  mainWindow.loadFile(path.join(__dirname, "index.html"));
}

app.whenReady().then(() => {
  createWindow();
  llmEngine.start(config.resolveModelPath(store.load().settings)).catch((err) => {
    console.error("[llmEngine] 起動失敗:", err);
  });

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  llmEngine.stop();
  if (process.platform !== "darwin") app.quit();
});

// --- 設定 (年齢確認/表現レベル/恋愛対象/好感度システム) ---

ipcMain.handle("settings:get", () => store.load().settings);

ipcMain.handle("settings:setAgeVerified", (_event, isAdult) => {
  const data = store.load();
  data.settings.ageVerified = isAdult;
  if (!isAdult) data.settings.contentLevel = "NORMAL";
  store.save();
  return data.settings;
});

ipcMain.handle("settings:setContentLevel", (_event, level) => {
  const data = store.load();
  const safeLevel =
    contentLevel.requiresAgeVerification(level) && !data.settings.ageVerified ? "SFW" : level;
  data.settings.contentLevel = safeLevel;
  store.save();
  return data.settings;
});

ipcMain.handle("settings:setRomanticTarget", (_event, target) => {
  const data = store.load();
  data.settings.romanticTarget = target;
  data.settings.romanticTargetSelected = true;
  store.save();
  return data.settings;
});

ipcMain.handle("settings:setAffectionSystemEnabled", (_event, enabled) => {
  const data = store.load();
  data.settings.affectionSystemEnabled = enabled;
  store.save();
  return data.settings;
});

ipcMain.handle("settings:setBusyHours", (_event, { start, end }) => {
  const data = store.load();
  data.settings.busyHoursStart = Math.min(23, Math.max(0, start));
  data.settings.busyHoursEnd = Math.min(23, Math.max(0, end));
  store.save();
  return data.settings;
});

/** 言語切り替え(itch.io向け英語対応)。モデルが異なるためllama-serverを再起動する。 */
ipcMain.handle("settings:setLanguage", async (_event, language) => {
  const data = store.load();
  data.settings.language = language;
  store.save();
  await llmEngine.switchModel(config.resolveModelPath(data.settings)).catch((err) => {
    console.error("[llmEngine] モデル切り替え失敗:", err);
  });
  return data.settings;
});

/** 日本語モードのモデルサイズ切り替え(1B=軽量・高速 / 7B=高品質・低速)。 */
ipcMain.handle("settings:setModelSize", async (_event, modelSize) => {
  const data = store.load();
  data.settings.modelSize = modelSize;
  store.save();
  await llmEngine.switchModel(config.resolveModelPath(data.settings)).catch((err) => {
    console.error("[llmEngine] モデル切り替え失敗:", err);
  });
  return data.settings;
});

// --- キャラクター管理 ---

ipcMain.handle("character:list", () => characterRepository.listCharacters());

ipcMain.handle("character:create", async (_event, { name, systemPrompt, appearance }) => {
  const character = characterRepository.createCharacter(name, systemPrompt);
  const dir = path.join(charactersDir(), character.id);
  fs.mkdirSync(dir, { recursive: true });
  const portraitPath = path.join(dir, "portrait.png");
  await imageGen.generatePortrait({ appearance, outputPath: portraitPath }).catch((err) => {
    console.error("[imageGen] 立ち絵生成失敗:", err);
    return null;
  });
  return { character, portraitPath };
});

// キャラクター削除("別れる")
ipcMain.handle("character:delete", (_event, id) => {
  characterRepository.deleteCharacter(id);
  return true;
});

// --- チャット ---

ipcMain.handle("chat:history", (_event, sessionId) => {
  const data = store.load();
  return data.turns[sessionId] || [];
});

ipcMain.handle("chat:send", async (_event, { sessionId, personaPrompt, message }) => {
  const level = store.load().settings.contentLevel;
  const reply = await chatRepository.sendMessage(sessionId, personaPrompt, message, level);
  return { reply };
});

/** 「タイミング」モード: 送信だけ即座に行い、返信は最大15分以内のランダムなタイミングで届く。 */
ipcMain.handle("chat:sendTimed", async (_event, { sessionId, personaPrompt, message }) => {
  await chatRepository.recordUserMessageOnly(sessionId, message);
  const level = store.load().settings.contentLevel;
  delayedReply.schedule(sessionId, personaPrompt, level, (reply) => {
    mainWindow?.webContents.send("chat:timedReplyReady", { sessionId, reply });
  });
  return true;
});
