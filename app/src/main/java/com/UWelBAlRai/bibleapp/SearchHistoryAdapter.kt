package com.UWelBAlRai.bibleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.databinding.ItemSearchHistoryBinding

class SearchHistoryAdapter(
    private var entries: List<SearchHistory.Entry>,
    private val onClick: (SearchHistory.Entry) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemSearchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.binding.root.context

        holder.binding.textHistoryKeywords.text =
            entry.keywords.joinToString(context.getString(R.string.search_history_separator))

        // AND／OR 只有在多關鍵字時才有意義，單一關鍵字就只顯示版本
        holder.binding.textHistoryMeta.text = if (entry.keywords.size > 1) {
            context.getString(
                R.string.search_history_meta,
                entry.versionCode,
                context.getString(
                    if (entry.useAnd) R.string.search_history_and else R.string.search_history_or
                )
            )
        } else {
            entry.versionCode
        }

        holder.binding.root.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount(): Int = entries.size

    fun submit(newEntries: List<SearchHistory.Entry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
