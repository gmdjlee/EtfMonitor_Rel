package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.analysis.model.MarketDepositData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이버 금융 고객예탁금/신용잔고 데이터 스크래퍼
 *
 * deposit_scraper.py (Python/BeautifulSoup)를 대체하는 네이티브 구현
 * Jsoup을 사용하여 HTML 파싱
 *
 * @see MarketDepositData 반환 모델
 */
@Singleton
class DepositScraper @Inject constructor() {

    companion object {
        private val logger = AppLogger.getLogger("DepositScraper")
        private const val BASE_URL = "https://finance.naver.com/sise/sise_deposit.naver"
        private const val TIMEOUT_MS = 30_000L
        private const val REQ_DELAY_MS = 300L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 증시 자금 동향 데이터 수집
     *
     * @param numPages 스크래핑할 페이지 수 (기본 5)
     * @return MarketDepositData 또는 null
     */
    suspend fun getMarketDepositData(numPages: Int = 5): MarketDepositData? =
        withContext(Dispatchers.IO) {
            try {
                if (numPages <= 0) return@withContext null

                logger.d("Scraping deposit data: $numPages pages")

                val allData = mutableListOf<DepositRecord>()

                withTimeout(TIMEOUT_MS) {
                    for (page in 1..numPages) {
                        val pageData = scrapePage(page)
                        if (pageData.isNotEmpty()) {
                            allData.addAll(pageData)
                            logger.d("Page $page: ${pageData.size} records")
                        }

                        if (page < numPages) {
                            delay(REQ_DELAY_MS)
                        }
                    }
                }

                if (allData.isEmpty()) {
                    logger.w("No data collected")
                    return@withContext null
                }

                // Deduplicate by date and sort
                val deduped = allData
                    .distinctBy { it.date }
                    .sortedBy { it.date }

                logger.d("Total: ${deduped.size} records (deduped)")

                MarketDepositData(
                    dates = deduped.map { it.date },
                    depositAmounts = deduped.map { it.depositAmount },
                    depositChanges = deduped.map { it.depositChange },
                    creditAmounts = deduped.map { it.creditAmount },
                    creditChanges = deduped.map { it.creditChange }
                )
            } catch (e: Exception) {
                logger.e("getMarketDepositData error", e)
                null
            }
        }

    /**
     * 최신 증시 자금 동향 (1 페이지)
     */
    suspend fun getLatestMarketData(): MarketDepositData? = getMarketDepositData(1)

    private fun scrapePage(page: Int): List<DepositRecord> {
        try {
            val request = Request.Builder()
                .url("$BASE_URL?page=$page")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .header("Referer", "https://finance.naver.com/")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.let { responseBody ->
                // Naver Finance uses euc-kr encoding
                val bytes = responseBody.bytes()
                String(bytes, charset("euc-kr"))
            } ?: return emptyList()

            val doc = Jsoup.parse(body)
            val table = doc.select("table.type_1").firstOrNull() ?: return emptyList()
            val rows = table.select("tr")

            val data = mutableListOf<DepositRecord>()
            // Skip header rows (first 2)
            for (i in 2 until rows.size) {
                val cols = rows[i].select("td")
                if (cols.size < 5) continue

                val rawDate = cols[0].text().trim()
                if (rawDate.isEmpty()) continue

                val date = parseDate(rawDate)
                if (date.isEmpty()) continue

                data.add(
                    DepositRecord(
                        date = date,
                        depositAmount = parseNum(cols[1].text().trim()),
                        depositChange = parseNum(cols[2].text().trim()),
                        creditAmount = parseNum(cols[3].text().trim()),
                        creditChange = parseNum(cols[4].text().trim())
                    )
                )
            }

            return data
        } catch (e: Exception) {
            logger.e("Error scraping page $page", e)
            return emptyList()
        }
    }

    private fun parseDate(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return ""

        // Already YYYY-MM-DD
        if (trimmed.length == 10 && trimmed[4] == '-') return trimmed

        // YYYY.MM.DD or YY.MM.DD
        if ('.' in trimmed) {
            val parts = trimmed.split('.')
            if (parts.size == 3) {
                var y = parts[0].trim()
                val m = parts[1].trim().padStart(2, '0')
                val d = parts[2].trim().padStart(2, '0')
                if (y.length == 2) y = "20$y"
                return "$y-$m-$d"
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
                var y = parts[0].trim()
                val m = parts[1].trim().padStart(2, '0')
                val d = parts[2].trim().padStart(2, '0')
                if (y.length == 2) y = "20$y"
                return "$y-$m-$d"
            }
        }

        return trimmed
    }

    private fun parseNum(s: String): Double {
        return try {
            s.replace(",", "")
                .replace("+", "")
                .replace("▲", "")
                .replace("▼", "-")
                .trim()
                .toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private data class DepositRecord(
        val date: String,
        val depositAmount: Double,
        val depositChange: Double,
        val creditAmount: Double,
        val creditChange: Double
    )
}
