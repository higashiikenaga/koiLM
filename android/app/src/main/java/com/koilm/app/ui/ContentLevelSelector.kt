package com.koilm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koilm.app.settings.ContentLevel

/**
 * 「通常表現 → SFW → NSFW」の順に表現強度を選べるセグメントコントロール。
 * NSFWは年齢確認未完了の場合は選択できず、タップすると onRequestAgeVerification が呼ばれる
 * (呼び出し元でAgeGateScreenを表示する)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentLevelSelector(
    currentLevel: ContentLevel,
    isAgeVerified: Boolean,
    onLevelSelected: (ContentLevel) -> Unit,
    onRequestAgeVerification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "表現レベル",
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
            ContentLevel.entries.forEachIndexed { index, level ->
                val locked = level.requiresAgeVerification && !isAgeVerified
                SegmentedButton(
                    selected = currentLevel == level,
                    onClick = {
                        if (locked) {
                            onRequestAgeVerification()
                        } else {
                            onLevelSelected(level)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, ContentLevel.entries.size),
                ) {
                    Text(if (locked) "${level.label} 🔒" else level.label)
                }
            }
        }
        if (currentLevel.requiresAgeVerification) {
            Text(
                text = "NSFWモードが有効です。成人向けの直接的な表現が出力される場合があります。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
