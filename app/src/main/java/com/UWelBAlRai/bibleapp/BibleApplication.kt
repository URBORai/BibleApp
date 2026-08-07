package com.UWelBAlRai.bibleapp

import android.app.Application

// 在任何 Activity 建立之前先套用使用者上次選擇的深色模式，避免畫面先閃一下淺色再切換
class BibleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySavedMode(this)
    }
}
