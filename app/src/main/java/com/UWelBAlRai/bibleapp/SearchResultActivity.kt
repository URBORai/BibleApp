package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivitySearchResultBinding
import kotlinx.coroutines.launch

class SearchResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

        // 保留原始 3 個欄位順序（含空白項目），讓 Adapter 能依欄位順序分配標註顏色
        val keywords = intent.getStringArrayListExtra("keywords") ?: return
        if (keywords.isEmpty() || keywords[0].isBlank()) return
        val useAnd = intent.getBooleanExtra("use_and", true)
        val versionCode = intent.getStringExtra("version_code") ?: "CUV"

        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            // getAllBooks() 已依 order_index 排序，直接照順序分組即可
            val books = db.bibleDao().getAllBooks()
            val results = db.bibleDao().searchTextMulti(versionCode, keywords, useAnd)

            binding.textEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE

            val resultsByBook = results.groupBy { it.book }
            val listItems = mutableListOf<SearchListItem>()
            for (book in books) {
                val bookResults = resultsByBook[book.bookCode] ?: continue
                // 分組標題的書卷名也跟著介面語言，才不會跟閱讀畫面的標題列一邊中一邊英
                listItems.add(SearchListItem.Header(book.displayName(this@SearchResultActivity)))
                bookResults.forEach { verse -> listItems.add(SearchListItem.Result(verse)) }
            }

            binding.recyclerSearchResults.adapter = SearchResultAdapter(
                items = listItems,
                keywords = keywords
            ) { verse ->
                val intent = Intent(this@SearchResultActivity, VerseReaderActivity::class.java)
                // Reader 宣告為 singleTop，但它此刻在堆疊底層、不在頂端，光靠 singleTop 仍會疊新的一份。
                // CLEAR_TOP 會把 Search／SearchResult 收掉並讓既有的 Reader 收到 onNewIntent，
                // 回到「Reader 只有一個、返回鍵直接離開 App」的行為
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra(VerseReaderActivity.EXTRA_BOOK_CODE, verse.book)
                intent.putExtra(VerseReaderActivity.EXTRA_CHAPTER, verse.chapter)
                intent.putExtra(VerseReaderActivity.EXTRA_VERSE, verse.verse)
                startActivity(intent)
            }
        }
    }
}
