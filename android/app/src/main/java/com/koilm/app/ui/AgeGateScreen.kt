package com.koilm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 起動時、または表現レベルをNSFWに変更しようとした際に表示する年齢確認画面。
 * 判定結果はAppPreferencesを通じて端末内にのみ保存され、外部送信は一切行わない。
 */
@Composable
fun AgeGateScreen(
    onAnswer: (isAdult: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "年齢確認",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "本アプリには恋愛・ロールプレイ会話向けの成人向け表現(NSFW)が含まれる場合があります。\n" +
                "あなたは18歳以上ですか？",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "回答は端末内にのみ保存され、外部には送信されません。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onAnswer(true) }) {
            Text("18歳以上です")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { onAnswer(false) }) {
            Text("18歳未満です")
        }
    }
}
