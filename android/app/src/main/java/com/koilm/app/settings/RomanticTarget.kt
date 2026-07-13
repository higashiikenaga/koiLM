package com.koilm.app.settings

/**
 * ユーザーの恋愛対象(初回起動時に選択)。性別ではなく「誰との恋愛を描くか」を選ぶ設定。
 * LoRA自体は作り直さず、system promptへの指示文でトーン・視点を切り替える。
 */
enum class RomanticTarget(val label: String) {
    MALE("男性が好き"),
    FEMALE("女性が好き"),
    BOTH("どちらも");

    /** モデルへの指示として system prompt に差し込むディレクティブ文。 */
    fun promptDirective(): String = when (this) {
        MALE -> "【恋愛対象: 男性】ユーザーは男性キャラクとの恋愛を求めています。落ち着いた／頼れる／情熱的など、" +
            "男性キャラクター視点での口説き文句・態度で会話してください。"
        FEMALE -> "【恋愛対象: 女性】ユーザーは女性キャラクターとの恋愛を求めています。可愛らしさ・甘えなど、" +
            "女性キャラクター視点での口説き文句・態度で会話してください。"
        BOTH -> "【恋愛対象: 指定なし】キャラクター自身の性別・個性に合わせた自然な口調で会話してください。"
    }

    companion object {
        fun fromName(name: String?): RomanticTarget =
            entries.firstOrNull { it.name == name } ?: BOTH
    }
}
