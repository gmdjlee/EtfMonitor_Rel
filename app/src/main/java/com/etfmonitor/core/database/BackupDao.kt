package com.etfmonitor.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etfmonitor.core.database.entities.*

/**
 * 백업/복구 전용 DAO
 * 모든 엔티티에 대한 일괄 조회 및 삽입 메서드 제공
 */
@Dao
interface BackupDao {

    // ==================== ETF ====================
    @Query("SELECT * FROM etfs")
    suspend fun getAllEtfs(): List<Etf>

    @Query("SELECT COUNT(*) FROM etfs")
    suspend fun getEtfCount(): Int

    @Query("SELECT ticker FROM etfs")
    suspend fun getAllEtfTickers(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEtfsIgnore(etfs: List<Etf>): List<Long>

    // ==================== Stock ====================
    @Query("SELECT * FROM stocks")
    suspend fun getAllStocks(): List<Stock>

    @Query("SELECT COUNT(*) FROM stocks")
    suspend fun getStockCount(): Int

    @Query("SELECT ticker FROM stocks")
    suspend fun getAllStockTickers(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStocksIgnore(stocks: List<Stock>): List<Long>

    // ==================== Setting ====================
    @Query("SELECT * FROM settings")
    suspend fun getAllSettings(): List<Setting>

    @Query("SELECT COUNT(*) FROM settings")
    suspend fun getSettingCount(): Int

    @Query("SELECT key FROM settings")
    suspend fun getAllSettingKeys(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIgnore(settings: List<Setting>): List<Long>

    // ==================== Holding ====================
    @Query("SELECT * FROM holdings")
    suspend fun getAllHoldings(): List<Holding>

    @Query("SELECT * FROM holdings WHERE date >= :startDate AND date <= :endDate")
    suspend fun getHoldingsByDateRange(startDate: String, endDate: String): List<Holding>

    @Query("SELECT COUNT(*) FROM holdings")
    suspend fun getHoldingCount(): Int

    @Query("SELECT MIN(date) FROM holdings")
    suspend fun getHoldingMinDate(): String?

    @Query("SELECT MAX(date) FROM holdings")
    suspend fun getHoldingMaxDate(): String?

    @Query("SELECT DISTINCT etfTicker || '-' || stockTicker || '-' || date FROM holdings")
    suspend fun getAllHoldingKeys(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHoldingsIgnore(holdings: List<Holding>): List<Long>

    // ==================== MarketDeposit ====================
    @Query("SELECT * FROM market_deposits")
    suspend fun getAllMarketDeposits(): List<MarketDeposit>

    @Query("SELECT * FROM market_deposits WHERE date >= :startDate AND date <= :endDate")
    suspend fun getMarketDepositsByDateRange(startDate: String, endDate: String): List<MarketDeposit>

    @Query("SELECT COUNT(*) FROM market_deposits")
    suspend fun getMarketDepositCount(): Int

    @Query("SELECT MIN(date) FROM market_deposits")
    suspend fun getMarketDepositMinDate(): String?

    @Query("SELECT MAX(date) FROM market_deposits")
    suspend fun getMarketDepositMaxDate(): String?

    @Query("SELECT date FROM market_deposits")
    suspend fun getAllMarketDepositDates(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarketDepositsIgnore(deposits: List<MarketDeposit>): List<Long>

    // ==================== FearGreedIndex ====================
    @Query("SELECT * FROM fear_greed_index")
    suspend fun getAllFearGreedIndices(): List<FearGreedIndex>

    @Query("SELECT * FROM fear_greed_index WHERE date >= :startDate AND date <= :endDate")
    suspend fun getFearGreedByDateRange(startDate: String, endDate: String): List<FearGreedIndex>

    @Query("SELECT COUNT(*) FROM fear_greed_index")
    suspend fun getFearGreedCount(): Int

    @Query("SELECT MIN(date) FROM fear_greed_index")
    suspend fun getFearGreedMinDate(): String?

    @Query("SELECT MAX(date) FROM fear_greed_index")
    suspend fun getFearGreedMaxDate(): String?

    @Query("SELECT id FROM fear_greed_index")
    suspend fun getAllFearGreedIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFearGreedIgnore(indices: List<FearGreedIndex>): List<Long>

    // ==================== MarketOscillatorData ====================
    @Query("SELECT * FROM market_oscillator")
    suspend fun getAllMarketOscillators(): List<MarketOscillatorData>

    @Query("SELECT * FROM market_oscillator WHERE date >= :startDate AND date <= :endDate")
    suspend fun getMarketOscillatorsByDateRange(startDate: String, endDate: String): List<MarketOscillatorData>

    @Query("SELECT COUNT(*) FROM market_oscillator")
    suspend fun getMarketOscillatorCount(): Int

    @Query("SELECT MIN(date) FROM market_oscillator")
    suspend fun getMarketOscillatorMinDate(): String?

    @Query("SELECT MAX(date) FROM market_oscillator")
    suspend fun getMarketOscillatorMaxDate(): String?

    @Query("SELECT id FROM market_oscillator")
    suspend fun getAllMarketOscillatorIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarketOscillatorsIgnore(oscillators: List<MarketOscillatorData>): List<Long>

    // ==================== MarketIndex ====================
    @Query("SELECT * FROM market_index")
    suspend fun getAllMarketIndices(): List<MarketIndex>

    @Query("SELECT * FROM market_index WHERE date >= :startDate AND date <= :endDate")
    suspend fun getMarketIndicesByDateRange(startDate: String, endDate: String): List<MarketIndex>

    @Query("SELECT COUNT(*) FROM market_index")
    suspend fun getMarketIndexCount(): Int

    @Query("SELECT MIN(date) FROM market_index")
    suspend fun getMarketIndexMinDate(): String?

    @Query("SELECT MAX(date) FROM market_index")
    suspend fun getMarketIndexMaxDate(): String?

    @Query("SELECT id FROM market_index")
    suspend fun getAllMarketIndexIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarketIndicesIgnore(indices: List<MarketIndex>): List<Long>

    // ==================== DailyEtfStatistics ====================
    @Query("SELECT * FROM daily_etf_statistics")
    suspend fun getAllDailyEtfStatistics(): List<DailyEtfStatistics>

    @Query("SELECT * FROM daily_etf_statistics WHERE date >= :startDate AND date <= :endDate")
    suspend fun getDailyEtfStatisticsByDateRange(startDate: String, endDate: String): List<DailyEtfStatistics>

    @Query("SELECT COUNT(*) FROM daily_etf_statistics")
    suspend fun getDailyEtfStatisticsCount(): Int

    @Query("SELECT MIN(date) FROM daily_etf_statistics")
    suspend fun getDailyEtfStatisticsMinDate(): String?

    @Query("SELECT MAX(date) FROM daily_etf_statistics")
    suspend fun getDailyEtfStatisticsMaxDate(): String?

    @Query("SELECT date FROM daily_etf_statistics")
    suspend fun getAllDailyEtfStatisticsDates(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyEtfStatisticsIgnore(stats: List<DailyEtfStatistics>): List<Long>

    // ==================== BloodIndicator ====================
    @Query("SELECT * FROM blood_indicator")
    suspend fun getAllBloodIndicators(): List<BloodIndicator>

    @Query("SELECT * FROM blood_indicator WHERE date >= :startDate AND date <= :endDate")
    suspend fun getBloodIndicatorsByDateRange(startDate: String, endDate: String): List<BloodIndicator>

    @Query("SELECT COUNT(*) FROM blood_indicator")
    suspend fun getBloodIndicatorCount(): Int

    @Query("SELECT MIN(date) FROM blood_indicator")
    suspend fun getBloodIndicatorMinDate(): String?

    @Query("SELECT MAX(date) FROM blood_indicator")
    suspend fun getBloodIndicatorMaxDate(): String?

    @Query("SELECT id FROM blood_indicator")
    suspend fun getAllBloodIndicatorIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBloodIndicatorsIgnore(indicators: List<BloodIndicator>): List<Long>

    // ==================== PriceCache ====================
    @Query("SELECT * FROM price_cache")
    suspend fun getAllPriceCaches(): List<PriceCache>

    @Query("SELECT * FROM price_cache WHERE date >= :startDate AND date <= :endDate")
    suspend fun getPriceCachesByDateRange(startDate: String, endDate: String): List<PriceCache>

    @Query("SELECT COUNT(*) FROM price_cache")
    suspend fun getPriceCacheCount(): Int

    @Query("SELECT MIN(date) FROM price_cache")
    suspend fun getPriceCacheMinDate(): String?

    @Query("SELECT MAX(date) FROM price_cache")
    suspend fun getPriceCacheMaxDate(): String?

    @Query("SELECT ticker || '-' || date FROM price_cache")
    suspend fun getAllPriceCacheKeys(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPriceCachesIgnore(caches: List<PriceCache>): List<Long>

    // ==================== StockAnalysisData ====================
    @Query("SELECT * FROM stock_analysis_data")
    suspend fun getAllStockAnalysisData(): List<StockAnalysisData>

    @Query("SELECT COUNT(*) FROM stock_analysis_data")
    suspend fun getStockAnalysisDataCount(): Int

    @Query("SELECT ticker FROM stock_analysis_data")
    suspend fun getAllStockAnalysisDataTickers(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStockAnalysisDataIgnore(data: List<StockAnalysisData>): List<Long>

    // ==================== AIAnalysisResult ====================
    @Query("SELECT * FROM ai_analysis_result")
    suspend fun getAllAIAnalysisResults(): List<AIAnalysisResult>

    @Query("SELECT COUNT(*) FROM ai_analysis_result")
    suspend fun getAIAnalysisResultCount(): Int

    @Query("SELECT id FROM ai_analysis_result")
    suspend fun getAllAIAnalysisResultIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAIAnalysisResultsIgnore(results: List<AIAnalysisResult>): List<Long>

    // ==================== AIChatSession ====================
    @Query("SELECT * FROM ai_chat_session")
    suspend fun getAllAIChatSessions(): List<AIChatSession>

    @Query("SELECT COUNT(*) FROM ai_chat_session")
    suspend fun getAIChatSessionCount(): Int

    @Query("SELECT id FROM ai_chat_session")
    suspend fun getAllAIChatSessionIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAIChatSessionsIgnore(sessions: List<AIChatSession>): List<Long>

    // ==================== AIChatMessage ====================
    @Query("SELECT * FROM ai_chat_message")
    suspend fun getAllAIChatMessages(): List<AIChatMessage>

    @Query("SELECT COUNT(*) FROM ai_chat_message")
    suspend fun getAIChatMessageCount(): Int

    @Query("SELECT id FROM ai_chat_message")
    suspend fun getAllAIChatMessageIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAIChatMessagesIgnore(messages: List<AIChatMessage>): List<Long>

    // ==================== CorrelationAnalysisResult ====================
    @Query("SELECT * FROM correlation_analysis_result")
    suspend fun getAllCorrelationResults(): List<CorrelationAnalysisResult>

    @Query("SELECT COUNT(*) FROM correlation_analysis_result")
    suspend fun getCorrelationResultCount(): Int

    @Query("SELECT id FROM correlation_analysis_result")
    suspend fun getAllCorrelationResultIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCorrelationResultsIgnore(results: List<CorrelationAnalysisResult>): List<Long>

    // ==================== SectorAnalysis ====================
    @Query("SELECT * FROM sector_analysis")
    suspend fun getAllSectorAnalyses(): List<SectorAnalysis>

    @Query("SELECT COUNT(*) FROM sector_analysis")
    suspend fun getSectorAnalysisCount(): Int

    @Query("SELECT id FROM sector_analysis")
    suspend fun getAllSectorAnalysisIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSectorAnalysesIgnore(analyses: List<SectorAnalysis>): List<Long>

    // ==================== EtfCorrelationCache ====================
    @Query("SELECT * FROM etf_correlation_cache")
    suspend fun getAllEtfCorrelationCaches(): List<EtfCorrelationCache>

    @Query("SELECT COUNT(*) FROM etf_correlation_cache")
    suspend fun getEtfCorrelationCacheCount(): Int

    @Query("SELECT id FROM etf_correlation_cache")
    suspend fun getAllEtfCorrelationCacheIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEtfCorrelationCachesIgnore(caches: List<EtfCorrelationCache>): List<Long>

    // ==================== LiquidityAnalysis ====================
    @Query("SELECT * FROM liquidity_analysis")
    suspend fun getAllLiquidityAnalyses(): List<LiquidityAnalysis>

    @Query("SELECT COUNT(*) FROM liquidity_analysis")
    suspend fun getLiquidityAnalysisCount(): Int

    @Query("SELECT date FROM liquidity_analysis")
    suspend fun getAllLiquidityAnalysisDates(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLiquidityAnalysesIgnore(analyses: List<LiquidityAnalysis>): List<Long>

    // ==================== StockIndicatorAIResult ====================
    @Query("SELECT * FROM stock_indicator_ai_result")
    suspend fun getAllStockIndicatorAIResults(): List<StockIndicatorAIResult>

    @Query("SELECT COUNT(*) FROM stock_indicator_ai_result")
    suspend fun getStockIndicatorAIResultCount(): Int

    @Query("SELECT id FROM stock_indicator_ai_result")
    suspend fun getAllStockIndicatorAIResultIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStockIndicatorAIResultsIgnore(results: List<StockIndicatorAIResult>): List<Long>

    // ==================== EnhancedPrediction ====================
    @Query("SELECT * FROM enhanced_predictions")
    suspend fun getAllEnhancedPredictions(): List<EnhancedPrediction>

    @Query("SELECT COUNT(*) FROM enhanced_predictions")
    suspend fun getEnhancedPredictionCount(): Int

    @Query("SELECT id FROM enhanced_predictions")
    suspend fun getAllEnhancedPredictionIds(): List<String>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEnhancedPredictionsIgnore(predictions: List<EnhancedPrediction>): List<Long>

    // ==================== SearchHistory ====================
    @Query("SELECT * FROM search_history")
    suspend fun getAllSearchHistories(): List<SearchHistory>

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getSearchHistoryCount(): Int

    @Query("SELECT id FROM search_history")
    suspend fun getAllSearchHistoryIds(): List<Int>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSearchHistoriesIgnore(histories: List<SearchHistory>): List<Long>

    // ==================== Global Date Range ====================

    /**
     * 전체 데이터베이스에서 날짜 범위 조회
     */
    @Query("""
        SELECT MIN(minDate) as minDate, MAX(maxDate) as maxDate FROM (
            SELECT MIN(date) as minDate, MAX(date) as maxDate FROM holdings
            UNION ALL
            SELECT MIN(date), MAX(date) FROM market_deposits
            UNION ALL
            SELECT MIN(date), MAX(date) FROM fear_greed_index
            UNION ALL
            SELECT MIN(date), MAX(date) FROM market_oscillator
            UNION ALL
            SELECT MIN(date), MAX(date) FROM market_index
            UNION ALL
            SELECT MIN(date), MAX(date) FROM daily_etf_statistics
            UNION ALL
            SELECT MIN(date), MAX(date) FROM blood_indicator
            UNION ALL
            SELECT MIN(date), MAX(date) FROM price_cache
        )
    """)
    suspend fun getGlobalDateRange(): GlobalDateRange?
}

/**
 * 전역 날짜 범위 데이터 클래스
 */
data class GlobalDateRange(
    val minDate: String?,
    val maxDate: String?
)
