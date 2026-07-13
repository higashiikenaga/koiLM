"""
恋愛・会話特化SLMのQLoRAファインチューニングスクリプト。

想定環境: コンシューマーGPU VRAM 8GB〜24GB (RTX 3060/3090/4070Ti/4090 等)
- 4bit量子化 (NF4) でベースモデルをロード
- LoRAアダプタのみ学習 (PEFT)
- Gradient Checkpointing でアクティベーションメモリを削減
- TRL の SFTTrainer で ShareGPT形式 (conversations) のデータをそのまま学習

使い方:
    python train.py \
        --base_model models/ninja-rp-7b \
        --dataset data/sample_dataset.jsonl \
        --output_dir output/koilm-lora \
        --epochs 3

VRAMが厳しい場合は --micro_batch_size 1 --max_seq_len 1024 などで調整してください。
"""

import argparse
import os

import torch
from datasets import load_dataset
from peft import LoraConfig, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
)
from trl import SFTConfig, SFTTrainer

from prompt_template import ensure_chat_template


def parse_args():
    p = argparse.ArgumentParser(description="恋愛・RP特化SLM QLoRA学習")
    p.add_argument("--base_model", required=True, help="ベースモデルのパス or HFリポジトリID")
    p.add_argument("--dataset", default="data/sample_dataset.jsonl", help="ShareGPT形式jsonl")
    p.add_argument("--output_dir", default="output/koilm-lora")
    p.add_argument("--epochs", type=float, default=3.0)
    p.add_argument("--micro_batch_size", type=int, default=2)
    p.add_argument("--grad_accum", type=int, default=8)
    p.add_argument("--lr", type=float, default=2e-4)
    p.add_argument("--max_seq_len", type=int, default=2048)
    p.add_argument("--lora_r", type=int, default=16)
    p.add_argument("--lora_alpha", type=int, default=32)
    p.add_argument("--lora_dropout", type=float, default=0.05)
    p.add_argument(
        "--target_modules",
        nargs="+",
        default=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        help="LoRAを適用する線形層名 (Llama/Qwen系のデフォルト)",
    )
    p.add_argument("--no_4bit", action="store_true", help="4bit量子化を無効化 (VRAMに余裕がある場合)")
    p.add_argument("--save_steps", type=int, default=50)
    p.add_argument("--logging_steps", type=int, default=5)
    return p.parse_args()


def to_chatml_text(example, tokenizer):
    """ShareGPT形式 {conversations:[{from, value}]} を chat_template でテキスト化"""
    role_map = {"system": "system", "human": "user", "gpt": "assistant"}
    messages = [
        {"role": role_map.get(turn["from"], turn["from"]), "content": turn["value"]}
        for turn in example["conversations"]
    ]
    text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)
    return {"text": text}


def main():
    args = parse_args()
    os.makedirs(args.output_dir, exist_ok=True)

    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    ensure_chat_template(tokenizer)

    # Pascal世代(compute capability 6.1以下)などbf16非対応GPUでは、bf16の重みは
    # ハードウェアアクセラレーションが効かずソフトウェアエミュレーションになり著しく遅くなる。
    # 学習ループのmixed precision設定(下記bf16=/fp16=)と揃える。
    compute_dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16

    quant_config = None
    if not args.no_4bit:
        quant_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=compute_dtype,
            bnb_4bit_use_double_quant=True,
        )

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=quant_config,
        device_map="auto",
        torch_dtype=compute_dtype if quant_config is None else None,
        trust_remote_code=True,
    )
    model.config.use_cache = False
    model.gradient_checkpointing_enable()

    if quant_config is not None:
        model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)

    lora_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        target_modules=args.target_modules,
        bias="none",
        task_type="CAUSAL_LM",
    )

    dataset = load_dataset("json", data_files=args.dataset, split="train")
    dataset = dataset.map(
        lambda ex: to_chatml_text(ex, tokenizer),
        remove_columns=dataset.column_names,
    )

    sft_config = SFTConfig(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.micro_batch_size,
        gradient_accumulation_steps=args.grad_accum,
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        learning_rate=args.lr,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=args.logging_steps,
        save_steps=args.save_steps,
        save_total_limit=2,
        bf16=torch.cuda.is_bf16_supported(),
        fp16=not torch.cuda.is_bf16_supported(),
        optim="paged_adamw_8bit" if quant_config is not None else "adamw_torch",
        max_length=args.max_seq_len,
        dataset_text_field="text",
        packing=False,
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        args=sft_config,
        train_dataset=dataset,
        peft_config=lora_config,
        processing_class=tokenizer,
    )

    trainer.train()

    trainer.model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"[train] LoRAアダプタを {args.output_dir} に保存しました")


if __name__ == "__main__":
    main()
