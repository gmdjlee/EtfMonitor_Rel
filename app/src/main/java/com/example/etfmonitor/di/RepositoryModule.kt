package com.etfmonitor.di

import com.chaquo.python.Python
import com.etfmonitor.database.*
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.python.MarketIndexPyClient
import com.etfmonitor.python.PyKrxClient
import com.etfmonitor.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 모듈: Repository 제공
 *
 * 최적화 포인트:
 * - 모든 Repository를 Singleton으로 관리
 * - DAO와 Python Client 의존성을 명시적으로 주입
 * - 수동 싱글톤 패턴 제거로 메모리 누수 위험 감소
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    // PyKrxClient는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    /**
     * DataRepository 제공 (Singleton)
     * ETF 데이터 수집 및 관리를 담당
     *
     * Production 최적화:
     * - Flow 반환 메서드들은 내부적으로 Dispatchers.IO 사용
     * - Coroutine 기반 병렬 처리로 성능 최적화
     */
    @Provides
    @Singleton
    fun provideDataRepository(
        etfDao: EtfDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao,
        pyKrxClient: PyKrxClient
    ): DataRepository {
        return DataRepository(etfDao, dailyEtfStatisticsDao, pyKrxClient)
    }

    // StockRepository는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    /**
     * StockAnalysisRepository 제공 (Singleton)
     * 주식 분석 데이터를 관리
     */
    @Provides
    @Singleton
    fun provideStockAnalysisRepository(
        stockAnalysisDao: StockAnalysisDao,
        python: Python
    ): StockAnalysisRepository {
        return StockAnalysisRepository(stockAnalysisDao, python)
    }

    // MarketDepositRepository는 @Inject constructor를 사용하므로 수동 제공 불필요
    // Hilt가 자동으로 의존성을 주입합니다

    /**
     * FearGreedRepository 제공 (Singleton)
     * 공포 탐욕 지수 데이터를 관리
     */
    @Provides
    @Singleton
    fun provideFearGreedRepository(
        fearGreedDao: FearGreedDao,
        python: Python
    ): FearGreedRepository {
        return FearGreedRepository(fearGreedDao, python)
    }

    /**
     * MarketOscillatorRepository 제공 (Singleton)
     * 과매수/과매도 데이터를 관리
     */
    @Provides
    @Singleton
    fun provideMarketOscillatorRepository(
        marketOscillatorDao: MarketOscillatorDao,
        oscillatorPyClient: OscillatorPyClient
    ): MarketOscillatorRepository {
        return MarketOscillatorRepository(marketOscillatorDao, oscillatorPyClient)
    }

    /**
     * MarketIndexRepository 제공 (Singleton)
     * 시장 지수 데이터를 관리
     */
    @Provides
    @Singleton
    fun provideMarketIndexRepository(
        marketIndexDao: MarketIndexDao,
        marketIndexPyClient: MarketIndexPyClient
    ): MarketIndexRepository {
        return MarketIndexRepository(marketIndexDao, marketIndexPyClient)
    }

    /**
     * StatisticsAnalysisRepository 제공 (Singleton)
     * ETF 통계 분석 및 상관관계 계산
     */
    @Provides
    @Singleton
    fun provideStatisticsAnalysisRepository(
        etfDao: EtfDao,
        marketIndexDao: MarketIndexDao,
        dailyEtfStatisticsDao: DailyEtfStatisticsDao
    ): StatisticsAnalysisRepository {
        return StatisticsAnalysisRepository(etfDao, marketIndexDao, dailyEtfStatisticsDao)
    }

    /**
     * StockPredictionRepository 제공 (Singleton)
     * ML 기반 주가 예측 및 결과 관리
     */
    @Provides
    @Singleton
    fun provideStockPredictionRepository(
        predictionDao: StockPredictionDao,
        etfDao: EtfDao,
        predictorClient: com.etfmonitor.python.StockPredictorPyClient
    ): StockPredictionRepository {
        return StockPredictionRepository(predictionDao, etfDao, predictorClient)
    }
}
