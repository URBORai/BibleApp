plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
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

    buildTypes {
        release {
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