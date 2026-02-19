package com.etfmonitor.feature.stock.di

import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.StockDao
import com.etfmonitor.core.data.repository.krx.KrxStockDataRepositoryImpl
import com.etfmonitor.core.domain.repository.StockDataRepository
import com.etfmonitor.core.domain.usecase.krx.GetDemarkTDDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetElderImpulseDataUseCase
import com.etfmonitor.core.domain.usecase.krx.GetStockOhlcvUseCase
import com.etfmonitor.core.domain.usecase.krx.GetTrendSignalDataUseCase
import com.etfmonitor.feature.stock.data.datasource.StockAnalysisLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import com.etfmonitor.feature.stock.data.repository.StockAnalysisRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockStatisticsRepositoryImpl
import com.etfmonitor.feature.stock.data.repository.StockTrendRepositoryImpl
import com.etfmonitor.feature.stock.domain.repository.StockAnalysisRepository
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.feature.stock.domain.repository.StockTrendRepository
import com.krxkt.KrxStock
import com.etfmonitor.feature.stock.domain.usecase.AnalyzeStockUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetCashDepositTrendUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStatisticsDatesUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockAnalysisUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockChangesUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockRankingUseCase
import com.etfmonitor.feature.stock.domain.usecase.GetStockTrendUseCase
import com.etfmonitor.feature.stock.domain.usecase.InitializeStocksUseCase
import com.etfmonitor.feature.stock.domain.usecase.SearchStocksUseCase
import com.etfmonitor.core.database.FinancialCacheDao
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.feature.stock.data.repository.financial.FinancialRepositoryImpl
import com.etfmonitor.feature.stock.domain.repository.FinancialRepository
import com.etfmonitor.feature.stock.domain.usecase.GetFinancialSummaryUseCase
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Stock Feature Hilt Module
 *
 * Stock Feature에 필요한 의존성을 제공합니다.
 *
 * ## T-012/T-013 MIGRATION (pykrx → kotlin_krx)
 * - Added StockDataRepository (kotlin_krx + TechnicalAnalysisEngine)
 * - Added 4 kotlin_krx UseCases
 * - Replaced OscillatorPyClient with StockDataRepository in StockRepository/StockAnalysisRepository
 *
 * ## 제공되는 의존성
 * - Data Sources: StockLocalDataSource, StockAnalysisLocalDataSource, StockStatisticsLocalDataSource
 * - Repositories: StockRepository, StockAnalysisRepository, StockTrendRepository, StockStatisticsRepository, StockDataRepository
 * - Use Cases: All stock-related use cases + 4 kotlin_krx technical analysis UseCases
 */
@Module
@InstallIn(SingletonComponent::class)
object StockModule {

    // ========== Data Sources ==========

    @Provides
    @Singleton
    fun provideStockLocalDataSource(
        stockDao: StockDao
    ): StockLocalDataSource = StockLocalDataSource(stockDao)

    @Provides
    @Singleton
    fun provideStockAnalysisLocalDataSource(
        stockAnalysisDao: StockAnalysisDao
    ): StockAnalysisLocalDataSource = StockAnalysisLocalDataSource(stockAnalysisDao)

    @Provides
    @Singleton
    fun provideStockStatisticsLocalDataSource(
        etfDao: EtfDao
    ): StockStatisticsLocalDataSource = StockStatisticsLocalDataSource(etfDao)

    // ========== Repositories ==========

    @Provides
    @Singleton
    fun provideStockDataRepository(
        krxStock: KrxStock,
        stockDao: StockDao
    ): StockDataRepository = KrxStockDataRepositoryImpl(krxStock, stockDao)

    @Provides
    @Singleton
    fun provideStockRepository(
        localDataSource: StockLocalDataSource,
        stockDataRepository: StockDataRepository
    ): StockRepository = StockRepositoryImpl(localDataSource, stockDataRepository)

    @Provides
    @Singleton
    fun provideStockAnalysisRepository(
        analysisLocalDataSource: StockAnalysisLocalDataSource,
        stockLocalDataSource: StockLocalDataSource,
        stockDataRepository: StockDataRepository
    ): StockAnalysisRepository = StockAnalysisRepositoryImpl(
        analysisLocalDataSource,
        stockLocalDataSource,
        stockDataRepository
    )

    @Provides
    @Singleton
    fun provideStockTrendRepository(
        localDataSource: StockStatisticsLocalDataSource
    ): StockTrendRepository = StockTrendRepositoryImpl(localDataSource)

    @Provides
    @Singleton
    fun provideStockStatisticsRepository(
        localDataSource: StockStatisticsLocalDataSource
    ): StockStatisticsRepository = StockStatisticsRepositoryImpl(localDataSource)

    // ========== Use Cases ==========

    @Provides
    @Singleton
    fun provideSearchStocksUseCase(
        repository: StockRepository
    ): SearchStocksUseCase = SearchStocksUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockTrendUseCase(
        repository: StockTrendRepository
    ): GetStockTrendUseCase = GetStockTrendUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockAnalysisUseCase(
        repository: StockAnalysisRepository
    ): GetStockAnalysisUseCase = GetStockAnalysisUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockRankingUseCase(
        repository: StockStatisticsRepository
    ): GetStockRankingUseCase = GetStockRankingUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStockChangesUseCase(
        repository: StockStatisticsRepository
    ): GetStockChangesUseCase = GetStockChangesUseCase(repository)

    @Provides
    @Singleton
    fun provideAnalyzeStockUseCase(
        repository: StockStatisticsRepository
    ): AnalyzeStockUseCase = AnalyzeStockUseCase(repository)

    @Provides
    @Singleton
    fun provideGetStatisticsDatesUseCase(
        repository: StockStatisticsRepository
    ): GetStatisticsDatesUseCase = GetStatisticsDatesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetCashDepositTrendUseCase(
        repository: StockStatisticsRepository
    ): GetCashDepositTrendUseCase = GetCashDepositTrendUseCase(repository)

    @Provides
    @Singleton
    fun provideInitializeStocksUseCase(
        repository: StockRepository
    ): InitializeStocksUseCase = InitializeStocksUseCase(repository)

    // ========== kotlin_krx Technical Analysis UseCases ==========

    @Provides
    @Singleton
    fun provideGetTrendSignalDataUseCase(
        stockDataRepository: StockDataRepository
    ): GetTrendSignalDataUseCase = GetTrendSignalDataUseCase(stockDataRepository)

    @Provides
    @Singleton
    fun provideGetElderImpulseDataUseCase(
        stockDataRepository: StockDataRepository
    ): GetElderImpulseDataUseCase = GetElderImpulseDataUseCase(stockDataRepository)

    @Provides
    @Singleton
    fun provideGetDemarkTDDataUseCase(
        stockDataRepository: StockDataRepository
    ): GetDemarkTDDataUseCase = GetDemarkTDDataUseCase(stockDataRepository)

    @Provides
    @Singleton
    fun provideGetStockOhlcvUseCase(
        stockDataRepository: StockDataRepository
    ): GetStockOhlcvUseCase = GetStockOhlcvUseCase(stockDataRepository)

    // ========== KIS API / Financial Info ==========

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class KisOkHttp

    @Provides
    @Singleton
    @KisOkHttp
    fun provideKisOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideFinancialJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideFinancialRepository(
        financialCacheDao: FinancialCacheDao,
        kisApiKeyProvider: KisApiKeyProvider,
        json: Json,
        @KisOkHttp httpClient: OkHttpClient
    ): FinancialRepository = FinancialRepositoryImpl(
        financialCacheDao, kisApiKeyProvider, json, httpClient
    )

    @Provides
    @Singleton
    fun provideGetFinancialSummaryUseCase(
        repository: FinancialRepository
    ): GetFinancialSummaryUseCase = GetFinancialSummaryUseCase(repository)
}
