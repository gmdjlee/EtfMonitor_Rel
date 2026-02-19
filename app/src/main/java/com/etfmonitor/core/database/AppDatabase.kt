package com.etfmonitor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etfmonitor.core.database.AIChatDao
import com.etfmonitor.core.database.AIAnalysisDao
import com.etfmonitor.core.database.CorrelationAnalysisDao
import com.etfmonitor.core.database.DailyEtfStatisticsDao
import com.etfmonitor.core.database.EtfCorrelationDao
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.LiquidityAnalysisDao
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.database.MarketOscillatorDao
import com.etfmonitor.core.database.SearchHistoryDao
import com.etfmonitor.core.database.SectorAnalysisDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.database.StockIndicatorAIResultDao
import com.etfmonitor.core.database.entities.AIChatMessage
import com.etfmonitor.core.database.entities.AIChatSession
import com.etfmonitor.core.database.entities.AIAnalysisResult
import com.etfmonitor.core.database.entities.CorrelationAnalysisResult
import com.etfmonitor.core.database.entities.DailyEtfStatistics
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.EtfCorrelationCache
import com.etfmonitor.core.database.entities.FearGreedIndex
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.LiquidityAnalysis
import com.etfmonitor.core.database.entities.MarketDeposit
import com.etfmonitor.core.database.entities.MarketIndex
import com.etfmonitor.core.database.entities.MarketOscillatorData
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.SectorAnalysis
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.core.database.entities.StockAnalysisData
import com.etfmonitor.core.database.entities.StockIndicatorAIResult
import com.etfmonitor.core.database.entities.PriceCache
import com.etfmonitor.core.database.entities.EnhancedPrediction
import com.etfmonitor.core.database.entities.BloodIndicator
import com.etfmonitor.core.database.entities.FinancialCache

@Database(
    entities = [
        Etf::class,
        Holding::class,
        Setting::class,
        Stock::class,
        MarketDeposit::class,
        StockAnalysisData::class,
        SearchHistory::class,
        FearGreedIndex::class,
        MarketOscillatorData::class,
        MarketIndex::class,
        DailyEtfStatistics::class,
        CorrelationAnalysisResult::class,
        AIAnalysisResult::class,
        AIChatSession::class,
        AIChatMessage::class,
        SectorAnalysis::class,
        EtfCorrelationCache::class,
        LiquidityAnalysis::class,
        StockIndicatorAIResult::class,
        PriceCache::class,
        EnhancedPrediction::class,
        BloodIndicator::class,
        FinancialCache::class
    ],
    version = 20,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EtfDao
    abstract fun stockDao(): StockDao
    abstract fun marketDepositDao(): MarketDepositDao
    abstract fun stockAnalysisDao(): StockAnalysisDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun fearGreedDao(): FearGreedDao
    abstract fun marketOscillatorDao(): MarketOscillatorDao
    abstract fun marketIndexDao(): MarketIndexDao
    abstract fun dailyEtfStatisticsDao(): DailyEtfStatisticsDao
    abstract fun correlationAnalysisDao(): CorrelationAnalysisDao
    abstract fun aiAnalysisDao(): AIAnalysisDao
    abstract fun aiChatDao(): AIChatDao
    abstract fun sectorAnalysisDao(): SectorAnalysisDao
    abstract fun etfCorrelationDao(): EtfCorrelationDao
    abstract fun liquidityAnalysisDao(): LiquidityAnalysisDao
    abstract fun stockIndicatorAIResultDao(): StockIndicatorAIResultDao
    abstract fun priceCacheDao(): PriceCacheDao
    abstract fun enhancedPredictionDao(): EnhancedPredictionDao
    abstract fun bloodIndicatorDao(): BloodIndicatorDao
    abstract fun financialCacheDao(): FinancialCacheDao
    abstract fun backupDao(): BackupDao
}

/**
 * Migration from version 1 to 2: Add Stock table
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stocks (
                ticker TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                market TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 2 to 3: Add MarketDeposit table
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS market_deposits (
                date TEXT PRIMARY KEY NOT NULL,
                depositAmount REAL NOT NULL,
                depositChange REAL NOT NULL,
                creditAmount REAL NOT NULL,
                creditChange REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 3 to 4: Add StockAnalysisData table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_analysis_data (
                ticker TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                dates TEXT NOT NULL,
                marketCap TEXT NOT NULL,
                foreign5d TEXT NOT NULL,
                institution5d TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                dataStartDate TEXT NOT NULL,
                dataEndDate TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 4 to 5: Add SearchHistory table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                market TEXT NOT NULL,
                searchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 5 to 6: Add FearGreedIndex table
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fear_greed_index (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                date TEXT NOT NULL,
                indexValue REAL NOT NULL,
                fearGreedValue REAL NOT NULL,
                oscillator REAL NOT NULL,
                rsi REAL NOT NULL,
                momentum REAL NOT NULL,
                putCallRatio REAL NOT NULL,
                volatility REAL NOT NULL,
                spread REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 6 to 7: Add MarketOscillator table
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS market_oscillator (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                date TEXT NOT NULL,
                indexValue REAL NOT NULL,
                oscillator REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 7 to 8: Optimize Holding table structure
 * - weight (REAL) → weightBps (INTEGER) : 비중을 basis point로 저장
 * - amount (REAL) → amountMillion (INTEGER) : 금액을 백만원 단위로 저장
 * - snapshotType (TEXT) 추가 : 스냅샷 타입 구분
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 임시 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS holdings_new (
                etfTicker TEXT NOT NULL,
                stockTicker TEXT NOT NULL,
                stockName TEXT NOT NULL,
                date TEXT NOT NULL,
                weightBps INTEGER NOT NULL,
                amountMillion INTEGER NOT NULL,
                snapshotType TEXT NOT NULL DEFAULT 'DAILY',
                PRIMARY KEY (etfTicker, stockTicker, date)
            )
            """.trimIndent()
        )

        // 2. 기존 데이터 변환하여 복사
        database.execSQL(
            """
            INSERT INTO holdings_new (etfTicker, stockTicker, stockName, date, weightBps, amountMillion, snapshotType)
            SELECT
                etfTicker,
                stockTicker,
                stockName,
                date,
                CAST(weight * 10000 AS INTEGER) as weightBps,
                CAST(amount / 1000000 AS INTEGER) as amountMillion,
                'DAILY' as snapshotType
            FROM holdings
            """.trimIndent()
        )

        // 3. 기존 테이블 삭제
        database.execSQL("DROP TABLE holdings")

        // 4. 새 테이블 이름 변경
        database.execSQL("ALTER TABLE holdings_new RENAME TO holdings")

        // 5. 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_date ON holdings(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_etfTicker ON holdings(etfTicker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_etfTicker_date ON holdings(etfTicker, date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_etfTicker_stockTicker ON holdings(etfTicker, stockTicker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_stockTicker_date ON holdings(stockTicker, date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_snapshotType ON holdings(snapshotType)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_holdings_date_snapshotType ON holdings(date, snapshotType)")
    }
}

/**
 * Migration from version 8 to 9: Add MarketIndex table
 * 시장 지수 데이터 저장 테이블 추가 (ETF 통계와의 상관관계 분석용)
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS market_index (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                date TEXT NOT NULL,
                closePrice REAL NOT NULL,
                openPrice REAL NOT NULL,
                highPrice REAL NOT NULL,
                lowPrice REAL NOT NULL,
                volume INTEGER NOT NULL,
                changeRate REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_market_index_market ON market_index(market)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_market_index_date ON market_index(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_market_index_market_date ON market_index(market, date)")
    }
}

/**
 * Migration from version 9 to 10: Add DailyEtfStatistics table
 * 일별 ETF 통계 데이터 저장 테이블 추가
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_etf_statistics (
                date TEXT PRIMARY KEY NOT NULL,
                newStockCount INTEGER NOT NULL,
                newStockAmount INTEGER NOT NULL,
                removedStockCount INTEGER NOT NULL,
                removedStockAmount INTEGER NOT NULL,
                increasedStockCount INTEGER NOT NULL,
                increasedStockAmount INTEGER NOT NULL,
                decreasedStockCount INTEGER NOT NULL,
                decreasedStockAmount INTEGER NOT NULL,
                cashDepositAmount INTEGER NOT NULL,
                cashDepositChange INTEGER NOT NULL,
                cashDepositChangeRate REAL NOT NULL,
                totalEtfCount INTEGER NOT NULL,
                totalHoldingAmount INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_daily_etf_statistics_date ON daily_etf_statistics(date)")
    }
}

/**
 * Migration from version 10 to 11: Add AI Analysis tables
 * - CorrelationAnalysisResult: 상관관계 분석 결과
 * - AIAnalysisResult: AI 분석 결과
 * - AIChatSession: 채팅 세션
 * - AIChatMessage: 채팅 메시지
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. CorrelationAnalysisResult 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS correlation_analysis_result (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                analysisDate TEXT NOT NULL,
                periodDays INTEGER NOT NULL,
                etfNewStockCorrelation REAL NOT NULL,
                etfRemovedStockCorrelation REAL NOT NULL,
                etfIncreasedCorrelation REAL NOT NULL,
                etfDecreasedCorrelation REAL NOT NULL,
                etfNetFlowCorrelation REAL NOT NULL,
                cashDepositCorrelation REAL NOT NULL,
                marketDepositCorrelation REAL,
                creditBalanceCorrelation REAL,
                fearGreedCorrelation REAL,
                fearGreedLeadCorrelation REAL,
                oscillatorCorrelation REAL,
                oscillatorLeadCorrelation REAL,
                compositeScore REAL NOT NULL,
                signal TEXT NOT NULL,
                confidence REAL NOT NULL,
                upProbability REAL NOT NULL,
                downProbability REAL NOT NULL,
                analysisContext TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. AIAnalysisResult 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_analysis_result (
                id TEXT PRIMARY KEY NOT NULL,
                market TEXT NOT NULL,
                analysisDate TEXT NOT NULL,
                correlationResultId TEXT,
                aiProvider TEXT NOT NULL,
                aiModel TEXT NOT NULL,
                signal TEXT NOT NULL,
                confidence REAL NOT NULL,
                upProbability REAL NOT NULL,
                downProbability REAL NOT NULL,
                riskLevel TEXT NOT NULL,
                reasoning TEXT NOT NULL,
                keyFactors TEXT NOT NULL,
                recommendation TEXT NOT NULL,
                alternativeScenarios TEXT,
                promptUsed TEXT NOT NULL,
                rawResponse TEXT NOT NULL,
                processingTimeMs INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 3. AIChatSession 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_chat_session (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                market TEXT,
                analysisDate TEXT,
                contextData TEXT,
                messageCount INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 4. AIChatMessage 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_chat_message (
                id TEXT PRIMARY KEY NOT NULL,
                sessionId TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                analysisResultId TEXT,
                aiProvider TEXT,
                aiModel TEXT,
                tokenCount INTEGER,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_message_sessionId ON ai_chat_message(sessionId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_message_sessionId_timestamp ON ai_chat_message(sessionId, timestamp)")
    }
}

/**
 * Migration from version 11 to 12: Add StockPrediction table
 * ML 모델 기반 주가 상승 예측 결과 저장
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_predictions (
                id TEXT PRIMARY KEY NOT NULL,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                predictionDate TEXT NOT NULL,
                status TEXT NOT NULL,
                confidence REAL NOT NULL,
                weightChange REAL NOT NULL,
                etfCount INTEGER NOT NULL,
                amountBillion REAL NOT NULL,
                daysAfter INTEGER NOT NULL,
                priceThreshold REAL NOT NULL,
                modelType TEXT NOT NULL,
                actualPriceChange REAL,
                wasCorrect INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_predictions_predictionDate ON stock_predictions(predictionDate)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_predictions_ticker ON stock_predictions(ticker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_predictions_confidence ON stock_predictions(confidence)")
    }
}

/**
 * Migration from version 12 to 13: Stock Master Integration
 * 1. stocks 테이블 확장 (sector, is_etf_holding)
 * 2. stock_analysis_data에서 name 컬럼 제거 (stocks JOIN으로 대체)
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. stocks 테이블에 새 컬럼 추가
        database.execSQL("ALTER TABLE stocks ADD COLUMN sector TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE stocks ADD COLUMN is_etf_holding INTEGER NOT NULL DEFAULT 0")

        // 2. stocks 인덱스 추가
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stocks_is_etf_holding ON stocks(is_etf_holding)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stocks_market ON stocks(market)")

        // 3. stock_analysis_data 테이블에서 name 컬럼 제거
        // SQLite는 DROP COLUMN을 지원하지 않으므로 테이블 재생성 필요
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_analysis_data_new (
                ticker TEXT PRIMARY KEY NOT NULL,
                dates TEXT NOT NULL,
                marketCap TEXT NOT NULL,
                foreign5d TEXT NOT NULL,
                institution5d TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL,
                dataStartDate TEXT NOT NULL,
                dataEndDate TEXT NOT NULL
            )
            """.trimIndent()
        )

        // 4. 기존 데이터 복사 (name 제외)
        database.execSQL(
            """
            INSERT INTO stock_analysis_data_new (ticker, dates, marketCap, foreign5d, institution5d, lastUpdated, dataStartDate, dataEndDate)
            SELECT ticker, dates, marketCap, foreign5d, institution5d, lastUpdated, dataStartDate, dataEndDate
            FROM stock_analysis_data
            """.trimIndent()
        )

        // 5. 기존 테이블 삭제 및 이름 변경
        database.execSQL("DROP TABLE stock_analysis_data")
        database.execSQL("ALTER TABLE stock_analysis_data_new RENAME TO stock_analysis_data")

        // 6. 기존 holdings 데이터를 기반으로 stocks 동기화 (is_etf_holding = 1)
        database.execSQL(
            """
            INSERT OR IGNORE INTO stocks (ticker, name, market, sector, is_etf_holding, lastUpdated)
            SELECT DISTINCT
                h.stockTicker as ticker,
                h.stockName as name,
                CASE
                    WHEN h.stockTicker LIKE '0%' OR h.stockTicker LIKE '1%'
                         OR h.stockTicker LIKE '2%' OR h.stockTicker LIKE '3%' THEN 'KOSPI'
                    ELSE 'KOSDAQ'
                END as market,
                '' as sector,
                1 as is_etf_holding,
                strftime('%s', 'now') * 1000 as lastUpdated
            FROM holdings h
            WHERE h.stockTicker NOT IN (SELECT ticker FROM stocks)
            """.trimIndent()
        )

        // 7. 기존 stocks 데이터 중 holdings에 있는 것은 is_etf_holding = 1로 업데이트
        database.execSQL(
            """
            UPDATE stocks SET is_etf_holding = 1
            WHERE ticker IN (SELECT DISTINCT stockTicker FROM holdings)
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 13 to 14: Add Advanced Analysis tables
 * - SectorAnalysis: 섹터별 Fear & Greed 분석
 * - EtfCorrelationCache: ETF 간 상관관계 캐시
 * - LiquidityAnalysis: 시장 유동성 분석
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. SectorAnalysis 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sector_analysis (
                id TEXT PRIMARY KEY NOT NULL,
                sector TEXT NOT NULL,
                sectorName TEXT NOT NULL,
                date TEXT NOT NULL,
                fearGreedValue REAL NOT NULL,
                etfFlowScore REAL NOT NULL,
                momentumScore REAL NOT NULL,
                volatilityScore REAL NOT NULL,
                stockCount INTEGER NOT NULL,
                newEntries INTEGER NOT NULL,
                removals INTEGER NOT NULL,
                avgWeightChange REAL NOT NULL,
                sentiment TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sector_analysis_date ON sector_analysis(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sector_analysis_sector ON sector_analysis(sector)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_sector_analysis_sector_date ON sector_analysis(sector, date)")

        // 2. EtfCorrelationCache 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS etf_correlation_cache (
                id TEXT PRIMARY KEY NOT NULL,
                etf1Ticker TEXT NOT NULL,
                etf1Name TEXT NOT NULL,
                etf2Ticker TEXT NOT NULL,
                etf2Name TEXT NOT NULL,
                date TEXT NOT NULL,
                overlapRatio REAL NOT NULL,
                weightCorrelation REAL NOT NULL,
                commonStockCount INTEGER NOT NULL,
                etf1StockCount INTEGER NOT NULL,
                etf2StockCount INTEGER NOT NULL,
                topCommonStocks TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_etf_correlation_cache_date ON etf_correlation_cache(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_etf_correlation_cache_etf1_etf2 ON etf_correlation_cache(etf1Ticker, etf2Ticker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_etf_correlation_cache_etf1 ON etf_correlation_cache(etf1Ticker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_etf_correlation_cache_etf2 ON etf_correlation_cache(etf2Ticker)")

        // 3. LiquidityAnalysis 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS liquidity_analysis (
                date TEXT PRIMARY KEY NOT NULL,
                depositAmount REAL NOT NULL,
                creditAmount REAL NOT NULL,
                totalMarketCap INTEGER NOT NULL,
                kospiMarketCap INTEGER NOT NULL,
                kosdaqMarketCap INTEGER NOT NULL,
                depositToMarketCapRatio REAL NOT NULL,
                creditToDepositRatio REAL NOT NULL,
                depositChange REAL NOT NULL,
                creditChange REAL NOT NULL,
                riskLevel TEXT NOT NULL,
                signal TEXT NOT NULL,
                historicalPercentile REAL NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * Migration from version 14 to 15: Add Enhanced ML Prediction tables
 * - PriceCache: ML 예측용 가격 캐시
 * - EnhancedPrediction: 28개 Feature 기반 향상된 예측 결과
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. PriceCache 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS price_cache (
                ticker TEXT NOT NULL,
                date TEXT NOT NULL,
                closePrice REAL NOT NULL,
                priceChange5d REAL,
                priceChange10d REAL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY (ticker, date)
            )
            """.trimIndent()
        )

        // PriceCache 인덱스
        database.execSQL("CREATE INDEX IF NOT EXISTS index_price_cache_date ON price_cache(date)")

        // 2. EnhancedPrediction 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS enhanced_predictions (
                id TEXT PRIMARY KEY NOT NULL,
                ticker TEXT NOT NULL,
                name TEXT NOT NULL,
                predictionDate TEXT NOT NULL,
                confidence REAL NOT NULL,
                status TEXT NOT NULL,
                keyFactors TEXT NOT NULL,
                riskScore REAL NOT NULL,
                featureValues TEXT NOT NULL,
                modelType TEXT NOT NULL,
                daysAfter INTEGER NOT NULL DEFAULT 5,
                priceThreshold REAL NOT NULL DEFAULT 3.0,
                actualPriceChange REAL,
                wasCorrect INTEGER,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // EnhancedPrediction 인덱스
        database.execSQL("CREATE INDEX IF NOT EXISTS index_enhanced_predictions_date ON enhanced_predictions(predictionDate DESC)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_enhanced_predictions_ticker ON enhanced_predictions(ticker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_enhanced_predictions_confidence ON enhanced_predictions(confidence DESC)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_enhanced_predictions_status ON enhanced_predictions(status)")
    }
}

/**
 * Migration from version 15 to 16: Add StockIndicatorAIResult table
 * 종목-지표 상관관계 AI 분석 결과 저장 테이블 추가
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // StockIndicatorAIResult 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_indicator_ai_result (
                id TEXT PRIMARY KEY NOT NULL,
                ticker TEXT NOT NULL,
                stockName TEXT NOT NULL,
                market TEXT NOT NULL,
                analysisDate TEXT NOT NULL,
                period TEXT NOT NULL,
                periodDays INTEGER NOT NULL,
                aiProvider TEXT NOT NULL,
                aiModel TEXT NOT NULL,
                signal TEXT NOT NULL,
                confidence REAL NOT NULL,
                upProbability REAL NOT NULL,
                downProbability REAL NOT NULL,
                riskLevel TEXT NOT NULL,
                keyCorrelations TEXT NOT NULL,
                marketSentimentImpact TEXT NOT NULL,
                fundFlowImpact TEXT NOT NULL,
                etfFlowImpact TEXT NOT NULL,
                reasoning TEXT NOT NULL,
                recommendation TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_indicator_ai_result_ticker ON stock_indicator_ai_result(ticker)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_indicator_ai_result_ticker_date ON stock_indicator_ai_result(ticker, analysisDate)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_indicator_ai_result_createdAt ON stock_indicator_ai_result(createdAt)")
    }
}

/**
 * Migration from version 16 to 17: Add historyType to SearchHistory
 * 검색 히스토리 유형 필드 추가 (메뉴별 분리 저장)
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. historyType 컬럼 추가 (기본값: STATISTICS)
        database.execSQL(
            "ALTER TABLE search_history ADD COLUMN historyType TEXT NOT NULL DEFAULT 'STATISTICS'"
        )

        // 2. 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_historyType ON search_history(historyType)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_historyType_searchedAt ON search_history(historyType, searchedAt)")
    }
}

/**
 * Migration from version 17 to 18: Add BloodIndicator table
 * US Treasury 기반 시장 건강도 지표 (BLOOD = IRX / (HYG Yield - 10Y Treasury))
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. blood_indicator 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS blood_indicator (
                id TEXT PRIMARY KEY NOT NULL,
                date TEXT NOT NULL,
                bloodValue REAL NOT NULL,
                irx REAL NOT NULL,
                hygYield REAL NOT NULL,
                tenYearYield REAL NOT NULL,
                spreadValue REAL NOT NULL,
                spyClose REAL,
                signalType TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 2. 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_blood_indicator_date ON blood_indicator(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_blood_indicator_signalType ON blood_indicator(signalType)")
    }
}

/**
 * Migration from version 18 to 19: Update BloodIndicator table for v2.0 FRED API
 * - Remove: irx, hygYield, tenYearYield, spreadValue
 * - Add: bloodSma (100-week SMA), us03my (3M T-Bill), highYieldSpread (FRED), signalColor
 *
 * BLOOD = US03MY / BAMLH0A0HYM2 (High Yield Spread from FRED)
 * - Above 100-week SMA = Risk On (Green)
 * - Below 100-week SMA = Risk Off (Red)
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 기존 테이블 삭제 (데이터는 다시 수집 가능)
        database.execSQL("DROP TABLE IF EXISTS blood_indicator")

        // 2. 새 스키마로 테이블 생성
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS blood_indicator (
                id TEXT PRIMARY KEY NOT NULL,
                date TEXT NOT NULL,
                bloodValue REAL NOT NULL,
                bloodSma REAL NOT NULL,
                us03my REAL NOT NULL,
                highYieldSpread REAL NOT NULL,
                spyClose REAL,
                signalType TEXT NOT NULL,
                signalColor TEXT NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // 3. 인덱스 생성
        database.execSQL("CREATE INDEX IF NOT EXISTS index_blood_indicator_date ON blood_indicator(date)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_blood_indicator_signalType ON blood_indicator(signalType)")
    }
}

/**
 * Migration from version 19 to 20: Add FinancialCache table
 * KIS API 재무정보 캐시 테이블 추가 (24h TTL)
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS financial_cache (
                ticker TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                data TEXT NOT NULL,
                cachedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
