package com.koilm.app.llm

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * LlamaCppEngine をアプリ全体で使い回すためのシングルトン提供者。
 *
 * モデルロードはVRAM/RAM・時間ともにコストが高いため、チャット画面と
 * AIBackgroundWorker(自発メッセージ生成)の両方から同じロード済みインスタンスを再利用する。
 * 実運用では「モデルファイルの配置パス」をユーザーが初回セットアップ時に選ぶ想定。
 */
object LlmEngineProvider {
    @Volatile
    private var engine: LlamaCppEngine? = null

    // suspend関数内での排他制御は synchronized ではなく Mutex を使うこと。
    // (synchronizedはコルーチンのサスペンドと相性が悪く、以前は二重チェックロッキングが
    //  不完全だったため、複数箇所から同時にgetOrLoad()が呼ばれるとモデルが二重ロードされ、
    //  RAM/CPUを圧迫するバグがあった)
    private val loadMutex = Mutex()

    /** モデルファイルの配置場所。実際はSAFで選んだパス等をAppPreferencesに保存して参照する。 */
    fun modelFile(context: Context): File =
        File(context.filesDir, "models/current/model.gguf")

    suspend fun getOrLoad(context: Context): LlamaCppEngine {
        engine?.let { return it }
        return loadMutex.withLock {
            // ロック取得までの間に他の呼び出しがロードを完了させている可能性があるため再チェック。
            engine?.let { return@withLock it }

            val newEngine = LlamaCppEngine()
            val modelFile = modelFile(context)
            if (modelFile.exists()) {
                newEngine.load(modelFile)
            }
            // モデル未配置の場合でもクラッシュさせず、呼び出し側で loaded 状態を確認させる想定。
            engine = newEngine
            newEngine
        }
    }

    /** モデル再インポート後に呼び、次回 getOrLoad() で新しいモデルを読み直させる。 */
    suspend fun reset() {
        loadMutex.withLock {
            engine?.close()
            engine = null
        }
    }
}
