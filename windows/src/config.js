const path = require("path");
const fs = require("fs");

// bin/ と models/ はサイズが大きいため git 管理外・配布exeにも同梱しない
// (配布は7z等で別ファイルとして配り、実行ファイルと同じフォルダに展開してもらう想定)。
//
// 参照先の解決:
//   - 開発時(npm start): __dirname(src/)の一つ上 = windows/ 直下の bin, models
//   - パッケージ後(electron-builderのportableビルド): 展開先の一時フォルダではなく、
//     元のポータブルexeが置かれているフォルダを指す PORTABLE_EXECUTABLE_DIR を使う
//     (electron-builderがportableビルド実行時に自動設定する環境変数)。
const baseDir = process.env.PORTABLE_EXECUTABLE_DIR || path.join(__dirname, "..");

module.exports = {
  llamaServerExe: process.env.KOILM_LLAMA_SERVER || path.join(baseDir, "bin", "llama-server.exe"),
  // 言語ごとに別モデルを使う(日本語=独自LoRA、英語=Qwen2.5-1.5B-Instructの素のまま)。
  // sarashina2.2はバイリンガルだが日本語RPデータのみでLoRA学習しているため、英語で応答させても
  // 学習した口調・テンポは再現されず、素のベース性能に落ちるだけ。専用の英語モデルに切り替える。
  //
  // 日本語モードはさらに1B(軽量・高速)/7B(旧Ninja-RPベース、高品質・低速)を選べる。
  // Android向けはRAM制約で7Bが使えなかったが、Windows/WebUI版はデスクトップの余裕あるRAMを
  // 前提にできるため7Bも選択肢にする(WebUI/Electron限定配布への切り替えに伴う対応)。
  modelPathJa1b: process.env.KOILM_MODEL_PATH_JA || path.join(baseDir, "models", "koilm-1b.gguf"),
  modelPathJa7b:
    process.env.KOILM_MODEL_PATH_JA_7B || path.join(baseDir, "models", "koilm-7b.gguf"),
  modelPathEn:
    process.env.KOILM_MODEL_PATH_EN || path.join(baseDir, "models", "qwen2.5-1.5b-instruct.gguf"),
  // llama-server.exeとはggml*.dllのビルド(バージョン)が異なるため、DLL衝突を避けて
  // bin/sd/ という別サブフォルダに配置する想定(Windowsは実行ファイル自身のフォルダを
  // 最優先でDLL探索するため、フォルダを分ければ同名dllが共存できる)。
  sdExe: process.env.KOILM_SD_EXE || path.join(baseDir, "bin", "sd", "sd-cli.exe"),
  // Animagine XL 4.0 (SDXLベース、アニメ調)。CLIPテキストエンコーダーはGGUF内に同梱されており
  // Z-Image-Turbo系と違いVAE/テキストエンコーダーを別途指定する必要がない。
  sdModelPath:
    process.env.KOILM_SD_MODEL_PATH || path.join(baseDir, "models", "animagine-xl-4.0-Q4_K.gguf"),
  llamaServerPort: 8734,

  /**
   * settings.language / settings.modelSize から実際に使うGGUFパスを決定する。
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
