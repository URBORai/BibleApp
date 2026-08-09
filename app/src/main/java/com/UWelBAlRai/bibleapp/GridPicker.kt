package com.UWelBAlRai.bibleapp

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.UWelBAlRai.bibleapp.databinding.DialogGridPickerBinding
import com.UWelBAlRai.bibleapp.databinding.ItemGridCellBinding

/**
 * 共用的「網格選擇」對話框：把一組項目排成方框按鈕的網格讓使用者點選。
 *
 * 書卷選擇與章節選擇都走這裡，樣式（方框底、框線、文字色）集中在
 * item_grid_cell.xml + bg_grid_cell.xml，兩處不會各自長出一套。
 *
 * 用 RecyclerView + GridLayoutManager 而不是靜態 GridLayout：
 * 詩篇 150 章這種長清單只會建出畫面內的格子，捲動不會因為一次 inflate 上百個 View 而卡頓。
 */
object GridPicker {

    /** 書卷網格欄數：中文簡稱多為 1-2 字，4 欄能維持寬鬆的點擊區與可讀字級 */
    const val COLUMNS_BOOK = 4

    /** 章節網格欄數：數字最多 3 位，用 5 欄壓低列數（詩篇 150 章 = 30 列），格子仍有 48dp 以上 */
    const val COLUMNS_CHAPTER = 5

    /**
     * 顯示網格選擇對話框。[items] 為空時不顯示，回傳 null。
     *
     * @param label 把項目轉成格子上的文字
     * @param onSelected 點擊後回呼，對話框會先關閉再回呼
     */
    fun <T> show(
        context: Context,
        title: String,
        items: List<T>,
        columns: Int,
        label: (T) -> String,
        onSelected: (T) -> Unit
    ): AlertDialog? {
        if (items.isEmpty()) return null

        val binding = DialogGridPickerBinding.inflate(LayoutInflater.from(context))
        binding.textGridTitle.text = title
        binding.recyclerGrid.layoutManager = GridLayoutManager(context, columns)

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setNegativeButton(context.getString(R.string.action_cancel), null)
            .create()

        binding.recyclerGrid.adapter = GridAdapter(items, label) { item ->
            // 先關對話框再回呼：回呼可能會再開下一層（書卷 → 章節），
            // 順序反過來會出現兩層對話框同時疊在畫面上
            dialog.dismiss()
            onSelected(item)
        }

        dialog.show()
        return dialog
    }

    private class GridAdapter<T>(
        private val items: List<T>,
        private val label: (T) -> String,
        private val onClick: (T) -> Unit
    ) : RecyclerView.Adapter<GridAdapter<T>.CellViewHolder>() {

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
            holder.binding.textGridCell.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
