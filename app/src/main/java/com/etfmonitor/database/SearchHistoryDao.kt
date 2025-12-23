package com.etfmonitor.database

import androidx.room.*
import com.etfmonitor.database.entities.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    /**
     * 최근 검색 히스토리 가져오기 (최신순) - 모든 기능
     */
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int): Flow<List<SearchHistory>>

    /**
     * 특정 기능의 최근 검색 히스토리 가져오기 (최신순)
     */
    @Query("SELECT * FROM search_history WHERE feature = :feature ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearchesByFeature(feature: String, limit: Int): Flow<List<SearchHistory>>

    /**
     * 검색 히스토리 추가 (중복 시 업데이트)
     * UNIQUE 인덱스(ticker, feature)로 인해 중복 시 REPLACE됨
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistory)

    /**
     * 기존 히스토리 삭제 후 새로 추가 (시간 업데이트용)
     */
    @Query("DELETE FROM search_history WHERE ticker = :ticker AND feature = :feature")
    suspend fun deleteByTickerAndFeature(ticker: String, feature: String)

    /**
     * 특정 종목 검색 히스토리 삭제
     */
    @Query("DELETE FROM search_history WHERE ticker = :ticker")
    suspend fun deleteByTicker(ticker: String)

    /**
     * 특정 기능의 오래된 히스토리 삭제 (limit 개수 초과분)
     */
    @Query("""
        DELETE FROM search_history
        WHERE feature = :feature AND id NOT IN (
            SELECT id FROM search_history
            WHERE feature = :feature
            ORDER BY searchedAt DESC
            LIMIT :limit
        )
    """)
    suspend fun deleteOldSearchesByFeature(feature: String, limit: Int)

    /**
     * 오래된 히스토리 삭제 (limit 개수 초과분) - 전체
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
     * 특정 기능의 모든 검색 히스토리 삭제
     */
    @Query("DELETE FROM search_history WHERE feature = :feature")
    suspend fun deleteAllByFeature(feature: String)

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

    /**
     * 특정 기능의 검색 히스토리 개수 조회
     */
    @Query("SELECT COUNT(*) FROM search_history WHERE feature = :feature")
    suspend fun getCountByFeature(feature: String): Int
}
