package com.UWelBAlRai.bibleapp

import com.UWelBAlRai.bibleapp.data.Verse

sealed class SearchListItem {
    data class Header(val bookName: String) : SearchListItem()
    data class Result(val verse: Verse) : SearchListItem()
}
