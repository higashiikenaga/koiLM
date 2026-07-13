const fs = require("fs");
const path = require("path");
const { app } = require("electron");

// Room(SQLite)の代わりに、単一ユーザー・低データ量のデスクトップアプリという前提で
// 単純なJSONファイルストアを採用(better-sqlite3等のネイティブモジュール・
// electron-rebuildの手間を避けるための「低コスト」な選択)。
function storePath() {
  return path.join(app.getPath("userData"), "koilm-data.json");
}

function defaultData() {
  return {
    settings: {
      ageVerified: false,
      contentLevel: "NORMAL",
      romanticTarget: "BOTH",
      romanticTargetSelected: false,
      affectionSystemEnabled: true,
      busyHoursStart: 9,
      busyHoursEnd: 18,
      language: "ja", // "ja" | "en" (itch.io海外向け配布用)
      modelSize: "1b", // "1b" | "7b" (日本語モード限定。7bは旧Ninja-RPベース、高品質・低速)
    },
    characters: [], // { id, name, systemPrompt, notificationsEnabled, affectionScore, createdAt }
    turns: {}, // sessionId -> [{ id, role, content, timestamp, readAt }]
    memorySummaries: {}, // sessionId -> { summaryText, updatedAt }
    profileMemory: {}, // sessionId -> { [key]: { value, updatedAt } }
    nextTurnId: 1,
  };
}

let cache = null;

function load() {
  if (cache) return cache;
  const p = storePath();
  if (fs.existsSync(p)) {
    try {
      cache = { ...defaultData(), ...JSON.parse(fs.readFileSync(p, "utf-8")) };
    } catch {
      cache = defaultData();
    }
  } else {
    cache = defaultData();
  }
  return cache;
}

function save() {
  if (!cache) return;
  fs.writeFileSync(storePath(), JSON.stringify(cache, null, 2), "utf-8");
}

module.exports = { load, save };
