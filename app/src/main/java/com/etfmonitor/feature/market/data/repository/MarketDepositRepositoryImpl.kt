package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.MarketDepositDao
import com.etfmonitor.core.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.core.network.scraper.NaverFinanceScraper
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDepositDomainList
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Deposit Repository Implementation
 *
 * Clean Architecture 패턴을 따르는 직접 구현:
 * - 12시간 캐싱 전략
 * - 스마트 업데이트 (캐시 확인 후 필요시 Naver Finance 스크래핑)
 * - NaverFinanceScraper를 통한 Kotlin 네이티브 웹 스크래핑
 */
@Singleton
class MarketDepositRepositoryImpl @Inject constructor(
    private val marketDepositDao: MarketDepositDao,
    private val naverFinanceScraper: NaverFinanceScraper
) : MarketDepositRepository {

    companion object {
        private val logger = AppLogger.getLogger("MktDepRepoImpl")
        private const val DATA_EXPIRY_HOURS = 12
    }

    override fun getAllDeposits(): Flow<List<MarketDeposit>> =
        marketDepositDao.getAllDeposits()
            .map { it.toDepositDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecentDeposits(limit: Int): Flow<List<MarketDeposit>> =
        marketDepositDao.getRecentDeposits(limit)
            .map { it.toDepositDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>> =
        marketDepositDao.getByDateRange(startDate, endDate)
            .map { it.toDepositDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getDepositByDate(date: String): MarketDeposit? =
        withContext(Dispatchers.IO) {
            marketDepositDao.getDepositByDate(date)?.toDomain()
        }

    override suspend fun getDepositCount(): Int =
        withContext(Dispatchers.IO) {
            marketDepositDao.getCount()
        }

    override suspend fun getLastUpdateTime(): Long? =
        withContext(Dispatchers.IO) {
            marketDepositDao.getLastUpdateTime()
        }

    /**
     * 증시 자금 데이터 초기화 (Naver Finance에서 스크래핑하여 DB에 저장)
     */
    override suspend fun initializeDeposits(
        numPages: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Initializing market deposit data from Naver Finance...")
            onProgress?.invoke("증시 자금 동향 데이터 수집 준비 중...", 0)

            // Naver Finance에서 증시 자금 데이터 스크래핑
            onProgress?.invoke("증시 자금 동향 데이터 수집 중...", 30)
            val marketData = try {
                naverFinanceScraper.scrapeDepositData(numPages)
            } catch (e: Exception) {
                logger.e("Naver Finance scraping failed", e)
                return@withContext Result.failure(Exception("Naver Finance 스크래핑 실패: ${e.message}", e))
            }

            if (marketData == null) {
                logger.e("Failed to get market deposit data from Naver Finance")
                return@withContext Result.failure(Exception("Naver Finance 스크래핑 실패: null 반환"))
            }

            onProgress?.invoke("데이터 처리 중...", 70)

            // MarketDepositData를 MarketDeposit 엔티티 리스트로 변환
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

            // DB에 일괄 저장
            onProgress?.invoke("데이터베이스 저장 중...", 90)
            marketDepositDao.deleteAll()
            marketDepositDao.insertAll(deposits)

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

    /**
     * 증시 자금 데이터 업데이트
     */
    override suspend fun updateDeposits(numPages: Int): Result<Int> {
        return initializeDeposits(numPages) // 전체 갱신
    }

    /**
     * 증시 자금 데이터 가져오기 (스마트 업데이트)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 업데이트
     */
    override suspend fun getOrUpdateMarketData(limit: Int): MarketDepositData? =
        withContext(Dispatchers.IO) {
            try {
                // 1. DB에서 기존 데이터 확인
                val existingDeposits = marketDepositDao.getRecentDeposits(limit).first()

                val today = DateFormatter.formatToday()
                val shouldUpdate = shouldUpdateMarketData(existingDeposits, today)

                if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                    logger.d("Using cached market deposit data (${existingDeposits.size} records)")
                    return@withContext convertToMarketDepositData(existingDeposits)
                }

                // 2. 업데이트 필요 - 최신 데이터만 가져오기
                logger.d("Fetching latest market deposit data from Naver Finance...")
                val latestMarketData = try {
                    naverFinanceScraper.getLatestData()
                } catch (e: Exception) {
                    logger.e("Naver Finance scraping failed", e)
                    // 스크래핑 실패 시 캐시된 데이터라도 반환
                    return@withContext if (existingDeposits.isNotEmpty()) {
                        logger.d("Returning cached market data due to scraping error")
                        convertToMarketDepositData(existingDeposits)
                    } else {
                        null
                    }
                }

                if (latestMarketData == null) {
                    logger.e("Failed to fetch latest market data from Naver Finance")
                    // 스크래핑 실패 시 캐시된 데이터라도 반환
                    return@withContext if (existingDeposits.isNotEmpty()) {
                        logger.d("Returning stale cached market data")
                        convertToMarketDepositData(existingDeposits)
                    } else {
                        null
                    }
                }

                // 3. 새 데이터를 DB에 저장 (기존 데이터 유지하면서 병합)
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

                // DB에 저장 (REPLACE 전략으로 중복 제거)
                marketDepositDao.insertAll(newDeposits)
                logger.d("Saved ${newDeposits.size} new market deposit records to DB")

                // 4. 업데이트된 전체 데이터 반환
                val updatedDeposits = marketDepositDao.getRecentDeposits(limit).first()
                convertToMarketDepositData(updatedDeposits)
            } catch (e: kotlinx.coroutines.CancellationException) {
                logger.w("Market data fetch cancelled")
                throw e
            } catch (e: Exception) {
                logger.e("Error getting or updating market data", e)
                // 에러 시 DB에 데이터가 있으면 반환
                val existingDeposits = marketDepositDao.getRecentDeposits(limit).first()
                if (existingDeposits.isNotEmpty()) {
                    convertToMarketDepositData(existingDeposits)
                } else {
                    null
                }
            }
        }

    /**
     * 마켓 데이터 업데이트가 필요한지 확인
     */
    private fun shouldUpdateMarketData(
        deposits: List<MarketDepositEntity>,
        today: String
    ): Boolean {
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

    /**
     * MarketDeposit 엔티티 리스트를 MarketDepositData로 변환
     */
    private fun convertToMarketDepositData(deposits: List<MarketDepositEntity>): MarketDepositData {
        // 날짜순 정렬 (오래된 것부터)
        val sorted = deposits.sortedBy { it.date }

        return MarketDepositData(
            dates = sorted.map { it.date },
            depositAmounts = sorted.map { it.depositAmount },
            depositChanges = sorted.map { it.depositChange },
            creditAmounts = sorted.map { it.creditAmount },
            creditChanges = sorted.map { it.creditChange }
        )
    }
}
