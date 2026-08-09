package com.UWelBAlRai.bibleapp.data

/**
 * 對照查詢的一節。哪個版本是「主體」由呼叫端決定（見 BibleDao.getParallelChapter），
 * 中文介面是 CUV 為主、NKJV 為對照，英文介面反過來，所以欄位刻意不叫 cuvText／nkjvText。
 */
data class ParallelVerse(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val primaryText: String,
    val secondaryText: String?,  // 可能為 null，對應之前討論的三處分節差異
    val pageNo: Int?
)
