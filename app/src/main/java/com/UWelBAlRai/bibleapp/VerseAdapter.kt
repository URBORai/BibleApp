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
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.data.ParallelVerse
import com.UWelBAlRai.bibleapp.databinding.ItemVerseBinding

class VerseAdapter(
    initialVerses: List<ParallelVerse> = emptyList()
) : RecyclerView.Adapter<VerseAdapter.VerseViewHolder>() {

    companion object {
        // 從搜尋結果跳轉過來時，目標節的高亮持續時間
        private const val HIGHLIGHT_DURATION_MS = 2500L

        // 對照經文相對於主體經文的字級比例，以及縮到最小也要看得清的下限
        private const val SECONDARY_TEXT_RATIO = 0.78f
        private const val MIN_SECONDARY_TEXT_SIZE_SP = 12f
    }

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

    // 是否在每節旁標示紙本頁碼，預設關閉；page_no 為 null 的節（例如英文對照或本來就沒資料）一律不顯示
    var showPageNumber: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var highlightedPosition: Int = -1
    private val highlightHandler = Handler(Looper.getMainLooper())

    // 目前被點選的節，一次只會有一個；NO_POSITION 代表沒有任何節被選取
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    // 選取狀態變動時回報給畫面（選到的節，或 null 表示取消選取），
    // 由 Activity 決定要不要顯示複製按鈕。Adapter 本身不碰剪貼簿也不管按鈕在哪
    var onSelectionChanged: ((ParallelVerse?) -> Unit)? = null

    // 保留 Runnable 參考才能精準移除；Adapter 被拆離 RecyclerView 時一定要清掉，
    // 否則這個延遲任務會抓著 Adapter（進而抓著 ViewHolder 與 Activity 的 Context）不放
    private var pendingHighlightClear: Runnable? = null

    class VerseViewHolder(val binding: ItemVerseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerseViewHolder {
        val binding = ItemVerseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VerseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VerseViewHolder, position: Int) {
        val verse = verses[position]
        val context = holder.binding.root.context
        holder.binding.textPrimary.text = buildVerseText(context, verse)
        holder.binding.textPrimary.textSize = textSizeSp

        if (showParallel) {
            holder.binding.layoutParallel.visibility = View.VISIBLE
            holder.binding.textSecondary.text =
                verse.secondaryText ?: context.getString(R.string.no_parallel_text)
            // 對照文字按比例縮小而不是固定減 4sp：字級上限拉到 44sp 之後，
            // 固定差值會讓兩者在大字級下幾乎一樣大，比例才能維持主從關係
            holder.binding.textSecondary.textSize =
                (textSizeSp * SECONDARY_TEXT_RATIO).coerceAtLeast(MIN_SECONDARY_TEXT_SIZE_SP)
            // 斜體只適合拉丁字母：中文介面下對照是英文（斜體沒問題），
            // 英文介面下對照變成中文，中文沒有真正的斜體字面，強制傾斜會變形，改回正體。
            //
            // 一定要以 serifTypeface 為基底再套字形，不能用 setTypeface(null, style)：
            // 傳 null 等於把字族重設回系統預設，版面裡設好的 Noto Serif 會被整個丟掉
            // （實測英文對照會變成無襯線斜體，跟中文主體對不起來）
            val secondaryIsChinese = secondaryIsChinese(context)
            holder.binding.textSecondary.typeface = Typeface.create(
                serifTypeface(context),
                if (secondaryIsChinese) Typeface.NORMAL else Typeface.ITALIC
            )
        } else {
            holder.binding.layoutParallel.visibility = View.GONE
        }

        // 沒有頁碼資料時整個標籤收掉（GONE），不留空行也不顯示佔位字樣
        val pageNo = verse.pageNo
        if (showPageNumber && pageNo != null) {
            holder.binding.textPageNo.visibility = View.VISIBLE
            holder.binding.textPageNo.text = context.getString(R.string.page_number, pageNo)
        } else {
            holder.binding.textPageNo.visibility = View.GONE
        }

        // 選取底色優先於搜尋跳轉的高亮：使用者剛點下去的動作，回饋要蓋過 2.5 秒的殘留高亮
        holder.binding.root.setBackgroundColor(
            when {
                position == selectedPosition ->
                    ContextCompat.getColor(context, R.color.selected_verse)
                position == highlightedPosition ->
                    ContextCompat.getColor(context, R.color.highlight_verse)
                else -> Color.TRANSPARENT
            }
        )

        // 整列可點：中文、英文對照、頁碼都在這個 root 底下，點哪裡都算點到這一節
        holder.binding.root.setOnClickListener {
            // 用 bindingAdapterPosition 而不是閉包裡的 position：
            // ViewHolder 會被回收重用，閉包捕捉到的 position 可能已經過期
            val clicked = holder.bindingAdapterPosition
            if (clicked != RecyclerView.NO_POSITION) selectVerse(clicked)
        }
    }

    override fun getItemCount(): Int = verses.size

    // 介面語言決定對照文字要不要用斜體。跟字族一樣只讀一次就快取——
    // 語言變更會重建 Activity 並產生新的 Adapter，所以快取不會過期
    private var cachedSecondaryIsChinese: Boolean? = null

    private fun secondaryIsChinese(context: Context): Boolean =
        cachedSecondaryIsChinese
            ?: context.resources.getBoolean(R.bool.ui_prefers_english)
                .also { cachedSecondaryIsChinese = it }

    // 經文字族只解析一次就快取：onBindViewHolder 會被頻繁呼叫，
    // 每次都去 resources 取字型是不必要的開銷
    private var cachedSerif: Typeface? = null

    private fun serifTypeface(context: Context): Typeface? =
        cachedSerif ?: ResourcesCompat.getFont(context, R.font.noto_serif_tc_family)
            ?.also { cachedSerif = it }

    // 點擊選取：點已選取的那節等於取消，點別節則自動把前一節的選取收掉（一次只能選一節）
    private fun selectVerse(position: Int) {
        val previous = selectedPosition
        selectedPosition = if (previous == position) RecyclerView.NO_POSITION else position

        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)

        onSelectionChanged?.invoke(verses.getOrNull(selectedPosition))
    }

    // 取消選取，供畫面其他互動（點空白處、換章節、複製完成）呼叫；本來就沒選取時不做事
    fun clearSelection() {
        val previous = selectedPosition
        if (previous == RecyclerView.NO_POSITION) return
        selectedPosition = RecyclerView.NO_POSITION
        notifyItemChanged(previous)
        onSelectionChanged?.invoke(null)
    }

    // 節號用上標小字 + 強調色接在本文前面：跟本文明確區隔，但不切斷段落的連續閱讀感。
    // 用 Span 而非獨立 TextView，經文才能自然環繞換行，字級縮放時節號也會等比跟著變
    private fun buildVerseText(context: Context, verse: ParallelVerse): SpannableString {
        val label = verse.verse.toString()
        val spannable = SpannableString("$label ${verse.primaryText}")
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

    // 切換章節時整批替換資料，重置高亮與選取狀態
    // （選取的是「第幾列」，換了資料之後同一個位置已經是另一節，一定要清掉）
    fun updateVerses(newVerses: List<ParallelVerse>) {
        cancelPendingHighlightClear()
        verses = newVerses
        highlightedPosition = -1
        val hadSelection = selectedPosition != RecyclerView.NO_POSITION
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
        if (hadSelection) onSelectionChanged?.invoke(null)
    }

    // 短暫高亮指定位置的節，2.5 秒後自動恢復
    fun highlightVerse(position: Int) {
        if (position < 0 || position >= verses.size) return
        // 前一次的清除任務先取消，避免它提早把這次的高亮抹掉
        cancelPendingHighlightClear()

        highlightedPosition = position
        notifyItemChanged(position)

        val clear = Runnable {
            pendingHighlightClear = null
            if (highlightedPosition == position) {
                highlightedPosition = -1
                notifyItemChanged(position)
            }
        }
        pendingHighlightClear = clear
        highlightHandler.postDelayed(clear, HIGHLIGHT_DURATION_MS)
    }

    private fun cancelPendingHighlightClear() {
        pendingHighlightClear?.let { highlightHandler.removeCallbacks(it) }
        pendingHighlightClear = null
    }

    // Activity 銷毀或換 Adapter 時 RecyclerView 都會呼叫這裡，
    // 是清掉未執行延遲任務最可靠的時機（不必再讓 Activity 記得手動通知）
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        cancelPendingHighlightClear()
    }
}
