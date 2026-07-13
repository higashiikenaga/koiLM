import java.util.Properties

plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" // Kotlin 2.0以降、Composeコンパイラは別プラグインが必須
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" // Room アノテーションプロセッサ
}

// リリース署名用のkeystore.properties(DLSite等での直接配布向け)。
// gitにコミットしないこと。存在しない場合はリリースビルドを無署名扱いにする(assembleDebug等には影響しない)。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.koilm.app"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.koilm.app"
        minSdk = 26 // llama.cpp NEON最適化・4GB以上RAM端末を主対象とするため
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                // llama.cpp は行列演算中心のためリリース最適化必須。
                // ARM最適化(dotprod等)の有効化は cpp/CMakeLists.txt の GGML_CPU_ARM_ARCH で行う。
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
                cppFlags += listOf("-std=c++17")
            }
        }

        ndk {
            // 主要な実機アーキテクチャのみ(サイズ削減のためx86系は任意)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    // composeOptions.kotlinCompilerExtensionVersion はKotlin 2.0の
    // org.jetbrains.kotlin.plugin.compose プラグイン導入に伴い不要(バージョンはKotlinプラグインに追従)。

    packaging {
        // llama.cpp .so の重複除外(GGML系ライブラリの重複コピー対策)
        jniLibs {
            useLegacyPackaging = false
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = if (variant.buildType.name == "release") "koiLM.apk" else "koiLM-debug.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 会話履歴・要約メモリの永続化(完全ローカルSQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 年齢確認フラグ・表現レベル設定の永続化(完全ローカル、外部送信なし)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // AIからの自発的メッセージ生成(裏で動作するバックグラウンドジョブ)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
