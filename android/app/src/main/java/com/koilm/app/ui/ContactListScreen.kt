package com.koilm.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koilm.app.data.db.CharacterEntity

/**
 * アプリのメイン画面。作成済みの「相手(キャラクター)」一覧をメッセージアプリ風に表示する。
 * 右上の歯車アイコンから設定画面(年齢確認・表現レベル・音声パック・OFUSE等)を開く。
 * 各行のベルアイコンで、そのキャラからの自発メッセージ通知をON/OFFできる。
 * 各行を長押しすると「別れる」(削除)確認ダイアログが表示される。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    characters: List<CharacterEntity>,
    onOpenCharacter: (CharacterEntity) -> Unit,
    onCreateCharacter: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleNotifications: (CharacterEntity) -> Unit,
    onBreakUp: (CharacterEntity) -> Unit,
) {
    var characterToBreakUpWith by remember { mutableStateOf<CharacterEntity?>(null) }

    characterToBreakUpWith?.let { character ->
        AlertDialog(
            onDismissRequest = { characterToBreakUpWith = null },
            title = { Text("${character.name}と別れますか？") },
            text = { Text("会話履歴やこれまでの記憶もすべて削除されます。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onBreakUp(character)
                    characterToBreakUpWith = null
                }) {
                    Text("別れる", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { characterToBreakUpWith = null }) {
                    Text("やめておく")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("koiLM") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateCharacter) {
                Icon(Icons.Filled.Add, contentDescription = "新しい相手を作成")
            }
        },
    ) { paddingValues ->
        if (characters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "まだ相手がいません。\n右下の + から最初のキャラクターを作成してください。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
            ) {
                items(characters, key = { it.id }) { character ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .combinedClickable(
                                onClick = { onOpenCharacter(character) },
                                onLongClick = { characterToBreakUpWith = character },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = character.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = character.systemPrompt.take(40) +
                                        if (character.systemPrompt.length > 40) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { onToggleNotifications(character) }) {
                                Text(
                                    text = if (character.notificationsEnabled) "🔔" else "🔕",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
