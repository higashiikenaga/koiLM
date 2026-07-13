cmake_minimum_required(VERSION 3.22.1)
project(koilm_llama)

set(CMAKE_CXX_STANDARD 17)

# llama.cpp は submodule として配置する想定:
#   git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp が見つかりません。"
        "android/app/src/main/cpp/ 直下で"
        "`git submodule add https://github.com/ggml-org/llama.cpp llama.cpp` を実行してください。")
endif()

# サーバー機能・CLIツール・Web UIはネットワーク機能を含む/不要なため無効化。
# 完全ローカル動作の要件上、コアの推論ライブラリ(libllama/ggml)のみをビルドする。
# 特に LLAMA_USE_PREBUILT_UI はビルド時にHF Bucketから外部ダウンロードを行うため必ずOFFにすること。
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_USE_PREBUILT_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# 重要: 以前は "LLAMA_NATIVE" という誤った変数名を指定しており、これはggmlのCMakeに
# 存在しないため何の効果も無かった(正しい変数名は GGML_NATIVE)。クロスコンパイルでは
# GGML_NATIVE はどのみち自動的にOFFになるが、そのままだとARM64のdotprod/fp16といった
# SIMD拡張命令が有効化されず、汎用カーネルにフォールバックして生成が著しく遅くなる。
# Snapdragon 888 5G / Dimensity 7400 Ultra 以上クラス(dotprod対応)をターゲットに、
# 明示的にアーキテクチャを指定して最適化カーネルを有効化する。
# armv8.2-a はAArch64(64bit)向けのアーキテクチャレベルなので、32bitのarmeabi-v7aビルドに
# 適用するとコンパイルエラーになる。arm64-v8aビルドの時だけ設定すること。
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
else()
    # armeabi-v7a(32bit)では armv8.2-a 指定はコンパイルエラーになるため、
    # 過去のCMakeキャッシュに値が残っていても必ず空に戻す。
    set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)
endif()

add_subdirectory(llama.cpp)

add_library(koilm_llama SHARED
    llama-android.cpp
)

target_link_libraries(koilm_llama
    llama
    android
    log
)
