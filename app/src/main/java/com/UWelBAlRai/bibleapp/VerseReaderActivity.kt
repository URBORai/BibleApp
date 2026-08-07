package com.UWelBAlRai.bibleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import kotlinx.coroutines.launch

class VerseReaderActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_LAST_READ_BOOK = "last_read_book"
        private const val KEY_LAST_READ_CHAPTER = "last_read_chapter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityVerseReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookCode = intent.getStringExtra("book_code") ?: return
        val chapter = intent.getIntExtra("chapter", -1)
        if (chapter == -1) return

        binding.recyclerVerses.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)

        val targetVerse = intent.getIntExtra("verse", -1)

        lifecycleScope.launch {
            val verses = db.bibleDao().getParallelChapter(bookCode, chapter)

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_READ_BOOK, bookCode)
                .putInt(KEY_LAST_READ_CHAPTER, chapter)
                .apply()

            val adapter = VerseAdapter(verses)
            binding.recyclerVerses.adapter = adapter

            binding.btnToggleParallel.setOnClickListener {
                adapter.showParallel = !adapter.showParallel
                binding.btnToggleParallel.text =
                    if (adapter.showParallel) "隱藏英文對照" else "顯示中英對照"
            }

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