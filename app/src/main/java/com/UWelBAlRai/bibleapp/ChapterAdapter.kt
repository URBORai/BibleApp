package com.UWelBAlRai.bibleapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.databinding.ItemChapterBinding

class ChapterAdapter(
    private val chapters: List<Int>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    class ChapterViewHolder(val binding: ItemChapterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.binding.textChapterNumber.text = "第 $chapter 章"
        holder.binding.root.setOnClickListener { onClick(chapter) }
    }

    override fun getItemCount(): Int = chapters.size
}