const path = require("path");
const fs = require("fs");

// bin/ と models/ はサイズが大きいためgit管理外。webui/ 直下に配置する想定
// (windows/bin, windows/models と同じファイルをコピーしてくれば良い)。
const baseDir = path.join(__dirname, "..");

module.exports = {
  llamaServerExe: process.env.KOILM_LLAMA_SERVER || path.join(baseDir, "bin", "llama-server.exe"),
  // 言語ごとに別モデルを使う(日本語=独自LoRA、英語=Qwen2.5-1.5B-Instructの素のまま)。
  // 日本語モードはさらに1B(軽量・高速)/7B(旧Ninja-RPベース、高品質・低速)を選べる
  // (WebUI/Electron限定配布への切り替えに伴い、デスクトップの余裕あるRAMを前提にできるため)。
  modelPathJa1b: process.env.KOILM_MODEL_PATH_JA || path.join(baseDir, "models", "koilm-1b.gguf"),
  modelPathJa7b:
    process.env.KOILM_MODEL_PATH_JA_7B || path.join(baseDir, "models", "koilm-7b.gguf"),
  modelPathEn:
    process.env.KOILM_MODEL_PATH_EN || path.join(baseDir, "models", "qwen2.5-1.5b-instruct.gguf"),
  // llama-server.exeとはggml*.dllのビルド(バージョン)が異なるため、DLL衝突を避けて
  // bin/sd/ という別サブフォルダに配置する想定(Windowsは実行ファイル自身のフォルダを
  // 最優先でDLL探索するため、フォルダを分ければ同名dllが共存できる)。
  sdExe: process.env.KOILM_SD_EXE || path.join(baseDir, "bin", "sd", "sd-cli.exe"),
  sdModelPath:
    process.env.KOILM_SD_MODEL_PATH || path.join(baseDir, "models", "animagine-xl-4.0-Q4_K.gguf"),
  llamaServerPort: 8734,
  webPort: Number(process.env.KOILM_WEB_PORT || 3939),
  pin: process.env.KOILM_PIN || null,

  /**
   * 7B選択時にファイルが未配置の場合は、無音で起動失敗するのを避けるため1Bにフォールバックする
   * (7Bはユーザーがオプションで別途ダウンロードする前提のため、未配置のまま選択されている状況が起こりうる)。
   */
  resolveModelPath(settings) {
    if (settings.language === "en") return module.exports.modelPathEn;
    if (settings.modelSize === "7b" && fs.existsSync(module.exports.modelPathJa7b)) {
      return module.exports.modelPathJa7b;
    }
    return module.exports.modelPathJa1b;
  },
};
