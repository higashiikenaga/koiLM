const { spawn } = require("child_process");
const fs = require("fs");
const config = require("./config");

// キャラ作成時に一度だけ呼ばれる想定。常駐プロセスは持たず、都度起動して終了を待つ。
function generatePortrait({ appearance, outputPath }) {
  return new Promise((resolve, reject) => {
    if (!fs.existsSync(config.sdExe) || !fs.existsSync(config.sdModelPath)) {
      console.warn(
        "[imageGen] sd.exe または画像生成モデルが未配置です。" +
          " windows/bin, windows/models にファイルを配置してください。"
      );
      resolve(null);
      return;
    }

    const prompt = buildPrompt(appearance);
    const args = [
      "-M",
      "img_gen",
      "-m",
      config.sdModelPath,
      "-p",
      prompt,
      "-W",
      "1024",
      "-H",
      "1024",
      "--steps",
      "24",
      "--cfg-scale",
      "5",
      "--sampling-method",
      "dpm++2m",
      "-o",
      outputPath,
    ];

    const proc = spawn(config.sdExe, args);
    proc.stdout.on("data", (d) => process.stdout.write(`[sd] ${d}`));
    proc.stderr.on("data", (d) => process.stderr.write(`[sd] ${d}`));
    proc.on("exit", (code) => {
      if (code === 0 && fs.existsSync(outputPath)) resolve(outputPath);
      else reject(new Error(`画像生成失敗 (code=${code})`));
    });
    proc.on("error", reject);
  });
}

// Animagine XL はdanbooruタグ順(character, rating, descriptors, quality)での
// 学習を前提としているため、自然文よりタグ列挙の方が意図通りの絵になりやすい。
function buildPrompt(appearance) {
  // 性別等の属性はキャラの appearance 側で指定してもらう想定
  // (例: "1girl" / "1boy" をユーザー入力に含める)。ここでは構図・画質タグのみ付与する。
  const tags = [
    "solo",
    "portrait",
    "upper body",
    "simple background",
    appearance,
    "masterpiece",
    "high score",
    "great score",
    "absurdres",
  ].filter(Boolean);
  return tags.join(", ");
}

module.exports = { generatePortrait };
