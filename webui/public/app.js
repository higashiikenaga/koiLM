const translations = {
  ja: {
    ageQuestion: "年齢確認: 18歳以上ですか?",
    ageYes: "18歳以上です",
    ageNo: "18歳未満です",
    romanticTargetQuestion: "恋愛対象を選んでください",
    targetMale: "男性が好き",
    targetFemale: "女性が好き",
    targetBoth: "どちらも",
    newCharacter: "+ 新規作成",
    msgPlaceholder: "メッセージを入力",
    send: "送信",
    settingsTitle: "設定",
    languageLabel: "言語:",
    modelSizeLabel: "モデルの品質:",
    modelSize1b: "軽量・高速(1B)",
    modelSize7b: "高品質・低速(7B)",
    contentLevelLabel: "表現レベル:",
    levelNormal: "通常表現",
    levelSfw: "SFW",
    levelNsfw: "NSFW(18歳以上)",
    affectionToggle: "好感度システム(既読・返信間隔)",
    busyHoursLabel: "仕事中(既読が付かない時間帯):",
    save: "保存",
    replyModeLabel: "返信モード:",
    replyImmediate: "即返信",
    replyTimed: "タイミング(最大15分以内)",
    close: "閉じる",
    breakUpConfirm: (name) => `本当に${name}と別れますか?`,
    breakUpLabel: "別れる",
    affectionLabel: (n) => `好感度:${n}`,
    charNamePrompt: "キャラクター名",
    systemPromptPrompt: "キャラ設定(口調・性格など)",
    appearancePrompt: "見た目の特徴(英語タグ推奨)",
    errorPrefix: "[エラー]",
    readLabel: "既読",
    pinError: "PINが違います",
  },
  en: {
    ageQuestion: "Age check: Are you 18 or older?",
    ageYes: "I am 18 or older",
    ageNo: "I am under 18",
    romanticTargetQuestion: "Who would you like to romance?",
    targetMale: "Men",
    targetFemale: "Women",
    targetBoth: "Either",
    newCharacter: "+ New Character",
    msgPlaceholder: "Type a message",
    send: "Send",
    settingsTitle: "Settings",
    languageLabel: "Language:",
    contentLevelLabel: "Content level:",
    levelNormal: "Normal",
    levelSfw: "SFW",
    levelNsfw: "NSFW (18+)",
    affectionToggle: "Affection system (read receipts / reply timing)",
    busyHoursLabel: "Busy hours (no read receipts):",
    save: "Save",
    replyModeLabel: "Reply mode:",
    replyImmediate: "Instant",
    replyTimed: "Delayed (up to 15 min)",
    close: "Close",
    breakUpConfirm: (name) => `Are you sure you want to break up with ${name}?`,
    breakUpLabel: "Break up",
    affectionLabel: (n) => `Affection: ${n}`,
    charNamePrompt: "Character name",
    systemPromptPrompt: "Character persona (tone, personality, etc.)",
    appearancePrompt: "Appearance tags (English recommended)",
    errorPrefix: "[Error]",
    readLabel: "Read",
    pinError: "Wrong PIN",
  },
};

let currentLang = "ja";
let token = localStorage.getItem("koilm_token") || null;
let settings = null;
let characters = [];
let currentCharacter = null;
let replyMode = "IMMEDIATE";
let pollTimer = null;

function t(key, ...args) {
  const entry = translations[currentLang]?.[key] ?? translations.ja[key];
  return typeof entry === "function" ? entry(...args) : entry;
}

function applyLanguage(lang) {
  currentLang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.getAttribute("data-i18n"));
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    el.placeholder = t(el.getAttribute("data-i18n-placeholder"));
  });
  document.getElementById("languageSelect").value = lang;
  updateModelSizeVisibility();
}

/** 7Bモデルは日本語版のみ選択肢に出す(英語版はQwen2.5-1.5B固定)。 */
function updateModelSizeVisibility() {
  document.getElementById("modelSizeRow").style.display = currentLang === "en" ? "none" : "block";
}

function apiHeaders() {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["X-Koilm-Token"] = token;
  return headers;
}

async function api(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    ...options,
    headers: { ...apiHeaders(), ...(options.headers || {}) },
  });
  if (res.status === 401) {
    localStorage.removeItem("koilm_token");
    location.reload();
    throw new Error("unauthorized");
  }
  return res.json();
}

async function init() {
  const { needsPin } = await fetch("/api/needs-pin").then((r) => r.json());
  if (needsPin && !token) {
    document.getElementById("pinScreen").classList.remove("hidden");
    return;
  }
  await afterAuth();
}

document.getElementById("pinSubmit").addEventListener("click", async () => {
  const pin = document.getElementById("pinInput").value;
  const res = await fetch("/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pin }),
  });
  if (!res.ok) {
    document.getElementById("pinError").textContent = t("pinError");
    return;
  }
  const data = await res.json();
  token = data.token;
  localStorage.setItem("koilm_token", token);
  document.getElementById("pinScreen").classList.add("hidden");
  await afterAuth();
});

async function afterAuth() {
  settings = await api("/settings");
  applyLanguage(settings.language || "ja");
  if (!settings.romanticTargetSelected) {
    document.getElementById("onboarding").classList.remove("hidden");
    return;
  }
  showApp();
}

document.querySelectorAll("#languageSection button[data-lang]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    settings = await api("/settings/language", {
      method: "POST",
      body: JSON.stringify({ language: btn.dataset.lang }),
    });
    applyLanguage(btn.dataset.lang);
    document.getElementById("languageSection").classList.add("hidden");
    document.getElementById("ageGateSection").classList.remove("hidden");
  });
});

document.getElementById("ageYesBtn").addEventListener("click", async () => {
  settings = await api("/settings/age-verified", { method: "POST", body: JSON.stringify({ isAdult: true }) });
  document.getElementById("ageGateSection").classList.add("hidden");
  document.getElementById("romanticTargetSection").classList.remove("hidden");
});
document.getElementById("ageNoBtn").addEventListener("click", async () => {
  settings = await api("/settings/age-verified", { method: "POST", body: JSON.stringify({ isAdult: false }) });
  document.getElementById("ageGateSection").classList.add("hidden");
  document.getElementById("romanticTargetSection").classList.remove("hidden");
});

document.querySelectorAll("#romanticTargetSection button[data-target]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    settings = await api("/settings/romantic-target", {
      method: "POST",
      body: JSON.stringify({ target: btn.dataset.target }),
    });
    document.getElementById("languageSection").classList.remove("hidden");
    document.getElementById("ageGateSection").classList.add("hidden");
    document.getElementById("romanticTargetSection").classList.add("hidden");
    document.getElementById("onboarding").classList.add("hidden");
    showApp();
  });
});

async function showApp() {
  document.getElementById("app").style.display = "flex";
  document.getElementById("contentLevelSelect").value = settings.contentLevel;
  document.getElementById("affectionToggle").checked = settings.affectionSystemEnabled;
  document.getElementById("busyStart").value = settings.busyHoursStart;
  document.getElementById("busyEnd").value = settings.busyHoursEnd;
  document.getElementById("modelSizeSelect").value = settings.modelSize || "1b";
  updateModelSizeVisibility();

  characters = await api("/characters");
  renderCharacterList();
}

function renderCharacterList() {
  const listEl = document.getElementById("charList");
  listEl.innerHTML = "";
  for (const c of characters) {
    const item = document.createElement("div");
    item.className = "char-item";
    const img = document.createElement("img");
    img.src = `/api/characters/${c.id}/portrait`;
    img.onerror = () => (img.style.display = "none");
    const nameSpan = document.createElement("span");
    nameSpan.textContent = `${c.name} (${t("affectionLabel", c.affectionScore || 0)})`;
    nameSpan.style.cursor = "pointer";
    nameSpan.style.flex = "1";
    nameSpan.addEventListener("click", () => openChat(c));
    const delBtn = document.createElement("button");
    delBtn.textContent = t("breakUpLabel");
    delBtn.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (!confirm(t("breakUpConfirm", c.name))) return;
      await api(`/characters/${c.id}`, { method: "DELETE" });
      characters = characters.filter((x) => x.id !== c.id);
      if (currentCharacter?.id === c.id) {
        currentCharacter = null;
        document.getElementById("log").innerHTML = "";
        stopPolling();
      }
      renderCharacterList();
    });
    const left = document.createElement("div");
    left.style.display = "flex";
    left.style.alignItems = "center";
    left.style.flex = "1";
    left.appendChild(img);
    left.appendChild(nameSpan);
    item.appendChild(left);
    item.appendChild(delBtn);
    listEl.appendChild(item);
  }
}

async function openChat(character) {
  currentCharacter = character;
  const history = await api(`/chat/${character.id}/history`);
  const logEl = document.getElementById("log");
  logEl.innerHTML = "";
  for (const turn of history) appendBubble(turn);
  stopPolling();
}

function appendBubble(turn) {
  const logEl = document.getElementById("log");
  const bubble = document.createElement("div");
  bubble.className = `bubble ${turn.role}`;
  bubble.textContent = turn.content;
  logEl.appendChild(bubble);
  if (turn.role === "user" && turn.readAt) {
    const label = document.createElement("div");
    label.className = "read-label";
    label.textContent = t("readLabel");
    logEl.appendChild(label);
  }
  logEl.scrollTop = logEl.scrollHeight;
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
}

document.getElementById("sendBtn").addEventListener("click", sendMessage);
document.getElementById("msgInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter") sendMessage();
});

async function sendMessage() {
  const input = document.getElementById("msgInput");
  const message = input.value.trim();
  if (!message || !currentCharacter) return;
  appendBubble({ role: "user", content: message, readAt: null });
  input.value = "";

  if (replyMode === "TIMED") {
    await api(`/chat/${currentCharacter.id}/send-timed`, {
      method: "POST",
      body: JSON.stringify({ personaPrompt: currentCharacter.systemPrompt, message }),
    });
    startPollingForTimedReply();
    return;
  }

  try {
    const { reply } = await api(`/chat/${currentCharacter.id}/send`, {
      method: "POST",
      body: JSON.stringify({ personaPrompt: currentCharacter.systemPrompt, message }),
    });
    appendBubble({ role: "assistant", content: reply });
    characters = await api("/characters");
    renderCharacterList();
  } catch (err) {
    appendBubble({ role: "assistant", content: `${t("errorPrefix")} ${err.message}` });
  }
}

function startPollingForTimedReply() {
  stopPolling();
  const sessionId = currentCharacter.id;
  pollTimer = setInterval(async () => {
    const { reply } = await api(`/chat/${sessionId}/timed-reply`);
    if (reply && currentCharacter?.id === sessionId) {
      appendBubble({ role: "assistant", content: reply });
      stopPolling();
    }
  }, 5000);
}

document.getElementById("newCharBtn").addEventListener("click", async () => {
  const name = prompt(t("charNamePrompt"));
  if (!name) return;
  const systemPrompt = prompt(t("systemPromptPrompt")) || "";
  const appearance = prompt(t("appearancePrompt")) || "";
  const { character } = await api("/characters", {
    method: "POST",
    body: JSON.stringify({ name, systemPrompt, appearance }),
  });
  characters.push(character);
  renderCharacterList();
});

// --- 設定パネル ---
document.getElementById("settingsBtn").addEventListener("click", () => {
  document.getElementById("settingsPanel").style.display = "block";
});
document.getElementById("closeSettingsBtn").addEventListener("click", () => {
  document.getElementById("settingsPanel").style.display = "none";
});
document.getElementById("languageSelect").addEventListener("change", async (e) => {
  settings = await api("/settings/language", {
    method: "POST",
    body: JSON.stringify({ language: e.target.value }),
  });
  applyLanguage(e.target.value);
});
document.getElementById("modelSizeSelect").addEventListener("change", async (e) => {
  settings = await api("/settings/model-size", {
    method: "POST",
    body: JSON.stringify({ modelSize: e.target.value }),
  });
});
document.getElementById("contentLevelSelect").addEventListener("change", async (e) => {
  settings = await api("/settings/content-level", { method: "POST", body: JSON.stringify({ level: e.target.value }) });
});
document.getElementById("affectionToggle").addEventListener("change", async (e) => {
  settings = await api("/settings/affection-system", {
    method: "POST",
    body: JSON.stringify({ enabled: e.target.checked }),
  });
});
document.getElementById("saveBusyHoursBtn").addEventListener("click", async () => {
  const start = parseInt(document.getElementById("busyStart").value, 10) || 0;
  const end = parseInt(document.getElementById("busyEnd").value, 10) || 0;
  settings = await api("/settings/busy-hours", { method: "POST", body: JSON.stringify({ start, end }) });
});
document.getElementById("replyModeSelect").addEventListener("change", (e) => {
  replyMode = e.target.value;
});

init();
