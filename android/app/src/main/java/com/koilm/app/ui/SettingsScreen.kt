package com.koilm.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.koilm.app.device.DeviceSpecChecker
import com.koilm.app.settings.ContentLevel
import com.koilm.app.settings.ReplyMode
import com.koilm.app.settings.RomanticTarget

/**
 * 設定画面。年齢確認/表現レベル・端末スペック警告・音声パック導入・
 * 自発メッセージのタイミング設定・OFUSE支援を集約する。
 * メイン画面(相手一覧)右上の歯車アイコンから遷移する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceSpec: DeviceSpecChecker.Result,
    contentLevel: ContentLevel,
    isAgeVerified: Boolean,
    showAgeGate: Boolean,
    onRequestAgeVerification: () -> Unit,
    onAgeAnswer: (Boolean) -> Unit,
    onContentLevelSelected: (ContentLevel) -> Unit,
    onOpenOfuse: () -> Unit,
    onBack: () -> Unit,
    quietHoursStart: Int,
    quietHoursEnd: Int,
    minIntervalHours: Int,
    maxIntervalHours: Int,
    onSaveNotificationTiming: (quietStart: Int, quietEnd: Int, minH: Int, maxH: Int) -> Unit,
    replyMode: ReplyMode,
    onReplyModeSelected: (ReplyMode) -> Unit,
    romanticTarget: RomanticTarget,
    onRomanticTargetSelected: (RomanticTarget) -> Unit,
    affectionSystemEnabled: Boolean,
    onAffectionSystemToggled: (Boolean) -> Unit,
    busyHoursStart: Int,
    busyHoursEnd: Int,
    onSaveBusyHours: (start: Int, end: Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            DeviceSpecWarningBanner(deviceSpec)

            if (showAgeGate) {
                AgeGateScreen(onAnswer = onAgeAnswer)
            } else {
                ContentLevelSelector(
                    currentLevel = contentLevel,
                    isAgeVerified = isAgeVerified,
                    onLevelSelected = onContentLevelSelected,
                    onRequestAgeVerification = onRequestAgeVerification,
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                ReplyModeSettings(
                    currentMode = replyMode,
                    onModeSelected = onReplyModeSelected,
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                RomanticTargetSettings(
                    currentTarget = romanticTarget,
                    onTargetSelected = onRomanticTargetSelected,
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                AffectionSystemSettings(
                    enabled = affectionSystemEnabled,
                    onEnabledChanged = onAffectionSystemToggled,
                    busyHoursStart = busyHoursStart,
                    busyHoursEnd = busyHoursEnd,
                    onSaveBusyHours = onSaveBusyHours,
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                NotificationTimingSettings(
                    initialQuietStart = quietHoursStart,
                    initialQuietEnd = quietHoursEnd,
                    initialMinHours = minIntervalHours,
                    initialMaxHours = maxIntervalHours,
                    onSave = onSaveNotificationTiming,
                )

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedButton(onClick = onOpenOfuse) {
                    Text("☕ OFUSEで応援する")
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                LicensesSection()
            }
        }
    }
}

/**
 * キャラクターの返信タイミング(即返信 / タイミング)を選ぶ設定。
 * 「タイミング」を選ぶと、送信後すぐには返信が来ず、最大15分以内のランダムなタイミングで
 * DelayedReplyWorkerが返信を生成し、通知で知らせる。
 */
@Composable
private fun ReplyModeSettings(
    currentMode: ReplyMode,
    onModeSelected: (ReplyMode) -> Unit,
) {
    Column {
        Text(text = "返信タイミング", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        ReplyMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = currentMode == mode,
                    onClick = { onModeSelected(mode) },
                )
                Text(text = mode.label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** 使用モデル・ライセンスのクレジット表示。 */
@Composable
private fun LicensesSection() {
    Column {
        Text(text = "使用モデル・ライセンス", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本アプリのキャラクターの発言はすべてAIによって生成されたものです。\n\n" +
                "・対話モデル: sarashina2.2-1b-instruct-v0.1 by SB Intuitions (MIT License) をベースに" +
                "独自LoRAでファインチューニング",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 恋愛対象(初回起動時に選んだもの)を後から変更するための設定。 */
@Composable
private fun RomanticTargetSettings(
    currentTarget: RomanticTarget,
    onTargetSelected: (RomanticTarget) -> Unit,
) {
    Column {
        Text(text = "恋愛対象", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        RomanticTarget.entries.forEach { target ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = currentTarget == target,
                    onClick = { onTargetSelected(target) },
                )
                Text(text = target.label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * 好感度システム(既読タイミング・返信間隔の演出)のON/OFFと、
 * 「仕事中」等で既読すら付かない時間帯の設定。
 */
@Composable
private fun AffectionSystemSettings(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    busyHoursStart: Int,
    busyHoursEnd: Int,
    onSaveBusyHours: (start: Int, end: Int) -> Unit,
) {
    var busyStartText by remember(busyHoursStart) { mutableStateOf(busyHoursStart.toString()) }
    var busyEndText by remember(busyHoursEnd) { mutableStateOf(busyHoursEnd.toString()) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "好感度システム(既読・返信間隔の演出)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChanged)
        }
        Text(
            text = "AIの返信内容から好感度を算出し、既読が付くタイミングや返信までの間隔に反映します。" +
                "指定した時間帯は「仕事中」として扱い、既読すら付きません。",
            style = MaterialTheme.typography.bodySmall,
        )
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "仕事中(既読が付かない時間帯)", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(
                    value = busyStartText,
                    onValueChange = { busyStartText = it.filter(Char::isDigit).take(2) },
                    label = { Text("開始(時)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = busyEndText,
                    onValueChange = { busyEndText = it.filter(Char::isDigit).take(2) },
                    label = { Text("終了(時)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSaveBusyHours(
                        busyStartText.toIntOrNull() ?: busyHoursStart,
                        busyEndText.toIntOrNull() ?: busyHoursEnd,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
        }
    }
}

/**
 * AIBackgroundWorkerによる自発メッセージの「送らない時間帯(静かな時間帯)」と
 * 「送信間隔(最短〜最長)」をユーザーが設定できるフォーム。
 */
@Composable
private fun NotificationTimingSettings(
    initialQuietStart: Int,
    initialQuietEnd: Int,
    initialMinHours: Int,
    initialMaxHours: Int,
    onSave: (quietStart: Int, quietEnd: Int, minH: Int, maxH: Int) -> Unit,
) {
    var quietStartText by remember { mutableStateOf(initialQuietStart.toString()) }
    var quietEndText by remember { mutableStateOf(initialQuietEnd.toString()) }
    var minHoursText by remember { mutableStateOf(initialMinHours.toString()) }
    var maxHoursText by remember { mutableStateOf(initialMaxHours.toString()) }

    Column {
        Text(text = "自発メッセージのタイミング設定", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "指定した時間帯は自発的な話しかけを行いません。それ以外の時間帯で、" +
                "最短〜最長時間のランダムな間隔で話しかけます。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "送らない時間帯(静かな時間帯)", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = quietStartText,
                onValueChange = { quietStartText = it.filter(Char::isDigit).take(2) },
                label = { Text("開始(時)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = quietEndText,
                onValueChange = { quietEndText = it.filter(Char::isDigit).take(2) },
                label = { Text("終了(時)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "送信間隔", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = minHoursText,
                onValueChange = { minHoursText = it.filter(Char::isDigit).take(2) },
                label = { Text("最短(時間)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = maxHoursText,
                onValueChange = { maxHoursText = it.filter(Char::isDigit).take(2) },
                label = { Text("最長(時間)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(
                    quietStartText.toIntOrNull() ?: initialQuietStart,
                    quietEndText.toIntOrNull() ?: initialQuietEnd,
                    minHoursText.toIntOrNull() ?: initialMinHours,
                    maxHoursText.toIntOrNull() ?: initialMaxHours,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
    }
}
