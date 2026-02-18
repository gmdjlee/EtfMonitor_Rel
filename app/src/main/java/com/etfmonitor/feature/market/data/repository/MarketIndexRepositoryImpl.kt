package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.MarketIndexDao
import com.etfmonitor.core.domain.usecase.krx.GetKrxIndexDataUseCase
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toIndexDomainList
import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.repository.MarketIndexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Market Index Repository Implementation
 *
 * Clean Architecture 패턴을 따르는 직접 구현:
 * - KOSPI/KOSDAQ 시장 지수 데이터 관리
 * - kotlin_krx를 통한 데이터 수집 (GetKrxIndexDataUseCase)
 */
@Singleton
class MarketIndexRepositoryImpl @Inject constructor(
    private val dao: MarketIndexDao,
    private val getKrxIndexDataUseCase: GetKrxIndexDataUseCase
) : MarketIndexRepository {

    companion object {
        private val logger = AppLogger.getLogger("MktIdxRepoImpl")
    }

    override fun getAllByMarket(market: String): Flow<List<MarketIndex>> =
        dao.getAllByMarket(market)
            .map { it.toIndexDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<MarketIndex>> =
        dao.getRecentByMarket(market, limit)
            .map { it.toIndexDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<MarketIndex>> =
        dao.getByMarketAndDateRange(market, startDate, endDate)
            .map { it.toIndexDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getByMarketAndDate(market: String, date: String): MarketIndex? =
        withContext(Dispatchers.IO) {
            dao.getByMarketAndDate(market, date)?.toDomain()
        }

    override suspend fun getByDate(date: String): List<MarketIndex> =
        withContext(Dispatchers.IO) {
            dao.getByDate(date).toIndexDomainList()
        }

    override suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) {
            dao.getCountByMarket(market)
        }

    override suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) {
            dao.getLatestDate(market)
        }

    override suspend fun getLastUpdateTime(market: String): Long? =
        withContext(Dispatchers.IO) {
            dao.getLastUpdateTime(market)
        }

    override suspend fun hasData(market: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getCountByMarket(market) > 0
        }

    /**
     * 시장 지수 데이터 초기화
     * 지정된 일수만큼의 KOSPI/KOSDAQ 데이터를 수집하여 저장
     *
     * @param days 수집할 일수 (기본 30일)
     * @return 저장된 레코드 수
     */
    override suspend fun initializeMarketIndex(days: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                logger.d("Initializing market index data for $days days")

                // 날짜 범위 계산
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(days.toLong())

                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                val startStr = startDate.format(formatter)
                val endStr = endDate.format(formatter)

                // kotlin_krx로 데이터 수집
                val indicesResult = getKrxIndexDataUseCase(startStr, endStr)

                // Result 처리
                indicesResult.fold(
                    onSuccess = { indices ->
                        if (indices.isEmpty()) {
                            logger.e("No market index data fetched")
                            return@withContext Result.failure(Exception("시장 지수 데이터를 가져올 수 없습니다"))
                        }

                        // DB에 저장 (atomic replace)
                        dao.replaceAll(indices)

                        logger.d("Successfully initialized ${indices.size} market index records")
                        Result.success(indices.size)
                    },
                    onFailure = { e ->
                        logger.e("Error fetching market index data from kotlin_krx", e)
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                logger.e("Error initializing market index data", e)
                Result.failure(e)
            }
        }

    /**
     * 시장 지수 데이터 업데이트
     * 최근 데이터를 수집하여 갱신
     *
     * @param days 수집할 일수 (기본 30일)
     * @return 저장된 레코드 수
     */
    override suspend fun updateMarketIndex(days: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                logger.d("Updating market index data for recent $days days")

                // kotlin_krx로 최근 데이터 수집
                val indicesResult = getKrxIndexDataUseCase.getRecentDays(days)

                // Result 처리
                indicesResult.fold(
                    onSuccess = { indices ->
                        if (indices.isEmpty()) {
                            logger.e("No market index data fetched for update")
                            return@withContext Result.failure(Exception("시장 지수 데이터를 가져올 수 없습니다"))
                        }

                        // DB에 저장 (REPLACE 전략으로 중복 제거)
                        dao.insertAll(indices)

                        logger.d("Successfully updated ${indices.size} market index records")
                        Result.success(indices.size)
                    },
                    onFailure = { e ->
                        logger.e("Error fetching market index data from kotlin_krx", e)
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                logger.e("Error updating market index data", e)
                Result.failure(e)
            }
        }

    override suspend fun deleteByMarket(market: String) =
        withContext(Dispatchers.IO) {
            dao.deleteByMarket(market)
        }

    override suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
}
