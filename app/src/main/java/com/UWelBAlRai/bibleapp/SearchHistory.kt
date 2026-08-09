package com.UWelBAlRai.bibleapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 搜尋歷史，存在跟其他偏好同一個 SharedPreferences（見 [AppPrefs]）。
 *
 * 用 JSON 而不是自己拼分隔字串：關鍵字是使用者輸入的自由文字，
 * 任何分隔符號都可能剛好出現在關鍵字裡而把資料切壞。
 * org.json 是 Android 內建的，不用多拉一個相依套件。
 */
object SearchHistory {

    private const val KEY_SEARCH_HISTORY = "search_history"

    /** 保留幾筆。10 筆大約是「找得回剛剛查過的東西」又不會把搜尋畫面整個佔滿的量 */
    const val MAX_ENTRIES = 10

    private const val FIELD_KEYWORDS = "keywords"
    private const val FIELD_USE_AND = "use_and"
    private const val FIELD_VERSION = "version"

    /**
     * 一筆搜尋。[keywords] 只留非空白的關鍵字，順序即為使用者輸入的欄位順序。
     */
    data class Entry(
        val keywords: List<String>,
        val useAnd: Boolean,
        val versionCode: String
    )

    fun load(context: Context): List<Entry> {
        val raw = AppPrefs.of(context).getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return runCatching { parse(raw) }
            // 存檔壞掉（例如手動改過偏好）不該讓搜尋畫面開不起來，當作沒有歷史就好
            .getOrDefault(emptyList())
    }

    /**
     * 記錄一次搜尋。關鍵字組合相同的舊紀錄會被移除再把新的放到最前面，
     * 所以重複搜尋同一組關鍵字只會「往前移」，不會多長一筆
     * （版本／AND-OR 以最後一次搜尋的為準）。
     */
    fun record(context: Context, entry: Entry) {
        if (entry.keywords.isEmpty()) return

        val updated = buildList {
            add(entry)
            addAll(load(context).filterNot { it.keywords == entry.keywords })
        }.take(MAX_ENTRIES)

        AppPrefs.of(context)
            .edit()
            .putString(KEY_SEARCH_HISTORY, serialize(updated))
            .apply()
    }

    fun clear(context: Context) {
        AppPrefs.of(context).edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    private fun parse(raw: String): List<Entry> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val keywordArray = item.optJSONArray(FIELD_KEYWORDS) ?: return@mapNotNull null
            val keywords = (0 until keywordArray.length())
                .map { keywordArray.optString(it) }
                .filter { it.isNotBlank() }
            if (keywords.isEmpty()) return@mapNotNull null

            Entry(
                keywords = keywords,
                useAnd = item.optBoolean(FIELD_USE_AND, true),
                versionCode = item.optString(FIELD_VERSION, "CUV")
            )
        }
    }

    private fun serialize(entries: List<Entry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(FIELD_KEYWORDS, JSONArray(entry.keywords))
                    .put(FIELD_USE_AND, entry.useAnd)
                    .put(FIELD_VERSION, entry.versionCode)
            )
        }
        return array.toString()
    }
}
