# llama.cpp JNIブリッジはリフレクションなしで直接ネイティブ呼び出しするため、
# 難読化で外部関数名が変わるとJNIリンクが壊れる。LlamaBridgeのメンバは保持する。
-keep class com.koilm.app.llm.LlamaBridge { *; }

# Room: エンティティ/DAOの生成コードはリフレクションで参照されるため保持。
-keep class com.koilm.app.data.db.** { *; }

# kotlinx.coroutines / DataStore の内部クラスに対する一般的な警告を抑制。
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.datastore.**
