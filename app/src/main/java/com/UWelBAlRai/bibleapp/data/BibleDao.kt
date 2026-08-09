package com.UWelBAlRai.bibleapp.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface BibleDao {

    // 取得某書卷某章的單一版本經文（依 code 查，例如 "CUV"）
    @Query("""
        SELECT v.* FROM verse v
        JOIN bible_version bv ON v.version_id = bv.id
        WHERE bv.code = :versionCode AND v.book = :book AND v.chapter = :chapter
        ORDER BY v.verse
    """)
    suspend fun getChapter(versionCode: String, book: String, chapter: Int): List<Verse>

    // 對照查詢：以 :primaryVersion 為主表，LEFT JOIN :secondaryVersion。
    // 主體版本由介面語言決定（中文介面 CUV 為主、英文介面 NKJV 為主），所以版本代碼是參數，
    // 不再寫死——LEFT JOIN 的方向決定「哪些節會出現」，把方向做對才不會漏節。
    // 對不上的節（版節差異三處）secondaryText 會是 null，交給 UI 層顯示「此節無對應」。
    //
    // pageNo 用 COALESCE 取兩邊非 null 的那個：page_no 只有 CUV 有值，
    // 不論 CUV 當主表還是對照表，頁碼都撈得到
    @Query("""
        SELECT
            p.book AS book,
            p.chapter AS chapter,
            p.verse AS verse,
            p.text AS primaryText,
            s.text AS secondaryText,
            COALESCE(p.page_no, s.page_no) AS pageNo
        FROM verse p
        LEFT JOIN verse s
            ON p.book = s.book
            AND p.chapter = s.chapter
            AND p.verse = s.verse
            AND s.version_id = (SELECT id FROM bible_version WHERE code = :secondaryVersion)
        WHERE p.version_id = (SELECT id FROM bible_version WHERE code = :primaryVersion)
            AND p.book = :book AND p.chapter = :chapter
        ORDER BY p.verse
    """)
    suspend fun getParallelChapter(
        primaryVersion: String,
        secondaryVersion: String,
        book: String,
        chapter: Int
    ): List<ParallelVerse>

    // 關鍵字搜尋（Phase 3 會用到，先放著）
    @Query("""
        SELECT v.* FROM verse v
        JOIN bible_version bv ON v.version_id = bv.id
        WHERE bv.code = :versionCode AND v.text LIKE '%' || :keyword || '%'
        ORDER BY v.book, v.chapter, v.verse
    """)
    suspend fun searchText(versionCode: String, keyword: String): List<Verse>

    // 多關鍵字組合查詢（最多 3 個）：useAnd = true 用 AND 組合，false 用 OR 組合
    // 空白的第 2、3 關鍵字會被忽略，不參與條件判斷
    suspend fun searchTextMulti(versionCode: String, keywords: List<String>, useAnd: Boolean): List<Verse> {
        val trimmed = keywords.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (trimmed.isEmpty()) return emptyList()
        val kw1 = trimmed.getOrElse(0) { "" }
        val kw2 = trimmed.getOrElse(1) { "" }
        val kw3 = trimmed.getOrElse(2) { "" }
        return searchTextMultiInternal(versionCode, kw1, kw2, kw3, useAnd)
    }

    // searchTextMulti 的實際查詢：
    // AND 模式下，空白的 kw2/kw3 視為「恆真」條件（不限制）
    // OR 模式下，空白的 kw2/kw3 視為「恆假」條件（不貢獻任何比對）
    @Query("""
        SELECT v.* FROM verse v
        JOIN bible_version bv ON v.version_id = bv.id
        WHERE bv.code = :versionCode
            AND (
                (:useAnd = 1
                    AND v.text LIKE '%' || :kw1 || '%'
                    AND (:kw2 = '' OR v.text LIKE '%' || :kw2 || '%')
                    AND (:kw3 = '' OR v.text LIKE '%' || :kw3 || '%')
                )
                OR
                (:useAnd = 0
                    AND (
                        v.text LIKE '%' || :kw1 || '%'
                        OR (:kw2 != '' AND v.text LIKE '%' || :kw2 || '%')
                        OR (:kw3 != '' AND v.text LIKE '%' || :kw3 || '%')
                    )
                )
            )
        ORDER BY v.book, v.chapter, v.verse
    """)
    suspend fun searchTextMultiInternal(
        versionCode: String,
        kw1: String,
        kw2: String,
        kw3: String,
        useAnd: Boolean
    ): List<Verse>

    // 書卷清單（給導覽選單用）
    @Query("SELECT * FROM book_order ORDER BY order_index")
    suspend fun getAllBooks(): List<BookOrder>

    // 取得某卷書有哪些章節（依 CUV 版本為準，因為兩版本章節結構應一致）
    @Query("""
    SELECT DISTINCT v.chapter FROM verse v
    JOIN bible_version bv ON v.version_id = bv.id
    WHERE bv.code = 'CUV' AND v.book = :book
    ORDER BY v.chapter
""")
    suspend fun getChapterList(book: String): List<Int>
}