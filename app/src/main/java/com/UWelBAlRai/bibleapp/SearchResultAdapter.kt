package com.UWelBAlRai.bibleapp

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.Verse
import com.UWelBAlRai.bibleapp.databinding.ItemSearchHeaderBinding
import com.UWelBAlRai.bibleapp.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val items: List<SearchListItem>,
    private val keywords: List<String>,
    private val onClick: (Verse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RESULT = 1
    }

    class HeaderViewHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class ResultViewHolder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchListItem.Header -> VIEW_TYPE_HEADER
            is SearchListItem.Result -> VIEW_TYPE_RESULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false))
        } else {
            ResultViewHolder(ItemSearchResultBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SearchListItem.Header -> {
                (holder as HeaderViewHolder).binding.textSearchHeader.text = item.bookName
            }
            is SearchListItem.Result -> {
                val verse = item.verse
                holder as ResultViewHolder
                holder.binding.textSearchLocation.text = "第${verse.chapter}章 第${verse.verse}節"
                holder.binding.textSearchSnippet.text = highlightKeywords(verse.text, keywords)
                holder.binding.root.setOnClickListener { onClick(verse) }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // 關鍵字 1、2、3 依序對應紅、藍、綠，方便使用者分辨標註對應哪個欄位
    private val keywordColors = listOf(Color.RED, Color.parseColor("#1565C0"), Color.parseColor("#2E7D32"))

    // 不分大小寫比對出現位置，但標註範圍取自原文字串，維持原本大小寫顯示
    // 多個關鍵字重疊時，以先出現（index 較小）的關鍵字顏色為主，後面的直接略過該段落
    private fun highlightKeywords(text: String, keywords: List<String>): SpannableString {
        val spannable = SpannableString(text)
        val lowerText = text.lowercase()
        val claimed = BooleanArray(text.length)

        keywords.forEachIndexed { keywordIndex, rawKeyword ->
            val keyword = rawKeyword.trim()
            if (keyword.isEmpty()) return@forEachIndexed
            val lowerKeyword = keyword.lowercase()
            val color = keywordColors.getOrElse(keywordIndex) { Color.RED }

            var startIndex = 0
            while (startIndex <= lowerText.length) {
                val index = lowerText.indexOf(lowerKeyword, startIndex)
                if (index == -1) break
                val endIndex = index + lowerKeyword.length
                startIndex = endIndex

                if ((index until endIndex).any { claimed[it] }) continue

                spannable.setSpan(ForegroundColorSpan(color), index, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), index, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                for (i in index until endIndex) claimed[i] = true
            }
        }
        return spannable
    }
}
