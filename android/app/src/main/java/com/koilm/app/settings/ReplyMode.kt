package com.koilm.app.settings

/**
 * キャラクターの返信タイミング。
 *   IMMEDIATE: 送信直後にその場でSLM生成+TTS再生(従来通り)。
 *   TIMED: 送信後、最大15分以内のランダムなタイミングでDelayedReplyWorkerが裏で生成し、
 *          通知で知らせる(「本当に人間が返信しているような」間を演出する)。
 */
enum class ReplyMode(val label: String) {
    IMMEDIATE("即返信"),
    TIMED("タイミング(最大15分以内)");

    companion object {
        fun fromName(name: String?): ReplyMode = entries.firstOrNull { it.name == name } ?: IMMEDIATE
    }
}
