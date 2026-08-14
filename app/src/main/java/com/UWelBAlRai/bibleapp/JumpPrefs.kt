package com.UWelBAlRai.bibleapp

import android.content.Context

/**
 * 兩個「直接輸入數字跳轉」入口要不要出現在閱讀畫面的工具列上。
 *
 * 寫法對齊 [HomeButtonPrefs] / [NightReadingPrefs] / [ChapterPagingPrefs]：
 * key 只留這裡一份，設定它的是外觀對話框，讀取它的是 VerseReaderActivity。
 *
 * 兩者都預設開啟：這是新功能，藏起來等於沒做——使用者不會知道去哪裡打開它。
 * 開關關掉只收起工具列上的按鈕，跳轉邏輯本身完全不受影響
 * （比照「顯示返回首頁按鈕」，那個關掉也只是收起捷徑，首頁仍然回得去）。
 */
object PageJumpPrefs {

    private const val KEY_SHOW_PAGE_JUMP = "show_page_jump"
    private const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean =
        AppPrefs.of(context).getBoolean(KEY_SHOW_PAGE_JUMP, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.of(context)
            .edit()
            .putBoolean(KEY_SHOW_PAGE_JUMP, enabled)
            .apply()
    }
}

/** 書卷編號跳轉入口的顯示開關，規則與 [PageJumpPrefs] 相同 */
object BookNumberJumpPrefs {

    private const val KEY_SHOW_BOOK_JUMP = "show_book_number_jump"
    private const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean =
        AppPrefs.of(context).getBoolean(KEY_SHOW_BOOK_JUMP, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.of(context)
            .edit()
            .putBoolean(KEY_SHOW_BOOK_JUMP, enabled)
            .apply()
    }
}
