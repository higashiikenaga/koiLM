const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("koilm", {
  // 設定
  getSettings: () => ipcRenderer.invoke("settings:get"),
  getLlmStatus: () => ipcRenderer.invoke("llm:status"),
  setAgeVerified: (isAdult) => ipcRenderer.invoke("settings:setAgeVerified", isAdult),
  setContentLevel: (level) => ipcRenderer.invoke("settings:setContentLevel", level),
  setRomanticTarget: (target) => ipcRenderer.invoke("settings:setRomanticTarget", target),
  setAffectionSystemEnabled: (enabled) =>
    ipcRenderer.invoke("settings:setAffectionSystemEnabled", enabled),
  setBusyHours: (start, end) => ipcRenderer.invoke("settings:setBusyHours", { start, end }),
  setLanguage: (language) => ipcRenderer.invoke("settings:setLanguage", language),
  setModelSize: (modelSize) => ipcRenderer.invoke("settings:setModelSize", modelSize),

  // キャラクター管理
  listCharacters: () => ipcRenderer.invoke("character:list"),
  createCharacter: (name, systemPrompt, appearance, imageNsfw) =>
    ipcRenderer.invoke("character:create", { name, systemPrompt, appearance, imageNsfw }),
  deleteCharacter: (id) => ipcRenderer.invoke("character:delete", id),
  setCharacterImageNsfw: (id, enabled) => ipcRenderer.invoke("character:setImageNsfw", { id, enabled }),

  // チャット
  getHistory: (sessionId) => ipcRenderer.invoke("chat:history", sessionId),
  sendMessage: (sessionId, personaPrompt, message) =>
    ipcRenderer.invoke("chat:send", { sessionId, personaPrompt, message }),
  sendMessageTimed: (sessionId, personaPrompt, message) =>
    ipcRenderer.invoke("chat:sendTimed", { sessionId, personaPrompt, message }),
  onTimedReplyReady: (callback) =>
    ipcRenderer.on("chat:timedReplyReady", (_event, payload) => callback(payload)),
});
