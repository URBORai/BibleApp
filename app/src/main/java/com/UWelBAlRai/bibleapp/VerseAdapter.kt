package com.UWelBAlRai.bibleapp

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

    // 中文經文字級（sp），英文對照會依此等比縮小 4sp，下限 12sp
    var textSizeSp: Float = 18f
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
        holder.binding.textCuv.textSize = textSizeSp

        if (showParallel) {
            holder.binding.layoutParallel.visibility = View.VISIBLE
            holder.binding.textNkjv.text = verse.nkjvText ?: "（此節無對應英文譯文）"
            holder.binding.textNkjv.textSize = (textSizeSp - 4f).coerceAtLeast(12f)
        } else {
            holder.binding.layoutParallel.visibility = View.GONE
        }

        val context = holder.binding.root.context
        holder.binding.root.setBackgroundColor(
            if (position == highlightedPosition) {
                ContextCompat.getColor(context, R.color.highlight_verse)
            } else {
                Color.TRANSPARENT
            }
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
