package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.etfmonitor.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EtfDao {

    // ========== ETF ==========

    @Query("SELECT * FROM etfs ORDER BY name LIMIT 1000")
    fun getAllEtfs(): Flow<List<Etf>>

    @Query("SELECT * FROM etfs ORDER BY name LIMIT 1000")
    suspend fun getAllEtfsSuspend(): List<Etf>

    @Query("SELECT * FROM etfs WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 100")
    fun searchEtfs(query: String): Flow<List<Etf>>

    // ✅ 단일 ETF 조회 추가
    @Query("SELECT * FROM etfs WHERE ticker = :ticker LIMIT 1")
    suspend fun getEtf(ticker: String): Etf?

    @Query("SELECT COUNT(*) FROM etfs")
    suspend fun getEtfCount(): Int

    @Query("SELECT COUNT(*) FROM holdings WHERE date = (SELECT MAX(date) FROM holdings)")
    suspend fun getHoldingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEtf(etf: Etf)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEtfs(etfs: List<Etf>)

    @Query("DELETE FROM etfs")
    suspend fun clearAllEtfs()

    // ========== Holdings ==========

    @Query("""
        SELECT * FROM holdings
        WHERE etfTicker = :etfTicker AND date = :date
        ORDER BY weightBps DESC
        LIMIT 500
    """)
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding>

    @Query("""
        SELECT DISTINCT date
        FROM holdings
        WHERE etfTicker = :etfTicker
        ORDER BY date DESC
        LIMIT 730
    """)
    suspend fun getDates(etfTicker: String): List<String>

    @Query("SELECT MAX(date) FROM holdings")
    suspend fun getLatestDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: Holding)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoldings(holdings: List<Holding>)

    @Query("DELETE FROM holdings")
    suspend fun clearAllHoldings()

    // ✅ 시계열 데이터 조회
    @Query("""
        SELECT
            date,
            CAST(weightBps AS REAL) / 10000.0 as weight,
            CAST(amountMillion AS REAL) * 1000000.0 as amount
        FROM holdings
        WHERE etfTicker = :etfTicker AND stockTicker = :stockTicker
        ORDER BY date ASC
        LIMIT 730
    """)
    suspend fun getHoldingTimeSeries(
        etfTicker: String,
        stockTicker: String
    ): List<HoldingTimeSeries>

    // ========== 전체 통계 쿼리 ==========

    /**
     * 전 종목 금액 순위 (모든 ETF 통합) - 상태별 ETF 수 포함
     * LIMIT 500: OOM 방지 및 메모리 최적화
     */
    @Query("""
        SELECT
            curr.stockTicker,
            curr.stockName,
            SUM(CAST(curr.amountMillion AS REAL) * 1000000.0) as totalAmount,
            COUNT(DISTINCT curr.etfTicker) as etfCount,
            MAX(CAST(curr.weightBps AS REAL) / 10000.0) as maxWeight,
            GROUP_CONCAT(DISTINCT curr.etfTicker) as etfList,
            COUNT(DISTINCT CASE
                WHEN NOT EXISTS (
                    SELECT 1 FROM holdings prev
                    WHERE prev.stockTicker = curr.stockTicker
                    AND prev.etfTicker = curr.etfTicker
                    AND prev.date = :previousDate
                ) THEN curr.etfTicker
            END) as newEtfCount,
            COUNT(DISTINCT CASE
                WHEN EXISTS (
                    SELECT 1 FROM holdings prev
                    WHERE prev.stockTicker = curr.stockTicker
                    AND prev.etfTicker = curr.etfTicker
                    AND prev.date = :previousDate
                    AND prev.weightBps < curr.weightBps
                ) THEN curr.etfTicker
            END) as increasedEtfCount,
            COUNT(DISTINCT CASE
                WHEN EXISTS (
                    SELECT 1 FROM holdings prev
                    WHERE prev.stockTicker = curr.stockTicker
                    AND prev.etfTicker = curr.etfTicker
                    AND prev.date = :previousDate
                    AND prev.weightBps > curr.weightBps
                ) THEN curr.etfTicker
            END) as decreasedEtfCount,
            IFNULL((
                SELECT COUNT(DISTINCT prev.etfTicker)
                FROM holdings prev
                WHERE prev.stockTicker = curr.stockTicker
                AND prev.date = :previousDate
                AND prev.etfTicker IN (:visibleEtfTickers)
                AND NOT EXISTS (
                    SELECT 1 FROM holdings curr2
                    WHERE curr2.stockTicker = prev.stockTicker
                    AND curr2.etfTicker = prev.etfTicker
                    AND curr2.date = :currentDate
                )
            ), 0) as removedEtfCount
        FROM holdings curr
        WHERE curr.date = :currentDate
        AND curr.etfTicker IN (:visibleEtfTickers)
        GROUP BY curr.stockTicker, curr.stockName
        ORDER BY totalAmount DESC
        LIMIT 500
    """)
    suspend fun getStockAmountRanking(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockAmountRanking>

    /**
     * 전체 신규 편입 종목 (LIMIT 300: 메모리 최적화)
     */
    @Query("""
        SELECT
            curr.stockTicker,
            curr.stockName,
            curr.etfTicker,
            e.name as etfName,
            0.0 as previousWeight,
            CAST(curr.weightBps AS REAL) / 10000.0 as currentWeight,
            CAST(curr.weightBps AS REAL) / 10000.0 as change,
            CAST(curr.amountMillion AS REAL) * 1000000.0 as currentAmount
        FROM holdings curr
        INNER JOIN etfs e ON curr.etfTicker = e.ticker
        WHERE curr.date = :currentDate
        AND curr.etfTicker IN (:visibleEtfTickers)
        AND NOT EXISTS (
            SELECT 1 FROM holdings prev
            WHERE prev.stockTicker = curr.stockTicker
            AND prev.etfTicker = curr.etfTicker
            AND prev.date = :previousDate
        )
        ORDER BY curr.amountMillion DESC
        LIMIT 300
    """)
    suspend fun getAllNewStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo>

    /**
     * 전체 제외 종목 (LIMIT 300: 메모리 최적화)
     */
    @Query("""
        SELECT
            prev.stockTicker,
            prev.stockName,
            prev.etfTicker,
            e.name as etfName,
            CAST(prev.weightBps AS REAL) / 10000.0 as previousWeight,
            0.0 as currentWeight,
            -CAST(prev.weightBps AS REAL) / 10000.0 as change,
            0.0 as currentAmount
        FROM holdings prev
        INNER JOIN etfs e ON prev.etfTicker = e.ticker
        WHERE prev.date = :previousDate
        AND prev.etfTicker IN (:visibleEtfTickers)
        AND NOT EXISTS (
            SELECT 1 FROM holdings curr
            WHERE curr.stockTicker = prev.stockTicker
            AND curr.etfTicker = prev.etfTicker
            AND curr.date = :currentDate
        )
        ORDER BY prev.amountMillion DESC
        LIMIT 300
    """)
    suspend fun getAllRemovedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo>

    /**
     * 전체 비중 증가 종목 (LIMIT 300: 메모리 최적화)
     */
    @Query("""
        SELECT
            curr.stockTicker,
            curr.stockName,
            curr.etfTicker,
            e.name as etfName,
            CAST(prev.weightBps AS REAL) / 10000.0 as previousWeight,
            CAST(curr.weightBps AS REAL) / 10000.0 as currentWeight,
            (CAST(curr.weightBps AS REAL) - CAST(prev.weightBps AS REAL)) / 10000.0 as change,
            CAST(curr.amountMillion AS REAL) * 1000000.0 as currentAmount
        FROM holdings curr
        INNER JOIN holdings prev
            ON curr.stockTicker = prev.stockTicker
            AND curr.etfTicker = prev.etfTicker
        INNER JOIN etfs e ON curr.etfTicker = e.ticker
        WHERE curr.date = :currentDate
        AND prev.date = :previousDate
        AND curr.etfTicker IN (:visibleEtfTickers)
        AND curr.weightBps > prev.weightBps + 100
        ORDER BY (curr.weightBps - prev.weightBps) DESC
        LIMIT 300
    """)
    suspend fun getAllIncreasedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo>

    // ========== Settings ==========

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: Setting)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteSetting(key: String)

    /**
     * 최근 2개 날짜 가져오기
     */
    @Query("""
        SELECT DISTINCT date
        FROM holdings
        ORDER BY date DESC
        LIMIT 2
    """)
    suspend fun getLatestTwoDates(): List<String>

    /**
     * 모든 날짜 가져오기 (최신순, 최대 limit개)
     */
    @Query("""
        SELECT DISTINCT date
        FROM holdings
        ORDER BY date DESC
        LIMIT :limit
    """)
    suspend fun getAllDistinctDates(limit: Int = 100): List<String>

    /**
     * 전체 비중 감소 종목 (LIMIT 300: 메모리 최적화)
     */
    @Query("""
        SELECT
            curr.stockTicker,
            curr.stockName,
            curr.etfTicker,
            e.name as etfName,
            CAST(prev.weightBps AS REAL) / 10000.0 as previousWeight,
            CAST(curr.weightBps AS REAL) / 10000.0 as currentWeight,
            (CAST(curr.weightBps AS REAL) - CAST(prev.weightBps AS REAL)) / 10000.0 as change,
            CAST(curr.amountMillion AS REAL) * 1000000.0 as currentAmount
        FROM holdings curr
        INNER JOIN holdings prev
            ON curr.stockTicker = prev.stockTicker
            AND curr.etfTicker = prev.etfTicker
        INNER JOIN etfs e ON curr.etfTicker = e.ticker
        WHERE curr.date = :currentDate
        AND prev.date = :previousDate
        AND curr.etfTicker IN (:visibleEtfTickers)
        AND curr.weightBps < prev.weightBps - 100
        ORDER BY (curr.weightBps - prev.weightBps) ASC
        LIMIT 300
    """)
    suspend fun getAllDecreasedStocks(currentDate: String, previousDate: String, visibleEtfTickers: List<String>): List<StockChangeInfo>

    /**
     * 원화예금 추이 (모든 ETF 합계)
     */
    @Query("""
        SELECT
            date,
            SUM(CAST(amountMillion AS REAL) * 1000000.0) as totalAmount,
            COUNT(DISTINCT etfTicker) as etfCount
        FROM holdings
        WHERE (stockName LIKE '%원화예금%' OR stockName LIKE '%cash%') AND etfTicker IN (:visibleEtfTickers)
        GROUP BY date
        ORDER BY date ASC
        LIMIT 730
    """)
    suspend fun getCashDepositTrend(visibleEtfTickers: List<String>): List<CashDepositTrend>

    /**
     * 특정 종목의 전체 ETF 통합 추이
     */
    @Query("""
        SELECT
            date,
            SUM(CAST(amountMillion AS REAL) * 1000000.0) as totalAmount,
            COUNT(DISTINCT etfTicker) as etfCount,
            MAX(CAST(weightBps AS REAL) / 10000.0) as maxWeight,
            AVG(CAST(weightBps AS REAL) / 10000.0) as avgWeight
        FROM holdings
        WHERE stockTicker = :stockTicker
        AND etfTicker IN (:visibleEtfTickers)
        GROUP BY date
        ORDER BY date ASC
        LIMIT 730
    """)
    suspend fun getStockAggregatedTrend(stockTicker: String, visibleEtfTickers: List<String>): List<StockAggregatedTimePoint>

    /**
     * 종목명 가져오기
     */
    @Query("""
        SELECT stockName
        FROM holdings
        WHERE stockTicker = :stockTicker
        LIMIT 1
    """)
    suspend fun getStockName(stockTicker: String): String?

    /**
     * 종목 검색 (종목명 또는 티커로 검색)
     */
    @Query("""
        SELECT DISTINCT stockTicker, stockName
        FROM holdings
        WHERE (stockName LIKE '%' || :query || '%'
           OR stockTicker LIKE '%' || :query || '%') AND etfTicker IN (:visibleEtfTickers)
        GROUP BY stockTicker
        ORDER BY stockName
        LIMIT 50
    """)
    suspend fun searchStocks(query: String, visibleEtfTickers: List<String>): List<StockSearchResult>

    /**
     * 특정 종목의 현재/이전 날짜 ETF 보유 현황
     */
    @Query("""
        SELECT
            h.etfTicker,
            e.name as etfName,
            CAST(h.weightBps AS REAL) / 10000.0 as weight,
            CAST(h.amountMillion AS REAL) * 1000000.0 as amount
        FROM holdings h
        INNER JOIN etfs e ON h.etfTicker = e.ticker
        WHERE h.stockTicker = :stockTicker AND h.date = :date
        AND h.etfTicker IN (:visibleEtfTickers)
        ORDER BY h.amountMillion DESC
        LIMIT 100
    """)
    suspend fun getStockHoldingsByDate(stockTicker: String, date: String, visibleEtfTickers: List<String>): List<StockHoldingByEtf>

    // ========== 데이터 아카이빙 관련 ==========

    /**
     * 특정 기간의 모든 holding 데이터 조회
     * LIMIT 500: OOM 방지
     */
    @Query("""
        SELECT * FROM holdings
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date ASC
        LIMIT 500
    """)
    suspend fun getHoldingsByDateRange(startDate: String, endDate: String): List<Holding>

    /**
     * 특정 날짜의 모든 holdings 조회 (통계 계산용)
     * LIMIT 500: OOM 방지
     */
    @Query("""
        SELECT * FROM holdings
        WHERE date = :date
        ORDER BY etfTicker, stockTicker
        LIMIT 500
    """)
    suspend fun getHoldingsByDate(date: String): List<Holding>

    /**
     * 특정 기간의 데이터 삭제
     */
    @Query("""
        DELETE FROM holdings
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun deleteHoldingsByDateRange(startDate: String, endDate: String)

    /**
     * 특정 날짜보다 오래된 데이터 삭제
     */
    @Query("""
        DELETE FROM holdings
        WHERE date < :beforeDate
    """)
    suspend fun deleteHoldingsBeforeDate(beforeDate: String)

    /**
     * 스냅샷 타입별 데이터 개수 조회
     */
    @Query("""
        SELECT snapshotType, COUNT(*) as count
        FROM holdings
        GROUP BY snapshotType
    """)
    suspend fun getSnapshotTypeCounts(): List<SnapshotTypeCount>

    /**
     * 전체 holding 데이터 개수
     */
    @Query("SELECT COUNT(*) FROM holdings")
    suspend fun getTotalHoldingCount(): Long

    /**
     * 기간별 데이터 개수
     */
    @Query("""
        SELECT COUNT(*) FROM holdings
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun getHoldingCountByDateRange(startDate: String, endDate: String): Long

    /**
     * yyyyMMdd → yyyy-MM-dd 날짜 형식 정규화 (Critical Rule #10 위반 데이터 수정)
     * 멱등(idempotent): 이미 yyyy-MM-dd인 데이터는 변경되지 않음
     */
    @Query("""
        UPDATE holdings
        SET date = SUBSTR(date, 1, 4) || '-' || SUBSTR(date, 5, 2) || '-' || SUBSTR(date, 7, 2)
        WHERE LENGTH(date) = 8 AND date NOT LIKE '%-%'
    """)
    suspend fun normalizeDateFormat()
}

// ✅ 종목 검색 결과
data class StockSearchResult(
    val stockTicker: String,
    val stockName: String
)

// ✅ 종목의 ETF 보유 현황
data class StockHoldingByEtf(
    val etfTicker: String,
    val etfName: String,
    val weight: Float,
    val amount: Float
)

// ✅ 스냅샷 타입별 개수
data class SnapshotTypeCount(
    val snapshotType: String,
    val count: Int
)