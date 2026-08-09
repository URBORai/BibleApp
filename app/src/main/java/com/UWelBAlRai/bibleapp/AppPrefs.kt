package com.UWelBAlRai.bibleapp

import android.content.Context
import android.content.SharedPreferences

/**
 * 全 App 共用的 SharedPreferences。
 *
 * 檔名原本在 VerseReaderActivity、SearchActivity、ThemePrefs、LocalePrefs 各自宣告一次，
 * 打錯一個字就會變成另一個檔、偏好靜悄悄地讀不到，所以集中在這裡當唯一來源。
 * 各自的 key 仍留在用得到的地方，這裡只管「存在哪個檔」。
 */
object AppPrefs {

    const val NAME = "bible_app_prefs"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
