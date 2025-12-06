package com.etfmonitor.python

import com.chaquo.python.Python
import com.etfmonitor.utils.AppLogger
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.Holding
import com.etfmonitor.database.entities.SnapshotType
import com.etfmonitor.utils.DataParsingException
import com.etfmonitor.utils.PythonRuntimeException
import com.etfmonitor.utils.PythonTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
    private val python: Python
) {

    companion object {
        private val logger = AppLogger.getLogger("PyKrxClient")
        private const val TIMEOUT_MS = 30_000L
        private const val MAX_RETRIES = 2
    }

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
        val weight: Double,
        val amount: Double
    )

    private val etfModule by lazy {
        //logger.d( "Loading etfcollector module")
        python.getModule("etfcollector")
    }
    private val stockModule by lazy {
        //logger.d( "Loading stocks module")
        python.getModule("stocks")
    }
    private val coreModule by lazy {
        //logger.d( "Loading core module")
        python.getModule("core")
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
            /*
            logger.d( "\n" + "=".repeat(80))
            logger.d( "PyKrxClient.getFilteredEtfList() called")
            logger.d( "=".repeat(80))

            // STEP 1: 입력 확인
            logger.d( "STEP 1: Input parameters")
            logger.d( "  date: $date")
            logger.d( "  includeKeywords type: ${includeKeywords::class.simpleName}")
            logger.d( "  includeKeywords size: ${includeKeywords.size}")
            logger.d( "  includeKeywords: $includeKeywords")
            logger.d( "  excludeKeywords type: ${excludeKeywords::class.simpleName}")
            logger.d( "  excludeKeywords size: ${excludeKeywords.size}")
            logger.d( "  excludeKeywords: $excludeKeywords")
             */

            // ✅ 검증: 빈 리스트 확인
            if (includeKeywords.isEmpty()) {
                logger.e( "ERROR: includeKeywords is empty in PyKrxClient")
                return@withContext emptyList()
            }

            // STEP 2: JSON 변환
            //logger.d( "\nSTEP 2: Convert to JSON")
            val includeJson = Json.encodeToString(includeKeywords)
            val excludeJson = Json.encodeToString(excludeKeywords)

            /*
            logger.d( "  includeJson length: ${includeJson.length}")
            logger.d( "  includeJson: $includeJson")
            logger.d( "  excludeJson length: ${excludeJson.length}")
            logger.d( "  excludeJson: $excludeJson")

             */

            // ✅ 검증: JSON 형식 확인
            if (!includeJson.startsWith("[") || !includeJson.endsWith("]")) {
                logger.e( "ERROR: Invalid JSON format for includeKeywords")
            }

            // STEP 3: Python 호출
            /*
            logger.d( "\nSTEP 3: Call Python module")
            logger.d( "  Calling: etfModule.get_etf_list_with_names")
            logger.d( "  Parameters:")
            logger.d( "    1. date_str: $date")
            logger.d( "    2. include_keywords_json: $includeJson")
            logger.d( "    3. exclude_keywords_json: $excludeJson")
            
             */

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
            logger.d( "getEtfList: $date")

            val jsonStr = withTimeout(TIMEOUT_MS) {
                etfModule.callAttr("get_etf_list", date).toString()
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
     */
    suspend fun getHoldings(etfTicker: String, date: String): List<Holding> =
        withContext(Dispatchers.IO) {
            retryWithTimeout(maxRetries = MAX_RETRIES) {
                try {
                    val jsonStr = etfModule.callAttr("get_etf_holdings", etfTicker, date).toString()

                    if (jsonStr == "[]" || jsonStr.isEmpty()) {
                        return@retryWithTimeout emptyList()
                    }

                    // kotlinx.serialization으로 파싱
                    val holdingJsonList = json.decodeFromString<List<HoldingJson>>(jsonStr)

                    val holdings = holdingJsonList.map { holdingJson ->
                        val stockName = getStockName(holdingJson.ticker)

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

                    holdings
                } catch (e: Exception) {
                    logger.e( "getHoldings error for $etfTicker", e)
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
            logger.d( "getBusinessDays: $days days")
            val end = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val start = LocalDate.now()
                .minusDays((days * 2).toLong())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            logger.d( "Date range: $start to $end")

            val jsonStr = withTimeout(TIMEOUT_MS) {
                coreModule.callAttr("get_business_days", start, end).toString()
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
        try {
            val name = withTimeout(TIMEOUT_MS) {
                stockModule.callAttr("get_stock_name", ticker).toString()
            }
            if (name == "None" || name.isEmpty()) ticker else name
        } catch (e: Exception) {
            logger.e( "Error getting stock name for $ticker: ${e.message}")
            ticker
        }
    }

    private fun formatDate(date: String): String {
        return if (date.length == 8) {
            "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
        } else {
            date
        }
    }
}