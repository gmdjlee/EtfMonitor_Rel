package com.etfmonitor.core.network.scraper

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Naver Finance 증시 자금 동향 데이터 스크래퍼
 *
 * Python deposit_scraper.py를 대체하는 Kotlin 구현:
 * - OkHttp + Jsoup로 Naver Finance 웹 스크래핑
 * - 예수금 및 신용잔고 데이터 수집
 */
@Singleton
class NaverFinanceScraper @Inject constructor() {

    companion object {
        private val logger = AppLogger.getLogger("NaverFinanceScraper")
        private const val BASE_URL = "https://finance.naver.com/sise/sise_deposit.naver"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val REQUEST_DELAY_MS = 500L
        private const val TIMEOUT_SECONDS = 15L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 증시 자금 동향 데이터 스크래핑
     *
     * @param numPages 수집할 페이지 수 (기본 5페이지)
     * @return MarketDepositData 또는 null (실패 시)
     */
    suspend fun scrapeDepositData(numPages: Int = 5): MarketDepositData? = withContext(Dispatchers.IO) {
        if (numPages <= 0) {
            logger.e("Invalid numPages: $numPages")
            return@withContext null
        }

        logger.d("Scraping deposit data: $numPages pages")
        val allData = mutableListOf<DepositRecord>()

        for (page in 1..numPages) {
            try {
                val pageData = scrapePage(page)
                if (pageData.isNotEmpty()) {
                    allData.addAll(pageData)
                    logger.d("Page $page: ${pageData.size} records")
                }

                // 다음 페이지 요청 전 딜레이
                if (page < numPages) {
                    delay(REQUEST_DELAY_MS)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logger.e("Error scraping page $page", e)
                // 개별 페이지 오류는 무시하고 계속 진행
            }
        }

        if (allData.isEmpty()) {
            logger.w("No data collected from any page")
            return@withContext null
        }

        // 날짜로 중복 제거 및 정렬
        val dedupedData = allData
            .distinctBy { it.date }
            .sortedBy { it.date }

        logger.d("Total: ${dedupedData.size} records (deduped from ${allData.size})")

        // MarketDepositData로 변환
        MarketDepositData(
            dates = dedupedData.map { it.date },
            depositAmounts = dedupedData.map { it.depositAmount },
            depositChanges = dedupedData.map { it.depositChange },
            creditAmounts = dedupedData.map { it.creditAmount },
            creditChanges = dedupedData.map { it.creditChange }
        )
    }

    /**
     * 최신 데이터 수집 (1페이지만)
     */
    suspend fun getLatestData(): MarketDepositData? {
        return scrapeDepositData(1)
    }

    /**
     * 단일 페이지 스크래핑
     */
    private fun scrapePage(page: Int): List<DepositRecord> {
        val url = "$BASE_URL?page=$page"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Referer", "https://finance.naver.com/")
            .build()

        val html = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.e("HTTP ${response.code} for page $page")
                    return emptyList()
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            logger.e("HTTP request failed for page $page", e)
            return emptyList()
        }

        if (html == null) {
            logger.e("Empty response body for page $page")
            return emptyList()
        }

        return parseHtml(html)
    }

    /**
     * HTML 파싱
     */
    private fun parseHtml(html: String): List<DepositRecord> {
        val doc: Document = try {
            Jsoup.parse(html, "EUC-KR")
        } catch (e: Exception) {
            logger.e("HTML parsing failed", e)
            return emptyList()
        }

        val table = doc.select("table.type_1").firstOrNull()
        if (table == null) {
            logger.w("table.type_1 not found in HTML")
            return emptyList()
        }

        val data = mutableListOf<DepositRecord>()
        val rows = table.select("tr")

        // 첫 2개 행은 헤더이므로 건너뛰기
        for (i in 2 until rows.size) {
            val cols = rows[i].select("td")
            if (cols.size < 5) continue

            val rawDate = cols[0].text().trim()
            if (rawDate.isEmpty()) continue

            val date = parseDate(rawDate) ?: continue

            data.add(
                DepositRecord(
                    date = date,
                    depositAmount = parseNumber(cols[1].text()),
                    depositChange = parseNumber(cols[2].text()),
                    creditAmount = parseNumber(cols[3].text()),
                    creditChange = parseNumber(cols[4].text())
                )
            )
        }

        return data
    }

    /**
     * 날짜 파싱 (여러 형식 지원)
     */
    private fun parseDate(s: String): String? {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return null

        // YYYY-MM-DD (이미 올바른 형식)
        if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            return trimmed
        }

        // YYYY.MM.DD 또는 YY.MM.DD
        if ('.' in trimmed) {
            val parts = trimmed.split('.')
            if (parts.size == 3) {
                var year = parts[0].trim()
                val month = parts[1].trim().padStart(2, '0')
                val day = parts[2].trim().padStart(2, '0')

                // 2자리 연도 → 4자리 변환
                if (year.length == 2) {
                    year = "20$year"
                }

                return "$year-$month-$day"
            }
        }

        // YYYYMMDD
        if (trimmed.length == 8 && trimmed.all { it.isDigit() }) {
            return "${trimmed.substring(0, 4)}-${trimmed.substring(4, 6)}-${trimmed.substring(6)}"
        }

        // YYYY/MM/DD
        if ('/' in trimmed) {
            val parts = trimmed.split('/')
            if (parts.size == 3) {
                var year = parts[0].trim()
                val month = parts[1].trim().padStart(2, '0')
                val day = parts[2].trim().padStart(2, '0')

                // 2자리 연도 → 4자리 변환
                if (year.length == 2) {
                    year = "20$year"
                }

                return "$year-$month-$day"
            }
        }

        logger.w("Unparseable date format: $trimmed")
        return null
    }

    /**
     * 숫자 파싱 (한글 단위 및 쉼표 제거)
     */
    private fun parseNumber(text: String): Double {
        val cleaned = text.trim()
            .replace(",", "")
            .replace("억원", "")
            .replace("억", "")
            .trim()

        return if (cleaned.isEmpty() || cleaned == "-") {
            0.0
        } else {
            try {
                cleaned.toDouble()
            } catch (e: NumberFormatException) {
                logger.w("Failed to parse number: $text")
                0.0
            }
        }
    }

    /**
     * 내부 데이터 클래스
     */
    private data class DepositRecord(
        val date: String,
        val depositAmount: Double,
        val depositChange: Double,
        val creditAmount: Double,
        val creditChange: Double
    )
}
