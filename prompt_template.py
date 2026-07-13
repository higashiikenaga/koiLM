"""
train.py / chat.py で共通して使うチャットテンプレート定義。

models/ninja-rp-7b (Aratako/Ninja-v1-RP-expressive-v2) のtokenizerには
chat_templateが設定されておらず(素のLlamaTokenizer)、apply_chat_template()が
そのままでは使えないため、シンプルな独自テンプレートをここで一元的に適用する。

学習時(train.py)と推論時(chat.py)で異なるテンプレートを使うと出力が崩れるため、
必ずこのモジュール経由でテンプレートを設定すること。
"""

CHAT_TEMPLATE = (
    "{% for message in messages %}"
    "{% if message['role'] == 'system' %}"
    "<|system|>\n{{ message['content'] }}\n"
    "{% elif message['role'] == 'user' %}"
    "<|user|>\n{{ message['content'] }}\n"
    "{% elif message['role'] == 'assistant' %}"
    "<|assistant|>\n{{ message['content'] }}\n"
    "{% endif %}"
    "{% endfor %}"
    "{% if add_generation_prompt %}<|assistant|>\n{% endif %}"
)


def ensure_chat_template(tokenizer):
    """tokenizerにchat_templateが未設定の場合のみ、共通テンプレートを適用する。"""
    if getattr(tokenizer, "chat_template", None) is None:
        tokenizer.chat_template = CHAT_TEMPLATE
    return tokenizer
