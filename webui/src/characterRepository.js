const crypto = require("crypto");
const store = require("./store");

// Android版 data/CharacterRepository.kt と同じロジックをJSで再現したもの。

function listCharacters() {
  return store.load().characters;
}

function getCharacter(id) {
  return store.load().characters.find((c) => c.id === id) || null;
}

function createCharacter(name, systemPrompt) {
  const data = store.load();
  const character = {
    id: crypto.randomUUID(),
    name,
    systemPrompt,
    notificationsEnabled: true,
    affectionScore: 0,
    createdAt: Date.now(),
  };
  data.characters.push(character);
  store.save();
  return character;
}

/** キャラクター削除("別れる")。会話履歴・長期記憶・プロフィールメモリも合わせて消す。 */
function deleteCharacter(id) {
  const data = store.load();
  data.characters = data.characters.filter((c) => c.id !== id);
  delete data.turns[id];
  delete data.memorySummaries[id];
  delete data.profileMemory[id];
  store.save();
}

function setNotificationsEnabled(id, enabled) {
  const character = getCharacter(id);
  if (!character) return;
  character.notificationsEnabled = enabled;
  store.save();
}

module.exports = { listCharacters, getCharacter, createCharacter, deleteCharacter, setNotificationsEnabled };
