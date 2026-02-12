package com.etfmonitor.feature.market.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.core.network.krx.FearGreedClient
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toFearGreedDomainList
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
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
 * Fear & Greed Repository Implementation
 *
 * Clean Architecture 패턴을 따르는 직접 구현:
 * - 3x 데이터 요청 로직 (분석 시 데이터 손실 보완)
 * - FearGreedClient를 사용한 네이티브 Kotlin 데이터 수집
 */
@Singleton
class FearGreedRepositoryImpl @Inject constructor(
    private val fearGreedDao: FearGreedDao,
    private val etfDao: EtfDao,
    private val fearGreedClient: FearGreedClient
) : FearGreedRepository {

    companion object {
        private val logger = AppLogger.getLogger("FearGreedRepoImpl")
        private const val DATA_EXPIRY_HOURS = 12
        private const val KEY_DIALOG_DISMISSED = "fear_greed_dialog_dismissed"
    }

    override fun getAllByMarket(market: String): Flow<List<FearGreedIndex>> =
        fearGreedDao.getAllByMarket(market)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getRecentByMarket(market: String, limit: Int): Flow<List<FearGreedIndex>> =
        fearGreedDao.getRecentByMarket(market, limit)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override fun getByMarketAndDateRange(
        market: String,
        startDate: String,
        endDate: String
    ): Flow<List<FearGreedIndex>> =
        fearGreedDao.getByMarketAndDateRange(market, startDate, endDate)
            .map { it.toFearGreedDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getByMarketAndDate(market: String, date: String): FearGreedIndex? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getByMarketAndDate(market, date)?.toDomain()
        }

    override suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) {
            fearGreedDao.getCountByMarket(market)
        }

    override suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getLatestDate(market)
        }

    override suspend fun getLastUpdateTime(market: String): Long? =
        withContext(Dispatchers.IO) {
            fearGreedDao.getLastUpdateTime(market)
        }

    override suspend fun isDialogDismissed(): Boolean = withContext(Dispatchers.IO) {
        etfDao.getSetting(KEY_DIALOG_DISMISSED) == "true"
    }

    override suspend fun saveDialogDismissed() = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(KEY_DIALOG_DISMISSED, "true"))
    }

    /**
     * Fear & Greed Index 데이터 초기화 (지정된 기간 동안의 데이터 수집)
     * @param days 데이터 수집 기간 (기본 365일)
     *
     * 주의: 분석 과정에서 대량의 데이터 손실이 발생합니다:
     * - Call/Put 옵션 5일 이동평균: 5일 손실
     * - 필수 데이터(Call/Put/VIX/국채) 없는 날짜 제거: 대량 손실
     * - RSI 계산(10일 rolling): 10일 손실
     * - MA 계산(125일 rolling): 125일 손실
     * - MACD 계산(26일 EMA): 26일 손실
     * 따라서 실제로는 약 3배의 데이터를 수집하여 원하는 기간만큼 남도록 합니다.
     * KRX API 제한으로 최대 730일(약 2년)까지만 수집합니다.
     */
    override suspend fun initializeFearGreed(
        days: Int,
        onProgress: ((String, Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 분석 과정의 데이터 손실을 고려하여 3배 수집, 최대 730일로 제한
            val collectionDays = minOf(days * 3, 730)
            logger.d("Initializing Fear & Greed Index data: requested=$days days, collecting=$collectionDays days (max 730)")

            onProgress?.invoke("Fear & Greed Index 데이터 수집 준비 중...", 0)

            // 날짜 범위 계산
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(collectionDays.toLong())

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            // FearGreedClient로 데이터 수집 및 분석
            onProgress?.invoke("시장 데이터 수집 중...", 20)
            val (kospiData, kosdaqData) = try {
                fearGreedClient.runAnalysis(startStr, endStr)
            } catch (e: Exception) {
                logger.e("FearGreedClient call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            val fearGreedData = kospiData + kosdaqData

            if (fearGreedData.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // DB에 저장
            onProgress?.invoke("데이터베이스 저장 중...", 90)
            fearGreedDao.deleteAll()
            fearGreedDao.insertAll(fearGreedData)

            logger.d("Successfully initialized ${fearGreedData.size} Fear & Greed records (KOSPI: ${kospiData.size}, KOSDAQ: ${kosdaqData.size})")
            onProgress?.invoke("완료", 100)
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Initialization cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error initializing Fear & Greed data", e)
            Result.failure(e)
        }
    }

    /**
     * Fear & Greed Index 데이터 업데이트 (최근 데이터만 갱신)
     *
     * 분석 과정의 데이터 손실을 고려하여 충분한 기간의 데이터를 수집합니다.
     */
    override suspend fun updateFearGreed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d("Updating Fear & Greed Index data...")

            // 최근 데이터 갱신 (데이터 손실 고려하여 150일 수집)
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(150)

            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val startStr = startDate.format(formatter)
            val endStr = endDate.format(formatter)

            // FearGreedClient로 데이터 수집 및 분석
            val (kospiData, kosdaqData) = try {
                fearGreedClient.runAnalysis(startStr, endStr)
            } catch (e: Exception) {
                logger.e("FearGreedClient call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            val fearGreedData = kospiData + kosdaqData

            if (fearGreedData.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // DB에 저장 (REPLACE 전략으로 중복 제거)
            fearGreedDao.insertAll(fearGreedData)

            logger.d("Successfully updated ${fearGreedData.size} Fear & Greed records")
            Result.success(fearGreedData.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Update cancelled")
            throw e
        } catch (e: Exception) {
            logger.e("Error updating Fear & Greed data", e)
            Result.failure(e)
        }
    }
}
