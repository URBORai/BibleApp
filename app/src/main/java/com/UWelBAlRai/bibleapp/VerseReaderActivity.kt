package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

// App 啟動首頁：沒有上次閱讀記錄時預設載入創世記第 1 章
class VerseReaderActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"
        private const val KEY_FONT_SIZE = "verse_font_size"

        private const val DEFAULT_FONT_SIZE = 18f
        private const val MIN_FONT_SIZE = 14f
        private const val MAX_FONT_SIZE = 28f
        private const val FONT_SIZE_STEP = 2f

        private const val DEFAULT_BOOK_CODE = "GEN"
        private const val DEFAULT_CHAPTER = 1

        // 舊約 39 卷排在 book_order 排序後的最前面，其餘為新約，用清單位置判斷，不依賴 order_index 的實際數值
        private const val OLD_TESTAMENT_BOOK_COUNT = 39
    }

    private lateinit var binding: ActivityVerseReaderBinding
    private lateinit var prefs: SharedPreferences
    private val db by lazy { BibleDatabase.getInstance(applicationContext) }
    private lateinit var adapter: VerseAdapter

    private var currentBookCode: String = DEFAULT_BOOK_CODE
    private var currentChapter: Int = DEFAULT_CHAPTER

    // 全部書卷（依 order_index 排序）快取一份，跨書卷翻頁與書卷選單共用，不用每次重查
    private var cachedBooks: List<BookOrder> = emptyList()

    // 目前書卷的章節清單，翻頁與「章」選單共用；章號不保證連號，一律用清單位置前後移動
    private var currentChapters: List<Int> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVerseReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        binding.recyclerVerses.layoutManager = LinearLayoutManager(this)
        adapter = VerseAdapter()
        adapter.textSizeSp = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        binding.recyclerVerses.adapter = adapter

        binding.btnToggleParallel.setOnClickListener {
            adapter.showParallel = !adapter.showParallel
            binding.btnToggleParallel.text =
                if (adapter.showParallel) "隱藏英文對照" else "顯示中英對照"
        }

        binding.btnIncreaseFont.setOnClickListener { changeFontSize(FONT_SIZE_STEP) }
        binding.btnDecreaseFont.setOnClickListener { changeFontSize(-FONT_SIZE_STEP) }

        binding.btnOpenSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.btnOldTestament.setOnClickListener { showBookPicker(isOldTestament = true) }
        binding.btnNewTestament.setOnClickListener { showBookPicker(isOldTestament = false) }
        binding.btnChapterPicker.setOnClickListener {
            lifecycleScope.launch {
                val book = books().find { it.bookCode == currentBookCode } ?: return@launch
                showChapterPicker(book)
            }
        }
        binding.btnThemeSettings.setOnClickListener { showThemeDialog() }

        binding.fabPreviousChapter.setOnClickListener { goToAdjacentChapter(-1) }
        binding.fabNextChapter.setOnClickListener { goToAdjacentChapter(1) }

        val intentBook = intent.getStringExtra("book_code")
        val intentChapter = intent.getIntExtra("chapter", -1)
        val intentVerse = intent.getIntExtra("verse", -1)

        val (initialBook, initialChapter) = if (intentBook != null && intentChapter != -1) {
            intentBook to intentChapter
        } else {
            val savedBook = prefs.getString(KEY_LAST_READ_BOOK, null)
            val savedChapter = prefs.getInt(KEY_LAST_READ_CHAPTER, -1)
            if (savedBook != null && savedChapter != -1) {
                savedBook to savedChapter
            } else {
                DEFAULT_BOOK_CODE to DEFAULT_CHAPTER
            }
        }

        loadChapter(initialBook, initialChapter, intentVerse)
    }

    private fun changeFontSize(delta: Float) {
        val newSize = (adapter.textSizeSp + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        adapter.textSizeSp = newSize
        prefs.edit().putFloat(KEY_FONT_SIZE, newSize).apply()
    }

    // 章節切換一律走這裡，不離開畫面：更新目前狀態、記住上次閱讀位置、換資料
    private fun loadChapter(bookCode: String, chapter: Int, targetVerse: Int = -1) {
        lifecycleScope.launch {
            val verses = db.bibleDao().getParallelChapter(bookCode, chapter)

            currentBookCode = bookCode
            currentChapter = chapter
            currentChapters = db.bibleDao().getChapterList(bookCode)

            prefs.edit()
                .putString(KEY_LAST_READ_BOOK, bookCode)
                .putInt(KEY_LAST_READ_CHAPTER, chapter)
                .apply()

            adapter.updateVerses(verses)
            binding.recyclerVerses.scrollToPosition(0)
            updateChapterNavButtons()

            // 所有切換途徑（舊約／新約選單、章選單、左右浮動按鈕）都會走到這裡，標題一律同步更新
            val bookName = books().find { it.bookCode == bookCode }?.cnName ?: bookCode
            binding.textCurrentLocation.text = "$bookName 第${chapter}章"

            // 從搜尋結果進入時，捲動到對應節並短暫高亮
            if (targetVerse != -1) {
                val targetIndex = verses.indexOfFirst { it.verse == targetVerse }
                if (targetIndex != -1) {
                    binding.recyclerVerses.post {
                        binding.recyclerVerses.scrollToPosition(targetIndex)
                        // 等 scrollToPosition 觸發的 layout 完成、目標 ViewHolder 已綁定後再套用高亮
                        binding.recyclerVerses.post {
                            adapter.highlightVerse(targetIndex)
                        }
                    }
                }
            }
        }
    }

    // getAllBooks() 已依 order_index 排序，清單位置就是正典順序，跨書卷翻頁直接用位置前後移動
    private suspend fun books(): List<BookOrder> {
        if (cachedBooks.isEmpty()) {
            cachedBooks = db.bibleDao().getAllBooks()
        }
        return cachedBooks
    }

    // 章節翻頁：本卷內還有前／後一章就直接換章，
    // 已經在本卷首章／末章則跨到相鄰書卷的末章／首章，只有整本聖經的頭尾才無路可走
    private fun goToAdjacentChapter(step: Int) {
        lifecycleScope.launch {
            val chapterIndex = currentChapters.indexOf(currentChapter)
            val targetIndex = chapterIndex + step
            if (chapterIndex != -1 && targetIndex in currentChapters.indices) {
                loadChapter(currentBookCode, currentChapters[targetIndex])
                return@launch
            }

            val adjacentBook = adjacentBook(step) ?: return@launch
            val chapters = db.bibleDao().getChapterList(adjacentBook.bookCode)
            // 往前翻進入前一卷的最後一章，往後翻進入下一卷的第一章
            val chapter = (if (step < 0) chapters.lastOrNull() else chapters.firstOrNull()) ?: return@launch
            loadChapter(adjacentBook.bookCode, chapter)
        }
    }

    private suspend fun adjacentBook(step: Int): BookOrder? {
        val allBooks = books()
        val bookIndex = allBooks.indexOfFirst { it.bookCode == currentBookCode }
        if (bookIndex == -1) return null
        return allBooks.getOrNull(bookIndex + step)
    }

    // 只有創世記第 1 章的「上一章」與啟示錄最後一章的「下一章」真的沒有去處，其餘一律可按
    private suspend fun updateChapterNavButtons() {
        val allBooks = books()
        val bookIndex = allBooks.indexOfFirst { it.bookCode == currentBookCode }
        val chapterIndex = currentChapters.indexOf(currentChapter)

        setNavEnabled(binding.fabPreviousChapter, chapterIndex > 0 || bookIndex > 0)
        setNavEnabled(
            binding.fabNextChapter,
            (chapterIndex != -1 && chapterIndex < currentChapters.lastIndex) ||
                (bookIndex != -1 && bookIndex < allBooks.lastIndex)
        )
    }

    // 停用時保留按鈕位置只調暗，避免版面在翻到頭尾時跳動
    private fun setNavEnabled(fab: FloatingActionButton, enabled: Boolean) {
        fab.isEnabled = enabled
        fab.alpha = if (enabled) 1f else 0.3f
    }

    private fun showBookPicker(isOldTestament: Boolean) {
        lifecycleScope.launch {
            val books = books()
            // book_order 已依 order_index 排序，前 39 筆固定是舊約，其餘是新約
            val filtered = if (isOldTestament) {
                books.take(OLD_TESTAMENT_BOOK_COUNT)
            } else {
                books.drop(OLD_TESTAMENT_BOOK_COUNT)
            }
            if (filtered.isEmpty()) return@launch

            val names = filtered.map { it.cnName }.toTypedArray()
            AlertDialog.Builder(this@VerseReaderActivity)
                .setTitle(if (isOldTestament) "舊約" else "新約")
                .setItems(names) { _, which ->
                    // 選完書卷不直接跳第 1 章，接著讓使用者挑章節
                    lifecycleScope.launch { showChapterPicker(filtered[which]) }
                }
                .show()
        }
    }

    private suspend fun showChapterPicker(book: BookOrder) {
        // 目前這卷的章節清單 loadChapter 已經取好，直接用快取；換卷才需要重查
        val chapters = if (book.bookCode == currentBookCode && currentChapters.isNotEmpty()) {
            currentChapters
        } else {
            db.bibleDao().getChapterList(book.bookCode)
        }
        if (chapters.isEmpty()) return

        val labels = chapters.map { "第 $it 章" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(book.cnName)
            .setItems(labels) { _, which ->
                loadChapter(book.bookCode, chapters[which])
            }
            .show()
    }

    private fun showThemeDialog() {
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
}
