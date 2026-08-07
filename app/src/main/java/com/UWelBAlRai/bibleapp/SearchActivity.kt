package com.UWelBAlRai.bibleapp

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.UWelBAlRai.bibleapp.databinding.ActivitySearchBinding

// 搜尋輸入畫面：從 VerseReaderActivity 頂部工具列的放大鏡圖示進入
class SearchActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "bible_app_prefs"
        private const val KEY_SEARCH_VERSION_CODE = "search_version_code"
        private const val KEY_SEARCH_MULTI_KEYWORD = "search_multi_keyword"
        private const val KEY_SEARCH_USE_AND = "search_use_and"

        private const val DEFAULT_MULTI_KEYWORD = false
        private const val DEFAULT_USE_AND = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsUtil.applySystemBarPadding(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedVersionCode = prefs.getString(KEY_SEARCH_VERSION_CODE, "CUV")
        binding.radioEnglish.isChecked = savedVersionCode == "NKJV"
        binding.radioChinese.isChecked = savedVersionCode != "NKJV"

        binding.radioGroupSearchLanguage.setOnCheckedChangeListener { _, checkedId ->
            val versionCode = if (checkedId == binding.radioEnglish.id) "NKJV" else "CUV"
            prefs.edit().putString(KEY_SEARCH_VERSION_CODE, versionCode).apply()
        }

        // 比照上面的語言偏好：先套用存下來的狀態，再掛監聽，避免還原時被自己的監聽器又寫一次
        val savedMultiKeyword = prefs.getBoolean(KEY_SEARCH_MULTI_KEYWORD, DEFAULT_MULTI_KEYWORD)
        binding.checkboxMultiKeyword.isChecked = savedMultiKeyword
        // 勾選狀態是程式設定的，不會觸發下面的監聽器，展開／收合要自己補一次
        binding.layoutMultiKeywordFields.visibility =
            if (savedMultiKeyword) View.VISIBLE else View.GONE

        binding.checkboxMultiKeyword.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutMultiKeywordFields.visibility = if (isChecked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(KEY_SEARCH_MULTI_KEYWORD, isChecked).apply()
        }

        val savedUseAnd = prefs.getBoolean(KEY_SEARCH_USE_AND, DEFAULT_USE_AND)
        binding.radioAnd.isChecked = savedUseAnd
        binding.radioOr.isChecked = !savedUseAnd

        binding.radioGroupAndOr.setOnCheckedChangeListener { _, checkedId ->
            val useAnd = checkedId == binding.radioAnd.id
            prefs.edit().putBoolean(KEY_SEARCH_USE_AND, useAnd).apply()
        }

        fun performSearch() {
            val keyword1 = binding.editSearchKeyword1.text.toString().trim()
            if (keyword1.isEmpty()) return

            val isMultiKeyword = binding.checkboxMultiKeyword.isChecked
            val keyword2 = if (isMultiKeyword) binding.editSearchKeyword2.text.toString().trim() else ""
            val keyword3 = if (isMultiKeyword) binding.editSearchKeyword3.text.toString().trim() else ""
            val useAnd = binding.radioAnd.isChecked
            val versionCode = if (binding.radioEnglish.isChecked) "NKJV" else "CUV"

            val intent = Intent(this@SearchActivity, SearchResultActivity::class.java)
            intent.putStringArrayListExtra("keywords", arrayListOf(keyword1, keyword2, keyword3))
            intent.putExtra("use_and", useAnd)
            intent.putExtra("version_code", versionCode)
            startActivity(intent)
        }

        val searchOnAction = { _: android.widget.TextView, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener { performSearch() }
        binding.editSearchKeyword1.setOnEditorActionListener(searchOnAction)
        binding.editSearchKeyword3.setOnEditorActionListener(searchOnAction)
    }
}
