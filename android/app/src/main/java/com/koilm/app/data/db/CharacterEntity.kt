package com.koilm.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ユーザーが作成する「相手(キャラクター)」。idはそのままChatDao/ConversationTurnEntityの
 * sessionIdとして使い、キャラクター単位で会話履歴・長期記憶が分離されるようにしている。
 *
 * systemPrompt はユーザー自身が自由に編集できるキャラ設定(口調・性格・世界観など)。
 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemPrompt: String,
    /** falseにすると、このキャラからのAIBackgroundWorkerによる自発メッセージ通知を停止する。 */
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 好感度スコア。AIの返信内容をAffectionScorerで評価した加点/減点の累計。
     * 既読〜返信までの間隔を短くする等、タイミング演出にのみ使う(会話内容そのものは変えない)。
     */
    val affectionScore: Int = 0,
)
