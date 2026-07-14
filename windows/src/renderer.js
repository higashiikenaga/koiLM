const translations = {
  ja: {
    welcome: "ようこそ",
    ageQuestion: "年齢確認: 18歳以上ですか?",
    ageYes: "18歳以上です",
    ageNo: "18歳未満です",
    romanticTargetQuestion: "恋愛対象を選んでください",
    targetMale: "男性が好き",
    targetFemale: "女性が好き",
    targetBoth: "どちらも",
    characters: "キャラクター",
    newCharacter: "+ 新規作成",
    settingsBtn: "⚙ 設定",
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
    oclock1: "時",
    oclock2: "時",
    save: "保存",
    replyModeLabel: "返信モード:",
    replyImmediate: "即返信",
    replyTimed: "タイミング(最大15分以内)",
    send: "送信",
    msgPlaceholder: "メッセージを入力",
    breakUpConfirm: (name) => `本当に${name}と別れますか?`,
    breakUpLabel: "別れる",
    affectionLabel: (n) => `好感度:${n}`,
    charNamePrompt: "キャラクター名",
    systemPromptPrompt: "キャラ設定(口調・性格など)",
    appearancePrompt: "見た目の特徴(英語タグ推奨)",
    cancel: "キャンセル",
    create: "作成",
    errorPrefix: "[エラー]",
    readLabel: "既読",
    llmNotRunning: "llama-serverが起動していません。bin/, models/ にファイルが正しく配置されているか確認してください。",
    imageNsfwPrompt: "NSFW画像を許可する(表現レベルがNSFW・年齢確認済みの場合のみ有効)",
    imageRequestBtn: "🖼",
    imageRequestMessage: "画像を見せて",
    imageNsfwToggleOn: "🔞ON",
    imageNsfwToggleOff: "🔞OFF",
  },
  en: {
    welcome: "Welcome",
    ageQuestion: "Age check: Are you 18 or older?",
    ageYes: "I am 18 or older",
    ageNo: "I am under 18",
    romanticTargetQuestion: "Who would you like to romance?",
    targetMale: "Men",
    targetFemale: "Women",
    targetBoth: "Either",
    characters: "Characters",
    newCharacter: "+ New Character",
    settingsBtn: "⚙ Settings",
    languageLabel: "Language:",
    modelSizeLabel: "Model quality:",
    modelSize1b: "Light & fast (1B)",
    modelSize7b: "High quality & slow (7B)",
    contentLevelLabel: "Content level:",
    levelNormal: "Normal",
    levelSfw: "SFW",
    levelNsfw: "NSFW (18+)",
    affectionToggle: "Affection system (read receipts / reply timing)",
    busyHoursLabel: "Busy hours (no read receipts):",
    oclock1: "",
    oclock2: "",
    save: "Save",
    replyModeLabel: "Reply mode:",
    replyImmediate: "Instant",
    replyTimed: "Delayed (up to 15 min)",
    send: "Send",
    msgPlaceholder: "Type a message",
    breakUpConfirm: (name) => `Are you sure you want to break up with ${name}?`,
    breakUpLabel: "Break up",
    affectionLabel: (n) => `Affection: ${n}`,
    charNamePrompt: "Character name",
    systemPromptPrompt: "Character persona (tone, personality, etc.)",
    appearancePrompt: "Appearance tags (English recommended)",
    cancel: "Cancel",
    create: "Create",
    errorPrefix: "[Error]",
    readLabel: "Read",
    llmNotRunning: "llama-server is not running. Please check that files are placed correctly in bin/ and models/.",
    imageNsfwPrompt: "Allow NSFW images (only takes effect when content level is NSFW and age is verified)",
    imageRequestBtn: "🖼",
    imageRequestMessage: "Show me a picture",
    imageNsfwToggleOn: "🔞ON",
    imageNsfwToggleOff: "🔞OFF",
  },
};

let currentLang = "ja";
let settings = null;
let characters = [];
let currentCharacter = null;
let replyMode = "IMMEDIATE";

const onboardingEl = document.getElementById("onboarding");
const languageSection = document.getElementById("languageSection");
const ageGateSection = document.getElementById("ageGateSection");
const romanticTargetSection = document.getElementById("romanticTargetSection");
const appRootEl = document.getElementById("appRoot");
const logEl = document.getElementById("log");
const msgInput = document.getElementById("msgInput");

function t(key, ...args) {
  const entry = translations[currentLang]?.[key] ?? translations.ja[key];
  return typeof entry === "function" ? entry(...args) : entry;
}

function applyLanguage(lang) {
  currentLang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.getAttribute("data-i18n");
    // strong/br等の子要素を持つブロック(ライセンス表示)は文言差し替え対象外にする
    if (el.children.length === 0) el.textContent = t(key);
  });
  document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
    el.placeholder = t(el.getAttribute("data-i18n-placeholder"));
  });
  document.getElementById("languageSelect").value = lang;
  updateModelSizeVisibility();
}

async function init() {
  settings = await window.koilm.getSettings();
  applyLanguage(settings.language || "ja");

  if (!settings.romanticTargetSelected) {
    onboardingEl.classList.remove("hidden");
    return;
  }

  showApp();
}

document.querySelectorAll("#languageSection button[data-lang]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    settings = await window.koilm.setLanguage(btn.dataset.lang);
    applyLanguage(btn.dataset.lang);
    languageSection.classList.add("hidden");
    ageGateSection.classList.remove("hidden");
  });
});

document.getElementById("ageYesBtn").addEventListener("click", async () => {
  settings = await window.koilm.setAgeVerified(true);
  ageGateSection.classList.add("hidden");
  romanticTargetSection.classList.remove("hidden");
});
document.getElementById("ageNoBtn").addEventListener("click", async () => {
  settings = await window.koilm.setAgeVerified(false);
  ageGateSection.classList.add("hidden");
  romanticTargetSection.classList.remove("hidden");
});

romanticTargetSection.querySelectorAll("button[data-target]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    settings = await window.koilm.setRomanticTarget(btn.dataset.target);
    onboardingEl.classList.add("hidden");
    showApp();
  });
});

async function showApp() {
  appRootEl.style.display = "flex";
  appRootEl.classList.remove("hidden");
  document.getElementById("contentLevelSelect").value = settings.contentLevel;
  document.getElementById("affectionToggle").checked = settings.affectionSystemEnabled;
  document.getElementById("busyStart").value = settings.busyHoursStart;
  document.getElementById("busyEnd").value = settings.busyHoursEnd;
  document.getElementById("modelSizeSelect").value = settings.modelSize || "1b";
  updateModelSizeVisibility();

  characters = await window.koilm.listCharacters();
  renderCharacterList();
  await refreshLlmStatusBanner();
}

async function refreshLlmStatusBanner() {
  const status = await window.koilm.getLlmStatus();
  document.getElementById("llmStatusBanner").classList.toggle("hidden", status.running);
}

/** 7Bモデルは日本語版のみ選択肢に出す(英語版はQwen2.5-1.5B固定)。 */
function updateModelSizeVisibility() {
  document.getElementById("modelSizeRow").style.display = currentLang === "en" ? "none" : "block";
}

function renderCharacterList() {
  const listEl = document.getElementById("charList");
  listEl.innerHTML = "";
  for (const c of characters) {
    const item = document.createElement("div");
    item.className = "char-item";
    const nameSpan = document.createElement("span");
    nameSpan.textContent = `${c.name} (${t("affectionLabel", c.affectionScore || 0)})`;
    nameSpan.style.cursor = "pointer";
    nameSpan.addEventListener("click", () => openChat(c));
    const nsfwBtn = document.createElement("button");
    nsfwBtn.textContent = c.imageNsfw ? t("imageNsfwToggleOn") : t("imageNsfwToggleOff");
    nsfwBtn.title = t("imageNsfwPrompt");
    nsfwBtn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const updated = await window.koilm.setCharacterImageNsfw(c.id, !c.imageNsfw);
      const idx = characters.findIndex((x) => x.id === c.id);
      if (idx !== -1) characters[idx] = updated;
      renderCharacterList();
    });
    const delBtn = document.createElement("button");
    delBtn.textContent = t("breakUpLabel");
    delBtn.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (!confirm(t("breakUpConfirm", c.name))) return;
      await window.koilm.deleteCharacter(c.id);
      characters = characters.filter((x) => x.id !== c.id);
      if (currentCharacter?.id === c.id) {
        currentCharacter = null;
        logEl.innerHTML = "";
      }
      renderCharacterList();
    });
    item.appendChild(nameSpan);
    item.appendChild(nsfwBtn);
    item.appendChild(delBtn);
    listEl.appendChild(item);
  }
}

async function openChat(character) {
  currentCharacter = character;
  const history = await window.koilm.getHistory(character.id);
  logEl.innerHTML = "";
  for (const turn of history) appendBubble(turn);
}

function appendBubble(turn) {
  const bubble = document.createElement("div");
  bubble.className = `bubble ${turn.role}`;
  if (turn.imageUrl) {
    const img = document.createElement("img");
    img.src = turn.imageUrl;
    img.style.maxWidth = "100%";
    img.style.borderRadius = "6px";
    bubble.appendChild(img);
  } else {
    bubble.textContent = turn.content;
  }
  logEl.appendChild(bubble);
  if (turn.role === "user" && turn.readAt) {
    const label = document.createElement("div");
    label.className = "read-label";
    label.textContent = t("readLabel");
    logEl.appendChild(label);
  }
}

async function sendCurrentMessage(message) {
  if (!message || !currentCharacter) return;
  appendBubble({ role: "user", content: message, readAt: null });
  msgInput.value = "";

  if (replyMode === "TIMED") {
    await window.koilm.sendMessageTimed(currentCharacter.id, currentCharacter.systemPrompt, message);
    return;
  }

  try {
    const { reply, image } = await window.koilm.sendMessage(
      currentCharacter.id,
      currentCharacter.systemPrompt,
      message
    );
    appendBubble({ role: "assistant", content: reply });
    if (image) appendBubble({ role: "assistant", content: "", imageUrl: image.url });
    characters = await window.koilm.listCharacters();
    renderCharacterList();
  } catch (err) {
    appendBubble({ role: "assistant", content: `${t("errorPrefix")} ${err.message}` });
    refreshLlmStatusBanner();
  }
}

document.getElementById("sendBtn").addEventListener("click", () => sendCurrentMessage(msgInput.value.trim()));
document.getElementById("imageRequestBtn").addEventListener("click", () => sendCurrentMessage(t("imageRequestMessage")));

window.koilm.onTimedReplyReady(({ sessionId, reply }) => {
  if (currentCharacter?.id === sessionId) {
    appendBubble({ role: "assistant", content: reply });
  }
});

const newCharModal = document.getElementById("newCharModal");
const newCharName = document.getElementById("newCharName");
const newCharSystemPrompt = document.getElementById("newCharSystemPrompt");
const newCharAppearance = document.getElementById("newCharAppearance");
const newCharImageNsfw = document.getElementById("newCharImageNsfw");

document.getElementById("newCharBtn").addEventListener("click", () => {
  newCharName.value = "";
  newCharSystemPrompt.value = "";
  newCharAppearance.value = "";
  newCharImageNsfw.checked = false;
  newCharModal.classList.remove("hidden");
  newCharName.focus();
});

document.getElementById("newCharCancelBtn").addEventListener("click", () => {
  newCharModal.classList.add("hidden");
});

document.getElementById("newCharCreateBtn").addEventListener("click", async () => {
  const name = newCharName.value.trim();
  if (!name) {
    newCharName.focus();
    return;
  }
  const systemPrompt = newCharSystemPrompt.value.trim();
  const appearance = newCharAppearance.value.trim();
  const imageNsfw = newCharImageNsfw.checked;
  newCharModal.classList.add("hidden");
  const { character } = await window.koilm.createCharacter(name, systemPrompt, appearance, imageNsfw);
  characters.push(character);
  renderCharacterList();
});

// --- 設定パネル ---
document.getElementById("settingsBtn").addEventListener("click", () => {
  document.getElementById("settingsPanel").classList.toggle("hidden");
});
document.getElementById("languageSelect").addEventListener("change", async (e) => {
  settings = await window.koilm.setLanguage(e.target.value);
  applyLanguage(e.target.value);
});
document.getElementById("modelSizeSelect").addEventListener("change", async (e) => {
  settings = await window.koilm.setModelSize(e.target.value);
  document.getElementById("modelSizeSelect").value = settings.modelSize;
  await refreshLlmStatusBanner();
});
document.getElementById("contentLevelSelect").addEventListener("change", async (e) => {
  settings = await window.koilm.setContentLevel(e.target.value);
});
document.getElementById("affectionToggle").addEventListener("change", async (e) => {
  settings = await window.koilm.setAffectionSystemEnabled(e.target.checked);
});
document.getElementById("saveBusyHoursBtn").addEventListener("click", async () => {
  const start = parseInt(document.getElementById("busyStart").value, 10) || 0;
  const end = parseInt(document.getElementById("busyEnd").value, 10) || 0;
  settings = await window.koilm.setBusyHours(start, end);
});
document.getElementById("replyModeSelect").addEventListener("change", (e) => {
  replyMode = e.target.value;
});

init();
