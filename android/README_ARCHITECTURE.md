# koiLM Android アーキテクチャ概要

## ゴール
- サーバー通信を一切行わない(=電気通信事業の届出対象外)完全ローカル動作。
- 1.5B〜3Bクラスの超軽量モデルをGGUF量子化し、端末内でllama.cppにより直接推論。
- 会話履歴と「要約メモリ」をSQLite(Room)に永続化し、端末内だけで長期記憶を実現。

## 全体構成

```
[UI (Compose)]
      │  ユーザー入力
      ▼
[ChatViewModel]
      │  1) ChatRepository.sendMessage(text)
      ▼
[ChatRepository] ── 会話履歴/要約メモリ取得 ──▶ [Room DB (SQLite)]
      │
      │  2) ContextMemoryManager.buildSystemPrompt()
      │     (キャラ設定 + プロフィールメモリ + 長期記憶サマリ を合成)
      ▼
[LlamaCppEngine (Kotlin)]
      │  JNI経由
      ▼
[llama-android.cpp / llama.cpp (C++, NDK)]
      │  GGUFモデルをロードして推論(サーバー通信なし、端末内で完結)
      ▼
[応答テキスト] ──▶ ChatRepository が Room に保存 ──▶ UIに表示
                         │
                         └─ 一定ターン数を超えたら
                            ContextMemoryManager が同じLlamaCppEngineで
                            「要約プロンプト」を生成 → 長期記憶サマリを更新して保存
```

ポイント: 要約もチャット応答と同じ端末内モデル(LlamaCppEngine)を使い回す。
外部APIには一切通信しないため、ネットワーク権限自体を付けない構成にできる
(AndroidManifest.xml で `android.permission.INTERNET` を要求しない)。

## モデル配置

- モデルファイル(.gguf)はアプリに同梱せず、初回起動時にユーザーが
  ローカルストレージ(端末内のファイル、SDカード等)から選択してコピーする方式を推奨。
  (aabにモデルを同梱すると配布サイズが肥大化し、ストア審査上も扱いが煩雑になるため)
- 推奨モデル: Qwen2.5-3B-Instruct や Ninja-v1-RP系をベースにLoRAマージ後、
  `llama.cpp/convert_hf_to_gguf.py` でGGUF化 → `llama-quantize` で Q4_K_M 等に量子化。
  3Bクラス Q4_K_M で概ね2GB前後、8GB RAM端末でも動作可能な範囲。

## スマホでの推奨スペック(7Bモデル + TTS)

`ninja-rp-7b`のような7BクラスをQ4_K_M量子化してAndroid実機で動かす場合、判定ロジックは
[`device/DeviceSpecChecker.kt`](app/src/main/java/com/koilm/app/device/DeviceSpecChecker.kt)
に実装しており、起動時に自動で判定してスペック不足時は`ui/DeviceSpecWarningBanner.kt`で警告表示する。

| 項目 | 最低ライン | 推奨ライン |
|---|---|---|
| RAM(端末総容量) | 6GB以上 | **8GB以上**(できれば12GB) |
| 空きストレージ | 8GB以上 | **10GB以上** |
| CPU/SoC | ARM64-v8a、NEON対応 | Snapdragon 8 Gen1 / Dimensity 9000 以降の2022年以降フラグシップ級 |
| Android バージョン | 8.0(API 26)以上 | 10以降 |

根拠:
- 7B Q4_K_M量子化GGUFのファイルサイズは約4.0〜4.5GB。
- llama.cppはmmapでモデルを読み込むが、KVキャッシュ(コンテキスト長に比例)・推論バッファ・
  Android自体のシステム常駐分を合わせると、実効RAM使用量はモデルファイルサイズの
  1.5〜2倍程度(6〜8GB前後)になりやすい。ミドルレンジ機(RAM6GB以下)では
  OSに強制終了(OOM Kill)されるリスクが高い。
- ONNX Runtime TTS(VITS系、数十〜数百MB)の推論もLLMと同時にRAM/CPUを使うため、
  LLM単体の要件に上乗せで見積もる必要がある。
- CPUが非力な機種(2020年以前のミドルレンジ等)では、7Bモデルの生成速度が
  実用に耐えない(1トークン/秒を大きく下回る)ことがある。より快適に動かしたい場合は
  1.5B〜3Bクラスへのダウングレードも検討すること(「モデル配置」参照)。

## llama.cpp の組み込み方

1. `android/app/src/main/cpp/` に llama.cpp を git submodule として配置
   (`git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp`)。
2. `CMakeLists.txt` で `add_subdirectory(llama.cpp)` し、`llama` ライブラリをリンク。
3. `llama-android.cpp` にJNIブリッジ関数(`Java_com_koilm_app_llm_LlamaBridge_*`)を実装し、
   `llama_model_load`, `llama_new_context`, `llama_decode` 等をラップする。
   (公式 `llama.cpp/examples/llama.android` のJNI実装を土台にすることを推奨)
4. Kotlin側は `LlamaBridge`(`external fun` 宣言のみ)と、それを使いやすくラップする
   `LlamaCppEngine`(コルーチンでトークンをストリーミング出力)の2層構成にする。

## 年齢確認・表現レベル制御(NSFWゲーティング)

成人向け(R18相当)表現をアプリ内で扱うため、以下の仕組みを完全ローカルで実装している。

- `settings/ContentLevel.kt`: 「通常表現 → SFW → NSFW」の3段階。各レベルごとに
  System Promptへ差し込む指示文(`promptDirective()`)を持つ。
- `settings/AppPreferences.kt`: 年齢確認済みフラグ・選択中の表現レベルを
  DataStore(端末内ファイル)にのみ保存する。外部送信は一切行わない。
- `ui/AgeGateScreen.kt`: 「あなたは18歳以上ですか？」を尋ねる確認画面。
  NSFWを選択しようとした時点(=R18相当の内容に到達する直前)で表示される。
  18歳未満と回答した場合はNSFWを選択不可にし、既にNSFWだった場合はSFWへ強制降格する。
- `ui/ContentLevelSelector.kt`: 3段階のセグメントコントロールUI。年齢確認未完了時は
  NSFWがロック表示(🔒)され、タップすると `AgeGateScreen` が呼び出される。
- `data/ChatRepository.kt`: UIの表示制御だけに頼らず、`sendMessage()` 内でも
  年齢確認フラグを再チェックし、未確認なのにNSFWが指定された場合は
  強制的にSFWへ降格してから生成する(多層防御)。

この年齢確認・表現レベルの判定と保存はすべて端末内で完結し、サーバーへの問い合わせや
送信は発生しない(通信ゼロの設計方針と一貫している)。

## AIからの自発的メッセージ生成(WorkManager)

アプリを閉じていても、キャラから時々話しかけてくる仕組みを完全ローカルのWorkManagerで実装している。

```
[ProactiveMessageScheduler]
      │ OneTimeWorkRequest(初回 or 前回実行の最後)
      │ 次回タイミング = 50%: 朝8:00/夜20:00のどちらか近い方
      │              50%: 2〜6時間後のランダム
      ▼
[AIBackgroundWorker (CoroutineWorker)]
      │ 1) ChatDao から直近セッションの会話履歴を取得
      │ 2) 現在時刻から 朝/昼/夜/深夜 を判定
      │ 3) LlmEngineProvider経由でLlamaCppEngineに「自発的な一言」を生成させる
      │ 4) 生成結果を ConversationTurnEntity(会話履歴) と
      │    PendingProactiveMessageEntity(未消費フラグ付き) の両方に保存
      │ 5) NotificationHelper でLocal Notificationを発火
      └ finally: ProactiveMessageScheduler.scheduleNext() で次回分を必ず再予約
```

- `worker/AIBackgroundWorker.kt`: 生成ロジック本体。
- `worker/ProactiveMessageScheduler.kt`: 「朝/夜狙い撃ち or 数時間おきランダム」のタイミング計算と
  WorkManagerへのユニークワーク登録(`enqueueUniqueWork` + `ExistingWorkPolicy.REPLACE`)。
- `notification/NotificationHelper.kt`: `NotificationChannel`の作成とLocal Notificationの表示。
  通知のPendingIntentには`PendingProactiveMessageEntity`のIDを積み、タップ時に`MainActivity`が
  そのメッセージを特定できるようにしている。
- `llm/LlmEngineProvider.kt`: モデルロードは高コストなため、チャット画面とWorkerの両方で
  同じ`LlamaCppEngine`インスタンスを使い回すApplicationスコープのシングルトン。

## 端末内音声合成(ONNX Runtime TTS)とフォアグラウンド/バックグラウンド連携

```
[アプリ起動中: チャット]                    [アプリ閉鎖中]
VoiceChatCoordinator.sendAndSpeak()         AIBackgroundWorker が
   │ ChatRepository.sendMessage()             PendingProactiveMessageEntity を保存
   │ ensureVoicePackLoaded()                   → NotificationHelper が通知発火
   │ LocalTTSEngine.synthesize()                  │ ユーザーが通知タップ
   ▼                                              ▼
AudioPlaybackManager.play()                 MainActivity起動
                                               → VoiceChatCoordinator
                                                   .consumePendingProactiveMessage()
                                               → 同じ ensureVoicePackLoaded() → synthesize() → play()
```

- `tts/LocalTTSEngine.kt`: `onnxruntime-android`のOrtSessionをラップ。
  `loadDefaultModel()`でassets同梱モデル、`loadModel(path)`で任意パスのモデル(音声パックDLC)を
  ロードでき、既存セッションを閉じて新セッションに差し替える動的切り替えに対応。
  入力テンソル名(`input`/`input_lengths`/`scales`/`sid`)はVITS系ONNXエクスポートの一般的な
  構成を想定した骨子であり、実際に使用するモデルのシグネチャに合わせて調整が必要。
- `tts/WavEncoder.kt`: モデル出力のfloat波形を16bit PCM WAVバイト列に変換。
- `tts/AudioPlaybackManager.kt`: `AudioTrack`でWAVを即時再生(一時ファイル経由のMediaPlayerより低遅延)。
- `tts/VoiceChatCoordinator.kt`: フォアグラウンド(チャット送信直後の再生)と
  バックグラウンド(通知タップ後の再生)の両方で同じ「音声パック解決→合成→再生」ロジックを共有する。
  直前と同じモデルパスなら再ロードをスキップするキャッシュを持つ。

## 追加音声パック(DLC)の動的インポート

アプリのアップデートなしに、ユーザーが外部(DLSite等)で入手した音声パックを追加できる。

```
[SAF: OpenDocument("application/zip")]
      │ ユーザーがZIPを選択
      ▼
[VoicePackManager.importVoicePack(uri)]
      │ filesDir/voice_packs/<packId>/ へZIP展開
      │ (Zip Slip対策: 各エントリの展開先が destDir 配下か canonicalPath で検証)
      │ (zip bomb対策: 展開後合計サイズに上限を設定)
      │ voice_config.json を探索してメタデータ(character_name/voice_style/model_file)を抽出
      ▼
[VoicePackEntity(Room)に登録] ── VoicePackRepository.importAndRegister()
      │
      ▼
[設定画面でパックを選択] → AppPreferences(DataStore)に selectedVoicePackId を保存
      │
      ▼
[VoiceChatCoordinator.ensureVoicePackLoaded()]
   チャット送信時・自発メッセージ再生時のどちらでも、選択中パックのmodelPathを
   LocalTTSEngine.loadModel()に渡してOrtSessionを動的に再構築する
```

想定するZIPの中身(直下でもネストしていても可):
```
model.onnx           # VITS/Style-Bert-VITS2系のONNXモデル本体
voice_config.json    # {"character_name": "つんちゃん", "voice_style": "tsundere", "model_file": "model.onnx"}
```

- `tts/VoicePackManager.kt`: SAF Uriからのインポート・展開・メタデータ抽出・削除。
- `data/db/VoicePackEntity.kt` / `data/db/VoicePackDao.kt`: インポート済みパックの永続化(Room)。
- `data/VoicePackRepository.kt`: `VoicePackManager` + `VoicePackDao` + `AppPreferences` を束ね、
  「インポート」「一覧」「選択中パックの解決」を1つのAPIにまとめる。
- `settings/AppPreferences.kt`: `selectedVoicePackId` を追加し、次回起動時・WorkManager実行時にも
  選択中の声を引き継げるようにしている。

すべての処理(ZIP展開・メタデータ管理・選択状態)は端末内ストレージとRoom/DataStoreのみで完結し、
外部サーバーへの問い合わせは発生しない。

## 通信ゼロを保証する設計上の注意

- `AndroidManifest.xml` に `INTERNET` パーミッションを追加しない
  (追加した瞬間に「通信を行うアプリ」と扱われるリスクがあるため、依存ライブラリ側の
  自動追加にも注意し、`<uses-permission ... tools:node="remove">` で明示的に除去する)。
- クラッシュレポートや解析SDK(Firebase Analytics等)はサーバー通信を伴うため組み込まない。
- モデル取得のみ初回にユーザー操作で行い(ファイル選択 or 事前にPC等でDL済みのファイルを転送)、
  アプリ自身が実行時にモデルをダウンロードする機能は持たせない。

## 無料配布・投げ銭方式(OFUSE連携)

有料ライセンスキー認証は廃止し、**無料配布+任意の投げ銭(OFUSE)**方式に変更した。
アプリ自体は引き続き通信ゼロで、OFUSEへの導線はOSの外部ブラウザを開くだけ
(`Intent.ACTION_VIEW`)なので、アプリがネットワーク通信を行うわけではない。

- `MainActivity.kt` の `OFUSE_SUPPORT_URL` 定数に実際のOFUSEページURLを設定すること
  (現状はプレースホルダ `https://ofuse.me/REPLACE_WITH_YOUR_HANDLE`)。
- 「☕ OFUSEで応援する」ボタンをタップすると、外部ブラウザでOFUSEページが開く。
  OFUSEには購入完了通知APIがないため、支援自体は完全に任意・アプリの機能制限とは無関係。

### リリース署名
- `android/keystore.properties` に署名鍵の設定を持つ(`koilm-release.keystore`)。
  **この2ファイルは絶対に公開しないこと**(将来gitリポジトリ化する際は`.gitignore`必須)。
- ビルド: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- リリースビルドは`debuggable=false`のため、`adb shell run-as`が使えない
  (=開発時のようにadb経由でモデルファイルを直接書き込めない)。
  そのためエンドユーザー向けに `llm/ModelImportManager.kt` を用意し、
  SAF経由で手元の.ggufファイルをアプリ内から選択・インポートできるようにしている。
