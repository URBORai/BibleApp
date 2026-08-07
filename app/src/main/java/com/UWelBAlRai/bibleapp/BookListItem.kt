package com.UWelBAlRai.bibleapp

import com.UWelBAlRai.bibleapp.data.BookOrder

sealed class BookListItem {
    data class Header(val title: String) : BookListItem()
    data class BookItem(val book: BookOrder) : BookListItem()
}
