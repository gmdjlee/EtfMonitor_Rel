package com.etfmonitor.python

import android.util.Log
import com.chaquo.python.Python
import com.etfmonitor.database.entities.Etf
import com.etfmonitor.database.entities.Holding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Level PyKrxClient
 *
 * 최적화 포인트:
 * 1. @Singleton: Hilt가 단일 인스턴스 관리
 * 2. @Inject: 생성자 주입으로 의존성 명확화
 * 3. kotlinx.serialization: org.json 대체하여 타입 안정성 향상 및 성능 개선
 * 4. withContext(Dispatchers.IO): 모든 Python 호출을 IO 스레드에서 실행
 *
 * JSON 파싱 성능 개선:
 * - kotlinx.serialization은 컴파일 타임에 직렬화 코드 생성
 * - org.json보다 약 3-5배 빠른 파싱 속도
 * - 타입 안정성 보장으로 런타임 오류 감소
 */
@Singleton
class PyKrxClient @Inject constructor(
    private val python: Python
) {

    companion object {
        private const val TAG = "PyKrxClient"
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
        //Log.d(TAG, "Loading etfcollector module")
        python.getModule("etfcollector")
    }
    private val stockModule by lazy {
        //Log.d(TAG, "Loading stockcollector module")
        python.getModule("stockcollector")
    }
    private val utilModule by lazy {
        //Log.d(TAG, "Loading utils module")
        python.getModule("utils")
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
            Log.d(TAG, "\n" + "=".repeat(80))
            Log.d(TAG, "PyKrxClient.getFilteredEtfList() called")
            Log.d(TAG, "=".repeat(80))

            // STEP 1: 입력 확인
            Log.d(TAG, "STEP 1: Input parameters")
            Log.d(TAG, "  date: $date")
            Log.d(TAG, "  includeKeywords type: ${includeKeywords::class.simpleName}")
            Log.d(TAG, "  includeKeywords size: ${includeKeywords.size}")
            Log.d(TAG, "  includeKeywords: $includeKeywords")
            Log.d(TAG, "  excludeKeywords type: ${excludeKeywords::class.simpleName}")
            Log.d(TAG, "  excludeKeywords size: ${excludeKeywords.size}")
            Log.d(TAG, "  excludeKeywords: $excludeKeywords")
             */

            // ✅ 검증: 빈 리스트 확인
            if (includeKeywords.isEmpty()) {
                Log.e(TAG, "❌ ERROR: includeKeywords is empty in PyKrxClient!")
                return@withContext emptyList()
            }

            // STEP 2: JSON 변환
            //Log.d(TAG, "\nSTEP 2: Convert to JSON")
            val includeJson = Json.encodeToString(includeKeywords)
            val excludeJson = Json.encodeToString(excludeKeywords)

            /*
            Log.d(TAG, "  includeJson length: ${includeJson.length}")
            Log.d(TAG, "  includeJson: $includeJson")
            Log.d(TAG, "  excludeJson length: ${excludeJson.length}")
            Log.d(TAG, "  excludeJson: $excludeJson")

             */

            // ✅ 검증: JSON 형식 확인
            if (!includeJson.startsWith("[") || !includeJson.endsWith("]")) {
                Log.e(TAG, "❌ ERROR: Invalid JSON format for includeKeywords!")
            }

            // STEP 3: Python 호출
            /*
            Log.d(TAG, "\nSTEP 3: Call Python module")
            Log.d(TAG, "  Calling: etfModule.get_etf_list_with_names")
            Log.d(TAG, "  Parameters:")
            Log.d(TAG, "    1. date_str: $date")
            Log.d(TAG, "    2. include_keywords_json: $includeJson")
            Log.d(TAG, "    3. exclude_keywords_json: $excludeJson")
            
             */

            val jsonStr = etfModule.callAttr(
                "get_etf_list_with_names",
                date,
                includeJson,
                excludeJson
            ).toString()

            // STEP 4: Python 응답 확인
            Log.d(TAG, "\nSTEP 4: Python response")
            Log.d(TAG, "  Response length: ${jsonStr.length}")
            if (jsonStr.length < 200) {
                Log.d(TAG, "  Full response: $jsonStr")
            } else {
                Log.d(TAG, "  First 200 chars: ${jsonStr.take(200)}...")
            }

            // STEP 5: JSON 파싱 (kotlinx.serialization 사용)
            Log.d(TAG, "\nSTEP 5: Parse response with kotlinx.serialization")
            val etfJsonList = json.decodeFromString<List<EtfJson>>(jsonStr)
            Log.d(TAG, "  Parsed ${etfJsonList.size} ETFs")

            val etfs = etfJsonList.map { etfJson ->
                Etf(etfJson.ticker, etfJson.name)
            }

            Log.d(TAG, "\nSTEP 6: Final result")
            Log.d(TAG, "  Returning ${etfs.size} ETFs")
            if (etfs.isNotEmpty()) {
                Log.d(TAG, "  Sample:")
                etfs.take(3).forEach {
                    Log.d(TAG, "    ${it.ticker}: ${it.name}")
                }
            }
            Log.d(TAG, "=".repeat(80) + "\n")

            etfs
        } catch (e: Exception) {
            Log.e(TAG, "❌ getFilteredEtfList ERROR", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * ETF 목록 조회 (필터링 없음)
     * kotlinx.serialization으로 JSON 파싱
     */
    suspend fun getEtfList(date: String): List<Etf> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getEtfList: $date")

            val jsonStr = etfModule.callAttr("get_etf_list", date).toString()
            val tickers = json.decodeFromString<List<String>>(jsonStr)
            Log.d(TAG, "Found ${tickers.size} tickers")

            if (tickers.isEmpty()) {
                return@withContext emptyList()
            }

            val etfs = tickers.mapNotNull { ticker ->
                try {
                    val name = etfModule.callAttr("get_etf_name", ticker).toString()
                    if (name.isNotEmpty() && name != "None") {
                        Etf(ticker, name)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting ETF name for $ticker: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "getEtfList result: ${etfs.size} ETFs")
            etfs
        } catch (e: Exception) {
            Log.e(TAG, "getEtfList error", e)
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

                        Holding(
                            etfTicker = etfTicker,
                            stockTicker = holdingJson.ticker,
                            stockName = stockName,
                            date = formatDate(date),
                            weight = holdingJson.weight.toFloat(),
                            amount = holdingJson.amount.toFloat()
                        )
                    }

                    holdings
                } catch (e: Exception) {
                    Log.e(TAG, "getHoldings error for $etfTicker", e)
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
            Log.d(TAG, "getBusinessDays: $days days")
            val end = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val start = LocalDate.now()
                .minusDays((days * 2).toLong())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            Log.d(TAG, "Date range: $start to $end")

            val jsonStr = utilModule.callAttr("get_business_days", start, end).toString()
            val datesList = json.decodeFromString<List<String>>(jsonStr)

            val businessDays = datesList
                .map { formatDate(it) }
                .takeLast(days)

            Log.d(TAG, "getBusinessDays result: ${businessDays.size} days")
            businessDays
        } catch (e: Exception) {
            Log.e(TAG, "getBusinessDays error", e)
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
                Log.w(TAG, "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")
                if (attempt == maxRetries - 1) {
                    Log.e(TAG, "All retry attempts failed")
                    return null
                }
            }
        }
        return null
    }

    private suspend fun getStockName(ticker: String): String = withContext(Dispatchers.IO) {
        try {
            val name = stockModule.callAttr("get_stock_name", ticker).toString()
            if (name == "None" || name.isEmpty()) ticker else name
        } catch (e: Exception) {
            Log.e(TAG, "Error getting stock name for $ticker: ${e.message}")
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