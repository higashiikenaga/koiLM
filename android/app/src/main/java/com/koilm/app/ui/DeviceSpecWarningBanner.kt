package com.koilm.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koilm.app.device.DeviceSpecChecker

/**
 * 端末スペックが7Bモデル+TTSの推奨/最低要件を満たさない場合に表示する警告バナー。
 * 満たしている場合は何も表示しない(呼び出し側でnullチェック不要にするため、
 * このComposable自体が条件分岐を持つ)。
 */
@Composable
fun DeviceSpecWarningBanner(result: DeviceSpecChecker.Result) {
    if (result.meetsRecommended) return

    val message = if (!result.meetsMinimum) {
        "この端末はスペック不足の可能性があります(RAM %.1fGB / 空き容量 %.1fGB)。".format(
            result.totalRamGb, result.freeStorageGb,
        ) + "動作が非常に遅い、またはクラッシュする場合があります。" +
            "最低推奨: RAM ${DeviceSpecChecker.MIN_RAM_GB}GB以上 / 空き容量 ${DeviceSpecChecker.MIN_FREE_STORAGE_GB}GB以上"
    } else {
        "この端末はギリギリ動作しますが、快適な速度には推奨スペックを下回っています" +
            "(RAM %.1fGB / 空き容量 %.1fGB)。".format(result.totalRamGb, result.freeStorageGb) +
            "推奨: RAM ${DeviceSpecChecker.RECOMMENDED_RAM_GB}GB以上 / 空き容量 ${DeviceSpecChecker.RECOMMENDED_FREE_STORAGE_GB}GB以上"
    }

    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
