package com.UWelBAlRai.bibleapp

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * 搜尋相關畫面上的「返回首頁」按鈕：顯示與否的偏好，以及按下去要做什麼。
 *
 * 開關寫法對齊 [ThemePrefs] / [LocalePrefs] / [NightReadingPrefs]：
 * 設定它的是 VerseReaderActivity 的外觀對話框，讀取它的是兩個搜尋畫面，
 * key 只留這裡一份，兩邊不可能對不上。
 */
object HomeButtonPrefs {

    private const val KEY_SHOW_HOME_BUTTON = "show_home_button"
    private const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean =
        AppPrefs.of(context).getBoolean(KEY_SHOW_HOME_BUTTON, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.of(context)
            .edit()
            .putBoolean(KEY_SHOW_HOME_BUTTON, enabled)
            .apply()
    }
}

/**
 * 回到閱讀畫面，並且是回到「使用者離開時正在看的那一章」。
 *
 * 刻意不帶任何 book／chapter 參數：VerseReaderActivity 是 singleTop，而且一直活在
 * 堆疊最底層，CLEAR_TOP + SINGLE_TOP 會把上面的搜尋畫面收掉、讓既有的那個 Reader
 * 收到 onNewIntent 而不是重建。它的 onNewIntent 在讀不到跳轉 extras 時會直接 return，
 * 不呼叫 loadChapter——所以畫面上的章節、捲動位置、字級全部原封不動。
 *
 * 這跟「點搜尋結果跳轉」用的是同一組 flag，差別只在那邊會帶 extras 指定要跳去哪一節。
 * 帶參數反而會出錯：那等於用「進搜尋前」的舊位置去覆蓋，
 * 使用者若在搜尋途中已經跳過章節，就會被拉回舊的地方。
 */
fun Activity.goToReaderHome() {
    val intent = Intent(this, VerseReaderActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    startActivity(intent)
}
