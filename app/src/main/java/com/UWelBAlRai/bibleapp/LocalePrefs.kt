package com.UWelBAlRai.bibleapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 介面顯示語言的儲存與套用邏輯，寫法刻意對齊 [ThemePrefs]。
 *
 * 只影響「介面文字」（strings.xml）。聖經經文本身來自資料庫的 CUV／NKJV，
 * 跟這裡完全無關；搜尋畫面的中文／英文選項選的是「查哪個版本的經文」，也是另一回事。
 *
 * 套用走 AppCompatDelegate.setApplicationLocales()：API 33 以上會轉交系統的
 * per-app language，33 以下由 AppCompat 自己重建 Activity 套用，呼叫端不必處理
 * Configuration，也不用自己 recreate()。
 */
object LocalePrefs {
    private const val PREFS_NAME = "bible_app_prefs"
    private const val KEY_UI_LANGUAGE = "ui_language"

    /** 不覆蓋，交給系統語言決定要用 values 還是 values-en */
    const val MODE_SYSTEM = "system"
    const val LANG_CHINESE = "zh"
    const val LANG_ENGLISH = "en"

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UI_LANGUAGE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UI_LANGUAGE, language)
            .apply()
    }

    fun applyLanguage(language: String) {
        // 空的 LocaleList 等於「清除覆蓋」，資源機制回頭跟隨系統語言
        val locales = if (language == MODE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applySavedLanguage(context: Context) {
        applyLanguage(getSavedLanguage(context))
    }
}
