"""
Hugging Face上の日本語ロールプレイ/対話データセットを取得し、
恋愛・会話特化SLM学習用の ShareGPT形式 JSONL (data/train.jsonl) に整形するパイプライン。

対象データセット:
    1. Aratako/Synthetic-Japanese-Roleplay-SFW-DeepSeek-V3-0324-20k-formatted
       - {"id": int, "messages": [{"role": "system"|"user"|"assistant", "content": str}, ...]}
       - system: 世界観・キャラ設定, user/assistant: 2キャラの掛け合い(主に「」の台詞)
       - License: MIT
    2. RumiaChannel/PersonaCast-JA
       - {"id", "persona_seed", "turns", "public_timeline", "public_transcript", "generation_config"}
       - public_timeline: 台詞+視認可能な行動(地の文に相当)を含む2キャラのマルチターン会話ログ
       - License: CC0-1.0
       - 注意: 本データセットは正式なdatasets-server変換がされていない/スキーマが変更される
         可能性があるため、フィールド名は複数の候補(エイリアス)を試す防御的な実装にしている。
         想定と異なるレコードは例外を投げずスキップし、件数を最後にレポートする。

処理の流れ:
    1. 各データセットを streaming で取得(全件DLせず、--max_samples_per_source 件で打ち切り可能)
    2. ShareGPT形式 {"conversations": [{"from": "system"/"human"/"gpt", "value": str}]} に変換
    3. 「User/Assistantの掛け合いが最低限成立しているか」(交互ターン・最小ターン数)を検証
    4. 「地の文(「」外のナレーション・行動描写)」の量や感情語彙の出現でエモさスコアを算出し、
       閾値未満を除外 → スコア降順にソートして「最もエモい会話」を優先的に残す
    5. data/train.jsonl に書き出し、直後に自動でバリデーション(パース確認・統計・サンプル表示)

使い方:
    python prepare_data.py --sources aratako personacast --max_samples_per_source 200 \
        --min_score 1.5 --output data/train.jsonl
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
from collections import Counter
from typing import Any, Iterable, Iterator, Optional

from datasets import load_dataset
from huggingface_hub import hf_hub_download

# ---------------------------------------------------------------------------
# データセット定義
# ---------------------------------------------------------------------------

ARATAKO_REPO = "Aratako/Synthetic-Japanese-Roleplay-SFW-DeepSeek-V3-0324-20k-formatted"
PERSONACAST_REPO = "RumiaChannel/PersonaCast-JA"
PERSONACAST_FILE = "PersonaCast-JA-Unscreened_v1.jsonl"

EMOTION_KEYWORDS = [
    "好き", "恋", "照れ", "赤ら", "抱きし", "抱きつ", "キス", "ドキドキ", "どきどき",
    "切な", "涙", "泣", "微笑", "そっと", "そばに", "傍に", "甘え", "胸が", "胸に",
    "心臓", "震え", "頬", "囁", "愛し", "温もり", "見つめ合", "手を握", "触れ",
]


# ---------------------------------------------------------------------------
# 共通ユーティリティ
# ---------------------------------------------------------------------------

def merge_consecutive_roles(conv: list[dict]) -> list[dict]:
    """同じ from が連続する場合は改行で結合し、交互ターンに正規化する。"""
    merged: list[dict] = []
    for turn in conv:
        if merged and merged[-1]["from"] == turn["from"]:
            merged[-1]["value"] = merged[-1]["value"] + "\n" + turn["value"]
        else:
            merged.append(dict(turn))
    return merged


def is_valid_multiturn(conv: list[dict], min_human: int = 2, min_gpt: int = 2) -> bool:
    """User/Assistantの掛け合いが最低限のターン数・交互性を満たしているか検証する。"""
    body = [t for t in conv if t["from"] != "system"]
    if not body:
        return False
    human_n = sum(1 for t in body if t["from"] == "human")
    gpt_n = sum(1 for t in body if t["from"] == "gpt")
    if human_n < min_human or gpt_n < min_gpt:
        return False
    # 交互性チェック(同じroleが連続していないこと。事前にmerge_consecutive_rolesを通す前提)
    for a, b in zip(body, body[1:]):
        if a["from"] == b["from"]:
            return False
    # 空文字ターンが無いこと
    if any(not t["value"].strip() for t in body):
        return False
    return True


def emotional_score(text: str) -> float:
    """「」内(台詞)を除いた残り(地の文)の量 + 感情語彙の出現数でエモさをスコア化する。"""
    narration = re.sub(r"「[^」]*」", "", text)
    narration_len = len(narration.strip())
    score = min(narration_len / 20.0, 5.0)
    score += sum(1.0 for kw in EMOTION_KEYWORDS if kw in text)
    if "……" in text or "…" in text:
        score += 0.5
    return score


def conversation_emotional_score(conv: list[dict]) -> float:
    gpt_turns = [t["value"] for t in conv if t["from"] == "gpt"]
    if not gpt_turns:
        return 0.0
    return sum(emotional_score(t) for t in gpt_turns) / len(gpt_turns)


# ---------------------------------------------------------------------------
# ソース1: Aratako Synthetic-Japanese-Roleplay
# ---------------------------------------------------------------------------

ROLE_MAP = {"system": "system", "user": "human", "assistant": "gpt"}


def convert_aratako_example(example: dict) -> Optional[list[dict]]:
    messages = example.get("messages")
    if not isinstance(messages, list) or not messages:
        return None

    conv = []
    for m in messages:
        role = ROLE_MAP.get(m.get("role"))
        content = m.get("content")
        if role is None or not content or not str(content).strip():
            continue
        conv.append({"from": role, "value": str(content).strip()})

    conv = merge_consecutive_roles(conv)
    if not is_valid_multiturn(conv):
        return None
    return conv


def iter_aratako(max_samples: Optional[int]) -> Iterator[dict]:
    ds = load_dataset(ARATAKO_REPO, split="train", streaming=True)
    for i, example in enumerate(ds):
        if max_samples is not None and i >= max_samples * 3:
            # フィルタで落ちる分を見込んで多めにスキャンする
            break
        yield example


# ---------------------------------------------------------------------------
# ソース2: RumiaChannel/PersonaCast-JA (スキーマ未確定なため防御的に実装)
# ---------------------------------------------------------------------------

SPEAKER_KEYS = ["speaker", "character", "character_name", "name", "role_name", "actor_name"]
UTTER_KEYS = ["utterance", "dialogue", "line", "text", "speech", "content", "message"]
ACTION_KEYS = ["action", "visible_action", "description", "narration", "gesture", "behavior"]


def _first_present(d: dict, keys: list[str]) -> Any:
    for k in keys:
        v = d.get(k)
        if v:
            return v
    return None


def extract_timeline_entry(entry: Any) -> Optional[dict]:
    if not isinstance(entry, dict):
        return None
    speaker = _first_present(entry, SPEAKER_KEYS)
    utterance = _first_present(entry, UTTER_KEYS)
    action = _first_present(entry, ACTION_KEYS)
    if speaker is None or (not utterance and not action):
        return None
    return {"speaker": str(speaker), "utterance": utterance, "action": action}


def format_turn_text(action: Any, utterance: Any) -> str:
    parts = []
    if action:
        parts.append(str(action).strip())
    if utterance:
        u = str(utterance).strip()
        if u and not (u.startswith("「") and u.endswith("」")):
            u = f"「{u}」"
        parts.append(u)
    return "\n".join(p for p in parts if p)


def find_persona_description(obj: Any, name: str, _depth: int = 0) -> Optional[str]:
    """persona_seed(構造未確定)を再帰探索し、name に一致するキャラの説明文らしきものを拾う。"""
    if _depth > 4:
        return None
    if isinstance(obj, dict):
        obj_name = _first_present(obj, ["name", "character_name", "speaker"])
        if obj_name and str(obj_name).strip() == name.strip():
            desc = _first_present(
                obj, ["personality", "description", "traits", "profile", "summary", "persona"]
            )
            if desc:
                return str(desc)
        for v in obj.values():
            found = find_persona_description(v, name, _depth + 1)
            if found:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = find_persona_description(item, name, _depth + 1)
            if found:
                return found
    return None


def build_persona_system_prompt(persona_seed: Any, gpt_speaker_name: str) -> str:
    desc = find_persona_description(persona_seed, gpt_speaker_name)
    if desc:
        return f"あなたは「{gpt_speaker_name}」です。以下の設定を踏まえてロールプレイをしてください。\n{desc}"
    return f"あなたは「{gpt_speaker_name}」としてロールプレイを行ってください。地の文で感情や仕草を描写しつつ、台詞は「」で表現してください。"


def convert_personacast_example(row: dict) -> Optional[list[dict]]:
    timeline = row.get("public_timeline")
    if not isinstance(timeline, list) or len(timeline) < 4:
        return None

    entries = [e for e in (extract_timeline_entry(x) for x in timeline) if e]
    if len(entries) < 4:
        return None

    speakers = list(dict.fromkeys(e["speaker"] for e in entries))
    if len(speakers) != 2:
        # 2者の掛け合いとして扱えないもの(ナレーターのみ・3人以上等)は除外
        return None
    human_speaker, gpt_speaker = speakers[0], speakers[1]

    system_prompt = build_persona_system_prompt(row.get("persona_seed"), gpt_speaker)
    conv = [{"from": "system", "value": system_prompt}]
    for e in entries:
        role = "human" if e["speaker"] == human_speaker else "gpt"
        text = format_turn_text(e["action"], e["utterance"])
        if text:
            conv.append({"from": role, "value": text})

    conv = merge_consecutive_roles(conv)
    if not is_valid_multiturn(conv):
        return None
    return conv


def iter_personacast(max_samples: Optional[int]) -> Iterator[dict]:
    """PersonaCast-JAは行ごとにフィールド構成が微妙に異なるため(例: 一部の行にのみ
    genre_note等の追加フィールドがある)、datasets.load_dataset("json", streaming=True)の
    厳密なArrowスキーマ推論だとcastエラーで落ちてしまう。
    そのためファイルをローカルにキャッシュDLし、行ごとに素のjson.loadsでパースする
    (1行の解析失敗は個別にスキップし、全体を止めない)。
    """
    local_path = hf_hub_download(repo_id=PERSONACAST_REPO, filename=PERSONACAST_FILE, repo_type="dataset")

    kept = 0
    with open(local_path, "r", encoding="utf-8") as f:
        for line in f:
            if max_samples is not None and kept >= max_samples * 5:
                break
            line = line.strip()
            if not line:
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            kept += 1
            yield row


# ---------------------------------------------------------------------------
# パイプライン本体
# ---------------------------------------------------------------------------

SOURCES = {
    "aratako": (iter_aratako, convert_aratako_example),
    "personacast": (iter_personacast, convert_personacast_example),
}


def process_source(
    name: str, max_samples: Optional[int], min_score: float
) -> tuple[list[dict], Counter]:
    iter_fn, convert_fn = SOURCES[name]
    stats: Counter = Counter()
    results: list[dict] = []

    try:
        iterator: Iterable[dict] = iter_fn(max_samples)
    except Exception as exc:  # データセット取得自体の失敗(ネットワーク/スキーマ変更等)
        print(f"[prepare_data] ソース '{name}' の取得に失敗しました: {exc}")
        return [], Counter({"fetch_error": 1})

    for example in iterator:
        stats["seen"] += 1
        try:
            conv = convert_fn(example)
        except Exception as exc:  # 個別レコードの想定外スキーマはスキップし継続する
            stats["skipped_parse_error"] += 1
            continue

        if conv is None:
            stats["skipped_invalid_structure"] += 1
            continue

        score = conversation_emotional_score(conv)
        if score < min_score:
            stats["skipped_low_emotion"] += 1
            continue

        results.append({"conversations": conv, "_source": name, "_score": round(score, 3)})
        if max_samples is not None and len(results) >= max_samples:
            break

    stats["kept"] = len(results)
    return results, stats


def run_pipeline(
    sources: list[str],
    output_path: str,
    max_samples_per_source: Optional[int],
    min_score: float,
    top_n: Optional[int],
    seed: int,
) -> None:
    random.seed(seed)
    all_results: list[dict] = []

    for source in sources:
        print(f"\n[prepare_data] === ソース取得中: {source} ===")
        results, stats = process_source(source, max_samples_per_source, min_score)
        print(f"[prepare_data] {source} 統計: {dict(stats)}")
        all_results.extend(results)

    # 最もエモい会話を優先: スコア降順ソート
    all_results.sort(key=lambda r: r["_score"], reverse=True)

    if top_n is not None:
        all_results = all_results[:top_n]

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        for r in all_results:
            record = {"conversations": r["conversations"]}
            f.write(json.dumps(record, ensure_ascii=False) + "\n")

    print(f"\n[prepare_data] 合計 {len(all_results)} 件を {output_path} に書き出しました")
    if all_results:
        avg_score = sum(r["_score"] for r in all_results) / len(all_results)
        print(f"[prepare_data] 平均エモさスコア: {avg_score:.2f}")


# ---------------------------------------------------------------------------
# バリデーション
# ---------------------------------------------------------------------------

def validate_jsonl(path: str, sample_n: int = 3) -> None:
    print(f"\n[validate] === {path} を検証 ===")
    if not os.path.exists(path):
        print("[validate] ファイルが存在しません")
        return

    with open(path, "r", encoding="utf-8") as f:
        lines = [l for l in f.readlines() if l.strip()]

    total = len(lines)
    turn_counts = []
    role_errors = 0
    json_errors = 0
    valid_records = []

    for i, line in enumerate(lines):
        try:
            obj = json.loads(line)
        except json.JSONDecodeError as e:
            print(f"[validate] JSON parse error at line {i}: {e}")
            json_errors += 1
            continue

        conv = obj.get("conversations")
        if not isinstance(conv, list) or not conv:
            print(f"[validate] 'conversations' が不正 at line {i}")
            json_errors += 1
            continue

        body = [t for t in conv if t.get("from") != "system"]
        alternating = all(body[j]["from"] != body[j + 1]["from"] for j in range(len(body) - 1))
        if not alternating:
            role_errors += 1

        turn_counts.append(len(conv))
        valid_records.append(obj)

    print(f"[validate] 総レコード数: {total}")
    print(f"[validate] JSONパースエラー: {json_errors}")
    print(f"[validate] role非交互レコード数: {role_errors}")
    if turn_counts:
        print(f"[validate] 平均ターン数: {sum(turn_counts) / len(turn_counts):.1f}")

    print(f"\n[validate] --- サンプル {min(sample_n, len(valid_records))} 件 ---")
    for obj in valid_records[:sample_n]:
        print(json.dumps(obj, ensure_ascii=False, indent=2))
        print("---")


def parse_args():
    p = argparse.ArgumentParser(description="HF日本語RPデータセット → ShareGPT形式 学習データ整形パイプライン")
    p.add_argument("--sources", nargs="+", default=["aratako", "personacast"], choices=list(SOURCES.keys()))
    p.add_argument("--output", default="data/train.jsonl")
    p.add_argument("--max_samples_per_source", type=int, default=300, help="ソースごとの最大採用件数(streamingで打ち切り)")
    p.add_argument("--min_score", type=float, default=1.0, help="この値未満のエモさスコアの会話は除外")
    p.add_argument("--top_n", type=int, default=None, help="全ソース合算後、スコア上位N件のみ残す")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--skip_validate", action="store_true")
    return p.parse_args()


def main():
    args = parse_args()
    run_pipeline(
        sources=args.sources,
        output_path=args.output,
        max_samples_per_source=args.max_samples_per_source,
        min_score=args.min_score,
        top_n=args.top_n,
        seed=args.seed,
    )
    if not args.skip_validate:
        validate_jsonl(args.output)


if __name__ == "__main__":
    main()
