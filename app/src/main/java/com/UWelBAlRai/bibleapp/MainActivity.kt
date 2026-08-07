package com.UWelBAlRai.bibleapp

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityBookListBinding
import kotlinx.coroutines.launch
import android.content.Intent

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_SEARCH_VERSION_CODE = "search_version_code"
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityBookListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerBooks.layoutManager = LinearLayoutManager(this)

        // 帶入上次選擇的搜尋語言，預設中文
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedVersionCode = prefs.getString(KEY_SEARCH_VERSION_CODE, "CUV")
        binding.radioEnglish.isChecked = savedVersionCode == "NKJV"
        binding.radioChinese.isChecked = savedVersionCode != "NKJV"

        binding.radioGroupSearchLanguage.setOnCheckedChangeListener { _, checkedId ->
            val versionCode = if (checkedId == binding.radioEnglish.id) "NKJV" else "CUV"
            prefs.edit().putString(KEY_SEARCH_VERSION_CODE, versionCode).apply()
        }

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            val books = db.bibleDao().getAllBooks()
            binding.recyclerBooks.adapter = BookAdapter(books) { book ->
                val intent = Intent(this@MainActivity, ChapterListActivity::class.java)
                intent.putExtra("book_code", book.bookCode)
                startActivity(intent)
            }

            // 用同一份 books 清單反查書名，不用另外查一次資料庫
            val lastReadBook = prefs.getString(KEY_LAST_READ_BOOK, null)
            val lastReadChapter = prefs.getInt(KEY_LAST_READ_CHAPTER, -1)
            val lastReadBookName = books.find { it.bookCode == lastReadBook }?.cnName

            if (lastReadBook != null && lastReadChapter != -1 && lastReadBookName != null) {
                binding.btnContinueReading.visibility = View.VISIBLE
                binding.btnContinueReading.text = "繼續閱讀：${lastReadBookName} 第${lastReadChapter}章"
                binding.btnContinueReading.setOnClickListener {
                    val intent = Intent(this@MainActivity, VerseReaderActivity::class.java)
                    intent.putExtra("book_code", lastReadBook)
                    intent.putExtra("chapter", lastReadChapter)
                    startActivity(intent)
                }
            }
        }

        binding.checkboxMultiKeyword.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutMultiKeywordFields.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        fun performSearch() {
            val keyword1 = binding.editSearchKeyword1.text.toString().trim()
            if (keyword1.isEmpty()) return

            val isMultiKeyword = binding.checkboxMultiKeyword.isChecked
            val keyword2 = if (isMultiKeyword) binding.editSearchKeyword2.text.toString().trim() else ""
            val keyword3 = if (isMultiKeyword) binding.editSearchKeyword3.text.toString().trim() else ""
            val useAnd = binding.radioAnd.isChecked
            val versionCode = if (binding.radioEnglish.isChecked) "NKJV" else "CUV"

            val intent = Intent(this@MainActivity, SearchResultActivity::class.java)
            intent.putStringArrayListExtra("keywords", arrayListOf(keyword1, keyword2, keyword3))
            intent.putExtra("use_and", useAnd)
            intent.putExtra("version_code", versionCode)
            startActivity(intent)
        }

        val searchOnAction = { _: android.widget.TextView, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener { performSearch() }
        binding.editSearchKeyword1.setOnEditorActionListener(searchOnAction)
        binding.editSearchKeyword3.setOnEditorActionListener(searchOnAction)
    }
}