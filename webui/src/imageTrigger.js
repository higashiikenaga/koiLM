// 画像生成をいつ行うかの判定ロジック。windows/src/imageTrigger.js と同一。

// 好感度がこの値の倍数を新たに超えるたびに、シーン画像を1枚生成する。
const AFFECTION_IMAGE_THRESHOLD = 5;

const IMAGE_REQUEST_KEYWORDS = [
  "画像", "写真", "イラスト", "自撮り", "見せて", "見たい", "絵を描いて", "描いて",
];

function isImageRequested(userInput) {
  return IMAGE_REQUEST_KEYWORDS.some((k) => userInput.includes(k));
}

/** beforeScore→afterScoreの間でAFFECTION_IMAGE_THRESHOLDの倍数を新たに跨いだかどうか。 */
function crossedAffectionThreshold(beforeScore, afterScore) {
  if (afterScore <= beforeScore) return false;
  return Math.floor(afterScore / AFFECTION_IMAGE_THRESHOLD) > Math.floor(beforeScore / AFFECTION_IMAGE_THRESHOLD);
}

module.exports = { AFFECTION_IMAGE_THRESHOLD, IMAGE_REQUEST_KEYWORDS, isImageRequested, crossedAffectionThreshold };
