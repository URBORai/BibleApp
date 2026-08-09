package com.UWelBAlRai.bibleapp

import com.UWelBAlRai.bibleapp.data.BookOrder

/**
 * 舊約／新約的切分。
 *
 * getAllBooks() 已依 order_index 排序，排序後的前 39 筆固定是舊約、其餘 27 筆是新約，
 * 所以用清單位置判斷就好，不必依賴 order_index 的實際數值。
 *
 * 集中在這裡是因為書卷選單與搜尋範圍都要切這一刀，39 這個數字只該寫一次。
 */
object Testament {

    private const val OLD_TESTAMENT_BOOK_COUNT = 39

    fun oldTestament(books: List<BookOrder>): List<BookOrder> =
        books.take(OLD_TESTAMENT_BOOK_COUNT)

    fun newTestament(books: List<BookOrder>): List<BookOrder> =
        books.drop(OLD_TESTAMENT_BOOK_COUNT)
}
