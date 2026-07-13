package com.koilm.app.device

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.core.content.getSystemService

/**
 * 7BクラスGGUFモデル(llama.cpp)+ ONNX TTS を端末内で動かすための
 * 推奨/最低スペックを判定するユーティリティ。
 *
 * 目安の根拠:
 *   - 7B Q4_K_M量子化GGUFのファイルサイズは約4.0〜4.5GB
 *   - llama.cppはmmapでモデルを読み込むが、KVキャッシュ(コンテキスト長に比例)や
 *     推論バッファ、Android自体のシステム常駐分を合わせると実効RAM使用量は
 *     モデルファイルサイズの1.5〜2倍程度になりやすい
 *   - ONNX Runtime TTS(VITS系、数十〜数百MB)の推論も同時にRAM/CPUを使うため、
 *     LLM単体の要件に上乗せで見積もる
 */
object DeviceSpecChecker {
    // 最低ライン: 一応動くが遅延・OOM Killのリスクがある
    const val MIN_RAM_GB = 6.0
    const val MIN_FREE_STORAGE_GB = 8.0

    // 推奨ライン: 実用的な速度(数トークン/秒以上)で動作する目安
    const val RECOMMENDED_RAM_GB = 8.0
    const val RECOMMENDED_FREE_STORAGE_GB = 10.0

    data class Result(
        val totalRamGb: Double,
        val freeStorageGb: Double,
        val meetsMinimum: Boolean,
        val meetsRecommended: Boolean,
    )

    fun check(context: Context): Result {
        val activityManager = context.getSystemService<ActivityManager>()
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val stat = StatFs(context.filesDir.absolutePath)
        val freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)

        val meetsMinimum = totalRamGb >= MIN_RAM_GB && freeStorageGb >= MIN_FREE_STORAGE_GB
        val meetsRecommended = totalRamGb >= RECOMMENDED_RAM_GB && freeStorageGb >= RECOMMENDED_FREE_STORAGE_GB

        return Result(
            totalRamGb = totalRamGb,
            freeStorageGb = freeStorageGb,
            meetsMinimum = meetsMinimum,
            meetsRecommended = meetsRecommended,
        )
    }
}
