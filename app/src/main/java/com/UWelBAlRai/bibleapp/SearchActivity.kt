package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.databinding.ActivitySearchBinding
import kotlinx.coroutines.launch

// 搜尋輸入畫面：從 VerseReaderActivity 頂部工具列的放大鏡圖示進入
class SearchActivity : BaseActivity() {

    companion object {
        private const val KEY_SEARCH_VERSION_CODE = "search_version_code"
        private const val KEY_SEARCH_MULTI_KEYWORD = "search_multi_keyword"
        private const val KEY_SEARCH_USE_AND = "search_use_and"

        private const val DEFAULT_MULTI_KEYWORD = false
        private const val DEFAULT_USE_AND = true

        private const val VERSION_CUV = "CUV"
        private const val VERSION_NKJV = "NKJV"
    }

    private lateinit var binding: ActivitySearchBinding
    private lateinit var historyAdapter: SearchHistoryAdapter

    private val db by lazy { BibleDatabase.getInstance(applicationContext) }
    private var cachedBooks: List<BookOrder> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = AppPrefs.of(this)
        val savedVersionCode = prefs.getString(KEY_SEARCH_VERSION_CODE, VERSION_CUV)
        binding.radioEnglish.isChecked = savedVersionCode == VERSION_NKJV
        binding.radioChinese.isChecked = savedVersionCode != VERSION_NKJV

        binding.radioGroupSearchLanguage.setOnCheckedChangeListener { _, checkedId ->
            val versionCode = if (checkedId == binding.radioEnglish.id) VERSION_NKJV else VERSION_CUV
            prefs.edit().putString(KEY_SEARCH_VERSION_CODE, versionCode).apply()
        }

        // 比照上面的語言偏好：先套用存下來的狀態，再掛監聽，避免還原時被自己的監聽器又寫一次
        val savedMultiKeyword = prefs.getBoolean(KEY_SEARCH_MULTI_KEYWORD, DEFAULT_MULTI_KEYWORD)
        binding.checkboxMultiKeyword.isChecked = savedMultiKeyword
        // 勾選狀態是程式設定的，不會觸發下面的監聽器，展開／收合要自己補一次
        binding.layoutMultiKeywordFields.visibility =
            if (savedMultiKeyword) View.VISIBLE else View.GONE

        binding.checkboxMultiKeyword.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutMultiKeywordFields.visibility = if (isChecked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(KEY_SEARCH_MULTI_KEYWORD, isChecked).apply()
        }

        val savedUseAnd = prefs.getBoolean(KEY_SEARCH_USE_AND, DEFAULT_USE_AND)
        binding.radioAnd.isChecked = savedUseAnd
        binding.radioOr.isChecked = !savedUseAnd

        binding.radioGroupAndOr.setOnCheckedChangeListener { _, checkedId ->
            val useAnd = checkedId == binding.radioAnd.id
            prefs.edit().putBoolean(KEY_SEARCH_USE_AND, useAnd).apply()
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

        setupHistory()
        setupSearchScope()

        binding.btnHome.setOnClickListener { goToReaderHome() }
    }

    // ── 搜尋範圍 ──────────────────────────────────────────────────────────

    private fun setupSearchScope() {
        updateScopeButtonText()
        binding.btnSearchScope.setOnClickListener { showScopeDialog() }
    }

    private fun updateScopeButtonText() {
        val mode = SearchScope.getMode(this)
        val description = when (mode) {
            SearchScope.MODE_OLD_TESTAMENT -> getString(R.string.old_testament_full)
            SearchScope.MODE_NEW_TESTAMENT -> getString(R.string.new_testament_full)
            SearchScope.MODE_CUSTOM -> {
                val count = SearchScope.getCustomBookCodes(this).size
                resources.getQuantityString(R.plurals.search_scope_custom_count, count, count)
            }
            else -> getString(R.string.search_scope_all)
        }
        binding.btnSearchScope.text = getString(R.string.search_scope_button, description)
    }

    private fun showScopeDialog() {
        val modes = arrayOf(
            SearchScope.MODE_ALL,
            SearchScope.MODE_OLD_TESTAMENT,
            SearchScope.MODE_NEW_TESTAMENT,
            SearchScope.MODE_CUSTOM
        )
        val labels = arrayOf(
            getString(R.string.search_scope_all),
            getString(R.string.old_testament_full),
            getString(R.string.new_testament_full),
            getString(R.string.search_scope_custom)
        )
        val checkedIndex = modes.indexOf(SearchScope.getMode(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.search_scope_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                if (modes[which] == SearchScope.MODE_CUSTOM) {
                    // 自訂要再挑書卷，等使用者在網格裡確認之後才寫入偏好
                    showCustomScopePicker()
                } else {
                    SearchScope.save(this, modes[which])
                    updateScopeButtonText()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCustomScopePicker() {
        lifecycleScope.launch {
            val allBooks = books()
            val selectedCodes = SearchScope.getCustomBookCodes(this@SearchActivity).toSet()

            GridPicker.showMultiSelect(
                context = this@SearchActivity,
                title = getString(R.string.search_scope_custom_title),
                items = allBooks,
                columns = GridPicker.COLUMNS_BOOK,
                label = { it.displayAbbr(this@SearchActivity) },
                initiallySelected = allBooks.filter { it.bookCode in selectedCodes }.toSet()
            ) { selected ->
                // 一卷都沒選就退回「全部」，不要讓使用者卡在一個永遠查不到東西的範圍
                if (selected.isEmpty()) {
                    SearchScope.save(this@SearchActivity, SearchScope.MODE_ALL)
                } else {
                    SearchScope.save(
                        this@SearchActivity,
                        SearchScope.MODE_CUSTOM,
                        selected.map { it.bookCode }
                    )
                }
                updateScopeButtonText()
            }
        }
    }

    // 書卷清單快取一份：自訂範圍的網格與送出搜尋時的範圍換算共用
    private suspend fun books(): List<BookOrder> {
        if (cachedBooks.isEmpty()) {
            cachedBooks = db.bibleDao().getAllBooks()
        }
        return cachedBooks
    }

    // 從搜尋結果按返回回到這裡時，剛才那次搜尋要立刻出現在歷史清單最上面
    override fun onResume() {
        super.onResume()
        refreshHistory()
        // 順便重讀「返回首頁」開關：使用者可能在閱讀畫面的外觀設定裡改過，
        // 這個畫面當時在背景收不到通知，回到前景時補套一次
        binding.btnHome.visibility =
            if (HomeButtonPrefs.isEnabled(this)) View.VISIBLE else View.GONE
    }

    private fun setupHistory() {
        binding.recyclerSearchHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = SearchHistoryAdapter(emptyList()) { entry -> applyHistoryEntry(entry) }
        binding.recyclerSearchHistory.adapter = historyAdapter

        binding.btnClearHistory.setOnClickListener {
            SearchHistory.clear(this)
            refreshHistory()
        }
    }

    private fun refreshHistory() {
        val entries = SearchHistory.load(this)
        historyAdapter.submit(entries)
        binding.layoutSearchHistory.visibility =
            if (entries.isEmpty()) View.GONE else View.VISIBLE
    }

    // 點歷史紀錄只把條件帶回欄位，不直接送出，讓使用者有機會先改再搜
    private fun applyHistoryEntry(entry: SearchHistory.Entry) {
        binding.editSearchKeyword1.setText(entry.keywords.getOrElse(0) { "" })
        binding.editSearchKeyword2.setText(entry.keywords.getOrElse(1) { "" })
        binding.editSearchKeyword3.setText(entry.keywords.getOrElse(2) { "" })

        // 這幾個 isChecked 會觸發各自的監聽器，順帶把偏好也更新成這筆歷史的設定，
        // 跟使用者自己手動點選的效果一致
        binding.checkboxMultiKeyword.isChecked = entry.keywords.size > 1
        binding.radioAnd.isChecked = entry.useAnd
        binding.radioOr.isChecked = !entry.useAnd
        binding.radioEnglish.isChecked = entry.versionCode == VERSION_NKJV
        binding.radioChinese.isChecked = entry.versionCode != VERSION_NKJV

        // 游標移到第一個欄位末端，使用者要接著改字很順手
        binding.editSearchKeyword1.setSelection(binding.editSearchKeyword1.text.length)
    }

    private fun performSearch() {
        val keyword1 = binding.editSearchKeyword1.text.toString().trim()
        if (keyword1.isEmpty()) return

        val isMultiKeyword = binding.checkboxMultiKeyword.isChecked
        val keyword2 = if (isMultiKeyword) binding.editSearchKeyword2.text.toString().trim() else ""
        val keyword3 = if (isMultiKeyword) binding.editSearchKeyword3.text.toString().trim() else ""
        val useAnd = binding.radioAnd.isChecked
        val versionCode = if (binding.radioEnglish.isChecked) VERSION_NKJV else VERSION_CUV

        SearchHistory.record(
            this,
            SearchHistory.Entry(
                keywords = listOf(keyword1, keyword2, keyword3).filter { it.isNotBlank() },
                useAnd = useAnd,
                versionCode = versionCode
            )
        )

        // 範圍換算需要書卷清單（DB 查詢），所以送出動作放進協程；
        // books() 有快取，第一次之後就不會再查
        lifecycleScope.launch {
            val bookCodes = SearchScope.resolve(
                mode = SearchScope.getMode(this@SearchActivity),
                customBookCodes = SearchScope.getCustomBookCodes(this@SearchActivity),
                allBooks = books()
            )

            val intent = Intent(this@SearchActivity, SearchResultActivity::class.java)
            intent.putStringArrayListExtra("keywords", arrayListOf(keyword1, keyword2, keyword3))
            intent.putExtra("use_and", useAnd)
            intent.putExtra("version_code", versionCode)
            intent.putStringArrayListExtra("book_codes", ArrayList(bookCodes))
            startActivity(intent)
        }
    }
}
