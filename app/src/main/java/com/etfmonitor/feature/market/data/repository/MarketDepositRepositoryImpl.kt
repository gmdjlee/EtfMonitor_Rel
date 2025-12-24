package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.feature.market.data.datasource.MarketDepositLocalDataSource
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomainDeposit
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toTrend
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositTrend
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 증시 자금 Repository 구현
 *
 * ⚠️ CRITICAL: 12시간 캐싱 로직 유지
 * - shouldUpdateMarketData() 메서드로 캐시 만료 확인
 * - DATA_EXPIRY_HOURS = 12
 */
@Singleton
class MarketDepositRepositoryImpl @Inject constructor(
    private val localDataSource: MarketDepositLocalDataSource,
    private val pyClient: OscillatorPyClient
) : MarketDepositRepository {

    companion object {
        private val logger = AppLogger.getLogger("MarketDepositRepoImpl")
        private const val DATA_EXPIRY_HOURS = 12 // ⚠️ 12시간 캐싱
    }

    override fun getAllDeposits(): Flow<List<MarketDeposit>> =
        localDataSource.getAllDeposits().map { it.toDomainDeposit() }

    override fun getRecentDeposits(limit: Int): Flow<List<MarketDeposit>> =
        localDataSource.getRecentDeposits(limit).map { it.toDomainDeposit() }

    override suspend fun getDepositByDate(date: String): MarketDeposit? =
        localDataSource.getDepositByDate(date)?.toDomain()

    override suspend fun getDepositCount(): Int =
        localDataSource.getCount()

    override suspend fun getLastUpdateTime(): Long? =
        localDataSource.getLastUpdateTime()

    override suspend fun initializeDeposits(
        numPages: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing market deposit data from Python...")
            onProgress?.invoke("증시 자금 동향 데이터 수집 준비 중...", 0)

            onProgress?.invoke("증시 자금 동향 데이터 수집 중...", 30)
            val marketData = try {
                pyClient.getMarketDepositData(numPages)
            } catch (e: Exception) {
                logger.e("Python call failed", e)
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: ${e.message}", e))
            }

            if (marketData == null) {
                logger.e("Failed to get market deposit data from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: null 반환"))
            }

            onProgress?.invoke("데이터 처리 중...", 70)

            val deposits = marketData.dates.mapIndexed { index, date ->
                MarketDepositEntity(
                    date = date,
                    depositAmount = marketData.depositAmounts[index],
                    depositChange = marketData.depositChanges[index],
                    creditAmount = marketData.creditAmounts[index],
                    creditChange = marketData.creditChanges[index],
                    lastUpdated = System.currentTimeMillis()
                )
            }

            if (deposits.isEmpty()) {
                logger.e("No deposit data to save")
                return@withContext Result.failure(Exception("데이터가 비어있습니다"))
            }

            onProgress?.invoke("데이터베이스 저장 중...", 90)
            localDataSource.deleteAll()
            localDataSource.insertAll(deposits)

            logger.d("Successfully initialized ${deposits.size} market deposit records")
            onProgress?.invoke("완료", 100)
            Result.success(deposits.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Initialization cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error initializing market deposits", e)
            Result.failure(e)
        }
    }

    override suspend fun updateDeposits(numPages: Int): Result<Int> =
        initializeDeposits(numPages) // 전체 갱신

    /**
     * 증시 자금 데이터 가져오기 (스마트 업데이트)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 업데이트
     *
     * ⚠️ CRITICAL: 12시간 캐싱 로직 유지
     */
    override suspend fun getOrUpdateMarketData(limit: Int): MarketDepositTrend? = withContext(Dispatchers.IO) {
        try {
            val existingDeposits = localDataSource.getRecentDepositsSuspend(limit)

            val today = DateFormatter.formatToday()
            val shouldUpdate = shouldUpdateMarketData(existingDeposits, today)

            if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                logger.d("Using cached market deposit data (${existingDeposits.size} records)")
                return@withContext existingDeposits.toDomainDeposit().toTrend()
            }

            logger.d("Fetching latest market deposit data from Python...")
            val latestMarketData = try {
                pyClient.getLatestMarketData()
            } catch (e: Exception) {
                logger.e("Python call failed", e)
                return@withContext if (existingDeposits.isNotEmpty()) {
                    logger.d("Returning cached market data due to Python error")
                    existingDeposits.toDomainDeposit().toTrend()
                } else {
                    null
                }
            }

            if (latestMarketData == null) {
                logger.e("Failed to fetch latest market data from Python")
                return@withContext if (existingDeposits.isNotEmpty()) {
                    logger.d("Returning stale cached market data")
                    existingDeposits.toDomainDeposit().toTrend()
                } else {
                    null
                }
            }

            val newDeposits = latestMarketData.dates.mapIndexed { index, date ->
                MarketDepositEntity(
                    date = date,
                    depositAmount = latestMarketData.depositAmounts[index],
                    depositChange = latestMarketData.depositChanges[index],
                    creditAmount = latestMarketData.creditAmounts[index],
                    creditChange = latestMarketData.creditChanges[index],
                    lastUpdated = System.currentTimeMillis()
                )
            }

            localDataSource.insertAll(newDeposits)
            logger.d("Saved ${newDeposits.size} new market deposit records to DB")

            val updatedDeposits = localDataSource.getRecentDepositsSuspend(limit)
            updatedDeposits.toDomainDeposit().toTrend()
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Market data fetch cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error getting or updating market data", e)
            val existingDeposits = localDataSource.getRecentDepositsSuspend(limit)
            if (existingDeposits.isNotEmpty()) {
                existingDeposits.toDomainDeposit().toTrend()
            } else {
                null
            }
        }
    }

    /**
     * 마켓 데이터 업데이트가 필요한지 확인
     *
     * ⚠️ CRITICAL: 12시간 캐싱 로직
     */
    private fun shouldUpdateMarketData(deposits: List<MarketDepositEntity>, today: String): Boolean {
        if (deposits.isEmpty()) {
            logger.d("No cached data, update needed")
            return true
        }

        // 1. 마지막 업데이트 시간 확인
        val lastUpdate = deposits.maxOfOrNull { it.lastUpdated } ?: 0L
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)

        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) {
            logger.d("Data expired (${hoursSinceUpdate}h old), update needed")
            return true
        }

        // 2. 최신 날짜가 오늘이 아니면 업데이트 필요
        val latestDate = deposits.maxOfOrNull { it.date } ?: ""
        if (latestDate != today) {
            logger.d("Latest date ($latestDate) != today ($today), update needed")
            return true
        }

        logger.d("Data is fresh, no update needed")
        return false
    }
}
