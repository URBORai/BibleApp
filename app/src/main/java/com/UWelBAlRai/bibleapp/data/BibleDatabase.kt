package com.UWelBAlRai.bibleapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version 3：移除 verse(text) 上的 idx_verse_text 索引並 VACUUM，資料庫從 18.5MB 降到 9.5MB。
// 那個索引佔了 7.7MB，但搜尋一律是 LIKE '%關鍵字%'，前置萬用字元讓 B-tree 索引在原理上
// 就用不到（EXPLAIN QUERY PLAN 實測走的是 idx_verse_lookup），純粹是死重量。
// 表結構與資料完全沒動，查詢真正依賴的 idx_verse_lookup 保留。
// 版號要跟著加，已安裝的機器才會換到瘦身後的檔案（唯讀參考資料，重copy 不會弄丟使用者資料）。
//
// version 2：book_order 新增 en_name／en_abbr 兩欄。
// 版號一定要跟著 entity 一起加，否則已安裝過的機器上，Room 會拿舊 schema 的
// 資料庫去對新的 entity，開檔時就丟 schema 不符。
// 加了版號之後，createFromAsset + 破壞性遷移會直接重新複製一份新的 assets/bible.db；
// 這個資料庫是唯讀的參考資料（閱讀進度存在 SharedPreferences），重copy 不會弄丟使用者資料
@Database(
    entities = [BibleVersion::class, BookOrder::class, Verse::class],
    version = 3,
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