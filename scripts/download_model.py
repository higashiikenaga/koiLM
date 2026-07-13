"""
ベースモデルのダウンロードスクリプト。

【リサーチ結果サマリー】(2026-07 時点, Hugging Face)
------------------------------------------------------------------
恋愛・会話・ロールプレイ(RP)特化、かつ表現の制限が緩い(Permissive/Uncensored)
オープンモデルの候補:

1. Aratako/Ninja-v1-RP-expressive-v2  (Qwen2.5系, ~7B)
   - 日本語RP特化。Task Vector合成+マージで作られた日本語ロールプレイモデル。
   - 感情豊かな地の文・台詞の生成に強い。ライセンスもCC-BY-NC系モデルを除外した緩め構成。
   - GGUF版: Aratako/Ninja-v1-RP-expressive-v2-GGUF (Android実機推論にそのまま使える)

2. shisa-ai/shisa-v2.1-qwen3-8b  (Qwen3ベース, 8B / 軽量版1.2B・3Bもあり)
   - 日本語性能が高いインストラクトモデル。素のままだと会話寄りだがLoRAでRP適性を伸ばしやすい。
   - 3B/1.2B版はAndroid等の超軽量端末向けに有力。

3. QuantFactory/Qwen2.5-7B-Instruct-Uncensored-GGUF
   - Qwen2.5-7B-Instructをアンセンサー化したモデルのGGUF量子化版。英語中心だが日本語も一定水準で対応。
   - 検閲/拒否応答が少なく、恋愛・RPデータでのLoRA適応がしやすい。

4. Qwen/Qwen2.5-3B-Instruct (ベースはApache-2.0, 素のままは検閲寄りだが軽量・多言語)
   - 3Bクラスでスマホ実機まで見据えた最軽量候補。LoRAでキャラ性・許容度を後付けする前提。

5. sbintuitions/sarashina2.2-1b-instruct-v0.1 (MITライセンス, 1B)
   - SB Intuitions(ソフトバンク系)による日本語ネイティブの軽量Instructモデル。
   - 「限定的な安全訓練しか受けていない」と明記されており、過度な検閲が入っていないため
     恋愛・RP用途でのLoRA適応がしやすい。1Bと超軽量でAndroid実機のメモリ制約に最も強い。
   - 実機テストでRAM7GB級端末では7Bモデルがメモリ不足で強制終了されたため、
     現状はこちらを実運用の第一候補としている。

本スクリプトでは学習用ベースに (5) sbintuitions/sarashina2.2-1b-instruct-v0.1 を
Android実機向け第一候補として推奨する(7B/3B系はハイエンドPC/上位端末向けの選択肢として残す)。
"""

import argparse
import os

from huggingface_hub import snapshot_download

MODEL_PRESETS = {
    # 学習(LoRA/QLoRA)用: 7B級、日本語RP特化・許容度が高い
    "ninja-rp-7b": "Aratako/Ninja-v1-RP-expressive-v2",
    # 学習用: 英語中心だがUncensored、7B級
    "qwen2.5-7b-uncensored": "QuantFactory/Qwen2.5-7B-Instruct-Uncensored-GGUF",
    # 学習用: 日本語性能が高いQwen3ベース、8B/3B/1.2Bあり
    "shisa-qwen3-8b": "shisa-ai/shisa-v2.1-qwen3-8b",
    # Android実機/超軽量端末向け: 3Bクラス
    "qwen2.5-3b": "Qwen/Qwen2.5-3B-Instruct",
    # 学習済みモデルのGGUF量子化版(推論・Android用)
    "ninja-rp-7b-gguf": "Aratako/Ninja-v1-RP-expressive-v2-GGUF",
    # Android実機向け第一候補: 1B、日本語ネイティブ、MITライセンス、検閲が緩い
    "sarashina2.2-1b": "sbintuitions/sarashina2.2-1b-instruct-v0.1",
    # 英語版(itch.io向け)第一候補: 1.5B、Apache-2.0、軽量・許容度も比較的高い
    "qwen2.5-1.5b": "Qwen/Qwen2.5-1.5B-Instruct",
}


def main():
    parser = argparse.ArgumentParser(description="ベースモデルをローカルにダウンロード")
    parser.add_argument(
        "--model",
        default="sarashina2.2-1b",
        choices=list(MODEL_PRESETS.keys()),
        help="ダウンロードするモデルのプリセット名",
    )
    parser.add_argument(
        "--output_dir",
        default=os.path.join(os.path.dirname(__file__), "..", "models"),
        help="保存先ディレクトリ",
    )
    parser.add_argument(
        "--revision", default=None, help="特定のリビジョン/ブランチを指定する場合"
    )
    args = parser.parse_args()

    repo_id = MODEL_PRESETS[args.model]
    local_dir = os.path.join(os.path.abspath(args.output_dir), args.model)
    os.makedirs(local_dir, exist_ok=True)

    print(f"[download_model] repo_id={repo_id}")
    print(f"[download_model] local_dir={local_dir}")

    snapshot_download(
        repo_id=repo_id,
        local_dir=local_dir,
        revision=args.revision,
        local_dir_use_symlinks=False,
    )

    print("[download_model] ダウンロード完了")
    print(f"[download_model] train.py --base_model \"{local_dir}\" で学習に利用できます")


if __name__ == "__main__":
    main()
