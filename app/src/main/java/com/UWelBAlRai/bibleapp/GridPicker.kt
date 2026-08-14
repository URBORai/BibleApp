package com.UWelBAlRai.bibleapp

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.databinding.DialogGridPickerBinding
import com.UWelBAlRai.bibleapp.databinding.ItemGridCellBinding

/**
 * 共用的「網格選擇」對話框：把一組項目排成方框按鈕的網格讓使用者點選。
 *
 * 書卷選擇、章節選擇、搜尋範圍的自訂書卷都走這裡，樣式（方框底、框線、文字色）集中在
 * item_grid_cell.xml + bg_grid_cell.xml + color/grid_cell_text.xml，各處不會各自長出一套。
 *
 * 用 RecyclerView + GridLayoutManager 而不是靜態 GridLayout：
 * 詩篇 150 章這種長清單只會建出畫面內的格子，捲動不會因為一次 inflate 上百個 View 而卡頓。
 *
 * [show] 另外支援分頁（pageSize）：章節網格用它把詩篇這種長清單切成幾頁翻，
 * 取代一路往下滑。書卷網格不傳這個參數，行為完全不變。
 */
object GridPicker {

    /** 書卷網格欄數：中文簡稱多為 1-2 字，4 欄能維持寬鬆的點擊區與可讀字級 */
    const val COLUMNS_BOOK = 4

    /** 章節網格欄數：數字最多 3 位，用 5 欄壓低列數（詩篇 150 章 = 30 列），格子仍有 48dp 以上 */
    const val COLUMNS_CHAPTER = 5

    /**
     * 單選：點一下就選定並關閉。[items] 為空時不顯示，回傳 null。
     *
     * @param label 把項目轉成格子上的文字
     * @param pageSize 一頁最多顯示幾個項目；null（預設）代表不分頁，一次列出全部可捲動。
     *   只有項目數真的超過這個上限時才會出現翻頁列，剛好裝得下就跟不分頁一樣。
     * @param onSelected 點擊後回呼，對話框會先關閉再回呼
     */
    fun <T> show(
        context: Context,
        title: String,
        items: List<T>,
        columns: Int,
        label: (T) -> String,
        pageSize: Int? = null,
        onSelected: (T) -> Unit
    ): AlertDialog? {
        if (items.isEmpty()) return null

        val binding = createBinding(context, title, columns)
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .create()

        val adapter = GridAdapter(
            items = items,
            label = label,
            // 單選不畫勾選狀態，格子外觀跟原本完全一樣
            isSelected = { false }
        ) { item ->
            // 先關對話框再回呼：回呼可能會再開下一層（書卷 → 章節），
            // 順序反過來會出現兩層對話框同時疊在畫面上
            dialog.dismiss()
            onSelected(item)
        }
        binding.recyclerGrid.adapter = adapter

        // 在 show() 之前就把第一頁換上去：RecyclerView 要等對話框顯示、量到寬高之後
        // 才會綁任何一格，所以中間這段「adapter 手上還是完整清單」的空窗期畫不出來
        setupPaging(context, binding, adapter, items, pageSize)

        dialog.show()
        return dialog
    }

    /**
     * 多選：點擊切換勾選狀態，按「確定」才一次回傳結果。
     *
     * 勾選狀態只存在對話框自己的暫存集合裡，按「取消」就整組丟掉，
     * 呼叫端拿到的一定是使用者確認過的結果。
     *
     * @param initiallySelected 開啟時就已勾選的項目
     * @param onConfirm 按下確定時回呼，帶回目前勾選的項目（可能是空集合）
     */
    fun <T> showMultiSelect(
        context: Context,
        title: String,
        items: List<T>,
        columns: Int,
        label: (T) -> String,
        initiallySelected: Set<T>,
        onConfirm: (Set<T>) -> Unit
    ): AlertDialog? {
        if (items.isEmpty()) return null

        val selection = initiallySelected.toMutableSet()
        val binding = createBinding(context, title, columns)

        val adapter = GridAdapter(
            items = items,
            label = label,
            isSelected = { it in selection }
        ) { item ->
            // 多選不關對話框，只切換這一格的勾選狀態
            if (!selection.remove(item)) selection.add(item)
        }
        binding.recyclerGrid.adapter = adapter

        return AlertDialog.Builder(context)
            .setView(binding.root)
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .setPositiveButton(context.getString(R.string.action_confirm)) { _, _ ->
                onConfirm(selection)
            }
            .create()
            .also { it.show() }
    }

    /**
     * 把清單切成多頁，並接上翻頁列。[pageSize] 為 null／非正數／大到一頁就裝得下時直接返回，
     * 翻頁列維持 gone，adapter 手上仍是完整清單——也就是「不分頁」的舊行為。
     *
     * 分頁做在這裡而不是另外寫一個章節專用的對話框：格子樣式、欄數、標題、取消鈕、
     * RecyclerView 的建立全都是共用的，分家等於把整套版面複製一份，之後改樣式要改兩處。
     * 分頁只是「adapter 看得到哪一段清單」的差別，塞進共用元件的成本比分家低得多。
     */
    private fun <T> setupPaging(
        context: Context,
        binding: DialogGridPickerBinding,
        adapter: GridAdapter<T>,
        items: List<T>,
        pageSize: Int?
    ) {
        if (pageSize == null || pageSize <= 0 || items.size <= pageSize) return

        val pages = items.chunked(pageSize)
        var pageIndex = 0

        binding.groupGridPaging.visibility = View.VISIBLE

        fun render() {
            adapter.showPage(pages[pageIndex])
            // 換頁後回到頂端：不重設的話翻到下一頁會停在前一頁捲到的位置，
            // 看起來像沒翻成功（新頁的前幾列在畫面外）
            binding.recyclerGrid.scrollToPosition(0)
            binding.textGridPageIndicator.text = context.getString(
                R.string.grid_page_indicator,
                pageIndex + 1,
                pages.size
            )
            // 頭尾兩端只把按鈕停用（TextButton 會自己轉成灰字），位置保留不隱藏，
            // 翻頁列的版面才不會在第一頁與中間頁之間跳動
            binding.buttonGridPreviousPage.isEnabled = pageIndex > 0
            binding.buttonGridNextPage.isEnabled = pageIndex < pages.lastIndex
        }

        binding.buttonGridPreviousPage.setOnClickListener {
            if (pageIndex > 0) {
                pageIndex--
                render()
            }
        }
        binding.buttonGridNextPage.setOnClickListener {
            if (pageIndex < pages.lastIndex) {
                pageIndex++
                render()
            }
        }

        render()
    }

    private fun createBinding(
        context: Context,
        title: String,
        columns: Int
    ): DialogGridPickerBinding {
        val binding = DialogGridPickerBinding.inflate(LayoutInflater.from(context))
        binding.textGridTitle.text = title
        binding.recyclerGrid.layoutManager = GridLayoutManager(context, columns)
        return binding
    }

    private class GridAdapter<T>(
        items: List<T>,
        private val label: (T) -> String,
        private val isSelected: (T) -> Boolean,
        private val onClick: (T) -> Unit
    ) : RecyclerView.Adapter<GridAdapter<T>.CellViewHolder>() {

        /** 目前畫在網格上的項目。不分頁時就是完整清單，分頁時是當前這一頁 */
        private var items: List<T> = items

        /**
         * 換一整頁。整份內容都換掉，逐項 diff 找不出任何可重用的位置，
         * notifyDataSetChanged 反而最省事、也不會有換頁動畫的殘影
         */
        @SuppressLint("NotifyDataSetChanged")
        fun showPage(page: List<T>) {
            items = page
            notifyDataSetChanged()
        }

        inner class CellViewHolder(val binding: ItemGridCellBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
            val binding = ItemGridCellBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return CellViewHolder(binding)
        }

        override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
            val item = items[position]
            holder.binding.textGridCell.text = label(item)
            // isSelected 驅動 bg_grid_cell 與 grid_cell_text 的 state_selected
            holder.binding.textGridCell.isSelected = isSelected(item)
            holder.binding.textGridCell.setOnClickListener {
                onClick(item)
                // 直接翻這一格的 selected 狀態，不用 notifyItemChanged：
                // 省掉 change 動畫的閃爍，而且回收重綁時本來就會重新問一次 isSelected
                holder.binding.textGridCell.isSelected = isSelected(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
