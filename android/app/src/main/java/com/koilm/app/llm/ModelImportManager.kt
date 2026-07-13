package com.koilm.app.llm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 端末内のファイル(SAFで選択したGGUFモデル)を、アプリ専用ストレージへコピーするインポーター。
 *
 * リリースビルド(debuggable=false)ではadb run-asが使えないため、
 * エンドユーザーが手元のGGUFファイルを導入する唯一の手段はアプリ自身のコード経由となる。
 * ネットワーク通信は行わず、あくまで端末内の既存ファイルをコピーするだけ。
 */
class ModelImportManager(private val context: Context) {

    suspend fun importModel(uri: Uri): File = withContext(Dispatchers.IO) {
        val destFile = LlmEngineProvider.modelFile(context)
        destFile.parentFile?.mkdirs()

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("モデルファイルを開けませんでした: $uri")

        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        input.use { inStream ->
            tmpFile.outputStream().use { outStream ->
                inStream.copyTo(outStream, bufferSize = 1 shl 20)
            }
        }
        // 途中で失敗した中途半端なファイルで既存モデルを壊さないよう、一時ファイル経由でアトミックに置き換える。
        tmpFile.renameTo(destFile)

        destFile
    }

    fun isModelImported(): Boolean = LlmEngineProvider.modelFile(context).exists()
}
