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
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
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
        binding.btnChapterPicker.setOnClickListener { showChapterPicker() }
        binding.btnThemeSettings.setOnClickListener { showThemeDialog() }

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

            prefs.edit()
                .putString(KEY_LAST_READ_BOOK, bookCode)
                .putInt(KEY_LAST_READ_CHAPTER, chapter)
                .apply()

            adapter.updateVerses(verses)
            binding.recyclerVerses.scrollToPosition(0)

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

    private fun showBookPicker(isOldTestament: Boolean) {
        lifecycleScope.launch {
            val books = db.bibleDao().getAllBooks()
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
                    loadChapter(filtered[which].bookCode, 1)
                }
                .show()
        }
    }

    private fun showChapterPicker() {
        lifecycleScope.launch {
            val chapters = db.bibleDao().getChapterList(currentBookCode)
            if (chapters.isEmpty()) return@launch

            val labels = chapters.map { "第 $it 章" }.toTypedArray()
            AlertDialog.Builder(this@VerseReaderActivity)
                .setTitle("選擇章節")
                .setItems(labels) { _, which ->
                    loadChapter(currentBookCode, chapters[which])
                }
                .show()
        }
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
