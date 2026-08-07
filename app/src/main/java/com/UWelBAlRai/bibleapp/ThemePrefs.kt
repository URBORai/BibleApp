package com.UWelBAlRai.bibleapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

// 深色模式選項的儲存與套用邏輯，VerseReaderActivity 的設定入口跟 BibleApplication 啟動時都會用到
object ThemePrefs {
    private const val PREFS_NAME = "bible_app_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    fun getSavedMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun saveMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun applyMode(mode: String) {
        val nightMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun applySavedMode(context: Context) {
        applyMode(getSavedMode(context))
    }
}
