package com.etfmonitor.core.network.python

import com.chaquo.python.Python
import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.entities.Etf
import com.etfmonitor.core.database.entities.Holding
import com.etfmonitor.core.database.entities.SnapshotType
import com.etfmonitor.core.common.util.DataParsingException
import com.etfmonitor.core.common.util.PythonRuntimeException
import com.etfmonitor.core.common.util.PythonTimeoutException
import com.etfmonitor.core.network.ai.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python 기반 한국 주식시장(KRX) 데이터 수집 클라이언트
 *
 * Chaquopy를 통해 Python pykrx 라이브러리를 활용하여
 * ETF 목록, 보유 종목, 영업일 정보 등을 수집합니다.
 *
 * ## 주요 기능
 * - ETF 목록 조회 (필터링 지원): [getFilteredEtfList], [getEtfList]
 * - ETF 보유 종목 조회: [getHoldings]
 * - 영업일 목록 조회: [getBusinessDays]
 *
 * ## Python 모듈
 * - `etfcollector`: ETF 목록 및 보유 종목 수집
 * - `stocks`: 종목명 조회
 * - `core`: 영업일 계산
 *
 * ## 성능 최적화
 * - [kotlinx.serialization]을 사용하여 JSON 파싱 성능 3-5배 향상
 * - 컴파일 타임 직렬화 코드 생성으로 런타임 오류 감소
 * - 모든 Python 호출은 [Dispatchers.IO]에서 실행
 *
 * ## 타임아웃 및 재시도
 * - 기본 타임아웃: 30초 ([TIMEOUT_MS])
 * - 보유 종목 조회 시 최대 2회 재시도 ([MAX_RETRIES])
 *
 * ## 예외 처리
 * - [PythonTimeoutException]: 타임아웃 발생 시
 * - [DataParsingException]: JSON 파싱 실패 시
 * - [PythonRuntimeException]: 기타 Python 실행 오류 시
 *
 * @property python Chaquopy Python 인스턴스
 *
 * @see OscillatorPyClient 추가 분석 데이터 수집
 * @see MarketIndexPyClient 시장 지수 데이터 수집
 */
@Singleton
class PyKrxClient @Inject constructor(
    private val python: Python,
    private val apiKeyProvider: ApiKeyProvider
) {

    companion object {
        private val logger = AppLogger.getLogger("PyKrxClient")
        private const val TIMEOUT_MS = 60_000L  // 60초로 증가 (Python 재시도 고려)
        private const val MAX_RETRIES = 2
    }

    // Mutex to prevent concurrent KIS client initialization
    private val kisInitMutex = Mutex()
    // Flag to track if KIS client was initialized in this session
    private var kisClientInitialized = false

    // 종목명 캐시 - API 호출 최소화
    private val stockNameCache = mutableMapOf<String, String>()

    /**
     * kotlinx.serialization 설정
     * - ignoreUnknownKeys: Python에서 추가 필드가 와도 무시
     * - isLenient: 유연한 JSON 파싱
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * JSON 파싱용 데이터 클래스
     */
    @Serializable
    private data class EtfJson(
        val ticker: String,
        val name: String
    )

    @Serializable
    private data class HoldingJson(
        val ticker: String,
        val name: String = "",  // KIS API에서 이미 제공 - 불필요한 API 호출 제거
        val weight: Double,
        val amount: Double
    )

    private val etfModule by lazy { python.getModule("etfcollector") }
    private val stockModule by lazy { python.getModule("stocks") }
    private val coreModule by lazy { python.getModule("core") }
    private val kisModule by lazy { python.getModule("kis_client") }

    // ==================== KIS API Auto-Initialization ====================

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

    // ==================== KIS API Initialization ====================

    /**
     * KIS API 클라이언트 초기화
     *
     * @param appKey KIS Open API APP KEY
     * @param appSecret KIS Open API APP SECRET
     * @return 초기화 성공 여부
     */
    suspend fun initializeKisClient(appKey: String, appSecret: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(TIMEOUT_MS) {
                    kisModule.callAttr("init_kis_client", appKey, appSecret)
                    logger.i("KIS API client initialized successfully")
                    true
                }
            } catch (e: TimeoutCancellationException) {
                logger.e("KIS client initialization timeout", e)
                false
            } catch (e: Exception) {
                logger.e("Failed to initialize KIS client", e)
                false
            }
        }

    /**
     * KIS API 클라이언트 초기화 상태 확인
     *
     * @return 초기화 여부
     */
    suspend fun isKisClientInitialized(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = kisModule.callAttr("is_client_initialized")
            result.toBoolean()
        } catch (e: Exception) {
            logger.e("Error checking KIS client status", e)
            false
        }
    }

    /**
     * KIS API 연결 테스트
     *
     * 삼성전자(005930) 종목명을 조회하여 API 연결 상태를 확인합니다.
     *
     * @return 연결 성공 여부
     */
    suspend fun testKisApiConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                // 삼성전자 종목명 조회로 연결 테스트
                val client = kisModule.callAttr("get_client")
                val result = client.callAttr("get_stock_name", "005930")
                val name = result.toString()
                logger.d("KIS API connection test: 005930 -> $name")
                name.isNotEmpty() && name != "None"
            }
        } catch (e: TimeoutCancellationException) {
            logger.e("KIS API connection test timeout", e)
            false
        } catch (e: Exception) {
            logger.e("KIS API connection test failed", e)
            false
        }
    }

    /**
     * ETF 목록을 필터링과 함께 조회 (최적화된 버전)
     */
    suspend fun getFilteredEtfList(
        date: String,
        includeKeywords: List<String>,
        excludeKeywords: List<String>
    ): List<Etf> = withContext(Dispatchers.IO) {
        try {
            // Ensure KIS client is initialized before making Python calls
            if (!ensureKisClientInitialized()) {
                logger.e("KIS client not initialized, cannot get ETF list")
                return@withContext emptyList()
            }

            if (includeKeywords.isEmpty()) {
                logger.e("ERROR: includeKeywords is empty in PyKrxClient")
                return@withContext emptyList()
            }

            val includeJson = Json.encodeToString(includeKeywords)
            val excludeJson = Json.encodeToString(excludeKeywords)

            if (!includeJson.startsWith("[") || !includeJson.endsWith("]")) {
                logger.e("ERROR: Invalid JSON format for includeKeywords")
            }

            val jsonStr = withTimeout(TIMEOUT_MS) {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    date,
                    includeJson,
                    excludeJson
                ).toString()
            }

            // STEP 4: Python 응답 확인
            logger.d( "\nSTEP 4: Python response")
            logger.d( "  Response length: ${jsonStr.length}")
            if (jsonStr.length < 200) {
                logger.d( "  Full response: $jsonStr")
            } else {
                logger.d( "  First 200 chars: ${jsonStr.take(200)}...")
            }

            // Check for error response (JSON object instead of array)
            if (jsonStr.trimStart().startsWith("{")) {
                logger.e("Received error response from Python: $jsonStr")
                return@withContext emptyList()
            }

            // STEP 5: JSON 파싱 (kotlinx.serialization 사용)
            logger.d( "\nSTEP 5: Parse response with kotlinx.serialization")
            val etfJsonList = json.decodeFromString<List<EtfJson>>(jsonStr)
            logger.d( "  Parsed ${etfJsonList.size} ETFs")

            val etfs = etfJsonList.map { etfJson ->
                Etf(etfJson.ticker, etfJson.name)
            }

            logger.d( "\nSTEP 6: Final result")
            logger.d( "  Returning ${etfs.size} ETFs")
            if (etfs.isNotEmpty()) {
                logger.d( "  Sample:")
                etfs.take(3).forEach {
                    logger.d( "    ${it.ticker}: ${it.name}")
                }
            }
            logger.d( "=".repeat(80) + "\n")

            etfs
        } catch (e: TimeoutCancellationException) {
            logger.e( "getFilteredEtfList timeout", PythonTimeoutException(TIMEOUT_MS, "etfcollector", "get_etf_list_with_names"))
            emptyList()
        } catch (e: SerializationException) {
            logger.e( "getFilteredEtfList parse error", DataParsingException("ETF 목록 JSON 파싱 실패", cause = e))
            emptyList()
        } catch (e: Exception) {
            logger.e( "getFilteredEtfList failed", PythonRuntimeException("ETF 목록 조회 실패", "etfcollector", "get_etf_list_with_names", cause = e))
            emptyList()
        }
    }

    /**
     * ETF 목록 조회 (필터링 없음)
     * kotlinx.serialization으로 JSON 파싱
     */
    suspend fun getEtfList(date: String): List<Etf> = withContext(Dispatchers.IO) {
        try {
            // Ensure KIS client is initialized before making Python calls
            if (!ensureKisClientInitialized()) {
                logger.e("KIS client not initialized, cannot get ETF list")
                return@withContext emptyList()
            }

            logger.d( "getEtfList: $date")

            val jsonStr = withTimeout(TIMEOUT_MS) {
                etfModule.callAttr("get_etf_list", date).toString()
            }

            // Check for error response
            if (jsonStr.trimStart().startsWith("{")) {
                logger.e("Received error response from Python: $jsonStr")
                return@withContext emptyList()
            }
            val tickers = json.decodeFromString<List<String>>(jsonStr)
            logger.d( "Found ${tickers.size} tickers")

            if (tickers.isEmpty()) {
                return@withContext emptyList()
            }

            val etfs = tickers.mapNotNull { ticker ->
                try {
                    val name = withTimeout(TIMEOUT_MS) {
                        etfModule.callAttr("get_etf_name", ticker).toString()
                    }
                    if (name.isNotEmpty() && name != "None") {
                        Etf(ticker, name)
                    } else {
                        null
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.w( "Timeout getting ETF name for $ticker")
                    null
                } catch (e: Exception) {
                    logger.w( "Error getting ETF name for $ticker: ${e.message}")
                    null
                }
            }

            logger.d( "getEtfList result: ${etfs.size} ETFs")
            etfs
        } catch (e: TimeoutCancellationException) {
            logger.e( "getEtfList timeout", PythonTimeoutException(TIMEOUT_MS, "etfcollector", "get_etf_list"))
            emptyList()
        } catch (e: SerializationException) {
            logger.e( "getEtfList parse error", DataParsingException("ETF 목록 JSON 파싱 실패", cause = e))
            emptyList()
        } catch (e: Exception) {
            logger.e( "getEtfList error", PythonRuntimeException("ETF 목록 조회 실패", "etfcollector", "get_etf_list", cause = e))
            emptyList()
        }
    }

    /**
     * ETF 보유 종목 조회
     * kotlinx.serialization으로 JSON 파싱 - 성능 최적화
     *
     * ## 성능 개선 (v2.2)
     * - KIS API에서 이미 종목명을 제공하므로 추가 API 호출 불필요
     * - 종목명이 비어있는 경우에만 캐시에서 조회
     */
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding> =
        withContext(Dispatchers.IO) {
            // Ensure KIS client is initialized before making Python calls
            if (!ensureKisClientInitialized()) {
                logger.e("KIS client not initialized, cannot get holdings")
                return@withContext emptyList()
            }

            // Circuit Breaker 상태 확인
            if (isCircuitBreakerOpen()) {
                logger.w("Circuit breaker is open, skipping holdings request for $etfTicker")
                return@withContext emptyList()
            }

            retryWithTimeout(maxRetries = MAX_RETRIES) {
                try {
                    val jsonStr = etfModule.callAttr("get_etf_holdings", etfTicker, date).toString()

                    if (jsonStr == "[]" || jsonStr.isEmpty()) {
                        return@retryWithTimeout emptyList()
                    }

                    // Check for error response
                    if (jsonStr.trimStart().startsWith("{")) {
                        logger.e("Received error response from Python: $jsonStr")
                        return@retryWithTimeout emptyList()
                    }

                    // kotlinx.serialization으로 파싱
                    val holdingJsonList = json.decodeFromString<List<HoldingJson>>(jsonStr)

                    // KIS API가 이미 종목명을 제공 - 불필요한 API 호출 제거
                    val holdings = holdingJsonList.map { holdingJson ->
                        // 종목명이 비어있거나 None인 경우에만 캐시에서 조회
                        val stockName = if (holdingJson.name.isNotBlank() && holdingJson.name != "None") {
                            holdingJson.name
                        } else {
                            // 캐시된 이름 사용 (API 호출 없음)
                            stockNameCache[holdingJson.ticker] ?: holdingJson.ticker
                        }

                        // 캐시에 저장
                        if (stockName != holdingJson.ticker) {
                            stockNameCache[holdingJson.ticker] = stockName
                        }

                        // 최적화된 형식으로 생성 (DAILY 스냅샷)
                        Holding.create(
                            etfTicker = etfTicker,
                            stockTicker = holdingJson.ticker,
                            stockName = stockName,
                            date = formatDate(date),
                            weight = holdingJson.weight.toFloat(),
                            amount = holdingJson.amount.toFloat(),
                            snapshotType = SnapshotType.DAILY
                        )
                    }

                    logger.d("Holdings for $etfTicker: ${holdings.size} items (no additional API calls)")
                    holdings
                } catch (e: Exception) {
                    logger.e("getHoldings error for $etfTicker", e)
                    throw e
                }
            } ?: emptyList()
        }

    /**
     * 영업일 조회
     * kotlinx.serialization으로 JSON 파싱
     */
    suspend fun getBusinessDays(days: Int): List<String> = withContext(Dispatchers.IO) {
        try {
            // Ensure KIS client is initialized before making Python calls
            if (!ensureKisClientInitialized()) {
                logger.e("KIS client not initialized, cannot get business days")
                return@withContext emptyList()
            }

            logger.d( "getBusinessDays: $days days")
            val end = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val start = LocalDate.now()
                .minusDays((days * 2).toLong())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            logger.d( "Date range: $start to $end")

            val jsonStr = withTimeout(TIMEOUT_MS) {
                coreModule.callAttr("get_business_days", start, end).toString()
            }

            // Check for error response
            if (jsonStr.trimStart().startsWith("{")) {
                logger.e("Received error response from Python: $jsonStr")
                return@withContext emptyList()
            }
            val datesList = json.decodeFromString<List<String>>(jsonStr)

            val businessDays = datesList
                .map { formatDate(it) }
                .takeLast(days)

            logger.d( "getBusinessDays result: ${businessDays.size} days")
            businessDays
        } catch (e: Exception) {
            logger.e( "getBusinessDays error", e)
            emptyList()
        }
    }

    private suspend fun <T> retryWithTimeout(
        maxRetries: Int = MAX_RETRIES,
        timeoutMs: Long = TIMEOUT_MS,
        block: suspend () -> T
    ): T? {
        repeat(maxRetries) { attempt ->
            try {
                return withTimeout(timeoutMs) {
                    block()
                }
            } catch (e: Exception) {
                logger.w( "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt == maxRetries - 1) {
                    logger.e( "All retry attempts failed")
                    return null
                }
            }
        }
        return null
    }

    private suspend fun getStockName(ticker: String): String = withContext(Dispatchers.IO) {
        // 캐시 우선 확인
        stockNameCache[ticker]?.let { return@withContext it }

        try {
            val name = withTimeout(TIMEOUT_MS) {
                stockModule.callAttr("get_stock_name", ticker).toString()
            }
            val result = if (name == "None" || name.isEmpty()) ticker else name
            if (result != ticker) {
                stockNameCache[ticker] = result
            }
            result
        } catch (e: Exception) {
            logger.e("Error getting stock name for $ticker: ${e.message}")
            ticker
        }
    }

    /**
     * Python Circuit Breaker 상태 확인
     *
     * KIS API 클라이언트의 Circuit Breaker가 열려있는지 확인합니다.
     * Circuit Breaker가 열려있으면 API 호출을 건너뛰어 불필요한 대기를 방지합니다.
     */
    private suspend fun isCircuitBreakerOpen(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!kisClientInitialized) return@withContext false

            val healthJson = withTimeout(5_000L) {
                kisModule.callAttr("get_client").callAttr("health_check").toString()
            }

            // JSON 파싱하여 circuit_breaker_open 확인
            val isOpen = healthJson.contains("\"circuit_breaker_open\": true") ||
                         healthJson.contains("'circuit_breaker_open': True")

            if (isOpen) {
                logger.w("Circuit breaker is open - KIS API may be experiencing issues")
            }
            isOpen
        } catch (e: Exception) {
            logger.w("Failed to check circuit breaker status: ${e.message}")
            false // 확인 실패 시 열려있지 않은 것으로 간주
        }
    }

    /**
     * 종목명 캐시 사전 로드
     *
     * 마스터 파일에서 모든 종목명을 미리 로드하여
     * 이후 개별 API 호출을 최소화합니다.
     *
     * @return 캐시된 종목 수
     */
    suspend fun preloadStockNameCache(): Int = withContext(Dispatchers.IO) {
        try {
            if (!ensureKisClientInitialized()) {
                logger.e("KIS client not initialized, cannot preload cache")
                return@withContext 0
            }

            logger.d("Preloading stock name cache from master files...")

            val jsonStr = withTimeout(TIMEOUT_MS) {
                stockModule.callAttr("get_all_stocks").toString()
            }

            // 에러 응답 확인
            if (jsonStr.contains("\"error\":") || jsonStr.contains("\"error\": true")) {
                logger.w("Error response from get_all_stocks: ${jsonStr.take(200)}")
                return@withContext 0
            }

            // JSON 파싱
            val stockList = json.decodeFromString<List<EtfJson>>(jsonStr)

            stockList.forEach { stock ->
                if (stock.name.isNotBlank() && stock.name != "None") {
                    stockNameCache[stock.ticker] = stock.name
                }
            }

            logger.i("Preloaded ${stockNameCache.size} stock names into cache")
            stockNameCache.size
        } catch (e: Exception) {
            logger.e("Failed to preload stock name cache", e)
            0
        }
    }

    /**
     * 캐시된 종목 수 반환
     */
    fun getCacheSize(): Int = stockNameCache.size

    /**
     * 캐시 초기화
     */
    fun clearCache() {
        stockNameCache.clear()
        logger.d("Stock name cache cleared")
    }

    private fun formatDate(date: String): String = DateFormatter.formatFromYYYYMMDD(date)
}
