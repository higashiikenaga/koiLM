package com.koilm.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.koilm.app.data.db.CharacterEntity
import com.koilm.app.data.db.ConversationTurnEntity
import com.koilm.app.device.DeviceSpecChecker
import com.koilm.app.llm.LlmEngineProvider
import com.koilm.app.notification.NotificationHelper
import com.koilm.app.settings.AppPreferences
import com.koilm.app.settings.ContentLevel
import com.koilm.app.settings.ReplyMode
import com.koilm.app.settings.RomanticTarget
import com.koilm.app.ui.CharacterEditorScreen
import com.koilm.app.ui.ChatScreen
import com.koilm.app.ui.ContactListScreen
import com.koilm.app.ui.SettingsScreen
import com.koilm.app.worker.DelayedReplyWorker
import com.koilm.app.worker.ProactiveMessageScheduler
import kotlinx.coroutines.launch

/** OFUSEの支援ページURL。 */
private const val OFUSE_SUPPORT_URL = "https://ofuse.me/a753ea67"

private sealed interface Screen {
    data object ContactList : Screen
    data object CreateCharacter : Screen
    data class Chat(val characterId: String) : Screen
    data object Settings : Screen
}

/**
 * アプリのエントリポイント(無料配布・投げ銭方式)。
 *
 * - メイン画面は「相手(キャラクター)一覧」。右上の歯車から設定画面(年齢確認・表現レベル・
 *   音声パック・OFUSE等)を開く。ユーザーは自分でキャラを作成し、システムプロンプト
 *   (口調・性格・世界観)を自由に決められる。
 * - 起動時: 通知タップ経由/コールドスタートを問わず、未消費の自発的メッセージがあれば
 *   該当キャラのチャット画面を直接開く。
 * - OFUSEでの支援は任意(外部ブラウザを開くだけで、アプリ自体は通信しない)。
 * - それ以外はすべて端末内で完結する(ネットワーク通信なし)。
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒否された場合はNotificationHelper側でnotify失敗を吸収する */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)

        NotificationHelper.ensureChannel(applicationContext)
        ensureNotificationPermission()
        ProactiveMessageScheduler.scheduleInitial(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(container)
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = container.preferences
    val isAgeVerified by prefs.isAgeVerified.collectAsState(initial = false)
    val contentLevel by prefs.contentLevel.collectAsState(initial = ContentLevel.NORMAL)
    val quietHoursStart by prefs.quietHoursStart.collectAsState(initial = AppPreferences.DEFAULT_QUIET_HOURS_START)
    val quietHoursEnd by prefs.quietHoursEnd.collectAsState(initial = AppPreferences.DEFAULT_QUIET_HOURS_END)
    val minIntervalHours by prefs.minIntervalHours.collectAsState(initial = AppPreferences.DEFAULT_MIN_INTERVAL_HOURS)
    val maxIntervalHours by prefs.maxIntervalHours.collectAsState(initial = AppPreferences.DEFAULT_MAX_INTERVAL_HOURS)
    val replyMode by prefs.replyMode.collectAsState(initial = ReplyMode.IMMEDIATE)
    val isRomanticTargetSelected by prefs.isRomanticTargetSelected.collectAsState(initial = false)
    val romanticTarget by prefs.romanticTarget.collectAsState(initial = RomanticTarget.BOTH)
    val affectionSystemEnabled by prefs.isAffectionSystemEnabled.collectAsState(initial = true)
    val busyHoursStart by prefs.busyHoursStart.collectAsState(initial = AppPreferences.DEFAULT_BUSY_HOURS_START)
    val busyHoursEnd by prefs.busyHoursEnd.collectAsState(initial = AppPreferences.DEFAULT_BUSY_HOURS_END)
    var showAgeGate by remember { mutableStateOf(false) }
    val deviceSpec = remember { DeviceSpecChecker.check(container.appContext) }

    var isModelImported by remember { mutableStateOf(container.modelImportManager.isModelImported()) }

    val importModelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { container.modelImportManager.importModel(uri) }
                    .onSuccess {
                        LlmEngineProvider.reset() // 新しいモデルを次回ロードで反映させる
                        isModelImported = true
                    }
            }
        }
    }

    if (!isModelImported) {
        // リリースビルドはadb run-asが使えないため、モデル導入はアプリ内SAFインポートのみが手段。
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "GGUFモデルファイルが未導入です。ダウンロード済みの .gguf ファイルを選択してください。",
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(onClick = { importModelLauncher.launch(arrayOf("*/*")) }) {
                Text("モデルファイルを選択")
            }
        }
        return
    }

    if (!isRomanticTargetSelected) {
        // 性別ではなく「誰との恋愛を描くか」を初回起動時に選んでもらう。
        // キャラクターごとのLoRAを分けるのではなく、system promptのトーン切り替えで対応する。
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "恋愛対象を選んでください",
                modifier = Modifier.padding(bottom = 24.dp),
            )
            RomanticTarget.entries.forEach { target ->
                Button(
                    onClick = { scope.launch { prefs.setRomanticTarget(target) } },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(target.label)
                }
            }
            Text(
                text = "後から設定画面で変更できます。",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    var characters by remember { mutableStateOf<List<CharacterEntity>>(emptyList()) }
    var screen by remember { mutableStateOf<Screen>(Screen.ContactList) }
    var messages by remember { mutableStateOf<List<ConversationTurnEntity>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }

    suspend fun reloadCharacters() {
        characters = container.characterRepository.listCharacters()
    }

    suspend fun reloadMessages(sessionId: String) {
        messages = container.database.chatDao().getTurns(sessionId)
    }

    // キャラ一覧はRoomへの単純なクエリだけなので、LLMエンジンのロード成否を待たず即座に読み込む。
    // (以前はLLMロードと同じLaunchedEffect内で行っていたため、モデルロードが遅い/失敗すると
    //  一覧がいつまでも表示されないバグがあった)
    LaunchedEffect(Unit) {
        try {
            reloadCharacters()
        } catch (e: Throwable) {
            Log.e("koilm_app", "キャラ一覧の読み込みに失敗しました", e)
        }
    }

    // 通知タップ/コールドスタート起動時に、未消費の自発的メッセージがあれば
    // 該当キャラのチャット画面を直接開く。LLMエンジンのロードもここで行う(時間がかかりうる)。
    LaunchedEffect(isModelImported) {
        Log.i("koilm_app", "LaunchedEffect開始: モデルロードを試みます")
        try {
            container.llmEngine()
            Log.i("koilm_app", "LLMエンジンロード完了")

            val chatDao = container.database.chatDao()
            val pending = chatDao.getLatestUnconsumedProactiveMessage()
            if (pending != null) {
                chatDao.markProactiveMessageConsumed(pending.id)
                reloadMessages(pending.sessionId)
                screen = Screen.Chat(pending.sessionId)
            }
            Log.i("koilm_app", "LaunchedEffect正常終了")
        } catch (e: Throwable) {
            Log.e("koilm_app", "LaunchedEffectで例外発生", e)
        }
    }

    when (val current = screen) {
        is Screen.ContactList -> {
            ContactListScreen(
                characters = characters,
                onOpenCharacter = { character ->
                    scope.launch { reloadMessages(character.id) }
                    screen = Screen.Chat(character.id)
                },
                onCreateCharacter = { screen = Screen.CreateCharacter },
                onOpenSettings = { screen = Screen.Settings },
                onToggleNotifications = { character ->
                    scope.launch {
                        container.characterRepository.setNotificationsEnabled(
                            character,
                            !character.notificationsEnabled,
                        )
                        reloadCharacters()
                    }
                },
                onBreakUp = { character ->
                    scope.launch {
                        container.characterRepository.breakUpWith(character)
                        reloadCharacters()
                    }
                },
            )
        }

        is Screen.CreateCharacter -> {
            CharacterEditorScreen(
                onSave = { name, systemPrompt ->
                    scope.launch {
                        container.characterRepository.createCharacter(name, systemPrompt)
                        reloadCharacters()
                        screen = Screen.ContactList
                    }
                },
                onCancel = { screen = Screen.ContactList },
            )
        }

        is Screen.Chat -> {
            val character = characters.find { it.id == current.characterId }
            if (character == null) {
                screen = Screen.ContactList
            } else {
                ChatScreen(
                    character = character,
                    messages = messages,
                    isSending = isSending,
                    onSendMessage = { text ->
                        if (replyMode == ReplyMode.TIMED) {
                            // 「タイミング」モード: ユーザー発言だけ即座に記録・表示し、
                            // 返信はDelayedReplyWorkerが最大15分以内のランダムなタイミングで
                            // 生成して通知する(その場では待たない=isSendingは立てない)。
                            scope.launch {
                                runCatching {
                                    container.chatRepository().recordUserMessageOnly(character.id, text)
                                }
                                reloadMessages(character.id)
                                DelayedReplyWorker.schedule(context, character.id)
                            }
                            return@ChatScreen
                        }

                        isSending = true
                        scope.launch {
                            runCatching {
                                container.chatRepository().sendMessage(
                                    sessionId = character.id,
                                    personaPrompt = character.systemPrompt,
                                    userInput = text,
                                    requestedLevel = contentLevel,
                                )
                            }
                            reloadMessages(character.id)
                            isSending = false
                        }
                    },
                    onBack = { screen = Screen.ContactList },
                )
            }
        }

        is Screen.Settings -> {
            SettingsScreen(
                deviceSpec = deviceSpec,
                contentLevel = contentLevel,
                isAgeVerified = isAgeVerified,
                showAgeGate = showAgeGate,
                onRequestAgeVerification = { showAgeGate = true },
                onAgeAnswer = { isAdult ->
                    scope.launch {
                        prefs.setAgeVerified(isAdult)
                        if (isAdult) {
                            prefs.setContentLevel(ContentLevel.NSFW)
                        }
                        showAgeGate = false
                    }
                },
                onContentLevelSelected = { level -> scope.launch { prefs.setContentLevel(level) } },
                onOpenOfuse = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFUSE_SUPPORT_URL)))
                },
                onBack = { screen = Screen.ContactList },
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
                minIntervalHours = minIntervalHours,
                maxIntervalHours = maxIntervalHours,
                onSaveNotificationTiming = { quietStart, quietEnd, minH, maxH ->
                    scope.launch {
                        prefs.setNotificationTiming(quietStart, quietEnd, minH, maxH)
                        ProactiveMessageScheduler.scheduleNext(context) // 新しい設定を直ちに反映
                    }
                },
                replyMode = replyMode,
                onReplyModeSelected = { mode -> scope.launch { prefs.setReplyMode(mode) } },
                romanticTarget = romanticTarget,
                onRomanticTargetSelected = { target -> scope.launch { prefs.setRomanticTarget(target) } },
                affectionSystemEnabled = affectionSystemEnabled,
                onAffectionSystemToggled = { enabled -> scope.launch { prefs.setAffectionSystemEnabled(enabled) } },
                busyHoursStart = busyHoursStart,
                busyHoursEnd = busyHoursEnd,
                onSaveBusyHours = { start, end -> scope.launch { prefs.setBusyHours(start, end) } },
            )
        }
    }
}
