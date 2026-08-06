package com.UWelBAlRai.bibleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityVerseReaderBinding
import kotlinx.coroutines.launch

class VerseReaderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityVerseReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bookCode = intent.getStringExtra("book_code") ?: return
        val chapter = intent.getIntExtra("chapter", -1)
        if (chapter == -1) return

        binding.recyclerVerses.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            val verses = db.bibleDao().getParallelChapter(bookCode, chapter)
            val adapter = VerseAdapter(verses)
            binding.recyclerVerses.adapter = adapter

            binding.btnToggleParallel.setOnClickListener {
                adapter.showParallel = !adapter.showParallel
                binding.btnToggleParallel.text =
                    if (adapter.showParallel) "隱藏英文對照" else "顯示中英對照"
            }
        }
    }
}