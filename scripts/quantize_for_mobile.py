"""
学習済み(または学習前)モデルを、Android実機で動かせるGGUF量子化モデルに変換するパイプライン。

ターゲット端末: Snapdragon 888 5G / Dimensity 7400 Ultra 以上クラス
(2021年前後の上位ミドル〜フラッグシップ、dotprod命令対応、RAM 8GB級)

【なぜ Q4_K_S をデフォルトにしているか】
- 上記クラスのSoCはARM dotprod命令(SDOT/UDOT)には対応しているが、
  2022年以降フラッグシップのi8mm命令には対応していないことが多い。
  llama.cppのK-quantカーネルはdotprodで十分に高速化されるため、Q4_K系は実用速度で動く。
- ただしRAMは8GB機が主流帯(12GBモデルもあるが8GBが下限)なので、
  Q4_K_M(7Bで約4.4GB)よりひと回り小さい Q4_K_S(約4.1GB)を既定にし、
  KVキャッシュ・OS常駐分・TTS用メモリの余裕を確保している。
- さらに余裕を持たせたい場合は --quant_types Q3_K_M も併用可能(品質はやや低下)。

処理の流れ:
    1. (LoRAアダプタが学習済みなら) ベースモデルにマージしてfp16フルモデルを生成
    2. llama.cppのconvert_hf_to_gguf.pyでGGUF(F16)に変換
    3. llama-quantizeでターゲット量子化タイプ(既定: Q4_K_S)に変換

使い方:
    # LoRA学習前(ベースモデルのみ)で動作確認する場合
    python scripts/quantize_for_mobile.py --base_model models/ninja-rp-7b

    # LoRA学習後、ファインチューン結果を反映する場合
    python scripts/quantize_for_mobile.py --base_model models/ninja-rp-7b \
        --lora_dir output/koilm-lora
"""

import argparse
import os
import shutil
import subprocess
import sys

# Snapdragon 888 5G / Dimensity 7400 Ultra クラス(dotprod対応・RAM8GB級)向けの既定量子化タイプ。
DEFAULT_QUANT_TYPES = ["Q4_K_S"]

# winget (ggml.llamacpp) でインストールした場合の既定パス。
WINGET_LLAMA_QUANTIZE = (
    r"C:\Users\sanyo\AppData\Local\Microsoft\WinGet\Packages"
    r"\ggml.llamacpp_Microsoft.Winget.Source_8wekyb3d8bbwe\llama-quantize.exe"
)


def parse_args():
    p = argparse.ArgumentParser(description="Android実機(Snapdragon 888/Dimensity 7400 Ultra級)向けGGUF量子化パイプライン")
    p.add_argument("--base_model", default="models/ninja-rp-7b")
    p.add_argument("--lora_dir", default="output/koilm-lora", help="学習済みLoRAアダプタ(無ければ自動でスキップしベースモデルのみ変換)")
    p.add_argument("--merged_dir", default="models/ninja-rp-7b-merged")
    p.add_argument("--llama_cpp_dir", default="tools/llama.cpp", help="convert_hf_to_gguf.pyを含むllama.cppのローカルクローン")
    p.add_argument("--llama_quantize_bin", default=None, help="llama-quantize(.exe)のパス。省略時はPATH→wingetの既定パスの順に自動検出")
    p.add_argument("--outdir", default="models/gguf")
    p.add_argument(
        "--quant_types",
        nargs="+",
        default=DEFAULT_QUANT_TYPES,
        help="生成する量子化タイプ(複数指定可)。例: Q4_K_S Q4_K_M Q3_K_M",
    )
    p.add_argument("--skip_merge", action="store_true", help="LoRAが存在してもマージせずベースモデルをそのまま変換する")
    return p.parse_args()


def find_llama_quantize(explicit_path: str | None) -> str:
    if explicit_path:
        if not os.path.exists(explicit_path):
            raise FileNotFoundError(f"指定された llama-quantize が見つかりません: {explicit_path}")
        return explicit_path

    found = shutil.which("llama-quantize") or shutil.which("llama-quantize.exe")
    if found:
        return found

    if os.path.exists(WINGET_LLAMA_QUANTIZE):
        return WINGET_LLAMA_QUANTIZE

    raise FileNotFoundError(
        "llama-quantize が見つかりません。`winget install ggml.llamacpp` でインストールするか、"
        "--llama_quantize_bin で明示的にパスを指定してください。"
    )


def has_lora_adapter(lora_dir: str) -> bool:
    return os.path.exists(os.path.join(lora_dir, "adapter_config.json"))


def merge_lora(base_model: str, lora_dir: str, merged_dir: str) -> str:
    """LoRAアダプタをベースモデルにマージし、fp16のフルモデルとして保存する。"""
    import torch
    from peft import PeftModel
    from transformers import AutoModelForCausalLM, AutoTokenizer

    print(f"[quantize_for_mobile] LoRAマージ開始: base={base_model} lora={lora_dir}")
    tokenizer = AutoTokenizer.from_pretrained(base_model, trust_remote_code=True)

    model = AutoModelForCausalLM.from_pretrained(
        base_model,
        torch_dtype=torch.float16,
        device_map="cpu",  # マージ処理はCPUで十分(量子化前提のためVRAM節約)
        trust_remote_code=True,
    )
    model = PeftModel.from_pretrained(model, lora_dir)
    model = model.merge_and_unload()

    os.makedirs(merged_dir, exist_ok=True)
    model.save_pretrained(merged_dir, safe_serialization=True)
    tokenizer.save_pretrained(merged_dir)
    print(f"[quantize_for_mobile] マージ済みモデルを保存: {merged_dir}")
    return merged_dir


def convert_to_gguf_f16(llama_cpp_dir: str, src_model_dir: str, outdir: str) -> str:
    convert_script = os.path.join(llama_cpp_dir, "convert_hf_to_gguf.py")
    if not os.path.exists(convert_script):
        raise FileNotFoundError(
            f"{convert_script} が見つかりません。"
            f"`git clone --depth 1 https://github.com/ggml-org/llama.cpp {llama_cpp_dir}` を実行してください。"
        )

    os.makedirs(outdir, exist_ok=True)
    f16_path = os.path.join(outdir, "model-f16.gguf")

    cmd = [
        sys.executable, convert_script,
        src_model_dir,
        "--outfile", f16_path,
        "--outtype", "f16",
    ]
    print(f"[quantize_for_mobile] GGUF(F16)変換開始: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)
    print(f"[quantize_for_mobile] F16 GGUF出力: {f16_path}")
    return f16_path


def quantize(llama_quantize_bin: str, f16_path: str, outdir: str, quant_type: str) -> str:
    out_path = os.path.join(outdir, f"model-{quant_type}.gguf")
    cmd = [llama_quantize_bin, f16_path, out_path, quant_type]
    print(f"[quantize_for_mobile] 量子化開始 ({quant_type}): {' '.join(cmd)}")
    subprocess.run(cmd, check=True)
    size_gb = os.path.getsize(out_path) / (1024 ** 3)
    print(f"[quantize_for_mobile] 出力: {out_path} ({size_gb:.2f} GB)")
    return out_path


def main():
    args = parse_args()
    llama_quantize_bin = find_llama_quantize(args.llama_quantize_bin)
    print(f"[quantize_for_mobile] llama-quantize: {llama_quantize_bin}")

    src_model_dir = args.base_model
    if not args.skip_merge and has_lora_adapter(args.lora_dir):
        src_model_dir = merge_lora(args.base_model, args.lora_dir, args.merged_dir)
    else:
        print(f"[quantize_for_mobile] 学習済みLoRAが見つからないため、ベースモデルをそのまま変換します: {args.base_model}")
        print("[quantize_for_mobile] 学習完了後は `--lora_dir output/koilm-lora` を指定して再実行してください。")

    f16_path = convert_to_gguf_f16(args.llama_cpp_dir, src_model_dir, args.outdir)

    results = []
    for quant_type in args.quant_types:
        results.append(quantize(llama_quantize_bin, f16_path, args.outdir, quant_type))

    print("\n[quantize_for_mobile] 完了。生成されたGGUFファイル:")
    for path in results:
        size_gb = os.path.getsize(path) / (1024 ** 3)
        print(f"  {path} ({size_gb:.2f} GB)")
    print(
        "\n[quantize_for_mobile] Snapdragon 888 5G / Dimensity 7400 Ultra 以上クラス(dotprod対応・RAM8GB級)"
        "であれば上記の量子化モデルで動作する想定です。"
        "Androidへは android/app/src/main/java/.../llm/LlmEngineProvider.kt が参照する"
        "filesDir/models/current/model.gguf に配置してください。"
    )


if __name__ == "__main__":
    main()
