package com.UWelBAlRai.bibleapp.data

data class ParallelVerse(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val cuvText: String,
    val nkjvText: String?,   // 可能為 null，對應之前討論的三處分節差異
    val pageNo: Int?
)