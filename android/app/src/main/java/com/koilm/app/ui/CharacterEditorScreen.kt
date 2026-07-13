package com.koilm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 新しい「相手(キャラクター)」を作成する画面。
 * ユーザー自身が名前とシステムプロンプト(口調・性格・世界観)を自由に決められる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(
    onSave: (name: String, systemPrompt: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新しい相手を作成") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("キャンセル") }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "システムプロンプト(口調・性格・世界観など)",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                placeholder = {
                    Text("例: あなたは「小春」。幼馴染で少しツンデレな女子高生キャラとして振る舞ってください。地の文を交えつつ、素っ気ない口調の中に好意をにじませてください。")
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(name.trim(), systemPrompt.trim()) },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("作成する")
            }
        }
    }
}
