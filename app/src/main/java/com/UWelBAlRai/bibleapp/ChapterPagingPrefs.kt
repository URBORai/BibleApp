package com.UWelBAlRai.bibleapp

import android.content.Context

/**
 * 章節選擇網格要不要分頁，以及一頁最多放幾章。寫法對齊 [NightReadingPrefs] / [HomeButtonPrefs]。
 *
 * 只服務章節網格：書卷最多 66 卷、[GridPicker.COLUMNS_BOOK] 是 4 欄，一次全列出也只有
 * 17 列，捲動距離本來就不長，所以書卷網格完全不碰這組偏好。
 *
 * 預設關閉，維持既有行為（一次顯示全部、可捲動）——已經習慣直接捲的人升級後不會突然被
 * 換成另一種操作方式。
 */
object ChapterPagingPrefs {

    private const val KEY_ENABLED = "chapter_paging_enabled"
    private const val KEY_PAGE_SIZE = "chapter_page_size"

    private const val DEFAULT_ENABLED = false

    /**
     * 可選的每頁章節數。三個值都是 [GridPicker.COLUMNS_CHAPTER]（5 欄）的倍數，
     * 每一頁都剛好填滿整列，不會在頁尾留下半列空格。
     * 換算成列數分別是 6 / 10 / 20 列。
     */
    val PAGE_SIZE_OPTIONS = listOf(30, 50, 100)

    /** 50 章 = 10 列，多數手機一頁看得完又不必翻太多次；詩篇 150 章剛好三頁 */
    const val DEFAULT_PAGE_SIZE = 50

    fun isEnabled(context: Context): Boolean =
        AppPrefs.of(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        AppPrefs.of(context)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * 存到的值不在 [PAGE_SIZE_OPTIONS] 裡就退回預設：選項清單日後若調整，
     * 舊版存下來的數字不會變成一個 UI 上選不到、卻仍在生效的幽靈設定。
     */
    fun getPageSize(context: Context): Int {
        val saved = AppPrefs.of(context).getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE)
        return if (saved in PAGE_SIZE_OPTIONS) saved else DEFAULT_PAGE_SIZE
    }

    fun setPageSize(context: Context, pageSize: Int) {
        AppPrefs.of(context)
            .edit()
            .putInt(KEY_PAGE_SIZE, pageSize)
            .apply()
    }

    /**
     * 直接餵給 [GridPicker.show] 的 pageSize 參數：關閉時回傳 null（= 不分頁）。
     *
     * 讓「開關 + 數量」兩個偏好在這裡收斂成一個值，呼叫端就不必自己寫
     * `if (isEnabled) getPageSize() else null`，也不會有人漏判開關。
     */
    fun pageSizeOrNull(context: Context): Int? =
        if (isEnabled(context)) getPageSize(context) else null
}
