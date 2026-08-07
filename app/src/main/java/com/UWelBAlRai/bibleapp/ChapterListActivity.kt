package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityChapterListBinding
import kotlinx.coroutines.launch

class ChapterListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityChapterListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

        val bookCode = intent.getStringExtra("book_code") ?: return

        binding.recyclerChapters.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            val chapters = db.bibleDao().getChapterList(bookCode)
            binding.recyclerChapters.adapter = ChapterAdapter(chapters) { chapter ->
                val intent = Intent(this@ChapterListActivity, VerseReaderActivity::class.java)
                intent.putExtra("book_code", bookCode)
                intent.putExtra("chapter", chapter)
                startActivity(intent)
            }
        }
    }
}