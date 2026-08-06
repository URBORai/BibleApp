package com.UWelBAlRai.bibleapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bible_version")
data class  BibleVersion(
    @PrimaryKey val id:Int,
    val code: String,
    val name: String
)
