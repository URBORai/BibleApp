package com.UWelBAlRai.bibleapp

import android.app.Application

// 在任何 Activity 建立之前先套用使用者上次選擇的深色模式與介面語言，
// 避免畫面先閃一下淺色／系統語言再切換。
// 語言偏好我們自己存在 SharedPreferences，所以每次啟動都要在這裡重新套用一次
// （沒有啟用 AppCompat 的 autoStoreLocales，API 33 以下不會自動幫我們記住）
class BibleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySavedMode(this)
        LocalePrefs.applySavedLanguage(this)
    }
}
