package com.etfmonitor.repository

import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.entities.MarketDeposit
import com.etfmonitor.oscillator.model.MarketDepositData
import com.etfmonitor.oscillator.python.OscillatorPyClient
import com.etfmonitor.utils.AppLogger
import com.etfmonitor.utils.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 증시 자금 동향 Repository
 *
 * Production 최적화:
 * - @Singleton: Hilt가 단일 인스턴스 관리
 * - @Inject: 생성자 주입으로 의존성 명확화
 * - flowOn(Dispatchers.IO): Flow 메서드에 명시적 디스패처 지정
 */
@Singleton
class MarketDepositRepository @Inject constructor(
    private val marketDepositDao: MarketDepositDao,
    private val pyClient: OscillatorPyClient
) {
    companion object {
        private val logger = AppLogger.getLogger("MarketDepositRepo")
        private const val DATA_EXPIRY_HOURS = 12 // 12시간 후 데이터 만료
    }

    fun getAllDeposits(): Flow<List<MarketDeposit>> =
        marketDepositDao.getAllDeposits().flowOn(Dispatchers.IO)

    fun getRecentDeposits(limit: Int = 100): Flow<List<MarketDeposit>> =
        marketDepositDao.getRecentDeposits(limit).flowOn(Dispatchers.IO)

    suspend fun getDepositByDate(date: String): MarketDeposit? =
        marketDepositDao.getDepositByDate(date)

    suspend fun getDepositCount(): Int = marketDepositDao.getCount()

    suspend fun getLastUpdateTime(): Long? = marketDepositDao.getLastUpdateTime()

    /**
     * 증시 자금 데이터 초기화 (Python에서 가져와서 DB에 저장)
     */
    suspend fun initializeDeposits(
        numPages: Int = 10,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            logger.d( "Initializing market deposit data from Python...")
            onProgress?.invoke("증시 자금 동향 데이터 수집 준비 중...", 0)

            // Python에서 증시 자금 데이터 가져오기
            onProgress?.invoke("증시 자금 동향 데이터 수집 중...", 30)
            val marketData = try {
                pyClient.getMarketDepositData(numPages)
            } catch (e: Exception) {
                logger.e( "Python call failed", e)
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: ${e.message}", e))
            }

            if (marketData == null) {
                logger.e( "Failed to get market deposit data from Python")
                return@withContext Result.failure(Exception("Python 모듈 호출 실패: null 반환"))
            }

            onProgress?.invoke("데이터 처리 중...", 70)

            // MarketDepositData를 MarketDeposit 엔티티 리스트로 변환
            val deposits = marketData.dates.mapIndexed { index, date ->
                MarketDeposit(
                    date = date,
                    depositAmount = marketData.depositAmounts[index],
                    depositChange = marketData.depositChanges[index],
                    creditAmount = marketData.creditAmounts[index],
                    creditChange = marketData.creditChanges[index],
                    lastUpdated = System.currentTimeMillis()
                )
            }

            if (deposits.isEmpty()) {
                logger.e( "No deposit data to save")
                return@withContext Result.failure(Exception("데이터가 비어있습니다"))
            }

            // DB에 일괄 저장
            onProgress?.invoke("데이터베이스 저장 중...", 90)
            marketDepositDao.deleteAll()
            marketDepositDao.insertAll(deposits)

            logger.d( "Successfully initialized ${deposits.size} market deposit records")
            onProgress?.invoke("완료", 100)
            Result.success(deposits.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Initialization cancelled")
            throw e // CancellationException은 다시 던져야 함
        } catch (e: Exception) {
            logger.e( "Error initializing market deposits", e)
            Result.failure(e)
        }
    }

    /**
     * 증시 자금 데이터 업데이트
     */
    suspend fun updateDeposits(numPages: Int = 10): Result<Int> {
        return initializeDeposits(numPages) // 전체 갱신
    }

    /**
     * DB에서만 증시 자금 데이터 가져오기 (업데이트 없음)
     * 화면 진입 시 사용 - 자동 업데이트 없이 캐시된 데이터만 반환
     */
    suspend fun getMarketDataFromDB(limit: Int = 100): MarketDepositData? = withContext(Dispatchers.IO) {
        try {
            val deposits = marketDepositDao.getRecentDeposits(limit).first()
            if (deposits.isEmpty()) {
                logger.d("No cached market deposit data")
                return@withContext null
            }
            logger.d("Loaded ${deposits.size} market deposit records from DB")
            convertToMarketDepositData(deposits)
        } catch (e: Exception) {
            logger.e("Error loading market data from DB", e)
            null
        }
    }

    /**
     * 증시 자금 데이터 가져오기 (스마트 업데이트)
     * DB에 데이터가 있고 최신이면 DB에서, 없거나 오래되면 업데이트
     * @deprecated 설정에서만 업데이트하도록 변경됨. getMarketDataFromDB() 사용 권장
     */
    @Deprecated("Use getMarketDataFromDB() instead. Updates should only be triggered from Settings.")
    suspend fun getOrUpdateMarketData(limit: Int = 100): MarketDepositData? = withContext(Dispatchers.IO) {
        try {
            // 1. DB에서 기존 데이터 확인
            val existingDeposits = marketDepositDao.getRecentDeposits(limit).first()

            val today = DateFormatter.formatToday()
            val shouldUpdate = shouldUpdateMarketData(existingDeposits, today)

            if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                logger.d( "Using cached market deposit data (${existingDeposits.size} records)")
                return@withContext convertToMarketDepositData(existingDeposits)
            }

            // 2. 업데이트 필요 - 최신 데이터만 가져오기
            logger.d( "Fetching latest market deposit data from Python...")
            val latestMarketData = try {
                pyClient.getLatestMarketData()
            } catch (e: Exception) {
                logger.e( "Python call failed", e)
                // Python 실패 시 캐시된 데이터라도 반환
                return@withContext if (existingDeposits.isNotEmpty()) {
                    logger.d( "Returning cached market data due to Python error")
                    convertToMarketDepositData(existingDeposits)
                } else {
                    null
                }
            }

            if (latestMarketData == null) {
                logger.e( "Failed to fetch latest market data from Python")
                // Python 실패 시 캐시된 데이터라도 반환
                return@withContext if (existingDeposits.isNotEmpty()) {
                    logger.d( "Returning stale cached market data")
                    convertToMarketDepositData(existingDeposits)
                } else {
                    null
                }
            }

            // 3. 새 데이터를 DB에 저장 (기존 데이터 유지하면서 병합)
            val newDeposits = latestMarketData.dates.mapIndexed { index, date ->
                MarketDeposit(
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
            logger.d( "Saved ${newDeposits.size} new market deposit records to DB")

            // 4. 업데이트된 전체 데이터 반환
            val updatedDeposits = marketDepositDao.getRecentDeposits(limit).first()
            convertToMarketDepositData(updatedDeposits)
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.w("Market data fetch cancelled")
            throw e // CancellationException은 다시 던져야 함
        } catch (e: Exception) {
            logger.e( "Error getting or updating market data", e)
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
    private fun shouldUpdateMarketData(deposits: List<MarketDeposit>, today: String): Boolean {
        if (deposits.isEmpty()) {
            logger.d( "No cached data, update needed")
            return true // 데이터가 없으면 업데이트 필요
        }

        // 1. 마지막 업데이트 시간 확인
        val lastUpdate = deposits.maxOfOrNull { it.lastUpdated } ?: 0L
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)

        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) {
            logger.d( "Data expired (${hoursSinceUpdate}h old), update needed")
            return true
        }

        // 2. 최신 날짜가 오늘이 아니면 업데이트 필요
        val latestDate = deposits.maxOfOrNull { it.date } ?: ""
        if (latestDate != today) {
            logger.d( "Latest date ($latestDate) != today ($today), update needed")
            return true
        }

        logger.d( "Data is fresh, no update needed")
        return false
    }

    /**
     * MarketDeposit 리스트를 MarketDepositData로 변환
     */
    private fun convertToMarketDepositData(deposits: List<MarketDeposit>): MarketDepositData {
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
