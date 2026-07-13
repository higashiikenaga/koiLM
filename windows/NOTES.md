# koiLM Windows版 設計メモ

## スコープ
キャラクター作成時に、1枚絵(立ち絵)を自動生成する。メッセージ毎の生成やボタン式のオンデマンド生成は行わない(低コスト優先)。

## アーキテクチャ

Android版のJNI直結とは違い、Windows版はデスクトップの余裕あるリソースを活かし、
`llama.cpp` / `stable-diffusion.cpp` の**公式ビルド済みバイナリをsubprocessとして起動**する方式にする。
(Node用のネイティブアドオンを自前ビルドするより保守コストが低く、両プロジェクトの更新に追従しやすい)

- テキスト生成: `llama-server.exe` をアプリ起動時にバックグラウンド起動し、`http://127.0.0.1:<port>/completion` へPOSTする。
  モデルはAndroid版と同じGGUF (`sarashina2.2-1b` LoRAマージ後の量子化版) を流用可能。
  デスクトップはRAM制約が緩いため、将来的には7B版に切り替えても良い。
- 画像生成: `sd.exe` (stable-diffusion.cpp) をキャラ作成時のみ**都度起動**して1枚絵を生成し、
  `userData/characters/<id>/portrait.png` に保存する。常駐は不要(生成頻度が低いため)。

## 画像生成モデルの選定 (確定: Animagine XL 4.0 GGUF)

採用: `offgrid-ai/animagine-xl-4.0-GGUF` (ベース: cagliostrolab/animagine-xl-4.0, SDXL派生)

- ライセンス: CreativeML Open RAIL++-M(商用利用・再配布可) — 配布前提のアプリに組み込みやすい
- アニメ調イラストに特化(840万枚のアニメ画像で再学習済み)、タグベースプロンプト(danbooru形式)
- SDXL系のためCLIPテキストエンコーダーがGGUFに同梱、VAE等の別ファイル管理が不要
  (Z-Image-Turbo系はQwen3-4Bクラスの外部テキストエンコーダーが別途必要でトータルサイズが重くなるため見送り)
- Turbo系ではない通常のSDXLなので24〜28ステップ必要(数秒〜数十秒程度)だが、
  キャラ作成時の一度きりの生成なので許容範囲と判断
- 量子化: Q4_K(軽量・デフォルト) / Q8_0(4.18GB・高品質)
- 検討して見送った候補:
  - `JusteLeo/Anima-GGUF` (circlestone-labs非商用ライセンスのため配布に不向き)
  - `leejet/Z-Image-Turbo-GGUF` (Apache-2.0だが外部テキストエンコーダーが重い)

コマンド例:
```
sd -M img_gen -m animagine-xl-4.0-Q4_K.gguf -p "<tags>" -W 1024 -H 1024 --steps 24 --cfg-scale 5 --sampling-method dpm++2m -o out.png
```

参考: https://github.com/leejet/stable-diffusion.cpp / https://huggingface.co/offgrid-ai/animagine-xl-4.0-GGUF

## ディレクトリ構成 (このリポジトリ内)

```
windows/
  package.json
  src/
    main.js        # Electronメインプロセス、ウィンドウ生成、IPCハンドラ
    preload.js      # レンダラー向けAPI橋渡し
    llmEngine.js    # llama-server.exe起動・HTTP呼び出し
    imageGen.js     # sd.exe都度起動・ポートレート生成
    index.html      # 最小限のチャット/キャラ作成UI(プレースホルダー)
  bin/              # (gitignore) llama-server.exe, sd.exe をここに配置
  models/           # (gitignore) GGUFモデル一式をここに配置
```

## 未実装・TODO
- `bin/`, `models/` は容量が大きいため同梱せず、初回起動時にダウンロードさせるか
  READMEで手動配置を案内する形にする(配布方法はAndroid版のDLsite分割と同様の検討が必要)。
- チャットUI・キャラ管理UIはプレースホルダーのみ。Android版の機能(記憶管理・通知タイミング等)を
  どこまで移植するかは別途スコープを詰める。
- キャラの「appearance」テキストから画像生成用プロンプトへの変換ロジックは未実装(現状は
  appearanceフィールドをほぼそのまま渡すだけの簡易版)。
