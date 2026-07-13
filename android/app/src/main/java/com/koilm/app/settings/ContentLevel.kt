package com.koilm.app.settings

/**
 * 表現強度レベル。「通常表現 < SFW < NSFW」の順に強度が上がる。
 * NSFWは年齢確認(18歳以上)が完了している場合のみ選択・適用できる(AppPreferences/ChatRepositoryで強制)。
 */
enum class ContentLevel(val label: String, val requiresAgeVerification: Boolean) {
    NORMAL("通常表現", requiresAgeVerification = false),
    SFW("SFW", requiresAgeVerification = false),
    NSFW("NSFW(18歳以上)", requiresAgeVerification = true);

    /** モデルへの指示として system prompt に差し込むディレクティブ文。 */
    fun promptDirective(): String = when (this) {
        NORMAL -> "【表現レベル: 通常表現】性的・暴力的な露骨な描写は避け、健全な会話のみを行ってください。"
        SFW -> "【表現レベル: SFW】恋愛描写・軽いスキンシップ表現は許容しますが、直接的な性描写は避けてください。"
        NSFW -> "【表現レベル: NSFW(成人向け)】ユーザーは年齢確認済みの成人です。直接的な性描写を含む表現を許可します。"
    }

    companion object {
        fun fromName(name: String?): ContentLevel =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}
