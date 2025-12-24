package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.feature.stock.data.datasource.StockStatisticsLocalDataSource
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toRankingDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toChangeInfoDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toCashDepositDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toSearchResultDomain
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockAnalysisResult
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import com.etfmonitor.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Statistics Repository Implementation
 *
 * 종목 통계 데이터를 관리합니다.
 *
 * ## 주요 기능
 * - 종목 금액순위 조회
 * - 신규/제외/비중변화 종목 조회
 * - 종목 분석 (ETF별 보유 분석)
 * - 원화예금 추이 조회
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 */
@Singleton
class StockStatisticsRepositoryImpl @Inject constructor(
    private val localDataSource: StockStatisticsLocalDataSource
) : StockStatisticsRepository {

    companion object {
        private val logger = AppLogger.getLogger("StockStatisticsRepoImpl")
    }

    // ========== 통계 날짜 ==========

    override suspend fun getStatisticsDates(): Pair<String, String>? = withContext(Dispatchers.IO) {
        // 임의의 ETF에서 날짜 2개 가져오기 (모든 ETF 동일한 날짜 가정)
        val latestDate = localDataSource.getLatestDate() ?: return@withContext null

        // 전일 날짜를 찾기 위해 첫 번째 ETF 사용
        val etf = localDataSource.getEtf("069500") // KODEX 200
        if (etf == null) return@withContext null

        val dates = localDataSource.getDates(etf.ticker)
        if (dates.size < 2) return@withContext null

        Pair(dates[0], dates[1])
    }

    // ========== 금액순위 ==========

    override suspend fun getStockAmountRanking(): List<StockAmountRanking> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        localDataSource.getStockAmountRanking(dates.first, dates.second).toRankingDomain()
    }

    // ========== 종목 변화 ==========

    override suspend fun getAllNewStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        localDataSource.getAllNewStocks(dates.first, dates.second).toChangeInfoDomain()
    }

    override suspend fun getAllRemovedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        localDataSource.getAllRemovedStocks(dates.first, dates.second).toChangeInfoDomain()
    }

    override suspend fun getAllIncreasedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        localDataSource.getAllIncreasedStocks(dates.first, dates.second).toChangeInfoDomain()
    }

    override suspend fun getAllDecreasedStocks(): List<StockChangeInfo> = withContext(Dispatchers.IO) {
        val dates = getStatisticsDates() ?: return@withContext emptyList()
        localDataSource.getAllDecreasedStocks(dates.first, dates.second).toChangeInfoDomain()
    }

    // ========== 종목 분석 ==========

    override suspend fun searchStocks(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        localDataSource.searchStocks(query).toSearchResultDomain()
    }

    override suspend fun analyzeStock(stockTicker: String): StockAnalysisResult? = withContext(Dispatchers.IO) {
        localDataSource.analyzeStock(stockTicker)?.toDomain()
    }

    // ========== 원화예금 추이 ==========

    override suspend fun getCashDepositTrend(): List<CashDepositTrend> = withContext(Dispatchers.IO) {
        localDataSource.getCashDepositTrend().toCashDepositDomain()
    }
}
