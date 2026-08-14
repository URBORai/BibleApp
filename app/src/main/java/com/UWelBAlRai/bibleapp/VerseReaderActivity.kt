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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.data.ParallelVerse
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import com.UWelBAlRai.bibleapp.databinding.DialogAppearanceBinding
import com.UWelBAlRai.bibleapp.databinding.DialogBookJumpBinding
import com.UWelBAlRai.bibleapp.databinding.DialogPageJumpBinding
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
        binding.btnPageJump.setOnClickListener { showPageJumpDialog() }
        binding.btnBookNumberJump.setOnClickListener { showBookNumberJumpDialog() }
        binding.btnThemeSettings.setOnClickListener { showAppearanceDialog() }
        applyToolbarEntryVisibility()

        binding.fabPreviousChapter.setOnClickListener { goToAdjacentChapter(-1) }
        binding.fabNextChapter.setOnClickListener { goToAdjacentChapter(1) }

        restoreInitialPosition(savedInstanceState)
    }

    /**
     * 依偏好決定兩個跳轉入口出不出現在工具列上。
     *
     * 用 GONE 而不是 INVISIBLE：關掉之後這排要真的變短。留一塊看不見的空白等於
     * 白白吃掉捲動寬度，使用者關了按鈕卻發現工具列還是得捲，會覺得開關沒生效。
     *
     * 這兩個偏好只有本畫面的外觀對話框改得到，所以 onCreate 設一次 + 開關當下再設一次
     * 就夠了，不必像護眼模式那樣每次 onResume 重讀。
     */
    private fun applyToolbarEntryVisibility() {
        binding.btnPageJump.visibility =
            if (PageJumpPrefs.isEnabled(this)) View.VISIBLE else View.GONE
        binding.btnBookNumberJump.visibility =
            if (BookNumberJumpPrefs.isEnabled(this)) View.VISIBLE else View.GONE
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
            label = { it.toString() },
            // 關閉時是 null，網格就維持「一次列出全部、可捲動」的原行為。
            // 每次開啟都重讀偏好，在外觀設定改完不必重啟就生效
            pageSize = ChapterPagingPrefs.pageSizeOrNull(this)
        ) { chapter ->
            loadChapter(book.bookCode, chapter)
        }
    }

    // ─────────────────────────── 直接輸入數字跳轉 ───────────────────────────

    /**
     * 兩個跳轉對話框共用的外框。
     *
     * 關鍵在「確定」鍵：AlertDialog 的預設行為是按下就關，但這裡要驗證通過才能關——
     * 輸入有誤時得留在原地把問題指出來，否則使用者得把整組數字重打一次。
     * 唯一能改掉這個預設的做法，就是等 show() 之後再覆寫按鈕自己的 OnClickListener；
     * 在 Builder 階段掛的 listener 一定會伴隨自動關閉，改不掉。
     *
     * [submit] 跑在協程裡（驗證要查資料庫），回傳 true 代表跳轉成功、可以收起對話框。
     */
    private fun showJumpDialog(titleRes: Int, content: View, submit: suspend () -> Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            lifecycleScope.launch { if (submit()) dialog.dismiss() }
        }
    }

    /**
     * 把錯誤顯示在對話框下緣固定的那一列，並把焦點移回出錯的欄位。
     *
     * 不用 EditText.setError() 的浮動氣泡：書卷／章／節三欄各自很窄，
     * 氣泡會蓋住旁邊兩欄，而且轉個螢幕就消失了。
     *
     * 回傳 false 讓呼叫端可以直接 `return showJumpError(...)`，
     * 「顯示錯誤」與「不要關對話框」永遠是同一件事，不會有人漏寫其中一半。
     */
    private fun showJumpError(errorView: TextView, field: View?, message: String): Boolean {
        errorView.text = message
        errorView.visibility = View.VISIBLE
        field?.requestFocus()
        return false
    }

    /**
     * 使用者一改輸入，上一則錯誤就過期了，留著只會自相矛盾——
     * 例如頁碼對話框已改選新約、說明也更新成「1 到 377」，
     * 下面卻還掛著上一次的「舊約是 1 到 1126」。
     */
    private fun clearJumpErrorOnEdit(errorView: TextView, vararg fields: EditText) {
        fields.forEach { field ->
            field.doOnTextChanged { _, _, _, _ -> errorView.visibility = View.GONE }
        }
    }

    /**
     * 頁碼跳轉一律要先知道是哪一約：和合本的舊約與新約各自從第 1 頁起算，
     * 「第 1 頁」同時是創世記 1:1 與馬太福音 1:1。
     *
     * 切分沿用 [Testament]（書卷選單與搜尋範圍也是用它），不自己再判一次哪一卷屬於哪一約。
     */
    private suspend fun testamentBookCodes(isOldTestament: Boolean): List<String> {
        val allBooks = books()
        val half = if (isOldTestament) {
            Testament.oldTestament(allBooks)
        } else {
            Testament.newTestament(allBooks)
        }
        return half.map { it.bookCode }
    }

    private fun testamentName(isOldTestament: Boolean): String =
        getString(if (isOldTestament) R.string.old_testament_full else R.string.new_testament_full)

    // 兩約各自的頁碼上限查一次就夠：bible.db 是唯讀的參考資料，跑一次 App 之內不會變。
    // key 是「是不是舊約」
    private val cachedMaxPageNo = mutableMapOf<Boolean, Int>()

    private suspend fun maxPageNo(isOldTestament: Boolean): Int? =
        cachedMaxPageNo[isOldTestament]
            ?: db.bibleDao().getMaxPageNo(testamentBookCodes(isOldTestament))
                ?.also { cachedMaxPageNo[isOldTestament] = it }

    private fun showPageJumpDialog() {
        val dialogBinding = DialogPageJumpBinding.inflate(layoutInflater)
        showJumpDialog(R.string.jump_page_title, dialogBinding.root) {
            submitPageJump(dialogBinding)
        }
        lifecycleScope.launch {
            // 預設選使用者現在正在讀的那一半：從詩篇按下去，想跳的多半也是舊約的頁碼，
            // 這樣多數情況下這組選項連碰都不用碰
            val readingOldTestament =
                currentBookCode in testamentBookCodes(isOldTestament = true)
            dialogBinding.groupPageJumpTestament.check(
                if (readingOldTestament) R.id.radioPageJumpOld else R.id.radioPageJumpNew
            )
            updatePageJumpHint(dialogBinding)
            // 監聽器掛在 check() 之後：先掛的話上面那行會立刻多觸發一次
            dialogBinding.groupPageJumpTestament.setOnCheckedChangeListener { _, _ ->
                // 換一約等於換了有效範圍，上一則錯誤立刻過期
                dialogBinding.textJumpError.visibility = View.GONE
                lifecycleScope.launch { updatePageJumpHint(dialogBinding) }
            }
        }
        clearJumpErrorOnEdit(dialogBinding.textJumpError, dialogBinding.editJumpPage)
    }

    /**
     * 說明文字裡的有效範圍隨選到的那一約而變，而且是問資料庫來的，
     * 不寫死「舊約 1126、新約 377」——換一份 bible.db 時這裡不必跟著改。
     * 查回來之前那一列先留白：先填一個假範圍再改掉，比空著更容易誤導
     */
    private suspend fun updatePageJumpHint(dialogBinding: DialogPageJumpBinding) {
        val isOldTestament = dialogBinding.radioPageJumpOld.isChecked
        val maxPage = maxPageNo(isOldTestament) ?: return
        dialogBinding.textPageJumpHint.text =
            getString(R.string.jump_page_hint, testamentName(isOldTestament), maxPage)
    }

    private suspend fun submitPageJump(dialogBinding: DialogPageJumpBinding): Boolean {
        // 每次按確定都先把上一次的錯誤收掉，訊息才一定對應這一次的輸入
        dialogBinding.textJumpError.visibility = View.GONE

        val isOldTestament = dialogBinding.radioPageJumpOld.isChecked
        val testamentName = testamentName(isOldTestament)
        val input = dialogBinding.editJumpPage.text.toString().trim().toIntOrNull()
        val maxPage = maxPageNo(isOldTestament)
        if (input == null) {
            return showJumpError(
                dialogBinding.textJumpError,
                dialogBinding.editJumpPage,
                getString(R.string.jump_error_page_empty)
            )
        }
        if (maxPage != null && (input < 1 || input > maxPage)) {
            return showJumpError(
                dialogBinding.textJumpError,
                dialogBinding.editJumpPage,
                getString(R.string.jump_error_page_range, testamentName, maxPage)
            )
        }

        // 落在範圍內仍可能查無資料（實測 334、378、414… 等頁號在 CUV 裡沒有節對應），
        // 所以查完要再判一次，不能只靠上下界擋
        val verse = db.bibleDao()
            .getFirstVerseOnPage(input, testamentBookCodes(isOldTestament))
            ?: return showJumpError(
                dialogBinding.textJumpError,
                dialogBinding.editJumpPage,
                getString(R.string.jump_error_page_missing, testamentName, input)
            )

        // 跳到該頁的第一節並高亮：一頁通常橫跨好幾章，不指出來使用者看不出停在哪
        loadChapter(verse.book, verse.chapter, verse.verse)
        return true
    }

    private fun showBookNumberJumpDialog() {
        val dialogBinding = DialogBookJumpBinding.inflate(layoutInflater)
        showJumpDialog(R.string.jump_book_title, dialogBinding.root) {
            submitBookNumberJump(dialogBinding)
        }
        lifecycleScope.launch {
            dialogBinding.textBookJumpHint.text =
                getString(R.string.jump_book_hint, books().size)
        }
        clearJumpErrorOnEdit(
            dialogBinding.textJumpError,
            dialogBinding.editJumpBook,
            dialogBinding.editJumpChapter,
            dialogBinding.editJumpVerse
        )
    }

    /**
     * 書卷編號（order_index）+ 章 + 節 → 跳轉。
     *
     * 三段各自驗證、各自給訊息，錯在哪一欄就講哪一欄，不會只丟一句「輸入有誤」。
     * 查詢一律沿用既有方法：order_index → book_code 由已快取的 [books] 直接對出來
     * （不必為此多寫一支查詢），章用 getChapterList，節用 getChapter。
     */
    private suspend fun submitBookNumberJump(dialogBinding: DialogBookJumpBinding): Boolean {
        dialogBinding.textJumpError.visibility = View.GONE
        val errorView = dialogBinding.textJumpError

        val allBooks = books()
        val bookNumber = dialogBinding.editJumpBook.text.toString().trim().toIntOrNull()
        // 空白、0、67、999 全部收斂成同一種情況：找不到這個編號的書卷
        val book = allBooks.find { it.orderIndex == bookNumber }
            ?: return showJumpError(
                errorView,
                dialogBinding.editJumpBook,
                getString(R.string.jump_error_book, allBooks.size)
            )
        val bookName = book.displayName(this)

        val chapters = db.bibleDao().getChapterList(book.bookCode)
        val chapter = dialogBinding.editJumpChapter.text.toString().trim().toIntOrNull()
        if (chapter == null || chapter < 1 || chapter > chapters.size) {
            // 沒填、或明顯超過這卷的章數：訊息直接把「這卷有幾章」講出來
            return showJumpError(
                errorView,
                dialogBinding.editJumpChapter,
                resources.getQuantityString(
                    R.plurals.jump_error_chapter,
                    chapters.size,
                    bookName,
                    chapters.size
                )
            )
        }
        if (chapter !in chapters) {
            // 章號不保證連號（見 currentChapters 的註解），落在總章數以內仍可能剛好缺這一章
            return showJumpError(
                errorView,
                dialogBinding.editJumpChapter,
                getString(R.string.jump_error_chapter_missing, bookName, chapter)
            )
        }

        // 節可以留空：等於「只跳到這一章的開頭」，不捲動也不高亮
        val verseInput = dialogBinding.editJumpVerse.text.toString().trim()
        var targetVerse = -1
        if (verseInput.isNotEmpty()) {
            val verseNumber = verseInput.toIntOrNull() ?: 0
            // 用主體版本驗證節號：畫面上列出的就是這一版的節，
            // 兩版有版節差異的那幾處才不會發生「說有、卻捲不過去」
            val chapterVerses =
                db.bibleDao().getChapter(primaryVersionCode, book.bookCode, chapter)
            if (chapterVerses.none { it.verse == verseNumber }) {
                return showJumpError(
                    errorView,
                    dialogBinding.editJumpVerse,
                    getString(R.string.jump_error_verse, bookName, chapter, verseNumber)
                )
            }
            targetVerse = verseNumber
        }

        // 高亮沿用搜尋跳轉那一套：這同樣是「使用者指名要看某一節」，
        // 一章三十幾節裡不標出來，跳完還得自己找
        loadChapter(book.bookCode, chapter, targetVerse)
        return true
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
        dialogBinding.switchHomeButton.isChecked = HomeButtonPrefs.isEnabled(this)
        dialogBinding.switchPageJump.isChecked = PageJumpPrefs.isEnabled(this)
        dialogBinding.switchBookNumberJump.isChecked = BookNumberJumpPrefs.isEnabled(this)

        // 章節分頁：開關關著時每頁數量仍照著上次選的顯示（只是停用），
        // 使用者重新打開開關就知道會回到哪個設定，不必再選一次
        dialogBinding.switchChapterPaging.isChecked = ChapterPagingPrefs.isEnabled(this)
        dialogBinding.groupChapterPageSize.check(
            pageSizeRadioId(ChapterPagingPrefs.getPageSize(this))
        )
        setChapterPageSizeEnabled(dialogBinding, ChapterPagingPrefs.isEnabled(this))

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
        // 這個開關影響的是搜尋畫面而不是眼前這個畫面，所以只負責存起來；
        // 兩個搜尋畫面各自在 onResume 重讀，下次進去（或從背景回到前景）就是新狀態
        dialogBinding.switchHomeButton.setOnCheckedChangeListener { _, isChecked ->
            HomeButtonPrefs.setEnabled(this, isChecked)
        }
        // 這兩個開關管的按鈕就在對話框後面那排工具列上，所以存完立刻重套一次可見度：
        // 關掉對話框就看得到結果，不必離開畫面再回來
        dialogBinding.switchPageJump.setOnCheckedChangeListener { _, isChecked ->
            PageJumpPrefs.setEnabled(this, isChecked)
            applyToolbarEntryVisibility()
        }
        dialogBinding.switchBookNumberJump.setOnCheckedChangeListener { _, isChecked ->
            BookNumberJumpPrefs.setEnabled(this, isChecked)
            applyToolbarEntryVisibility()
        }
        dialogBinding.switchNightReading.setOnCheckedChangeListener { _, isChecked ->
            NightReadingPrefs.setEnabled(this, isChecked)
            // 這個畫面就在眼前，直接套用讓使用者當場看到效果；
            // 其他畫面回到前景時會由 BaseActivity.onResume 自己補上
            applyNightReadingMode(isChecked)
        }
        // 分頁設定影響的是「下次打開章節網格」時的樣子，這裡只負責存，
        // showChapterPicker 每次都重讀，關掉對話框直接按「章」就看得到新設定
        dialogBinding.switchChapterPaging.setOnCheckedChangeListener { _, isChecked ->
            ChapterPagingPrefs.setEnabled(this, isChecked)
            setChapterPageSizeEnabled(dialogBinding, isChecked)
        }
        dialogBinding.groupChapterPageSize.setOnCheckedChangeListener { _, id ->
            ChapterPagingPrefs.setPageSize(this, pageSizeOf(id))
        }

        dialog.show()
    }

    /** 每頁章節數 ←→ RadioButton 的對應，兩個方向都只寫在這裡，改選項時不會漏改一邊 */
    private fun pageSizeRadioId(pageSize: Int): Int = when (pageSize) {
        30 -> R.id.radioChapterPageSize30
        100 -> R.id.radioChapterPageSize100
        else -> R.id.radioChapterPageSize50
    }

    private fun pageSizeOf(radioId: Int): Int = when (radioId) {
        R.id.radioChapterPageSize30 -> 30
        R.id.radioChapterPageSize100 -> 100
        else -> ChapterPagingPrefs.DEFAULT_PAGE_SIZE
    }

    /**
     * 每頁數量整組跟著開關啟用／停用。
     *
     * 用停用＋調淡而不是 gone：隱藏會讓對話框在切換開關時整個抽動一下，
     * 而且看不到「開起來之後可以調什麼」。調淡則是同時傳達「這組屬於上面那個開關」。
     */
    private fun setChapterPageSizeEnabled(
        dialogBinding: DialogAppearanceBinding,
        enabled: Boolean
    ) {
        val alpha = if (enabled) 1f else 0.4f
        dialogBinding.textChapterPageSizeLabel.alpha = alpha
        dialogBinding.groupChapterPageSize.alpha = alpha
        // RadioGroup 自己的 isEnabled 不會傳給子項，要逐一設定才真的點不動
        dialogBinding.groupChapterPageSize.children.forEach { it.isEnabled = enabled }
    }
}
