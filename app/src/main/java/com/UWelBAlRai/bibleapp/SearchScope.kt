package com.UWelBAlRai.bibleapp

import android.content.Context
import com.UWelBAlRai.bibleapp.data.BookOrder

/**
 * 搜尋範圍：限定要掃描哪些書卷。
 *
 * 四種模式最後都會被 [resolve] 攤平成一份 book_code 清單再交給查詢，
 * 所以 DAO 只有一條路徑，不必為「全部／舊約／新約／自訂」寫四種 SQL。
 *
 * 只影響「查詢時掃描哪些書卷」，不影響搜尋結果畫面的分組呈現。
 */
object SearchScope {

    const val MODE_ALL = "all"
    const val MODE_OLD_TESTAMENT = "old"
    const val MODE_NEW_TESTAMENT = "new"
    const val MODE_CUSTOM = "custom"

    private const val KEY_SCOPE_MODE = "search_scope_mode"
    private const val KEY_SCOPE_BOOKS = "search_scope_books"

    // book_code 是資料庫定義的短代碼（GEN、1SA…），不會出現逗號，
    // 所以用逗號串接就夠了，不需要像搜尋歷史那樣為了自由文字動用 JSON
    private const val BOOK_SEPARATOR = ","

    fun getMode(context: Context): String =
        AppPrefs.of(context).getString(KEY_SCOPE_MODE, MODE_ALL) ?: MODE_ALL

    fun getCustomBookCodes(context: Context): List<String> =
        AppPrefs.of(context).getString(KEY_SCOPE_BOOKS, null)
            ?.split(BOOK_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** [customBookCodes] 只有 [MODE_CUSTOM] 用得到，其他模式一律存空的蓋掉舊資料 */
    fun save(context: Context, mode: String, customBookCodes: List<String> = emptyList()) {
        AppPrefs.of(context)
            .edit()
            .putString(KEY_SCOPE_MODE, mode)
            .putString(KEY_SCOPE_BOOKS, customBookCodes.joinToString(BOOK_SEPARATOR))
            .apply()
    }

    /**
     * 把模式攤平成實際要查的 book_code 清單。
     *
     * 自訂範圍會跟目前資料庫的書卷取交集：萬一存下來的代碼因為換資料庫而不存在了，
     * 直接丟掉就好，不要把不存在的代碼送進查詢。
     * 交集後變成空的（或本來就沒選）時退回全部，避免使用者卡在一個永遠查不到東西的範圍。
     */
    fun resolve(mode: String, customBookCodes: List<String>, allBooks: List<BookOrder>): List<String> {
        val allCodes = allBooks.map { it.bookCode }
        return when (mode) {
            MODE_OLD_TESTAMENT -> Testament.oldTestament(allBooks).map { it.bookCode }
            MODE_NEW_TESTAMENT -> Testament.newTestament(allBooks).map { it.bookCode }
            MODE_CUSTOM -> customBookCodes.filter { it in allCodes }.ifEmpty { allCodes }
            else -> allCodes
        }
    }
}
