# koiLM — 恋愛・会話特化型SLM ローカルLoRA学習 & 完全ローカルアプリ

完全ローカル動作(外部通信一切なし)の恋愛・会話AIアプリです。
**現在の配布対象は Windows版(Electron) と WebUI版(PCで起動し、同じWi-Fi内のスマホ等からもアクセス可)です。**
(`android/` は開発初期のプロトタイプで、モバイル端末のRAM制約が大きいため現在は配布対象外・参考実装として残しています)

## ダウンロード

- **アプリ本体・軽量モデル(1B/Qwen2.5-1.5B)・推論エンジン(llama-server.exe)**: [Releases](https://github.com/higashiikenaga/koiLM/releases) から取得してください(`SETUP.txt`に配置方法を記載)。
- **高品質7Bモデル(任意)**: ファイルサイズが大きいため(4GB超)、[Hugging Face](https://huggingface.co/kk0518/koiLM-7b-gguf) で別途配布しています。
  導入すると設定画面から「高品質・低速(7B)」を選べるようになります。
- WebUI版はGitHubリポジトリを取得し、`webui/` フォルダで `npm install && npm start`(モデル配置は上記と同様)。
- 導入方法・言語切替(日本語/English)については [紹介ページ](https://higashiikenaga.github.io/koiLM/) を参照してください。
- 画像生成(キャラクター1枚絵の自動生成)は現在準備中で、まだ配布パッケージには含まれていません。

## 使用モデル・ライセンス

- 対話モデル(日本語・軽量): [sarashina2.2-1b-instruct-v0.1](https://huggingface.co/sbintuitions/sarashina2.2-1b-instruct-v0.1) (MIT) をベースに独自LoRAでファインチューニング
- 対話モデル(日本語・高品質版、任意): [Ninja-v1-RP-expressive-v2](https://huggingface.co/Aratako/Ninja-v1-RP-expressive-v2) ベースに同上LoRA
- 対話モデル(English): [Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct) (Apache-2.0)
- 画像生成モデル(準備中): [Animagine XL 4.0](https://huggingface.co/cagliostrolab/animagine-xl-4.0) (CreativeML Open RAIL++-M) を予定
- 推論ランタイム: [llama.cpp](https://github.com/ggml-org/llama.cpp) / [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (MIT)

違法行為・未成年者の搾取・なりすまし・誹謗中傷・差別扇動目的での利用は禁止します。

---

## 開発メモ(以下、学習パイプライン・旧Androidプロトタイプの技術詳細。現行配布物とは対象が異なります)

## 1. リサーチ結果: ベースモデル候補

| プリセット名 | Hugging Face リポジトリ | 特徴 |
|---|---|---|
| `ninja-rp-7b` (デフォルト推奨) | [Aratako/Ninja-v1-RP-expressive-v2](https://huggingface.co/Aratako/Ninja-v1-RP-expressive-v2) | 日本語RP特化。Task Vector合成による日本語ロールプレイモデル。地の文・感情表現が豊か。CC-BY-NC系を除外した緩めライセンス構成 |
| `ninja-rp-7b-gguf` | [Aratako/Ninja-v1-RP-expressive-v2-GGUF](https://huggingface.co/Aratako/Ninja-v1-RP-expressive-v2-GGUF) | 上記のGGUF量子化版。Android実機推論にそのまま利用可 |
| `qwen2.5-7b-uncensored` | [QuantFactory/Qwen2.5-7B-Instruct-Uncensored-GGUF](https://huggingface.co/QuantFactory/Qwen2.5-7B-Instruct-Uncensored-GGUF) | Qwen2.5-7B-InstructをUncensored化。拒否応答が少なく英語中心 |
| `shisa-qwen3-8b` | [shisa-ai/shisa-v2.1-qwen3-8b](https://huggingface.co/shisa-ai/shisa-v2.1-qwen3-8b) | 日本語性能が高いQwen3ベース。1.2B/3B軽量版もあり |
| `qwen2.5-3b` | [Qwen/Qwen2.5-3B-Instruct](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct) | Apache-2.0、3Bクラスの多言語モデル。Android実機の超軽量候補 |

参考: EVA-UNIT-01 の Eva-Qwen2.5 系(1.5B/7B/14B/32B, ChatMLロールプレイデータでUncensored学習)も
英語RPでは高評価ですが、日本語表現力は Ninja-RP 系のほうが優位です。

学習の起点には日本語RP適性が最初から高い `ninja-rp-7b` を推奨。
Android実機で1.5B〜3Bクラスが必要な場合は `qwen2.5-3b` にLoRAをマージしGGUF量子化してください。

## 2. リサーチ結果: データセット候補

- **ShareGPT形式の会話・RPデータ**: `Aratako` 氏が公開している日本語RP/雑談コーパス群(Ninjaシリーズの学習に使用されたもの)。
- **恋愛小説・ライトノベル系コーパス**: 日本語の小説投稿サイト由来のクロールデータセット(ライセンスに要注意、二次配布不可のものが多いため自前データでの補完を推奨)。
- **Airoboros系会話データ**: 英語中心のマルチターン会話・キャラRPデータ。日本語データと混ぜて多様性を確保する用途に有効。

`data/sample_dataset.jsonl` に、上記を参考にした「地の文+感情豊かな口調」を含む
ShareGPT形式の恋愛チャットRPサンプルを16件、動作確認用にモック生成済みです。

### 実データセットからの自動データ生成 (`prepare_data.py`)

実在の日本語RPデータセットから学習データを自動生成するパイプラインです。

```bash
python prepare_data.py --sources aratako personacast \
    --max_samples_per_source 300 --min_score 1.0 --output data/train.jsonl
```

- [Aratako/Synthetic-Japanese-Roleplay-SFW-DeepSeek-V3-0324-20k-formatted](https://huggingface.co/datasets/Aratako/Synthetic-Japanese-Roleplay-SFW-DeepSeek-V3-0324-20k-formatted) (MIT)
- [RumiaChannel/PersonaCast-JA](https://huggingface.co/datasets/RumiaChannel/PersonaCast-JA) (CC0-1.0)

streamingで取得するため全件ダウンロード不要、`--max_samples_per_source`で打ち切れます。
「」外のナレーション量+感情語彙の出現数で「エモさスコア」を算出し、スコア降順で
`data/train.jsonl`に書き出した後、自動でJSON妥当性・ターン交互性・平均ターン数を検証します。
PersonaCast-JAはデータセットの内部スキーマが公式に確定していないため、フィールド名を
複数候補(エイリアス)から探す防御的な実装になっており、実際に少数サンプルで動作確認済みです。

注意: `aratako`ソースは「SFW」と銘打たれていますが、実データを確認したところ性描写を含む
R18相当のコンテンツも混在しています。学習に使う前に `--min_score` や独自のNGワードフィルタ等で
用途に応じた追加スクリーニングを行うことを推奨します。

## 3. セットアップ

```bash
pip install -r requirements.txt

# ベースモデルのダウンロード(デフォルト: ninja-rp-7b)
python scripts/download_model.py --model ninja-rp-7b

# QLoRA学習(VRAM 8-24GB想定)
python train.py --base_model models/ninja-rp-7b --dataset data/sample_dataset.jsonl \
    --output_dir output/koilm-lora --epochs 3

# 対話テスト(学習前)
python chat.py --base_model models/ninja-rp-7b

# 対話テスト(学習後 LoRA適用 + ローカル記憶マネージャー有効化)
python chat.py --base_model models/ninja-rp-7b --lora_dir output/koilm-lora --with_memory
```

VRAMが厳しい場合は `--micro_batch_size 1 --max_seq_len 1024` 等で調整してください。

## 4. 端末内メモリ管理(長期記憶の強化)

`local_memory/context_manager.py` に、サーバーを使わず端末内SLM自身で
過去ログを要約・圧縮し、System Promptへ自動で差し込む `ContextMemoryManager` を実装しています。

- 会話ターン数が閾値(デフォルト20)を超えると、古いログを要約 → 「長期記憶サマリ」に圧縮。
- 正規表現ベースで「呼び方」「好きな話題」「地雷ワード」等をプロフィールメモリとして抽出。
- 推論直前に (キャラ設定 + プロフィール + 長期記憶サマリ) を文字数予算内で合成しSystem Promptへ注入。

単体で動作確認する場合:

```bash
python local_memory/context_manager.py
```

Android版は `android/app/.../memory/ContextMemoryManager.kt` に同じロジックを、
永続化をRoom(SQLite)、要約実行を端末内 `LlamaCppEngine` に置き換えて実装しています。

## 5. Android アプリ化(完全ローカル・通信ゼロ)

詳細アーキテクチャは [android/README_ARCHITECTURE.md](android/README_ARCHITECTURE.md) を参照してください。

要点:
- モデルはGGUF形式に量子化(Q4_K_M推奨)し、`llama.cpp` をNDK/JNI経由で直接呼び出す。
- `AndroidManifest.xml` から `INTERNET` 権限を明示的に除去し(`tools:node="remove"`)、
  サーバー通信が発生しない構成を保証(電気通信事業の届出対象外の構成)。
- 会話履歴・要約メモリ・ユーザープロフィールはすべてRoom(SQLite)で端末内に永続化。
- 表現レベルは「通常表現 / SFW / NSFW」の3段階。NSFW選択時は年齢確認ダイアログ
  (「あなたは18歳以上ですか？」)を挟み、判定結果は端末内(DataStore)にのみ保存(詳細は5-1)。
- 7Bモデル+TTSを動かす推奨スペックは **RAM 8GB以上・空き容量10GB以上・2022年以降フラグシップ級SoC**
  (最低ラインはRAM6GB/空き容量8GB)。起動時に`DeviceSpecChecker`が自動判定し、
  不足時は画面に警告バナーを表示する(詳細は[android/README_ARCHITECTURE.md](android/README_ARCHITECTURE.md)の「スマホでの推奨スペック」)。
- ディレクトリ構成:
  ```
  android/
    settings.gradle.kts
    app/
      build.gradle.kts
      src/main/
        AndroidManifest.xml
        cpp/
          CMakeLists.txt         # llama.cpp submoduleをビルド
          llama-android.cpp      # JNIブリッジ実装(モデルロード/生成)
        java/com/koilm/app/
          MainActivity.kt          # 年齢確認・表現レベルUIのエントリポイント
          llm/LlamaBridge.kt       # external fun 宣言のみ
          llm/LlamaCppEngine.kt    # Kotlinラッパー(コルーチン)
          memory/ContextMemoryManager.kt  # 長期記憶マネージャー
          settings/ContentLevel.kt        # 通常表現/SFW/NSFWの定義
          settings/AppPreferences.kt      # 年齢確認・表現レベルの端末内永続化(DataStore)
          ui/AgeGateScreen.kt             # 年齢確認画面
          ui/ContentLevelSelector.kt      # 表現レベル選択UI(NSFWはロック表示)
          data/ChatRepository.kt   # sendMessage()で年齢確認を多層防御チェック
          data/VoicePackRepository.kt     # 音声パックDLCのインポート/選択状態を統括
          data/db/Entities.kt      # 会話ログ/要約/プロフィールのRoom Entity
          data/db/ChatDao.kt
          data/db/AppDatabase.kt
          data/db/VoicePackEntity.kt / VoicePackDao.kt        # 音声パックDLCのメタデータ
          data/db/PendingProactiveMessageEntity.kt            # 自発メッセージの未消費キュー
          worker/AIBackgroundWorker.kt          # 自発的メッセージ生成(CoroutineWorker)
          worker/ProactiveMessageScheduler.kt   # 朝/夜狙い撃ち or 数時間おきランダムの再スケジュール
          notification/NotificationHelper.kt    # Local NotificationのChannel/表示
          tts/LocalTTSEngine.kt        # ONNX Runtime TTS(動的モデル切替対応)
          tts/WavEncoder.kt            # float波形→WAVバイト列
          tts/AudioPlaybackManager.kt  # AudioTrackによる即時再生
          tts/VoicePackManager.kt      # SAF経由ZIPインポート(Zip Slip対策込み)
          tts/VoiceChatCoordinator.kt  # フォアグラウンド/バックグラウンド再生の共通ロジック
          device/DeviceSpecChecker.kt      # 7B+TTSの推奨/最低スペック判定(RAM・空き容量)
          ui/DeviceSpecWarningBanner.kt    # スペック不足時の警告バナーUI
          AppContainer.kt          # 手動DIコンテナ(各層の依存を集約)
  ```
- 導入手順の概要:
  1. `git submodule add https://github.com/ggml-org/llama.cpp android/app/src/main/cpp/llama.cpp`
  2. `llama-android.cpp` の `generate()` を公式 `examples/llama.android` 準拠でトークン生成ループまで実装
  3. LoRAマージ済みモデルを `convert_hf_to_gguf.py` → `llama-quantize` でQ4_K_M化し、端末に配置
  4. Android Studio でビルド(NDK/CMakeが必要)

### 5-1. 年齢確認・表現レベル制御(NSFWゲーティング)

`prepare_data.py`実行時に判明した通り、学習データにR18相当の表現が混在し得るため、
Androidアプリ側でも表現レベルに応じたゲーティングを実装している。

- 表現レベルは `ContentLevel.NORMAL / SFW / NSFW` の3段階(`settings/ContentLevel.kt`)。
- `ui/ContentLevelSelector.kt` の3段階セグメントUIでNSFWを選択しようとすると、
  `ui/AgeGateScreen.kt`(「あなたは18歳以上ですか？」)が表示される。
- 回答結果は `settings/AppPreferences.kt` 経由でDataStore(端末内ファイル)にのみ保存。
  18歳未満と回答した場合はNSFWが選択不可になり、既存の設定がNSFWだった場合はSFWへ自動降格する。
- `data/ChatRepository.kt` の `sendMessage()` は、UIの制御をすり抜けてNSFWが指定されても
  年齢確認フラグを再チェックし、未確認ならSFWへ強制降格してから生成する(多層防御)。
- これらの判定・保存はすべて端末内で完結し、サーバーへの通信は一切発生しない。

### 5-2. AIからの自発的メッセージ生成(WorkManager)とTTS連携

アプリを閉じていても、キャラから時々話しかけてくる仕組みを実装している。詳細は
[android/README_ARCHITECTURE.md](android/README_ARCHITECTURE.md) を参照。

- `ProactiveMessageScheduler` が「朝8:00/夜20:00のどちらか近い方」または
  「2〜6時間後のランダム」のいずれかで次回実行を自己再スケジュール(WorkManagerの
  `PeriodicWorkRequest`は固定間隔しか扱えないため、`OneTimeWorkRequest`を積み直す方式)。
- `AIBackgroundWorker` が直近の会話履歴+時間帯からSLMで自発的な一言を生成し、
  会話履歴への保存・`PendingProactiveMessageEntity`への保留登録・Local Notification発火まで行う。
- アプリを開いてチャット中は `VoiceChatCoordinator.sendAndSpeak()` がSLM応答を
  `LocalTTSEngine`(ONNX Runtime)でその場で音声合成し`AudioPlaybackManager`(AudioTrack)で再生。
- 通知をタップしてアプリを開くと `VoiceChatCoordinator.consumePendingProactiveMessage()` が
  未消費の自発メッセージを取り出し、同じ経路で即座に音声再生する。

### 5-3. 追加音声パック(DLC)の動的インポート

アプリのアップデートなしに、外部で入手した音声パック(ONNXモデル+`voice_config.json`を含むZIP)を
追加できる。

- `VoicePackManager.importVoicePack()` がSAF(`OpenDocument`)経由で選ばれたZIPを
  `filesDir/voice_packs/<packId>/` へ展開(Zip Slipパストラバーサル対策・zip bombサイズ上限あり)。
- メタデータ(`character_name`/`voice_style`/`model_file`)を`VoicePackEntity`としてRoomに永続化。
- 選択中の音声パックIDは`AppPreferences`(DataStore)に保存し、次回起動時・WorkManager実行時にも
  同じ声を引き継ぐ。
- `VoiceChatCoordinator`がチャット送信時・自発メッセージ再生時のどちらでも選択中パックの
  `modelPath`を`LocalTTSEngine.loadModel()`に渡し、OrtSessionを動的に再構築する
  (直前と同じモデルなら再ロードをスキップ)。
