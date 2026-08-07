package com.UWelBAlRai.bibleapp

import android.graphics.Color
import android.os.Handler
import android.os.Looper
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

    private var highlightedPosition: Int = -1
    private val highlightHandler = Handler(Looper.getMainLooper())

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

        holder.binding.root.setBackgroundColor(
            if (position == highlightedPosition) Color.parseColor("#FFF59D") else Color.TRANSPARENT
        )
    }

    override fun getItemCount(): Int = verses.size

    // 短暫高亮指定位置的節，2.5 秒後自動恢復
    fun highlightVerse(position: Int) {
        if (position < 0 || position >= verses.size) return
        highlightedPosition = position
        notifyItemChanged(position)
        highlightHandler.postDelayed({
            if (highlightedPosition == position) {
                highlightedPosition = -1
                notifyItemChanged(position)
            }
        }, 2500)
    }
}
