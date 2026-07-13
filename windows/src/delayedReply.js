const store = require("./store");
const chatRepository = require("./chatRepository");

// Android版 worker/DelayedReplyWorker.kt と同じ考え方をJSのsetTimeoutで再現したもの。
// (Windows版はアプリが起動している間のみ有効。OSレベルのバックグラウンドワーカーは持たない)
const BASE_MAX_DELAY_MINUTES = 15;

/** candidateDate が busyStart時〜busyEnd時(日またぎ可)に入っていれば、その終了時刻まで押し出す。 */
function pushOutsideBusyHours(candidateDate, busyStart, busyEnd) {
  if (busyStart === busyEnd) return candidateDate;
  const hour = candidateDate.getHours();
  const inBusyHours =
    busyStart < busyEnd ? hour >= busyStart && hour < busyEnd : hour >= busyStart || hour < busyEnd;
  if (!inBusyHours) return candidateDate;

  const pushed = new Date(candidateDate);
  pushed.setHours(busyEnd, 0, 0, 0);
  if (pushed <= candidateDate) pushed.setDate(pushed.getDate() + 1);
  return pushed;
}

/**
 * ユーザーがメッセージを送った直後に呼ぶ。0秒〜15分のランダムな遅延で返信をスケジュールするが、
 * 好感度システムが有効な場合は好感度が高いほど最大遅延を短くし、
 * 「仕事中」時間帯に該当する場合はその時間帯が終わるまで押し出す。
 */
function schedule(sessionId, personaPrompt, requestedLevel, onReplyReady) {
  const data = store.load();
  const affectionEnabled = data.settings.affectionSystemEnabled;

  let maxDelayMinutes = BASE_MAX_DELAY_MINUTES;
  if (affectionEnabled) {
    const character = data.characters.find((c) => c.id === sessionId);
    const affection = character ? character.affectionScore || 0 : 0;
    maxDelayMinutes = Math.min(BASE_MAX_DELAY_MINUTES, Math.max(1, BASE_MAX_DELAY_MINUTES - affection));
  }

  const now = new Date();
  let candidate = new Date(now.getTime() + Math.random() * maxDelayMinutes * 60 * 1000);

  if (affectionEnabled) {
    candidate = pushOutsideBusyHours(candidate, data.settings.busyHoursStart, data.settings.busyHoursEnd);
  }

  const delayMs = Math.max(0, candidate.getTime() - now.getTime());
  setTimeout(async () => {
    try {
      const reply = await chatRepository.generateReply(sessionId, personaPrompt, requestedLevel);
      onReplyReady(reply);
    } catch (err) {
      console.error("[delayedReply] 返信生成失敗:", err);
    }
  }, delayMs);
}

module.exports = { schedule };
