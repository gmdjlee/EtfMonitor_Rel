package com.etfmonitor.core.database

import androidx.room.*
import com.etfmonitor.core.database.entities.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    /**
     * 최근 검색 히스토리 가져오기 (최신순)
     */
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int): Flow<List<SearchHistory>>

    /**
     * 검색 히스토리 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistory)

    /**
     * 특정 종목 검색 히스토리 삭제
     */
    @Query("DELETE FROM search_history WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    /**
     * 오래된 히스토리 삭제 (limit 개수 초과분)
     */
    @Query("""
        DELETE FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history
            ORDER BY searchedAt DESC
            LIMIT :limit
        )
    """)
    suspend fun deleteOldSearches(limit: Int)

    /**
     * 모든 검색 히스토리 삭제
     */
    @Query("DELETE FROM search_history")
    suspend fun deleteAll()

    /**
     * 검색 히스토리 개수 조회
     */
    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int
}
