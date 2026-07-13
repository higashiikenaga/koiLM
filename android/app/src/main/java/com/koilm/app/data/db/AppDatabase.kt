package com.koilm.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationTurnEntity::class,
        MemorySummaryEntity::class,
        ProfileMemoryEntity::class,
        PendingProactiveMessageEntity::class,
        CharacterEntity::class,
    ],
    // v2: 自発的メッセージ受け渡し(PendingProactiveMessageEntity)と
    //     音声パックDLCメタデータ(VoicePackEntity)を追加。
    // v3: ユーザーが作成する「相手(キャラクター)」(CharacterEntity)を追加。
    // v4: ProfileMemoryEntityにsessionIdを追加(キャラクター間で記憶が混ざるバグの修正)、
    //     CharacterEntityにnotificationsEnabledを追加(キャラ別の自発メッセージ通知ON/OFF)。
    // v5: ConversationTurnEntityにreadAt(既読タイムスタンプ)、
    //     CharacterEntityにaffectionScore(好感度)を追加。
    // v6: TTS機能を撤去したためVoicePackEntityを削除。
    // v7: 好感度マイルストーンでの1枚絵自動生成をAndroidに試験導入したが、モバイルCPUでは
    //     現実的な速度が出ないため撤去(Windows版限定の機能とする)。CharacterEntityの
    //     appearanceTags/portraitPath/lastPortraitAffectionScoreを削除しv6相当の構成に戻す。
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun characterDao(): CharacterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    // 端末内ローカルDBのみ。クラウド同期・バックアップは行わない。
                    "koilm_local.db",
                )
                    // 本スキャフォールドでは破壊的マイグレーションで簡略化している。
                    // 本番ではユーザーの会話履歴を失わないよう Migration を実装すること。
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
