// JNIブリッジ: Kotlin (LlamaBridge.kt) <-> llama.cpp
//
// モデルロード・コンテキスト生成・トークン単位のデコード/サンプリングをラップし、
// すべての推論を端末内(オンデバイス)で完結させる。ネットワーク通信は一切行わない。

#include <android/log.h>
#include <jni.h>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "llama.h"

#define LOG_TAG "koilm_llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
};

std::mutex g_handles_mutex;
std::unordered_map<jlong, LlamaHandle> g_handles;
jlong g_next_handle = 1;
bool g_backend_initialized = false;

void ensure_backend_initialized() {
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_koilm_app_llm_LlamaBridge_loadModel(
        JNIEnv *env, jobject /* thiz */, jstring model_path, jint n_ctx, jint n_threads) {
    LOGI("loadModel: 呼び出されました n_ctx=%d n_threads=%d", n_ctx, n_threads);
    ensure_backend_initialized();

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("loadModel: パス=%s", path);

    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        LOGE("モデルのロードに失敗しました");
        return 0;
    }
    LOGI("loadModel: llama_model_load_from_file 成功");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx);
    ctx_params.n_batch = static_cast<uint32_t>(n_ctx);
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("コンテキストの生成に失敗しました");
        llama_model_free(model);
        return 0;
    }
    LOGI("loadModel: コンテキスト生成成功、ロード完了");

    LlamaHandle handle;
    handle.model = model;
    handle.ctx = ctx;
    handle.vocab = llama_model_get_vocab(model);

    std::lock_guard<std::mutex> lock(g_handles_mutex);
    jlong id = g_next_handle++;
    g_handles[id] = handle;
    return id;
}

extern "C" JNIEXPORT void JNICALL
Java_com_koilm_app_llm_LlamaBridge_freeModel(JNIEnv *, jobject, jlong handle_id) {
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_handles.find(handle_id);
    if (it == g_handles.end()) return;

    if (it->second.ctx) llama_free(it->second.ctx);
    if (it->second.model) llama_model_free(it->second.model);
    g_handles.erase(it);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_koilm_app_llm_LlamaBridge_generate(
        JNIEnv *env, jobject, jlong handle_id, jstring prompt,
        jint max_new_tokens, jfloat temperature, jfloat top_p) {
    LlamaHandle handle;
    {
        std::lock_guard<std::mutex> lock(g_handles_mutex);
        auto it = g_handles.find(handle_id);
        if (it == g_handles.end()) {
            LOGE("不正なハンドルです: %lld", (long long) handle_id);
            return env->NewStringUTF("");
        }
        handle = it->second;
    }

    const char *prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    llama_context *ctx = handle.ctx;
    const llama_vocab *vocab = handle.vocab;

    // --- プロンプトのトークナイズ ---
    const int n_prompt_tokens_max = static_cast<int>(prompt_str.size()) + 32;
    std::vector<llama_token> prompt_tokens(n_prompt_tokens_max);
    int n_prompt_tokens = llama_tokenize(
            vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
            prompt_tokens.data(), n_prompt_tokens_max, /*add_special=*/true, /*parse_special=*/true);
    if (n_prompt_tokens < 0) {
        prompt_tokens.resize(-n_prompt_tokens);
        n_prompt_tokens = llama_tokenize(
                vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
                prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), true, true);
    }
    prompt_tokens.resize(n_prompt_tokens);

    // コンテキストに収まらない場合は古い部分を切り詰める(先頭を落として直近を残す)
    const uint32_t n_ctx = llama_n_ctx(ctx);
    const int keep_budget = static_cast<int>(n_ctx) - max_new_tokens - 8;
    if (keep_budget > 0 && static_cast<int>(prompt_tokens.size()) > keep_budget) {
        int drop = static_cast<int>(prompt_tokens.size()) - keep_budget;
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.begin() + drop);
        LOGI("プロンプトがコンテキスト長を超えたため先頭%dトークンを切り詰めました", drop);
    }

    llama_memory_seq_rm(llama_get_memory(ctx), 0, -1, -1);  // KVキャッシュをクリアして毎回新規セッションとして扱う

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));
    if (llama_decode(ctx, batch) != 0) {
        LOGE("プロンプトのdecodeに失敗しました");
        return env->NewStringUTF("");
    }

    // --- サンプラーチェーンの構築 ---
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 学習時(prompt_template.py)の <|system|>/<|user|>/<|assistant|> はいずれも
    // 特殊トークンとして登録された本物のEOSではなく、素のテキストとして学習させたタグである。
    // そのため llama_vocab_is_eog() だけでは「1ターン分の応答」の終わりを検出できず、
    // モデルは学習データの癖で次のユーザーターンまで生成を続けてしまう。
    // ここでは生成テキストにこれらのタグが現れた時点で、そこより前を応答として確定させる
    // ストップシーケンス方式で打ち切る。
    static const char *STOP_SEQUENCES[] = {"<|user|>", "<|system|>", "<|assistant|>"};

    std::string result;
    int n_generated = 0;
    llama_token new_token = -1;

    while (n_generated < max_new_tokens) {
        new_token = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char piece_buf[256];
        int piece_len = llama_token_to_piece(vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
        if (piece_len > 0) {
            result.append(piece_buf, piece_len);
        }

        bool hit_stop_sequence = false;
        for (const char *stop : STOP_SEQUENCES) {
            size_t pos = result.find(stop);
            if (pos != std::string::npos) {
                result.erase(pos);
                hit_stop_sequence = true;
                break;
            }
        }
        if (hit_stop_sequence) {
            break;
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, next_batch) != 0) {
            LOGE("生成中のdecodeに失敗しました");
            break;
        }
        n_generated++;
    }

    llama_sampler_free(sampler);

    // 末尾の空白・改行を整える(ストップシーケンス直前の改行が残ることがあるため)
    while (!result.empty() && (result.back() == '\n' || result.back() == ' ')) {
        result.pop_back();
    }

    return env->NewStringUTF(result.c_str());
}
