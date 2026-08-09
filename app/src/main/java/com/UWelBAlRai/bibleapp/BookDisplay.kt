package com.UWelBAlRai.bibleapp

import android.content.Context
import com.UWelBAlRai.bibleapp.data.BookOrder

/**
 * 書卷名稱要顯示中文還是英文，集中在這裡決定，避免每個畫面各自判斷。
 *
 * 判斷依據跟「經文主體版本」同一個旗標（ui_prefers_english，locale 限定的 bool 資源），
 * 所以英文介面下經文是 NKJV、書卷名也是英文，兩者不會一邊中一邊英。
 */
private fun Context.useEnglishBookNames(): Boolean =
    resources.getBoolean(R.bool.ui_prefers_english)

/** 全名，給標題列與搜尋結果的分組標題用 */
fun BookOrder.displayName(context: Context): String =
    if (context.useEnglishBookNames()) enName else cnName

/** 簡稱，給書卷選擇網格的窄格子用 */
fun BookOrder.displayAbbr(context: Context): String =
    if (context.useEnglishBookNames()) enAbbr else cnAbbr
