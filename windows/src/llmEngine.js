const { spawn } = require("child_process");
const http = require("http");
const fs = require("fs");
const config = require("./config");
const promptTemplate = require("./promptTemplate");

let serverProcess = null;
let currentModelPath = null;

function waitForServerReady(timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tryPing = () => {
      const req = http.get(
        { host: "127.0.0.1", port: config.llamaServerPort, path: "/health" },
        (res) => {
          res.resume();
          if (res.statusCode === 200) resolve();
          else retryOrFail();
        }
      );
      req.on("error", retryOrFail);
    };
    const retryOrFail = () => {
      if (Date.now() > deadline) reject(new Error("llama-server起動タイムアウト"));
      else setTimeout(tryPing, 500);
    };
    tryPing();
  });
}

/** @param modelPath 省略時は config.modelPathJa(既定言語)を使う。 */
async function start(modelPath = config.modelPathJa) {
  if (serverProcess) return;
  if (!fs.existsSync(config.llamaServerExe) || !fs.existsSync(modelPath)) {
    console.warn(
      "[llmEngine] llama-server.exe またはモデルが未配置です。" +
        ` windows/bin, windows/models にファイルを配置してください。(${modelPath})`
    );
    return;
  }
  serverProcess = spawn(config.llamaServerExe, [
    "-m",
    modelPath,
    "--port",
    String(config.llamaServerPort),
    "-c",
    "4096",
  ]);
  currentModelPath = modelPath;
  serverProcess.stdout.on("data", (d) => process.stdout.write(`[llama-server] ${d}`));
  serverProcess.stderr.on("data", (d) => process.stderr.write(`[llama-server] ${d}`));
  serverProcess.on("exit", (code) => {
    console.log(`[llmEngine] llama-server終了 (code=${code})`);
    serverProcess = null;
    currentModelPath = null;
  });
  await waitForServerReady();
}

function stop() {
  if (serverProcess) {
    serverProcess.kill();
    serverProcess = null;
    currentModelPath = null;
  }
}

/**
 * 言語切り替え時に呼ぶ。指定モデルが既にロード中なら何もしない
 * (言語設定変更のたびに無駄な再起動をしないため)。
 */
async function switchModel(modelPath) {
  if (currentModelPath === modelPath) return;
  stop();
  await start(modelPath);
}

function postCompletion(body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const req = http.request(
      {
        host: "127.0.0.1",
        port: config.llamaServerPort,
        path: "/completion",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let chunks = "";
        res.on("data", (c) => (chunks += c));
        res.on("end", () => {
          try {
            resolve(JSON.parse(chunks));
          } catch (err) {
            reject(err);
          }
        });
      }
    );
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

/** 組み立て済みプロンプト文字列から直接生成する低レベルAPI。ChatRepository/MemoryManagerが呼ぶ。 */
async function completeRaw(prompt, { nPredict = 256, temperature = 0.9, topP = 0.9 } = {}) {
  if (!serverProcess) {
    throw new Error("llama-serverが起動していません(モデル未配置の可能性があります)");
  }
  const result = await postCompletion({
    prompt,
    n_predict: nPredict,
    temperature,
    top_p: topP,
    stop: promptTemplate.STOP_SEQUENCES,
  });
  return (result.content || "").trim();
}

/**
 * Android版 LlamaCppEngine.summarize() と同じプロンプトで長期記憶の要約を行う。
 * 要約専用の別モデルは持たず、通信も発生させずに同じエンジンを使い回す。
 */
async function summarize(logText, existingSummary) {
  const prompt =
    "以下はキャラクターとユーザーの過去の会話ログです。\n" +
    "今後の会話で覚えておくべき重要な事実・感情の変化・約束事だけを、\n" +
    "簡潔な日本語の箇条書きで200字以内に要約してください。\n\n" +
    `[既存の長期記憶]\n${existingSummary}\n\n` +
    `[新しい会話ログ]\n${logText}`;
  return completeRaw(prompt, { nPredict: 200, temperature: 0.4, topP: 0.9 });
}

module.exports = {
  start,
  stop,
  switchModel,
  completeRaw,
  summarize,
  isRunning: () => serverProcess !== null,
  currentModelPath: () => currentModelPath,
};
