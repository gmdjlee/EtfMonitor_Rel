package com.etfmonitor.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.AIChatSession
import com.etfmonitor.database.entities.AIAnalysisResult
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import com.etfmonitor.database.entities.DailyEtfStatistics
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.FearGreedIndex
import com.etfmonitor.database.entities.Holding
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.database.entities.MarketIndex
import com.etfmonitor.database.entities.MarketOscillatorData
import com.etfmonitor.database.entities.SearchHistory
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.database.entities.Stock
import com.etfmonitor.database.entities.StockAnalysisData

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
        AIChatMessage::class
    ],
    version = 11,
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