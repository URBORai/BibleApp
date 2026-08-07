package com.UWelBAlRai.bibleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.BookOrder
import com.UWelBAlRai.bibleapp.databinding.ItemBookBinding
import com.UWelBAlRai.bibleapp.databinding.ItemSearchHeaderBinding

class BookAdapter(
    private val items: List<BookListItem>,
    private val onClick: (BookOrder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_BOOK = 1
    }

    class HeaderViewHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class BookViewHolder(val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BookListItem.Header -> VIEW_TYPE_HEADER
            is BookListItem.BookItem -> VIEW_TYPE_BOOK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false))
        } else {
            BookViewHolder(ItemBookBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BookListItem.Header -> {
                (holder as HeaderViewHolder).binding.textSearchHeader.text = item.title
            }
            is BookListItem.BookItem -> {
                holder as BookViewHolder
                holder.binding.textBookName.text = item.book.cnName
                holder.binding.root.setOnClickListener { onClick(item.book) }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
