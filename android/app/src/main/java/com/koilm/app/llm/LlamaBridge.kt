package com.koilm.app.llm

/**
 * llama.cpp (JNI/C++) への薄いブリッジ。ロジックを持たず、ネイティブ関数の宣言のみ行う。
 * 実装本体は android/app/src/main/cpp/llama-android.cpp。
 */
object LlamaBridge {
    init {
        System.loadLibrary("koilm_llama")
    }

    /** GGUFモデルをロードし、ハンドル(内部インデックス)を返す。失敗時は0。 */
    external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Long

    /** ロード済みモデルを解放する。 */
    external fun freeModel(handle: Long)

    /** プロンプトから応答テキストを生成する(同期・ブロッキング呼び出し)。 */
    external fun generate(
        handle: Long,
        prompt: String,
        maxNewTokens: Int,
        temperature: Float,
        topP: Float,
    ): String
}
