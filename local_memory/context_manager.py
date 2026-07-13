"""
ローカル・コンテキスト管理マネージャー (Python モック)

サーバーを一切使わず、端末内のSLM自身を使って
「過去の重要なやり取り」「ユーザーの好み・設定」を動的に要約し、
毎回のSystem Promptに自動で差し込む仕組みのリファレンス実装。

Android版 (Kotlin) は android/app/.../memory/ContextMemoryManager.kt を参照。
両者は同じロジック(閾値要約 + カテゴリ別メモリ + トークン予算管理)を持つ。

設計方針:
1. 会話ターンは SQLite/JSONL 等に全件保存する(生ログ)。
2. 生ログが一定ターン数(閾値)を超えたら、古い部分をSLM自身に要約させ
   「長期記憶サマリ」として1つのテキストに圧縮する(要約の要約=再帰的圧縮)。
3. 「ユーザーの好み・設定」は要約とは別に key-value の「プロフィールメモリ」として
   抽出・更新する(例: 呼び方、好きな話題、地雷ワード、キャラの口調設定)。
4. 推論直前に、(a)固定キャラ設定 (b)プロフィールメモリ (c)長期記憶サマリ
   (d)直近の生ログ数ターン、をトークン予算内で組み立ててSystem Promptに差し込む。
"""

from __future__ import annotations

import json
import os
import re
import time
from dataclasses import dataclass, field
from typing import Callable, List, Optional

SummarizeFn = Callable[[str], str]


@dataclass
class Turn:
    role: str  # "user" | "assistant"
    content: str
    timestamp: float = field(default_factory=time.time)


@dataclass
class MemoryStore:
    """会話履歴・要約・プロフィールを保持する永続化レイヤー(JSONLベースの簡易実装)。

    Android実機ではこの役割を SQLite (Room) が担う。
    """

    storage_path: str
    turns: List[Turn] = field(default_factory=list)
    long_term_summary: str = ""
    profile: dict = field(default_factory=dict)

    def load(self) -> None:
        if not os.path.exists(self.storage_path):
            return
        with open(self.storage_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.turns = [Turn(**t) for t in data.get("turns", [])]
        self.long_term_summary = data.get("long_term_summary", "")
        self.profile = data.get("profile", {})

    def save(self) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(self.storage_path)), exist_ok=True)
        data = {
            "turns": [t.__dict__ for t in self.turns],
            "long_term_summary": self.long_term_summary,
            "profile": self.profile,
        }
        with open(self.storage_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)


PROFILE_PATTERNS = {
    # 簡易ルールベース抽出。実運用ではSLMに構造化抽出させてもよい。
    "呼び方": re.compile(r"(私|僕|俺)のことは(.+?)って呼んで"),
    "好きな話題": re.compile(r"(.+?)(の話|について話す)のが好き"),
    "地雷": re.compile(r"(.+?)(の話|の話題)はやめて|苦手"),
}


class ContextMemoryManager:
    """端末内RAG/メモリ管理マネージャー本体。

    summarize_fn には端末内SLMの生成関数を渡す
    (例: lambda prompt: llm.generate(prompt, max_new_tokens=200))。
    テスト用に summarize_fn を省略した場合は単純な切り詰め要約にフォールバックする。
    """

    def __init__(
        self,
        store: MemoryStore,
        summarize_fn: Optional[SummarizeFn] = None,
        summarize_threshold_turns: int = 20,
        keep_recent_turns: int = 6,
        max_context_chars: int = 1500,
    ):
        self.store = store
        self.summarize_fn = summarize_fn or self._fallback_summarize
        self.summarize_threshold_turns = summarize_threshold_turns
        self.keep_recent_turns = keep_recent_turns
        self.max_context_chars = max_context_chars

    # ---- 記録 ----------------------------------------------------------
    def add_turn(self, role: str, content: str) -> None:
        self.store.turns.append(Turn(role=role, content=content))
        self._extract_profile(content)
        if len(self.store.turns) > self.summarize_threshold_turns:
            self._compress()
        self.store.save()

    def _extract_profile(self, content: str) -> None:
        for key, pattern in PROFILE_PATTERNS.items():
            m = pattern.search(content)
            if m:
                self.store.profile[key] = m.group(0).strip()

    # ---- 要約(圧縮) -----------------------------------------------------
    def _compress(self) -> None:
        old_turns = self.store.turns[: -self.keep_recent_turns]
        recent_turns = self.store.turns[-self.keep_recent_turns :]
        if not old_turns:
            return

        old_log = "\n".join(f"{t.role}: {t.content}" for t in old_turns)
        prompt = (
            "以下はキャラクターとユーザーの過去の会話ログです。"
            "今後の会話で覚えておくべき重要な事実・感情の変化・約束事だけを、"
            "簡潔な日本語の箇条書きで200字以内に要約してください。\n\n"
            f"[既存の長期記憶]\n{self.store.long_term_summary}\n\n"
            f"[新しい会話ログ]\n{old_log}"
        )
        new_summary = self.summarize_fn(prompt)

        self.store.long_term_summary = new_summary.strip()
        self.store.turns = recent_turns

    def _fallback_summarize(self, prompt: str) -> str:
        """summarize_fn 未指定時の簡易フォールバック(SLMなしでも動作確認できる)。"""
        log_section = prompt.split("[新しい会話ログ]\n")[-1]
        lines = [l for l in log_section.splitlines() if l.strip()]
        return "・" + "\n・".join(lines[-5:])[:200]

    # ---- 差し込み --------------------------------------------------------
    def build_system_prompt(self, base_persona_prompt: str) -> str:
        """固定キャラ設定 + プロフィールメモリ + 長期記憶サマリ を合成する。

        トークン予算(max_context_chars)を超える場合は優先度の低いものから削る:
        キャラ設定 > プロフィール > 長期記憶サマリ
        """
        profile_text = ""
        if self.store.profile:
            profile_lines = [f"- {k}: {v}" for k, v in self.store.profile.items()]
            profile_text = "【ユーザー設定】\n" + "\n".join(profile_lines)

        summary_text = f"【これまでの記憶】\n{self.store.long_term_summary}" if self.store.long_term_summary else ""

        parts = [base_persona_prompt, profile_text, summary_text]
        parts = [p for p in parts if p]
        combined = "\n\n".join(parts)

        if len(combined) > self.max_context_chars:
            budget = self.max_context_chars - len(base_persona_prompt) - len(profile_text) - 20
            if budget > 0:
                summary_text = summary_text[:budget] + "…"
            else:
                summary_text = ""
            parts = [p for p in [base_persona_prompt, profile_text, summary_text] if p]
            combined = "\n\n".join(parts)

        return combined

    def recent_dialogue_text(self) -> str:
        return "\n".join(f"{t.role}: {t.content}" for t in self.store.turns)


if __name__ == "__main__":
    # 動作確認用のシンプルなデモ(SLM未接続でもフォールバック要約で動く)
    store = MemoryStore(storage_path="output/memory_demo.json")
    store.load()
    mgr = ContextMemoryManager(store, summarize_threshold_turns=6, keep_recent_turns=2)

    persona = "あなたは「小春」。ツンデレな幼馴染キャラとして会話してください。"
    sample_lines = [
        ("user", "小春、私のことは『はるくん』って呼んで"),
        ("assistant", "……別に呼んでもいいけど。はるくん、で合ってる?"),
        ("user", "うん!今日学校で楽しいことあったんだ"),
        ("assistant", "へえ、聞いてあげてもいいよ"),
        ("user", "実は好きな人ができて、今度告白しようと思うんだ"),
        ("assistant", "……そう。頑張れば、いいんじゃない"),
        ("user", "映画の話するのが好きなんだ"),
        ("assistant", "知ってる、いつも語ってくるもんね"),
    ]
    for role, content in sample_lines:
        mgr.add_turn(role, content)

    print("=== System Prompt ===")
    print(mgr.build_system_prompt(persona))
    print("\n=== Profile ===")
    print(store.profile)
    print("\n=== Long-term summary ===")
    print(store.long_term_summary)
