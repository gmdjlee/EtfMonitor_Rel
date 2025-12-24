package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.feature.stock.data.datasource.StockLocalDataSource
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toDomain
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.database.entities.Stock as StockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Repository Implementation
 *
 * 종목 마스터 데이터 관리를 담당합니다.
 *
 * ## 주요 기능
 * - 전체 종목 조회/검색
 * - ETF 보유 종목 자동 동기화
 * - 종목 데이터 초기화 (Python에서 가져오기)
 *
 * ## 스레드 안전성
 * - 모든 Flow는 flowOn(Dispatchers.IO)로 실행
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 실행
 */
@Singleton
class StockRepositoryImpl @Inject constructor(
    private val localDataSource: StockLocalDataSource,
    private val pyClient: OscillatorPyClient
) : StockRepository {

    companion object {
        private val logger = AppLogger.getLogger("StockRepositoryImpl")
    }

    /**
     * 네트워크 오류를 나타내는 예외
     */
    class NetworkException(message: String) : Exception(message)

    // ========== 조회 ==========

    override fun getAllStocks(): Flow<List<Stock>> =
        localDataSource.getAllStocks()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override fun searchStocks(query: String): Flow<List<Stock>> =
        localDataSource.searchStocks(query)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override fun getEtfHoldingStocks(): Flow<List<Stock>> =
        localDataSource.getEtfHoldingStocks()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override fun getStocksByMarket(market: String): Flow<List<Stock>> =
        localDataSource.getStocksByMarket(market)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getStock(ticker: String): Stock? = withContext(Dispatchers.IO) {
        localDataSource.getStock(ticker)?.toDomain()
    }

    override suspend fun getStockName(ticker: String): String = withContext(Dispatchers.IO) {
        localDataSource.getStockName(ticker) ?: ticker
    }

    override suspend fun getStockCount(): Int = withContext(Dispatchers.IO) {
        localDataSource.getCount()
    }

    override suspend fun getEtfHoldingCount(): Int = withContext(Dispatchers.IO) {
        localDataSource.getEtfHoldingCount()
    }

    override suspend fun getLastUpdateTime(): Long? = withContext(Dispatchers.IO) {
        localDataSource.getLastUpdateTime()
    }

    // ========== ETF 보유 종목 동기화 ==========

    override suspend fun syncFromHolding(ticker: String, name: String) = withContext(Dispatchers.IO) {
        try {
            val market = Stock.inferMarket(ticker)
            localDataSource.upsertFromHolding(ticker, name, market, System.currentTimeMillis())
        } catch (e: Exception) {
            logger.e("Failed to sync stock: $ticker", e)
        }
    }

    override suspend fun syncFromHoldings(holdings: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        try {
            if (holdings.isEmpty()) return@withContext
            localDataSource.syncFromHoldings(holdings)
            logger.d("Synced ${holdings.size} stocks from holdings")
        } catch (e: Exception) {
            logger.e("Failed to sync stocks from holdings", e)
        }
    }

    // ========== 전체 종목 초기화 ==========

    override suspend fun initializeStocks(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing stock data from Python...")

            val stockList = pyClient.getAllStocksList()

            if (stockList.isEmpty()) {
                logger.e("Failed to get stocks list from Python (empty result - possible network issue)")
                return@withContext Result.failure(
                    NetworkException("종목 데이터를 가져올 수 없습니다. 네트워크 연결을 확인해 주세요.")
                )
            }

            val stocks = stockList.map { (ticker, name) ->
                StockEntity(
                    ticker = ticker,
                    name = name,
                    market = Stock.inferMarket(ticker),
                    lastUpdated = System.currentTimeMillis()
                )
            }

            localDataSource.deleteAll()
            localDataSource.insertAll(stocks)

            logger.d("Successfully initialized ${stocks.size} stocks")
            Result.success(stocks.size)
        } catch (e: Exception) {
            logger.e("Error initializing stocks", e)
            Result.failure(e)
        }
    }

    override suspend fun updateStocks(): Result<Int> = initializeStocks()
}
