package com.koilm.app

import android.content.Context
import com.koilm.app.data.CharacterRepository
import com.koilm.app.data.ChatRepository
import com.koilm.app.data.db.AppDatabase
import com.koilm.app.llm.LlamaCppEngine
import com.koilm.app.llm.LlmEngineProvider
import com.koilm.app.llm.ModelImportManager
import com.koilm.app.memory.ContextMemoryManager
import com.koilm.app.settings.AppPreferences

/**
 * 簡易的な手動DIコンテナ。フレームワーク非依存で、各層の依存関係を1箇所に集約する。
 * (Hilt/Koin等を導入する場合はこのクラスを置き換える)
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val preferences = AppPreferences(appContext)

    private var cachedEngine: LlamaCppEngine? = null

    suspend fun llmEngine(): LlamaCppEngine =
        cachedEngine ?: LlmEngineProvider.getOrLoad(appContext).also { cachedEngine = it }

    val modelImportManager: ModelImportManager by lazy { ModelImportManager(appContext) }
    val characterRepository: CharacterRepository by lazy {
        CharacterRepository(database.characterDao(), database.chatDao())
    }

    suspend fun chatRepository(): ChatRepository {
        val engine = llmEngine()
        val memoryManager = ContextMemoryManager(database.chatDao(), engine)
        return ChatRepository(database.chatDao(), database.characterDao(), engine, memoryManager, preferences)
    }
}
