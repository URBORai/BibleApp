package com.UWelBAlRai.bibleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.databinding.ItemBookBinding

class BookAdapter(
    private val books: List<BookOrder>,
    private val onClick: (BookOrder) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    inner class BookViewHolder(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.binding.textBookName.text = book.cnName
        holder.binding.root.setOnClickListener { onClick(book) }
    }

    override fun getItemCount(): Int = books.size
}