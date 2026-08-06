package com.UWelBAlRai.bibleapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "verse",
    indices = [
        Index(value = ["version_id", "book", "chapter", "verse"], name = "idx_verse_lookup"),
        Index(value = ["text"], name = "idx_verse_text")
    ]
)
data class Verse(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "version_id") val versionId: Int,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    @ColumnInfo(name = "page_no") val pageNo: Int?
)