package com.UWelBAlRai.bibleapp

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// 統一處理 edge-to-edge 的系統列 inset，所有 Activity 都要呼叫這個，
// 避免各畫面各自用 fitsSystemWindows、處理方式不一致
object WindowInsetsUtil {

    // 把狀態列／導覽列的 inset 轉成 root 的 padding，蓋在原本 XML 就有的 padding 之上
    fun applySystemBarPadding(root: View) {
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
    }
}
