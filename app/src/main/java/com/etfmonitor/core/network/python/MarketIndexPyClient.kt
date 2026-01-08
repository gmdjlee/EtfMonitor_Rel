package com.etfmonitor.core.network.python

import android.content.Context
import com.chaquo.python.Python
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.entities.MarketIndex
import com.etfmonitor.core.network.ai.ApiKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KOSPI/KOSDAQ 시장 지수 데이터 수집을 위한 Python 클라이언트
 * market.py 모듈 사용
 *
 * KIS API 클라이언트 자동 초기화를 지원합니다.
 */
@Singleton
class MarketIndexPyClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private val logger = AppLogger.getLogger("MarketIndexPy")
        private const val TIMEOUT_MS = 60_000L  // 60초로 증가 (Python 재시도 고려)
    }

    private val python = Python.getInstance()
    private val module = python.getModule("market")
    private val kisModule by lazy { python.getModule("kis_client") }

    // Mutex to prevent concurrent KIS client initialization
    private val kisInitMutex = Mutex()
    // Flag to track if KIS client was initialized in this session
    private var kisClientInitialized = false

    /**
     * KIS API 클라이언트 자동 초기화
     *
     * Python 호출 전에 KIS 클라이언트가 초기화되어 있는지 확인하고,
     * 필요한 경우 ApiKeyProvider에서 자격 증명을 가져와 초기화합니다.
     *
     * @return 초기화 성공 여부
     */
    private suspend fun ensureKisClientInitialized(): Boolean {
        // Fast path: already initialized in this session
        if (kisClientInitialized) {
            return true
        }

        return kisInitMutex.withLock {
            // Double-check after acquiring lock
            if (kisClientInitialized) {
                return@withLock true
            }

            // Check if already initialized in Python
            try {
                val isInitialized = kisModule.callAttr("is_client_initialized").toBoolean()
                if (isInitialized) {
                    kisClientInitialized = true
                    logger.d("KIS client already initialized in Python")
                    return@withLock true
                }
            } catch (e: Exception) {
                logger.w("Error checking KIS client status: ${e.message}")
            }

            // Check if credentials are configured
            if (!apiKeyProvider.isKisApiConfigured()) {
                logger.e("KIS API credentials not configured")
                return@withLock false
            }

            val appKey = apiKeyProvider.getKisAppKey()
            val appSecret = apiKeyProvider.getKisAppSecret()

            if (appKey.isNullOrBlank() || appSecret.isNullOrBlank()) {
                logger.e("KIS API credentials are empty")
                return@withLock false
            }

            // Initialize KIS client
            try {
                withTimeout(TIMEOUT_MS) {
                    kisModule.callAttr("init_kis_client", appKey, appSecret)
                }
                kisClientInitialized = true
                logger.i("KIS API client auto-initialized successfully")
                true
            } catch (e: TimeoutCancellationException) {
                logger.e("KIS client initialization timeout", e)
                false
            } catch (e: Exception) {
                logger.e("Failed to initialize KIS client", e)
                false
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 지정된 기간 동안의 시장 지수 데이터 수집
     *
     * @param startDate 시작 날짜 (YYYYMMDD 형식)
     * @param endDate 종료 날짜 (YYYYMMDD 형식)
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchMarketIndices(
        startDate: String,
        endDate: String,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        // Ensure KIS client is initialized before making Python calls
        if (!ensureKisClientInitialized()) {
            logger.e("KIS client not initialized, cannot fetch market indices")
            return@withContext emptyList()
        }

        try {
            logger.d( "Fetching market indices: markets=$markets, from=$startDate to=$endDate")

            withTimeout(TIMEOUT_MS) {
                // Convert to array for Python iteration compatibility
                val result = module.callAttr(
                    "fetch_all_markets",
                    startDate,
                    endDate,
                    markets.toTypedArray()
                ).toString()

                val dtoList = json.decodeFromString<List<MarketIndexDto>>(result)

                dtoList.map { dto ->
                    MarketIndex(
                        id = "${dto.market}-${dto.date}",
                        date = dto.date,
                        market = dto.market,
                        closePrice = dto.closePrice,
                        openPrice = dto.openPrice,
                        highPrice = dto.highPrice,
                        lowPrice = dto.lowPrice,
                        volume = dto.volume,
                        changeRate = dto.changeRate,
                        lastUpdated = System.currentTimeMillis()
                    )
                }.also {
                    logger.d( "Successfully fetched ${it.size} market index records")
                }
            }
        } catch (e: Exception) {
            logger.e( "Error fetching market indices", e)
            emptyList()
        }
    }

    /**
     * 최근 N일의 시장 지수 데이터 수집
     *
     * @param days 수집할 일수 (기본 30일)
     * @param markets 수집할 시장 목록
     * @return 수집된 MarketIndex 리스트
     */
    suspend fun fetchRecentDays(
        days: Int = 30,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): List<MarketIndex> = withContext(Dispatchers.IO) {
        // Ensure KIS client is initialized before making Python calls
        if (!ensureKisClientInitialized()) {
            logger.e("KIS client not initialized, cannot fetch recent market data")
            return@withContext emptyList()
        }

        try {
            logger.d( "Fetching recent $days days for markets: $markets")

            withTimeout(TIMEOUT_MS) {
                // Convert to array for Python iteration compatibility
                val result = module.callAttr(
                    "fetch_recent_days",
                    days,
                    markets.toTypedArray()
                ).toString()

                val dtoList = json.decodeFromString<List<MarketIndexDto>>(result)

                dtoList.map { dto ->
                    MarketIndex(
                        id = "${dto.market}-${dto.date}",
                        date = dto.date,
                        market = dto.market,
                        closePrice = dto.closePrice,
                        openPrice = dto.openPrice,
                        highPrice = dto.highPrice,
                        lowPrice = dto.lowPrice,
                        volume = dto.volume,
                        changeRate = dto.changeRate,
                        lastUpdated = System.currentTimeMillis()
                    )
                }.also {
                    logger.d( "Successfully fetched ${it.size} recent market index records")
                }
            }
        } catch (e: Exception) {
            logger.e( "Error fetching recent market indices", e)
            emptyList()
        }
    }

    /**
     * 특정 시장의 최신 지수 데이터 조회
     *
     * @param market "KOSPI" 또는 "KOSDAQ"
     * @return 최신 MarketIndex 또는 null
     */
    suspend fun getLatestIndex(market: String): MarketIndex? = withContext(Dispatchers.IO) {
        // Ensure KIS client is initialized before making Python calls
        if (!ensureKisClientInitialized()) {
            logger.e("KIS client not initialized, cannot get latest index")
            return@withContext null
        }

        try {
            logger.d( "Getting latest index for market: $market")

            withTimeout(TIMEOUT_MS) {
                val result = module.callAttr("get_latest_index", market)

                // Python에서 None을 반환하면 null로 처리
                if (result.toString() == "None") {
                    logger.w("No latest index data found for $market")
                    return@withTimeout null
                }

                val dto = json.decodeFromString<MarketIndexDto>(result.toString())

                MarketIndex(
                    id = "${dto.market}-${dto.date}",
                    date = dto.date,
                    market = dto.market,
                    closePrice = dto.closePrice,
                    openPrice = dto.openPrice,
                    highPrice = dto.highPrice,
                    lowPrice = dto.lowPrice,
                    volume = dto.volume,
                    changeRate = dto.changeRate,
                    lastUpdated = System.currentTimeMillis()
                ).also {
                    logger.d( "Successfully fetched latest index for $market: date=${it.date}, close=${it.closePrice}")
                }
            }
        } catch (e: Exception) {
            logger.e( "Error getting latest index for $market", e)
            null
        }
    }

    /**
     * Python 모듈에서 받는 JSON 데이터 형식
     */
    @Serializable
    private data class MarketIndexDto(
        val date: String,
        val market: String,
        val closePrice: Double,
        val openPrice: Double,
        val highPrice: Double,
        val lowPrice: Double,
        val volume: Long,
        val changeRate: Double
    )
}
