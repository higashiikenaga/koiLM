// Android版 settings/ContentLevel.kt と同じ定義・ディレクティブ文をJSで再現したもの。
const LEVELS = {
  NORMAL: {
    label: "通常表現",
    requiresAgeVerification: false,
    directive: "【表現レベル: 通常表現】性的・暴力的な露骨な描写は避け、健全な会話のみを行ってください。",
  },
  SFW: {
    label: "SFW",
    requiresAgeVerification: false,
    directive: "【表現レベル: SFW】恋愛描写・軽いスキンシップ表現は許容しますが、直接的な性描写は避けてください。",
  },
  NSFW: {
    label: "NSFW(18歳以上)",
    requiresAgeVerification: true,
    directive: "【表現レベル: NSFW(成人向け)】ユーザーは年齢確認済みの成人です。直接的な性描写を含む表現を許可します。",
  },
};

function directiveFor(level) {
  return (LEVELS[level] || LEVELS.NORMAL).directive;
}

function requiresAgeVerification(level) {
  return (LEVELS[level] || LEVELS.NORMAL).requiresAgeVerification;
}

module.exports = { LEVELS, directiveFor, requiresAgeVerification };
