package com.UWelBAlRai.bibleapp

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityBookListBinding
import kotlinx.coroutines.launch
import android.content.Intent

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_SEARCH_VERSION_CODE = "search_version_code"
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"

        // 舊約 39 卷排在 book_order 排序後的最前面，其餘為新約，用清單位置判斷，不依賴 order_index 的實際數值
        private const val OLD_TESTAMENT_BOOK_COUNT = 39
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityBookListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

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

        fun showThemeDialog() {
            val options = arrayOf("跟隨系統", "淺色", "深色")
            val modes = arrayOf(ThemePrefs.MODE_SYSTEM, ThemePrefs.MODE_LIGHT, ThemePrefs.MODE_DARK)
            val checkedIndex = modes.indexOf(ThemePrefs.getSavedMode(this)).coerceAtLeast(0)

            AlertDialog.Builder(this)
                .setTitle("外觀模式")
                .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                    val selectedMode = modes[which]
                    ThemePrefs.saveMode(this, selectedMode)
                    ThemePrefs.applyMode(selectedMode)
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnThemeSettings.setOnClickListener { showThemeDialog() }

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            val books = db.bibleDao().getAllBooks()

            // book_order 已依 order_index 排序，前 39 筆固定是舊約，其餘是新約
            val oldTestamentBooks = books.take(OLD_TESTAMENT_BOOK_COUNT)
            val newTestamentBooks = books.drop(OLD_TESTAMENT_BOOK_COUNT)
            val bookListItems = mutableListOf<BookListItem>()
            if (oldTestamentBooks.isNotEmpty()) {
                bookListItems.add(BookListItem.Header("舊約"))
                oldTestamentBooks.forEach { bookListItems.add(BookListItem.BookItem(it)) }
            }
            if (newTestamentBooks.isNotEmpty()) {
                bookListItems.add(BookListItem.Header("新約"))
                newTestamentBooks.forEach { bookListItems.add(BookListItem.BookItem(it)) }
            }

            binding.recyclerBooks.adapter = BookAdapter(bookListItems) { book ->
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