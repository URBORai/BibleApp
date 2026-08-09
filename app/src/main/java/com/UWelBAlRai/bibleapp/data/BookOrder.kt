package com.UWelBAlRai.bibleapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * 書卷清單。中英兩組名稱並存，顯示哪一組由介面語言決定
 * （見 BookOrder.displayName / displayAbbr）。
 *
 * en_name 採 NKJV 的書卷標題，跟本 App 的英文經文版本一致；
 * en_abbr 是統一長度的三字母縮寫，給書卷選擇網格的窄格子用。
 */
@Entity(tableName = "book_order")
data class BookOrder(
    @PrimaryKey @ColumnInfo(name = "book_code") val bookCode: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "cn_name") val cnName: String,
    @ColumnInfo(name = "cn_abbr") val cnAbbr: String,
    @ColumnInfo(name = "en_name") val enName: String,
    @ColumnInfo(name = "en_abbr") val enAbbr: String
)
