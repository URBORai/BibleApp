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
    //
    // bookCodes 是搜尋範圍（要掃描哪些書卷）。全部／舊約／新約／自訂在呼叫端就已經
    // 被 SearchScope.resolve() 攤平成一份代碼清單，這裡不必分四種情況。
    // 空清單代表沒有任何書卷可查，直接回空結果——也順便避開 SQL 的 IN () 空清單問題
    suspend fun searchTextMulti(
        versionCode: String,
        keywords: List<String>,
        useAnd: Boolean,
        bookCodes: List<String>
    ): List<Verse> {
        val trimmed = keywords.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (trimmed.isEmpty() || bookCodes.isEmpty()) return emptyList()
        val kw1 = trimmed.getOrElse(0) { "" }
        val kw2 = trimmed.getOrElse(1) { "" }
        val kw3 = trimmed.getOrElse(2) { "" }
        return searchTextMultiInternal(versionCode, kw1, kw2, kw3, useAnd, bookCodes)
    }

    // searchTextMulti 的實際查詢：
    // AND 模式下，空白的 kw2/kw3 視為「恆真」條件（不限制）
    // OR 模式下，空白的 kw2/kw3 視為「恆假」條件（不貢獻任何比對）
    @Query("""
        SELECT v.* FROM verse v
        JOIN bible_version bv ON v.version_id = bv.id
        WHERE bv.code = :versionCode
            AND v.book IN (:bookCodes)
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
        useAnd: Boolean,
        bookCodes: List<String>
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

    // 紙本頁碼跳轉：取出這一頁的第一節，用它的 book／chapter 決定要載入哪一章。
    // 版本固定 CUV：page_no 只有和合本有值（見 getParallelChapter 的 COALESCE 註解），
    // 這裡查的是「紙本第幾頁」這個實體書屬性，跟介面語言無關。
    //
    // bookCodes 決定「在哪一半裡找」，不能省：和合本的舊約與新約各自從第 1 頁起算
    // （創世記 1:1 與馬太福音 1:1 都印在第 1 頁），光給一個頁碼問不出唯一答案。
    // 呼叫端用 Testament 切好再傳進來，這裡不必自己知道哪一卷屬於哪一約——
    // 跟 searchTextMultiInternal 收 bookCodes 的理由一樣。
    //
    // 排序一定要 JOIN book_order 走 order_index：直接 ORDER BY v.book 會變成
    // 'EXO' < 'GEN' 的字母序，一頁橫跨兩卷書時就會挑到正典順序在後面的那一節。
    @Query("""
        SELECT v.* FROM verse v
        JOIN bible_version bv ON v.version_id = bv.id
        JOIN book_order b ON b.book_code = v.book
        WHERE bv.code = 'CUV' AND v.page_no = :pageNo AND v.book IN (:bookCodes)
        ORDER BY b.order_index, v.chapter, v.verse
        LIMIT 1
    """)
    suspend fun getFirstVerseOnPage(pageNo: Int, bookCodes: List<String>): Verse?

    // 該半部的頁碼上限：給輸入框的說明文字與超出範圍的錯誤訊息用，
    // 不必在程式裡寫死「舊約 1126、新約 377」這兩個數字
    @Query("SELECT MAX(page_no) FROM verse WHERE book IN (:bookCodes)")
    suspend fun getMaxPageNo(bookCodes: List<String>): Int?
}