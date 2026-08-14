import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

// 簽署設定從 keystore.properties 讀，該檔不進版控。
// 檔案不存在時（例如別台機器或 CI 沒放金鑰）不套用 signingConfig，
// release 仍可建置成 unsigned，不會讓整個 build 掛掉。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.UWelBAlRai.bibleapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.UWelBAlRai.bibleapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 開啟 R8 的程式碼縮減與混淆。keep 規則放在 src/main/keepRules/，
            // AGP 會把該目錄下所有規則檔合併後交給 R8。
            // 資源縮減沒有開：這個專案的資源本來就沒有未使用項目（lint UnusedResources = 0），
            // 開了只是多一層誤刪風險，沒有相對應的收益
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures{
        viewBinding=true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
//加入Room相關套件的引用
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    implementation(libs.androidx.recyclerview)
}