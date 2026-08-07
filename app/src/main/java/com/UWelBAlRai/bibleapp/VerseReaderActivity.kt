package com.UWelBAlRai.bibleapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import kotlinx.coroutines.launch

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityVerseReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

        val bookCode = intent.getStringExtra("book_code") ?: return
        val chapter = intent.getIntExtra("chapter", -1)
        if (chapter == -1) return

        binding.recyclerVerses.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val targetVerse = intent.getIntExtra("verse", -1)

        lifecycleScope.launch {
            val verses = db.bibleDao().getParallelChapter(bookCode, chapter)

            prefs.edit()
                .putString(KEY_LAST_READ_BOOK, bookCode)
                .putInt(KEY_LAST_READ_CHAPTER, chapter)
                .apply()

            val adapter = VerseAdapter(verses)
            adapter.textSizeSp = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
            binding.recyclerVerses.adapter = adapter

            binding.btnToggleParallel.setOnClickListener {
                adapter.showParallel = !adapter.showParallel
                binding.btnToggleParallel.text =
                    if (adapter.showParallel) "隱藏英文對照" else "顯示中英對照"
            }

            fun changeFontSize(delta: Float) {
                val newSize = (adapter.textSizeSp + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
                adapter.textSizeSp = newSize
                prefs.edit().putFloat(KEY_FONT_SIZE, newSize).apply()
            }

            binding.btnIncreaseFont.setOnClickListener { changeFontSize(FONT_SIZE_STEP) }
            binding.btnDecreaseFont.setOnClickListener { changeFontSize(-FONT_SIZE_STEP) }

            // 從搜尋結果進入時，捲動到對應節並短暫高亮；一般從章節清單進入則不帶 verse，維持原本行為
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
}
