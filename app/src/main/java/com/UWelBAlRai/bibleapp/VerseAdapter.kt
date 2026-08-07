package com.UWelBAlRai.bibleapp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.ParallelVerse
import com.UWelBAlRai.bibleapp.databinding.ItemVerseBinding

class VerseAdapter(
    initialVerses: List<ParallelVerse> = emptyList()
) : RecyclerView.Adapter<VerseAdapter.VerseViewHolder>() {

    private var verses: List<ParallelVerse> = initialVerses

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
        val context = holder.binding.root.context
        holder.binding.textCuv.text = buildVerseText(context, verse)
        holder.binding.textCuv.textSize = textSizeSp

        if (showParallel) {
            holder.binding.layoutParallel.visibility = View.VISIBLE
            holder.binding.textNkjv.text = verse.nkjvText ?: "（此節無對應英文譯文）"
            holder.binding.textNkjv.textSize = (textSizeSp - 4f).coerceAtLeast(12f)
        } else {
            holder.binding.layoutParallel.visibility = View.GONE
        }

        holder.binding.root.setBackgroundColor(
            if (position == highlightedPosition) {
                ContextCompat.getColor(context, R.color.highlight_verse)
            } else {
                Color.TRANSPARENT
            }
        )
    }

    override fun getItemCount(): Int = verses.size

    // 節號用上標小字 + 強調色接在本文前面：跟本文明確區隔，但不切斷段落的連續閱讀感。
    // 用 Span 而非獨立 TextView，經文才能自然環繞換行，字級縮放時節號也會等比跟著變
    private fun buildVerseText(context: Context, verse: ParallelVerse): SpannableString {
        val label = verse.verse.toString()
        val spannable = SpannableString("$label ${verse.cuvText}")
        val end = label.length

        spannable.setSpan(RelativeSizeSpan(0.7f), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(SuperscriptSpan(), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.verse_number)),
            0,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    // 切換章節時整批替換資料，重置高亮狀態
    fun updateVerses(newVerses: List<ParallelVerse>) {
        verses = newVerses
        highlightedPosition = -1
        notifyDataSetChanged()
    }

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
