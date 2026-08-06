package com.UWelBAlRai.bibleapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.ParallelVerse
import com.UWelBAlRai.bibleapp.databinding.ItemVerseBinding

class VerseAdapter(
    private val verses: List<ParallelVerse>
) : RecyclerView.Adapter<VerseAdapter.VerseViewHolder>() {

    // 是否顯示英文對照，預設關閉（單語模式）
    var showParallel: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VerseViewHolder(val binding: ItemVerseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseViewHolder {
        val binding = ItemVerseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VerseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VerseViewHolder, position: Int) {
        val verse = verses[position]
        holder.binding.textCuv.text = "${verse.verse}　${verse.cuvText}"

        if (showParallel) {
            holder.binding.textNkjv.visibility = View.VISIBLE
            holder.binding.textNkjv.text = verse.nkjvText ?: "（此節無對應英文譯文）"
        } else {
            holder.binding.textNkjv.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = verses.size
}