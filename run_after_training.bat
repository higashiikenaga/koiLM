@echo off
REM =========================================================
REM  train.py の学習完了後に実行する後処理スクリプト
REM  1. LoRAアダプタ(output\koilm-lora)をベースモデルにマージ
REM  2. llama.cpp で GGUF(F16) に変換
REM  3. Q4_K_S に量子化
REM  4. 最終ファイルを output\koilm-7b-Q4_K_S.gguf にリネーム
REM
REM 使い方: train.py の完了を確認したら、このファイルを
REM         ダブルクリック（またはターミナルで実行）するだけ。
REM =========================================================

cd /d "%~dp0"

echo [1/4] 仮想環境を有効化しています...
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo [ERROR] venv の有効化に失敗しました。venv\Scripts\activate.bat の場所を確認してください。
    pause
    exit /b 1
)

echo [2/4] LoRA成果物の存在を確認しています...
if not exist "output\koilm-lora\adapter_config.json" (
    echo [ERROR] output\koilm-lora\adapter_config.json が見つかりません。
    echo         学習がまだ完了していないか、output_dir が異なる可能性があります。
    pause
    exit /b 1
)
echo   OK: output\koilm-lora が見つかりました。

echo [3/4] llama-quantize の存在を確認しています...
where llama-quantize.exe >nul 2>nul
if errorlevel 1 (
    if exist "%LOCALAPPDATA%\Microsoft\WinGet\Packages\ggml.llamacpp_Microsoft.Winget.Source_8wekyb3d8bbwe\llama-quantize.exe" (
        echo   OK: winget版 llama-quantize.exe を使用します。
    ) else (
        echo [ERROR] llama-quantize が見つかりません。
        echo         次のコマンドでインストールしてから再実行してください:
        echo             winget install ggml.llamacpp
        pause
        exit /b 1
    )
) else (
    echo   OK: PATH上の llama-quantize.exe を使用します。
)

echo [4/4] マージ + GGUF変換 + Q4_K_S量子化を実行します...
python scripts\quantize_for_mobile.py ^
    --base_model models\ninja-rp-7b ^
    --lora_dir output\koilm-lora ^
    --merged_dir output\koilm-merged ^
    --llama_cpp_dir tools\llama.cpp ^
    --outdir output ^
    --quant_types Q4_K_S

if errorlevel 1 (
    echo [ERROR] quantize_for_mobile.py の実行に失敗しました。上記のログを確認してください。
    pause
    exit /b 1
)

if exist "output\model-Q4_K_S.gguf" (
    move /Y "output\model-Q4_K_S.gguf" "output\koilm-7b-Q4_K_S.gguf" >nul
    echo.
    echo =========================================================
    echo  完了しました！
    echo  最終GGUFファイル: output\koilm-7b-Q4_K_S.gguf
    echo =========================================================
) else (
    echo [WARN] output\model-Q4_K_S.gguf が見つかりませんでした。output フォルダを確認してください。
)

pause
