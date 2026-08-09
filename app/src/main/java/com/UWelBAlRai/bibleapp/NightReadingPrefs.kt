package com.UWelBAlRai.bibleapp

import android.content.Context

/**
 * 護眼模式（暖色濾鏡）的開關狀態，寫法對齊 [ThemePrefs] 與 [LocalePrefs]。
 *
 * 抽出來的理由：這個偏好有兩組使用者——設定它的是 VerseReaderActivity 的外觀對話框，
 * 讀取它的是 [BaseActivity]（三個畫面都要套用）。key 只留在這裡一份，
 * 兩邊就不可能對不上。
 *
 * 這跟深色模式是兩回事：深色模式換的是色彩資源，護眼模式只是往畫面上疊一層暖色，
 * 兩者可以同時開啟。
 */
object NightReadingPrefs {

    private const val KEY_NIGHT_READING_MODE = "night_reading_mode"
    private const val DEFAULT_ENABLED = false

    fun isEnabled(context: Context): Boolean =
        AppPrefs.of(context).getBoolean(KEY_NIGHT_READING_MODE, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.of(context)
            .edit()
            .putBoolean(KEY_NIGHT_READING_MODE, enabled)
            .apply()
    }
}
