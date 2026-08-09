package com.UWelBAlRai.bibleapp

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * 所有畫面共用的基底：edge-to-edge 的系統列 inset，以及護眼模式的暖色濾鏡。
 *
 * 這兩件事原本都得由每個 Activity 自己記得做，漏掉不會有任何錯誤提示——
 * 只會有一個畫面被系統列蓋住、或是一個畫面沒跟著變暖。搬到基底類別之後，
 * 新畫面只要繼承就自動有正確行為。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 要在 setContentView 之前關掉 decor fit，子類別的 super.onCreate() 一定先跑到這裡
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        // 不論子類別用哪一種 setContentView 多載，框架都會在內容換好之後呼叫這裡，
        // 不會有「只有某一種寫法才生效」的陷阱
        contentRoot()?.let { WindowInsetsUtil.applySystemBarPadding(it) }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前景都重讀偏好：使用者可能在別的畫面（外觀設定對話框）改過護眼開關，
        // 這個畫面當時在背景收不到通知，回來時補套一次就會跟其他畫面同步。
        // 第一次顯示也走這裡——onResume 早於第一次繪製，不會閃一下沒濾鏡的畫面
        applyNightReadingMode(NightReadingPrefs.isEnabled(this))
    }

    /**
     * 在畫面 root 的 foreground 疊一層半透明暖色。
     *
     * 用 foreground 而不是在版面裡多放一個覆蓋用的 View，有三個好處：
     * foreground 在所有子 View（含有 elevation 的浮動按鈕）之後才繪製，一定蓋得住；
     * 它完全不參與觸控分派，不必擔心攔掉點擊或捲動；
     * 而且 root 的邊界是整個視窗（系統列 inset 是做成 padding），濾鏡連狀態列底下
     * 的區域一起覆蓋，不會在上緣留一條沒染色的斷層。
     *
     * 開關當下要立即生效的畫面（例如外觀設定對話框所在的閱讀畫面）可以直接呼叫這個，
     * 不必等下一次 onResume。
     */
    protected fun applyNightReadingMode(enabled: Boolean) {
        val root = contentRoot() ?: return
        root.foreground = if (enabled) {
            ColorDrawable(ContextCompat.getColor(this, R.color.night_reading_filter))
        } else {
            null
        }
    }

    // content 容器的第一個子 View 就是子類別傳給 setContentView 的那個 root
    private fun contentRoot(): View? =
        findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
}
