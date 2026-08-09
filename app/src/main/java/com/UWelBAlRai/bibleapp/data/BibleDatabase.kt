package com.UWelBAlRai.bibleapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version 2：book_order 新增 en_name／en_abbr 兩欄。
// 版號一定要跟著 entity 一起加，否則已安裝過的機器上，Room 會拿舊 schema 的
// 資料庫去對新的 entity，開檔時就丟 schema 不符。
// 加了版號之後，createFromAsset + 破壞性遷移會直接重新複製一份新的 assets/bible.db；
// 這個資料庫是唯讀的參考資料（閱讀進度存在 SharedPreferences），重copy 不會弄丟使用者資料
@Database(
    entities = [BibleVersion::class, BookOrder::class, Verse::class],
    version = 2,
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