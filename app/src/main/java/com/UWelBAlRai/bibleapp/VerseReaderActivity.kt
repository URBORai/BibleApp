package com.UWelBAlRai.bibleapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.data.ParallelVerse
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import com.UWelBAlRai.bibleapp.databinding.DialogAppearanceBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// App 啟動首頁：沒有上次閱讀記錄時預設載入創世記第 1 章
class VerseReaderActivity : BaseActivity() {

    companion object {
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"
        private const val KEY_FONT_SIZE = "verse_font_size"
        private const val KEY_SHOW_PARALLEL = "show_parallel"
        private const val KEY_SHOW_PAGE_NUMBER = "show_page_number"

        private const val DEFAULT_SHOW_PARALLEL = false
        private const val DEFAULT_SHOW_PAGE_NUMBER = false
        // 字級範圍涵蓋手機到平板：平板螢幕大、視距遠，原本的 28sp 上限在上面偏小。
        // 上限拉到 44sp，級距同步從 2sp 放大到 3sp，從下限點到上限剛好 10 下，不必狂點
        private const val DEFAULT_FONT_SIZE = 18f
        private const val MIN_FONT_SIZE = 14f
        private const val MAX_FONT_SIZE = 44f
        private const val FONT_SIZE_STEP = 3f

        // 經文版本代碼；哪一個當主體由介面語言決定，見 primaryVersionCode
        private const val VERSION_CUV = "CUV"
        private const val VERSION_NKJV = "NKJV"

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

    // 目前被點選的那一節，複製按鈕的資料來源；沒有選取時為 null（複製列也跟著隱藏）
    private var selectedVerse: ParallelVerse? = null

    // 經文主體＝介面語言那一邊，對照＝另一邊。ui_prefers_english 是 locale 限定的
    // bool 資源（values / values-en），所以「跟隨系統語言」與 App 內手動切換都自動生效。
    // 語言一變 AppCompat 會重建 Activity，onCreate 重新載入章節時就換成新的主體版本
    private val useEnglishAsPrimary: Boolean
        get() = resources.getBoolean(R.bool.ui_prefers_english)
    private val primaryVersionCode: String
        get() = if (useEnglishAsPrimary) VERSION_NKJV else VERSION_CUV
    private val secondaryVersionCode: String
        get() = if (useEnglishAsPrimary) VERSION_CUV else VERSION_NKJV

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVerseReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPrefs.of(this)

        binding.recyclerVerses.layoutManager = LinearLayoutManager(this)
        adapter = VerseAdapter()
        adapter.textSizeSp = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        adapter.showParallel = prefs.getBoolean(KEY_SHOW_PARALLEL, DEFAULT_SHOW_PARALLEL)
        adapter.showPageNumber = prefs.getBoolean(KEY_SHOW_PAGE_NUMBER, DEFAULT_SHOW_PAGE_NUMBER)
        binding.recyclerVerses.adapter = adapter
        setupVerseSelection()

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
        binding.btnThemeSettings.setOnClickListener { showAppearanceDialog() }

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

    // 點擊整節選取 → 浮出複製列 → 複製純經文。一次只選一節的規則由 VerseAdapter 維護
    private fun setupVerseSelection() {
        adapter.onSelectionChanged = { verse ->
            selectedVerse = verse
            binding.layoutCopyBar.visibility = if (verse != null) View.VISIBLE else View.GONE
        }
        binding.btnCopyVerse.setOnClickListener { copySelectedVerse() }

        // 點在經文區的空白處（章末留白、或段落之間沒有節的地方）＝ 點畫面其他地方，取消選取。
        // 點在某一節上不歸這裡管，交給該節自己的點擊處理（選取／取消選取）
        val tapDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean = true
            }
        )
        binding.recyclerVerses.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (tapDetector.onTouchEvent(e) && rv.findChildViewUnder(e.x, e.y) == null) {
                        adapter.clearSelection()
                    }
                    // 一律不攔截：捲動與整列點擊都照原本的路徑走，這裡只是旁聽
                    return false
                }
            }
        )
    }

    // 複製的是純經文，不含節號數字；有開對照時一併帶上對照版本（畫面上選到的就是這些）
    private fun copySelectedVerse() {
        val verse = selectedVerse ?: return
        val text = buildString {
            append(verse.primaryText)
            if (adapter.showParallel) {
                verse.secondaryText?.let { append("\n").append(it) }
            }
        }

        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), text))

        // Android 13 起系統自己會顯示複製完成的提示，再跳一次 Toast 會變成重複回饋
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.copied_verse), Toast.LENGTH_SHORT).show()
        }
        adapter.clearSelection()
    }

    // 按鈕文字描述「按下去會發生什麼」，啟動套用偏好與點擊切換兩個進入點共用
    private fun updateParallelButtonText() {
        binding.btnToggleParallel.text =
            if (adapter.showParallel) {
                getString(R.string.toggle_parallel_hide)
            } else {
                getString(R.string.toggle_parallel_show)
            }
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
        val verses = db.bibleDao()
            .getParallelChapter(primaryVersionCode, secondaryVersionCode, bookCode, chapter)

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
        // 書卷名跟著介面語言換（book_order 有中英兩組名稱）
        val bookName = books().find { it.bookCode == bookCode }?.displayName(this)
        // 連書卷都查不到時退回 book_code，至少讓標題誠實反映要求的位置，不要假裝載入成功
        binding.textCurrentLocation.text =
            getString(R.string.current_location, bookName ?: bookCode, chapter)

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

    // 書卷選擇：舊約 39 卷與新約 27 卷分開兩個網格，不混在同一份清單裡
    private fun showBookPicker(isOldTestament: Boolean) {
        lifecycleScope.launch {
            val books = books()
            val filtered = if (isOldTestament) {
                Testament.oldTestament(books)
            } else {
                Testament.newTestament(books)
            }

            GridPicker.show(
                context = this@VerseReaderActivity,
                title = getString(
                    if (isOldTestament) R.string.old_testament else R.string.new_testament
                ),
                items = filtered,
                columns = GridPicker.COLUMNS_BOOK,
                // 網格格子窄，用簡稱而不是全名才不會被截斷
                label = { it.displayAbbr(this@VerseReaderActivity) }
            ) { book ->
                // 選完書卷不直接跳第 1 章，接著彈章節網格讓使用者挑
                lifecycleScope.launch { showChapterPicker(book) }
            }
        }
    }

    private suspend fun showChapterPicker(book: BookOrder) {
        // 目前這卷的章節清單 loadChapter 已經取好，直接用快取；換卷才需要重查
        val chapters = if (book.bookCode == currentBookCode && currentChapters.isNotEmpty()) {
            currentChapters
        } else {
            db.bibleDao().getChapterList(book.bookCode)
        }

        GridPicker.show(
            context = this,
            title = book.displayName(this),
            items = chapters,
            columns = GridPicker.COLUMNS_CHAPTER,
            label = { it.toString() }
        ) { chapter ->
            loadChapter(book.bookCode, chapter)
        }
    }

    private fun showAppearanceDialog() {
        val dialogBinding = DialogAppearanceBinding.inflate(layoutInflater)

        val checkedId = when (ThemePrefs.getSavedMode(this)) {
            ThemePrefs.MODE_LIGHT -> R.id.radioThemeLight
            ThemePrefs.MODE_DARK -> R.id.radioThemeDark
            else -> R.id.radioThemeSystem
        }
        dialogBinding.groupThemeMode.check(checkedId)

        // 介面顯示語言：只換 UI 文字。經文顯示哪個版本、搜尋查哪個版本都不受這裡影響
        val checkedLanguageId = when (LocalePrefs.getSavedLanguage(this)) {
            LocalePrefs.LANG_CHINESE -> R.id.radioLanguageChinese
            LocalePrefs.LANG_ENGLISH -> R.id.radioLanguageEnglish
            else -> R.id.radioLanguageSystem
        }
        dialogBinding.groupLanguage.check(checkedLanguageId)

        dialogBinding.switchPageNumber.isChecked = adapter.showPageNumber
        dialogBinding.switchNightReading.isChecked = NightReadingPrefs.isEnabled(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.appearance_settings)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_done, null)
            .create()

        // 監聽器一律在初始值設定完之後才掛：先掛的話 check()／isChecked 會立刻觸發一次，
        // 等於一開啟對話框就重新套用主題（Activity 會被 recreate）
        dialogBinding.groupThemeMode.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.radioThemeLight -> ThemePrefs.MODE_LIGHT
                R.id.radioThemeDark -> ThemePrefs.MODE_DARK
                else -> ThemePrefs.MODE_SYSTEM
            }
            ThemePrefs.saveMode(this, mode)
            // 切換模式會讓 Activity 重建，對話框跟著消失，所以先自己收起來
            dialog.dismiss()
            ThemePrefs.applyMode(mode)
        }
        dialogBinding.groupLanguage.setOnCheckedChangeListener { _, id ->
            val language = when (id) {
                R.id.radioLanguageChinese -> LocalePrefs.LANG_CHINESE
                R.id.radioLanguageEnglish -> LocalePrefs.LANG_ENGLISH
                else -> LocalePrefs.MODE_SYSTEM
            }
            LocalePrefs.saveLanguage(this, language)
            // 跟主題一樣：AppCompat 會為了套用新語言重建 Activity，先把對話框收起來
            dialog.dismiss()
            LocalePrefs.applyLanguage(language)
        }
        dialogBinding.switchPageNumber.setOnCheckedChangeListener { _, isChecked ->
            adapter.showPageNumber = isChecked
            prefs.edit().putBoolean(KEY_SHOW_PAGE_NUMBER, isChecked).apply()
        }
        // 濾鏡即時套用，對話框不用關掉就看得到效果，方便使用者當場判斷要不要開
        dialogBinding.switchNightReading.setOnCheckedChangeListener { _, isChecked ->
            NightReadingPrefs.setEnabled(this, isChecked)
            // 這個畫面就在眼前，直接套用讓使用者當場看到效果；
            // 其他畫面回到前景時會由 BaseActivity.onResume 自己補上
            applyNightReadingMode(isChecked)
        }

        dialog.show()
    }
}
