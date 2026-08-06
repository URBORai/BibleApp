package com.UWelBAlRai.bibleapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BibleVersion::class, BookOrder::class, Verse::class],
    version = 1,
    exportSchema = false
)
abstract class BibleDatabase : RoomDatabase() {

    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: BibleDatabase? = null

        fun getInstance(context: Context): BibleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BibleDatabase::class.java,
                    "bible.db"
                )
                    .createFromAsset("bible.db")   // 關鍵：從 assets 匯入現成資料庫，不建空表
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}