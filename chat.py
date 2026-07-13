"""
学習前後のモデルを読み込んでキャラRP会話をテストするCLIチャットスクリプト。

使い方:
    # ベースモデルのみ(学習前)
    python chat.py --base_model models/ninja-rp-7b

    # LoRAアダプタを適用(学習後)
    python chat.py --base_model models/ninja-rp-7b --lora_dir output/koilm-lora

    # 端末内メモリマネージャーを併用(会話が続くほどキャラが記憶を持つ)
    python chat.py --base_model models/ninja-rp-7b --lora_dir output/koilm-lora --with_memory
"""

import argparse

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

from local_memory.context_manager import ContextMemoryManager, MemoryStore
from prompt_template import ensure_chat_template

DEFAULT_PERSONA = (
    "あなたは「小春」。幼馴染で少しツンデレな女子高生キャラとして振る舞ってください。"
    "地の文(仕草・表情の描写)を交えつつ、素っ気ない口調の中に好意をにじませてください。"
)


def parse_args():
    p = argparse.ArgumentParser(description="ローカルRP-SLM 対話テスト")
    p.add_argument("--base_model", required=True)
    p.add_argument("--lora_dir", default=None, help="学習済みLoRAアダプタのディレクトリ(省略で学習前ベースモデルのまま)")
    p.add_argument("--persona", default=DEFAULT_PERSONA)
    p.add_argument("--max_new_tokens", type=int, default=256)
    p.add_argument("--temperature", type=float, default=0.9)
    p.add_argument("--top_p", type=float, default=0.9)
    p.add_argument("--no_4bit", action="store_true")
    p.add_argument("--with_memory", action="store_true", help="ローカル・コンテキスト管理マネージャーを有効化")
    p.add_argument("--memory_path", default="output/memory_chat.json")
    return p.parse_args()


def load_model(args):
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    ensure_chat_template(tokenizer)

    quant_config = None
    if not args.no_4bit:
        quant_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
        )

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=quant_config,
        device_map="auto",
        torch_dtype=torch.bfloat16 if quant_config is None else None,
        trust_remote_code=True,
    )

    if args.lora_dir:
        model = PeftModel.from_pretrained(model, args.lora_dir)
        print(f"[chat] LoRAアダプタを適用しました: {args.lora_dir}")
    else:
        print("[chat] ベースモデルのみで実行します(学習前)")

    model.eval()
    return model, tokenizer


def generate(model, tokenizer, messages, max_new_tokens, temperature, top_p):
    prompt = tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True
    )
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    with torch.no_grad():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=True,
            temperature=temperature,
            top_p=top_p,
            pad_token_id=tokenizer.pad_token_id,
        )
    new_tokens = output_ids[0][inputs["input_ids"].shape[1] :]
    return tokenizer.decode(new_tokens, skip_special_tokens=True).strip()


def main():
    args = parse_args()
    model, tokenizer = load_model(args)

    memory_mgr = None
    if args.with_memory:
        store = MemoryStore(storage_path=args.memory_path)
        store.load()

        def slm_summarize(prompt: str) -> str:
            summary_messages = [{"role": "user", "content": prompt}]
            return generate(model, tokenizer, summary_messages, 200, 0.5, 0.9)

        memory_mgr = ContextMemoryManager(store, summarize_fn=slm_summarize)
        print("[chat] ローカル・コンテキスト管理マネージャーを有効化しました")

    print("=== koiLM chat test (exit で終了) ===")
    history = []
    while True:
        try:
            user_input = input("あなた> ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if user_input.lower() in {"exit", "quit"}:
            break
        if not user_input:
            continue

        if memory_mgr:
            memory_mgr.add_turn("user", user_input)
            system_prompt = memory_mgr.build_system_prompt(args.persona)
        else:
            system_prompt = args.persona

        messages = [{"role": "system", "content": system_prompt}]
        messages += history[-6:]
        messages.append({"role": "user", "content": user_input})

        reply = generate(
            model, tokenizer, messages, args.max_new_tokens, args.temperature, args.top_p
        )
        print(f"キャラ> {reply}\n")

        history.append({"role": "user", "content": user_input})
        history.append({"role": "assistant", "content": reply})

        if memory_mgr:
            memory_mgr.add_turn("assistant", reply)


if __name__ == "__main__":
    main()
