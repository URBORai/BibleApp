package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// App 啟動首頁：沒有上次閱讀記錄時預設載入創世記第 1 章
class VerseReaderActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"
        private const val KEY_FONT_SIZE = "verse_font_size"
        private const val KEY_SHOW_PARALLEL = "show_parallel"

        private const val DEFAULT_SHOW_PARALLEL = false
        private const val DEFAULT_FONT_SIZE = 18f
        private const val MIN_FONT_SIZE = 14f
        private const val MAX_FONT_SIZE = 28f
        private const val FONT_SIZE_STEP = 2f

        private const val DEFAULT_BOOK_CODE = "GEN"
        private const val DEFAULT_CHAPTER = 1

        // 搜尋結果跳轉時帶進來的 Intent extras
        const val EXTRA_BOOK_CODE = "book_code"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_VERSE = "verse"

        // onSaveInstanceState 用的 key：重建後要恢復的是「畫面當下真正在看的位置」，
        // 而不是當初把這個 Activity 帶起來的 Intent 指定的位置
        private const val STATE_BOOK_CODE = "state_book_code"
        private const val STATE_CHAPTER = "state_chapter"
        private const val STATE_SHOW_PARALLEL = "state_show_parallel"

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

    // 進行中的章節載入。新的載入會先取消舊的，避免慢查詢回來後覆蓋掉較新的結果
    private var loadJob: Job? = null

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
        adapter.showParallel = prefs.getBoolean(KEY_SHOW_PARALLEL, DEFAULT_SHOW_PARALLEL)
        binding.recyclerVerses.adapter = adapter

        // 啟動時就依偏好把按鈕文字校正一次，不能沿用版面裡的預設字串
        updateParallelButtonText()
        binding.btnToggleParallel.setOnClickListener {
            adapter.showParallel = !adapter.showParallel
            prefs.edit().putBoolean(KEY_SHOW_PARALLEL, adapter.showParallel).apply()
            updateParallelButtonText()
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

        restoreInitialPosition(savedInstanceState)
    }

    // 決定啟動要顯示哪一章，優先序刻意如此：
    // 1. savedInstanceState — 旋轉／主題切換等重建，恢復重建前畫面實際在看的位置
    // 2. Intent extras — 從搜尋結果新跳轉進來（讀完就消耗掉，重建時不會再被讀到）
    // 3. SharedPreferences — 上次閱讀記錄
    // 4. 預設創世記第 1 章
    private fun restoreInitialPosition(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val book = savedInstanceState.getString(STATE_BOOK_CODE)
            val chapter = savedInstanceState.getInt(STATE_CHAPTER, -1)
            adapter.showParallel =
                savedInstanceState.getBoolean(STATE_SHOW_PARALLEL, adapter.showParallel)
            updateParallelButtonText()
            if (book != null && chapter != -1) {
                loadChapter(book, chapter)
                return
            }
        }

        val navigation = consumeNavigationExtras()
        if (navigation != null) {
            loadChapter(navigation.bookCode, navigation.chapter, navigation.verse)
            return
        }

        val savedBook = prefs.getString(KEY_LAST_READ_BOOK, null)
        val savedChapter = prefs.getInt(KEY_LAST_READ_CHAPTER, -1)
        if (savedBook != null && savedChapter != -1) {
            loadChapter(savedBook, savedChapter)
        } else {
            loadChapter(DEFAULT_BOOK_CODE, DEFAULT_CHAPTER)
        }
    }

    // singleTop 之下，從搜尋結果跳回來時走這裡而不是重建 Activity：
    // 只換掉顯示的章節，字級／對照開關／back stack 都原封不動
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 換掉 getIntent() 的回傳值，之後的 consumeNavigationExtras() 才讀得到新的 extras
        setIntent(intent)
        val navigation = consumeNavigationExtras() ?: return
        loadChapter(navigation.bookCode, navigation.chapter, navigation.verse)
    }

    private data class NavigationTarget(val bookCode: String, val chapter: Int, val verse: Int)

    // 讀出跳轉參數後就把 extras 從 Intent 移除。
    // 這是第 2 點的關鍵：Intent 會跟著 Activity 一起活著，不清掉的話每次重建都會再讀到同一組
    // 舊參數，把使用者後來手動翻到的章節蓋掉
    private fun consumeNavigationExtras(): NavigationTarget? {
        val bookCode = intent.getStringExtra(EXTRA_BOOK_CODE)
        val chapter = intent.getIntExtra(EXTRA_CHAPTER, -1)
        val verse = intent.getIntExtra(EXTRA_VERSE, -1)
        if (bookCode == null || chapter == -1) return null

        intent.removeExtra(EXTRA_BOOK_CODE)
        intent.removeExtra(EXTRA_CHAPTER)
        intent.removeExtra(EXTRA_VERSE)
        return NavigationTarget(bookCode, chapter, verse)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_BOOK_CODE, currentBookCode)
        outState.putInt(STATE_CHAPTER, currentChapter)
        outState.putBoolean(STATE_SHOW_PARALLEL, adapter.showParallel)
    }

    // 按鈕文字描述「按下去會發生什麼」，啟動套用偏好與點擊切換兩個進入點共用
    private fun updateParallelButtonText() {
        binding.btnToggleParallel.text =
            if (adapter.showParallel) "隱藏英文對照" else "顯示中英對照"
    }

    private fun changeFontSize(delta: Float) {
        val newSize = (adapter.textSizeSp + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        adapter.textSizeSp = newSize
        prefs.edit().putFloat(KEY_FONT_SIZE, newSize).apply()
    }

    // 章節切換一律走這裡，不離開畫面
    private fun loadChapter(bookCode: String, chapter: Int, targetVerse: Int = -1) {
        startChapterJob { showChapter(bookCode, chapter, targetVerse) }
    }

    // 所有會改動顯示章節的操作共用的進入點：
    // 取消還沒回來的上一次查詢，並在查詢期間把翻頁按鈕鎖住，
    // 避免連點時用到還沒更新的 currentChapter／currentChapters 算出錯誤的目標
    private fun startChapterJob(block: suspend () -> Unit) {
        loadJob?.cancel()
        setNavEnabled(binding.fabPreviousChapter, false)
        setNavEnabled(binding.fabNextChapter, false)
        loadJob = lifecycleScope.launch { block() }
    }

    // 實際換資料：更新目前狀態、記住上次閱讀位置、更新標題與按鈕
    private suspend fun showChapter(bookCode: String, chapter: Int, targetVerse: Int = -1) {
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

        // 查無資料（例如帶了無效的 book_code）時給明確提示，不要留一片白畫面
        binding.textChapterEmpty.visibility = if (verses.isEmpty()) View.VISIBLE else View.GONE

        // 所有切換途徑（舊約／新約選單、章選單、左右浮動按鈕）都會走到這裡，標題一律同步更新
        val bookName = books().find { it.bookCode == bookCode }?.cnName
        binding.textCurrentLocation.text = if (bookName != null) {
            "$bookName 第${chapter}章"
        } else {
            // 連書卷都查不到，至少讓標題誠實反映要求的位置，不要假裝載入成功
            "$bookCode 第${chapter}章"
        }

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
        // 跟 loadChapter 共用同一個 job：翻頁與其他切換途徑互相取消，不會有兩個載入同時進行
        startChapterJob {
            val chapterIndex = currentChapters.indexOf(currentChapter)
            val targetIndex = chapterIndex + step
            if (chapterIndex != -1 && targetIndex in currentChapters.indices) {
                showChapter(currentBookCode, currentChapters[targetIndex])
                return@startChapterJob
            }

            val adjacentBook = adjacentBook(step)
            val chapters = adjacentBook?.let { db.bibleDao().getChapterList(it.bookCode) }
            // 往前翻進入前一卷的最後一章，往後翻進入下一卷的第一章
            val chapter = if (step < 0) chapters?.lastOrNull() else chapters?.firstOrNull()
            if (adjacentBook == null || chapter == null) {
                // 沒有相鄰章節可去，把進入 job 時鎖住的按鈕狀態還原
                updateChapterNavButtons()
                return@startChapterJob
            }
            showChapter(adjacentBook.bookCode, chapter)
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
