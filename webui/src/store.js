const fs = require("fs");
const path = require("path");
const os = require("os");

// windows/src/store.js と同じ構造だが、Electronに依存せず素のNode.jsだけで動く
// (WebUI版はブラウザから操作するサーバーなので、Electronのapp.getPath相当が無い)。
const DATA_DIR = path.join(os.homedir(), ".koilm-webui");

function storePath() {
  return path.join(DATA_DIR, "koilm-data.json");
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
      language: "ja", // "ja" | "en"
      modelSize: "1b", // "1b" | "7b" (日本語モード限定)
    },
    characters: [],
    turns: {},
    memorySummaries: {},
    profileMemory: {},
    nextTurnId: 1,
  };
}

let cache = null;

function load() {
  if (cache) return cache;
  fs.mkdirSync(DATA_DIR, { recursive: true });
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
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(storePath(), JSON.stringify(cache, null, 2), "utf-8");
}

function portraitDir() {
  const dir = path.join(DATA_DIR, "characters");
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

module.exports = { load, save, portraitDir };
