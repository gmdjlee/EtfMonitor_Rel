package com.etfmonitor.feature.market.data.repository

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.FearGreedDao
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toFearGreedDomainList
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fear & Greed Repository Implementation
 *
 * Clean Architecture 패턴을 따르는 직접 구현:
 * - 3x 데이터 요청 로직 (Python 분석 시 데이터 손실 보완)
 * - Python feargreed 모듈을 사용한 데이터 수집
 */
@Singleton
class FearGreedRepositoryImpl @Inject constructor(
    private val fearGreedDao: FearGreedDao,
    private val etfDao: EtfDao,
    private val python: Python
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
     * 주의: Python 분석 과정에서 대량의 데이터 손실이 발생합니다:
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

            // Python에서 Fear & Greed 데이터 가져오기
            onProgress?.invoke("시장 데이터 수집 중...", 20)
            coroutineContext.ensureActive()  // 취소 상태 확인
            val fearGreedData = try {
                calculateFearGreed(startStr, endStr, onProgress)
            } catch (e: Exception) {
                logger.e("Python call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

            if (fearGreedData.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
                return@withContext Result.failure(Exception("계산된 데이터가 없습니다"))
            }

            // DB에 저장
            onProgress?.invoke("데이터베이스 저장 중...", 90)
            fearGreedDao.deleteAll()
            fearGreedDao.insertAll(fearGreedData)

            logger.d("Successfully initialized ${fearGreedData.size} Fear & Greed records")
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

            // Python에서 Fear & Greed 데이터 가져오기
            coroutineContext.ensureActive()  // 취소 상태 확인
            val fearGreedData = try {
                calculateFearGreed(startStr, endStr)
            } catch (e: Exception) {
                logger.e("Python call failed", e)
                return@withContext Result.failure(Exception("Fear & Greed 계산 실패: ${e.message}", e))
            }

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

    /**
     * Python 스크립트를 호출하여 Fear & Greed Index 계산
     */
    private suspend fun calculateFearGreed(
        startDate: String,
        endDate: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): List<FearGreedEntity> = withContext(Dispatchers.IO) {
        try {
            logger.d("Calculating Fear & Greed for period: $startDate ~ $endDate")
            val module = python.getModule("feargreed")

            // combine 함수 호출하여 데이터 수집
            onProgress?.invoke("원시 데이터 수집 중...", 30)
            val combineFunc = module["combine"]
            if (combineFunc == null) {
                logger.e("combine function not found in Python module")
                return@withContext emptyList()
            }

            val dfObject = withTimeout(60_000L) {
                combineFunc.call(startDate, endDate)
            }
            if (dfObject == null || dfObject.toString() == "None") {
                logger.e("Failed to get combined data from Python (returned None)")
                return@withContext emptyList()
            }

            logger.d("Combined data retrieved successfully")

            // analyze 함수 호출하여 분석
            onProgress?.invoke("Fear & Greed Index 분석 중...", 60)
            val analyzeFunc = module["analyze"]
            if (analyzeFunc == null) {
                logger.e("analyze function not found in Python module")
                return@withContext emptyList()
            }

            val result = withTimeout(60_000L) {
                analyzeFunc.call(dfObject)
            }
            if (result == null) {
                logger.e("analyze function returned null")
                return@withContext emptyList()
            }

            logger.d("Analyze function completed")
            onProgress?.invoke("데이터 파싱 중...", 80)

            // 결과 파싱 (KOSPI, KOSDAQ) - Python에서 튜플 (kp_df, kq_df) 반환
            val indices = mutableListOf<FearGreedEntity>()

            // 튜플을 리스트로 변환
            val resultList = result.asList()
            if (resultList == null || resultList.size < 2) {
                logger.e("Invalid result tuple from analyze function")
                return@withContext emptyList()
            }

            try {
                // 튜플의 첫 번째 요소: KOSPI 데이터
                val kospiDf = resultList.getOrNull(0)
                if (kospiDf != null && kospiDf.toString() != "None") {
                    logger.d("Parsing KOSPI data...")
                    val kospiIndices = parseFearGreedData(kospiDf, "KOSPI")
                    logger.d("KOSPI parsed: ${kospiIndices.size} records")
                    indices.addAll(kospiIndices)
                } else {
                    logger.w("KOSPI data is None")
                }
            } catch (e: Exception) {
                logger.e("Error parsing KOSPI data", e)
            }

            try {
                // 튜플의 두 번째 요소: KOSDAQ 데이터
                val kosdaqDf = resultList.getOrNull(1)
                if (kosdaqDf != null && kosdaqDf.toString() != "None") {
                    logger.d("Parsing KOSDAQ data...")
                    val kosdaqIndices = parseFearGreedData(kosdaqDf, "KOSDAQ")
                    logger.d("KOSDAQ parsed: ${kosdaqIndices.size} records")
                    indices.addAll(kosdaqIndices)
                } else {
                    logger.w("KOSDAQ data is None")
                }
            } catch (e: Exception) {
                logger.e("Error parsing KOSDAQ data", e)
            }

            if (indices.isEmpty()) {
                logger.e("No Fear & Greed data calculated")
            } else {
                logger.d("Total Fear & Greed records: ${indices.size}")
            }

            indices
        } catch (e: Exception) {
            logger.e("Error calculating Fear & Greed", e)
            emptyList()
        }
    }

    /**
     * Python DataFrame을 FearGreedIndex 리스트로 변환
     */
    private fun parseFearGreedData(df: PyObject, market: String): List<FearGreedEntity> {
        try {
            val indices = mutableListOf<FearGreedEntity>()

            // DataFrame을 딕셔너리 리스트로 변환
            val recordsFunc = df["to_dict"]
            if (recordsFunc == null) {
                logger.e("to_dict method not found on DataFrame")
                return emptyList()
            }

            val records = recordsFunc.call("records")
            if (records == null) {
                logger.e("Failed to convert DataFrame to records (returned null)")
                return emptyList()
            }

            val recordsList = records.asList()
            if (recordsList == null) {
                logger.e("Failed to convert records to list")
                return emptyList()
            }

            logger.d("Processing ${recordsList.size} records for $market")

            for ((index, record) in recordsList.withIndex()) {
                try {
                    // 첫 번째 레코드에서 디버깅 정보 출력
                    if (index == 0) {
                        logger.d("First record type: ${record.javaClass.name}")
                        logger.d("First record toString: ${record.toString()}")
                        try {
                            val keys = record.asMap()?.keys
                            logger.d("Record keys: $keys")
                        } catch (e: Exception) {
                            logger.w("Cannot get keys as map: ${e.message}")
                        }
                    }

                    // Python dict의 get 메서드 사용
                    val getFunc = record["get"]
                    if (getFunc == null) {
                        logger.e("Record $index: get method not found")
                        continue
                    }

                    val dateObj = getFunc.call("거래일")
                    val date = dateObj?.toString()
                    if (date == null || date == "None") {
                        if (index < 3) logger.w("Record $index: missing date (dateObj=$dateObj)")
                        continue
                    }

                    val indexValueObj = getFunc.call(market)
                    val indexValue = try {
                        indexValueObj?.toDouble()
                    } catch (e: Exception) {
                        if (index < 3) logger.w("Record $index ($date): cannot convert $market to double: $indexValueObj")
                        null
                    }
                    if (indexValue == null) {
                        if (index < 3) logger.w("Record $index ($date): missing $market value")
                        continue
                    }

                    val fgObj = getFunc.call("FG")
                    val fg = try {
                        fgObj?.toDouble()
                    } catch (e: Exception) {
                        if (index < 3) logger.w("Record $index ($date): cannot convert FG to double: $fgObj")
                        null
                    }
                    if (fg == null) {
                        if (index < 3) logger.w("Record $index ($date): missing FG value")
                        continue
                    }

                    val oscObj = getFunc.call("Osc")
                    val osc = try {
                        oscObj?.toDouble()
                    } catch (e: Exception) {
                        if (index < 3) logger.w("Record $index ($date): cannot convert Osc to double: $oscObj")
                        null
                    }
                    if (osc == null) {
                        if (index < 3) logger.w("Record $index ($date): missing Osc value")
                        continue
                    }

                    val rsi = try { getFunc.call("RSI")?.toDouble() } catch (e: Exception) { null } ?: 0.0
                    val mom = try { getFunc.call("Mom")?.toDouble() } catch (e: Exception) { null } ?: 0.0
                    val pcr = try { getFunc.call("PCR")?.toDouble() } catch (e: Exception) { null } ?: 0.0
                    val vol = try { getFunc.call("Vol")?.toDouble() } catch (e: Exception) { null } ?: 0.0
                    val spread = try { getFunc.call("Spread")?.toDouble() } catch (e: Exception) { null } ?: 0.0

                    // 날짜 형식 변환 (Timestamp -> YYYY-MM-DD)
                    val formattedDate = formatDate(date)

                    indices.add(
                        FearGreedEntity(
                            id = "$market-$formattedDate",
                            market = market,
                            date = formattedDate,
                            indexValue = indexValue,
                            fearGreedValue = fg,
                            oscillator = osc,
                            rsi = rsi,
                            momentum = mom,
                            putCallRatio = pcr,
                            volatility = vol,
                            spread = spread,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    if (index < 3) logger.w("Failed to parse record $index: ${e.message}", e)
                    continue
                }
            }

            logger.d("Successfully parsed ${indices.size} out of ${recordsList.size} records for $market")
            return indices
        } catch (e: Exception) {
            logger.e("Error parsing Fear & Greed data for $market", e)
            return emptyList()
        }
    }

    private fun formatDate(dateStr: String): String = DateFormatter.formatFromYYYYMMDD(dateStr)
}
