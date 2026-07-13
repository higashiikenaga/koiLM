// Android版 settings/RomanticTarget.kt と同じ定義・ディレクティブ文をJSで再現したもの。
const TARGETS = {
  MALE: {
    label: "男性が好き",
    directive:
      "【恋愛対象: 男性】ユーザーは男性キャラクターとの恋愛を求めています。落ち着いた／頼れる／情熱的など、" +
      "男性キャラクター視点での口説き文句・態度で会話してください。",
  },
  FEMALE: {
    label: "女性が好き",
    directive:
      "【恋愛対象: 女性】ユーザーは女性キャラクターとの恋愛を求めています。可愛らしさ・甘えなど、" +
      "女性キャラクター視点での口説き文句・態度で会話してください。",
  },
  BOTH: {
    label: "どちらも",
    directive: "【恋愛対象: 指定なし】キャラクター自身の性別・個性に合わせた自然な口調で会話してください。",
  },
};

function directiveFor(target) {
  return (TARGETS[target] || TARGETS.BOTH).directive;
}

module.exports = { TARGETS, directiveFor };
