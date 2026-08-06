package com.UWelBAlRai.bibleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.UWelBAlRai.bibleapp.data.BibleDatabase
import com.UWelBAlRai.bibleapp.databinding.ActivityBookListBinding
import kotlinx.coroutines.launch
import android.content.Intent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityBookListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerBooks.layoutManager = LinearLayoutManager(this)

        val db = BibleDatabase.getInstance(applicationContext)

        lifecycleScope.launch {
            val books = db.bibleDao().getAllBooks()
            binding.recyclerBooks.adapter = BookAdapter(books) { book ->
                val intent = Intent(this@MainActivity, ChapterListActivity::class.java)
                intent.putExtra("book_code", book.bookCode)
                startActivity(intent)
            }
        }
    }
}