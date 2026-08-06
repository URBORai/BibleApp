package com.UWelBAlRai.bibleapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "book_order")
data class BookOrder(
    @PrimaryKey @ColumnInfo(name = "book_code") val bookCode: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "cn_name") val cnName: String,
    @ColumnInfo(name = "cn_abbr") val cnAbbr: String
)