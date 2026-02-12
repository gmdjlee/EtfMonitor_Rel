package com.etfmonitor.core.network.krx

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fear & Greed Index 네이티브 Kotlin 클라이언트
 *
 * feargreed.py (Python/Chaquopy)를 대체하는 네이티브 구현
 * KRX API에 직접 HTTP POST를 수행하여 데이터 수집 및 분석
 *
 * ## 5개 지표 (각 20% 가중치)
 * 1. Momentum: (지수 - MA) / MA * 100
 * 2. PCR: Put / Call (5일 이동평균)
 * 3. Volatility: VKOSPI
 * 4. Spread: 10년국채 - 5년국채
 * 5. RSI: 10일 RSI
 *
 * ## 데이터 소스
 * - 콜/풋 옵션 거래량 (KRX 파생상품)
 * - 5년/10년 국채수익률 (KRX 지수)
 * - VKOSPI (KRX 변동성지수)
 * - KOSPI/KOSDAQ 지수 (KRX 시장지수)
 */
@Singleton
class FearGreedClient @Inject constructor() {

    companion object {
        private val logger = AppLogger.getLogger("FearGreedClient")
        private const val KRX_URL = "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd"
        private const val KRX_SESSION_URL = "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd"
        private const val REQ_DELAY_MS = 300L
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fear & Greed Index 분석 실행
     *
     * @param startDate 시작일 (yyyyMMdd)
     * @param endDate 종료일 (yyyyMMdd)
     * @return (KOSPI 결과, KOSDAQ 결과) 쌍
     */
    suspend fun runAnalysis(
        startDate: String,
        endDate: String
    ): Pair<List<FearGreedEntity>, List<FearGreedEntity>> = withContext(Dispatchers.IO) {
        try {
            logger.d("Fear & Greed analysis: $startDate ~ $endDate")

            // Initialize KRX session
            initSession()

            // Fetch option data
            val callData = fetchOption(startDate, endDate, "C")
            delay(REQ_DELAY_MS)
            val putData = fetchOption(startDate, endDate, "P")
            delay(REQ_DELAY_MS)

            if (callData.isEmpty() || putData.isEmpty()) {
                logger.e("Failed to fetch option data")
                return@withContext Pair(emptyList(), emptyList())
            }

            // Fetch index data
            val bond5y = fetchIndex(startDate, endDate, "5년국채")
            delay(REQ_DELAY_MS)
            val bond10y = fetchIndex(startDate, endDate, "10년국채")
            delay(REQ_DELAY_MS)
            val vkospi = fetchIndex(startDate, endDate, "VKOSPI")
            delay(REQ_DELAY_MS)
            val kospi = fetchIndex(startDate, endDate, "KOSPI")
            delay(REQ_DELAY_MS)
            val kosdaq = fetchIndex(startDate, endDate, "KOSDAQ")

            if (bond5y.isEmpty() || bond10y.isEmpty() || vkospi.isEmpty()) {
                logger.e("Failed to fetch required index data")
                return@withContext Pair(emptyList(), emptyList())
            }

            // Calculate 5-day MA for options
            val callMa = rollingMean(callData.map { it.second }, 5)
            val putMa = rollingMean(putData.map { it.second }, 5)
            val callDatesValues = callData.mapIndexed { i, (date, _) -> date to (callMa[i] ?: 0.0) }
            val putDatesValues = putData.mapIndexed { i, (date, _) -> date to (putMa[i] ?: 0.0) }

            // Merge all data by date
            val allDates = (callDatesValues.map { it.first } +
                    putDatesValues.map { it.first } +
                    bond5y.map { it.first } +
                    bond10y.map { it.first } +
                    vkospi.map { it.first } +
                    kospi.map { it.first } +
                    kosdaq.map { it.first })
                .distinct().sorted()

            val callMap = callDatesValues.toMap()
            val putMap = putDatesValues.toMap()
            val b5Map = bond5y.toMap()
            val b10Map = bond10y.toMap()
            val vixMap = vkospi.toMap()
            val kospiMap = kospi.toMap()
            val kosdaqMap = kosdaq.toMap()

            // Build merged dataset
            val merged = allDates.map { date ->
                MergedRow(
                    date = date,
                    call = callMap[date],
                    put = putMap[date],
                    bond5y = b5Map[date],
                    bond10y = b10Map[date],
                    vix = vixMap[date],
                    kospiVal = kospiMap[date],
                    kosdaqVal = kosdaqMap[date]
                )
            }.filter { row ->
                // Drop rows missing required data
                row.call != null && row.put != null &&
                        row.bond5y != null && row.bond10y != null && row.vix != null
            }

            if (merged.size < 15) {
                logger.e("Insufficient data: ${merged.size} rows (min 15 required)")
                return@withContext Pair(emptyList(), emptyList())
            }

            logger.d("Combined data: ${merged.size} rows")

            // Analyze KOSPI
            val kospiResult = if (merged.any { it.kospiVal != null }) {
                calcFearGreed(merged, "KOSPI") { it.kospiVal }
            } else emptyList()

            // Analyze KOSDAQ
            val kosdaqResult = if (merged.any { it.kosdaqVal != null }) {
                calcFearGreed(merged, "KOSDAQ") { it.kosdaqVal }
            } else emptyList()

            logger.d("KOSPI FG: ${kospiResult.size} rows, KOSDAQ FG: ${kosdaqResult.size} rows")
            Pair(kospiResult, kosdaqResult)
        } catch (e: Exception) {
            logger.e("Analysis error", e)
            Pair(emptyList(), emptyList())
        }
    }

    // ======== Fear & Greed Calculation ========

    private fun <T> calcFearGreed(
        data: List<T>,
        market: String,
        getDate: (T) -> String = { (it as MergedRow).date },
        getCall: (T) -> Double? = { (it as MergedRow).call },
        getPut: (T) -> Double? = { (it as MergedRow).put },
        getBond5y: (T) -> Double? = { (it as MergedRow).bond5y },
        getBond10y: (T) -> Double? = { (it as MergedRow).bond10y },
        getVix: (T) -> Double? = { (it as MergedRow).vix },
        getIndexVal: (T) -> Double?
    ): List<FearGreedEntity> where T : Any {
        // Filter rows where index value is present
        val validData = data.filter { getIndexVal(it) != null }
        if (validData.size < 15) return emptyList()

        val n = validData.size
        val maPeriod = min(125, max(10, (n * 0.9).toInt()))

        // Extract raw values
        val indexValues = validData.map { getIndexVal(it)!! }
        val callValues = validData.map { getCall(it) ?: 0.0 }
        val putValues = validData.map { getPut(it) ?: 0.0 }
        val bond5yValues = validData.map { getBond5y(it) ?: 0.0 }
        val bond10yValues = validData.map { getBond10y(it) ?: 0.0 }
        val vixValues = validData.map { getVix(it) ?: 0.0 }

        // Calculate raw indicators
        val ma = rollingMean(indexValues, maPeriod)
        val rawMom = indexValues.mapIndexed { i, v ->
            val maVal = ma[i]
            if (maVal != null && maVal != 0.0) (v - maVal) / maVal * 100 else null
        }
        val rawPcr = putValues.mapIndexed { i, put ->
            val call = callValues[i]
            if (call != 0.0) put / call else null
        }
        val rawVol = vixValues.map { if (it != 0.0) it else null }
        val rawSpread = bond10yValues.mapIndexed { i, b10 -> b10 - bond5yValues[i] }
        val rawRsi = calcRsi(indexValues, 10)

        // Collect valid feature vectors for MinMax scaling
        val featureIndices = mutableListOf<Int>()
        val rawFeatures = mutableListOf<List<Double>>() // [mom, pcr, vol, spread, rsi] per row

        for (i in validData.indices) {
            val mom = rawMom[i]
            val pcr = rawPcr[i]
            val vol = rawVol[i]
            val spread = rawSpread[i]
            val rsi = rawRsi[i]

            if (mom != null && pcr != null && vol != null && rsi != null) {
                featureIndices.add(i)
                rawFeatures.add(listOf(mom, pcr, vol, spread, rsi))
            }
        }

        if (rawFeatures.isEmpty()) return emptyList()

        // MinMax scaling
        val featureCount = 5
        val mins = DoubleArray(featureCount) { col -> rawFeatures.minOf { it[col] } }
        val maxs = DoubleArray(featureCount) { col -> rawFeatures.maxOf { it[col] } }

        val scaledFeatures = rawFeatures.map { row ->
            row.mapIndexed { col, v ->
                val range = maxs[col] - mins[col]
                if (range > 0) (v - mins[col]) / range else 0.5
            }
        }

        // Calculate FG = Mom*0.2 + (1-PCR)*0.2 + (1-Vol)*0.2 + Spread*0.2 + RSI*0.2
        val fgValues = scaledFeatures.map { feat ->
            feat[0] * 0.2 + (1 - feat[1]) * 0.2 + (1 - feat[2]) * 0.2 + feat[3] * 0.2 + feat[4] * 0.2
        }

        // Calculate MACD oscillator on FG
        val fgOsc = calcMacdOscillator(fgValues)

        // Build results
        val results = mutableListOf<FearGreedEntity>()
        for (j in featureIndices.indices) {
            val i = featureIndices[j]
            val dateStr = getDate(validData[i])
            val formattedDate = DateFormatter.formatFromYYYYMMDD(dateStr)
            val fg = fgValues[j]
            val osc = fgOsc[j]

            if (fg.isNaN() || osc.isNaN()) continue

            results.add(
                FearGreedEntity(
                    id = "$market-$formattedDate",
                    market = market,
                    date = formattedDate,
                    indexValue = indexValues[i],
                    fearGreedValue = fg,
                    oscillator = osc,
                    rsi = scaledFeatures[j][4],
                    momentum = scaledFeatures[j][0],
                    putCallRatio = scaledFeatures[j][1],
                    volatility = scaledFeatures[j][2],
                    spread = scaledFeatures[j][3],
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }

        return results
    }

    // ======== Technical Indicators ========

    private fun calcRsi(values: List<Double>, period: Int): List<Double?> {
        if (values.size < 2) return values.map { null }

        val deltas = listOf<Double?>(null) + values.zipWithNext { a, b -> b - a }
        val result = mutableListOf<Double?>()

        for (i in values.indices) {
            if (i < period) {
                result.add(null)
                continue
            }

            var avgGain = 0.0
            var avgLoss = 0.0
            for (j in (i - period + 1)..i) {
                val d = deltas[j] ?: 0.0
                if (d > 0) avgGain += d else avgLoss += abs(d)
            }
            avgGain /= period
            avgLoss /= period

            val rsi = if (avgLoss == 0.0) 100.0
            else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
            result.add(rsi)
        }

        return result
    }

    private fun calcMacdOscillator(
        values: List<Double>,
        shortPeriod: Int = 12,
        longPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<Double> {
        if (values.isEmpty()) return emptyList()

        val emaShort = calcEma(values, shortPeriod)
        val emaLong = calcEma(values, longPeriod)
        val macd = emaShort.zip(emaLong) { s, l -> s - l }
        val signal = calcEma(macd, signalPeriod)
        return macd.zip(signal) { m, s -> m - s }
    }

    private fun calcEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf(values[0])
        for (i in 1 until values.size) {
            result.add(values[i] * multiplier + result[i - 1] * (1 - multiplier))
        }
        return result
    }

    private fun rollingMean(values: List<Double>, window: Int): List<Double?> {
        return values.mapIndexed { i, _ ->
            if (i < window - 1) null
            else {
                val start = i - window + 1
                values.subList(start, i + 1).average()
            }
        }
    }

    // ======== KRX Data Fetching ========

    private fun initSession() {
        try {
            val request = Request.Builder()
                .url(KRX_SESSION_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            logger.w("Session init failed: ${e.message}")
        }
    }

    /**
     * 옵션 거래량 데이터 조회
     */
    private fun fetchOption(start: String, end: String, optType: String): List<Pair<String, Double>> {
        logger.d("Fetching option data: $start ~ $end, type=$optType")

        val params = mapOf(
            "bld" to "dbms/MDC/STAT/standard/MDCSTAT13102",
            "inqTpCd" to "2", "prtType" to "QTY", "prtCheck" to "SU",
            "isuCd02" to "KR___OPK2I", "isuCd" to "KR___OPK2I",
            "prodId" to "KR___OPK2I", "aggBasTpCd" to "",
            "strtDd" to start, "endDd" to end, "isuOpt" to optType
        )

        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (key, value) -> formBuilder.add(key, value) }

            val request = Request.Builder()
                .url(KRX_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", "https://data.krx.co.kr")
                .header("Referer", "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd")
                .post(formBuilder.build())
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val rows = jsonObj["block1"]?.jsonArray
                ?: jsonObj["output"]?.jsonArray
                ?: return emptyList()

            return rows.mapNotNull { row ->
                val obj = row.jsonObject
                val date = obj["TRD_DD"]?.jsonPrimitive?.content?.let {
                    DateFormatter.formatFromYYYYMMDD(it.replace("/", "").replace("-", ""))
                } ?: return@mapNotNull null
                val value = obj["AMT_OR_QTY"]?.jsonPrimitive?.content?.let { parseNum(it) }
                    ?: return@mapNotNull null
                date to value
            }.sortedBy { it.first }
        } catch (e: Exception) {
            logger.e("Error fetching option type $optType", e)
            return emptyList()
        }
    }

    /**
     * 지수 데이터 조회 (국채, VKOSPI, KOSPI, KOSDAQ)
     */
    private fun fetchIndex(start: String, end: String, key: String): List<Pair<String, Double>> {
        data class IndexConfig(
            val type: String,
            val params: Map<String, String>
        )

        val config = when (key) {
            "5년국채" -> IndexConfig("D", mapOf(
                "bld" to "dbms/MDC/STAT/standard/MDCSTAT01201",
                "locale" to "ko_KR",
                "indTpCd" to "D", "idxIndCd" to "896",
                "idxCd" to "D", "idxCd2" to "896",
                "strtDd" to start, "endDd" to end, "csvxls_isNo" to "false"
            ))
            "10년국채" -> IndexConfig("D", mapOf(
                "bld" to "dbms/MDC/STAT/standard/MDCSTAT01201",
                "locale" to "ko_KR",
                "indTpCd" to "1", "idxIndCd" to "309",
                "idxCd" to "1", "idxCd2" to "309",
                "strtDd" to start, "endDd" to end, "csvxls_isNo" to "false"
            ))
            "VKOSPI" -> IndexConfig("D", mapOf(
                "bld" to "dbms/MDC/STAT/standard/MDCSTAT01201",
                "locale" to "ko_KR",
                "indTpCd" to "1", "idxIndCd" to "300",
                "idxCd" to "1", "idxCd2" to "300",
                "strtDd" to start, "endDd" to end, "csvxls_isNo" to "false"
            ))
            "KOSPI" -> IndexConfig("M", mapOf(
                "bld" to "dbms/MDC/STAT/standard/MDCSTAT00301",
                "locale" to "ko_KR",
                "indIdx" to "1", "indIdx2" to "001",
                "strtDd" to start, "endDd" to end,
                "share" to "2", "money" to "3", "csvxls_isNo" to "false"
            ))
            "KOSDAQ" -> IndexConfig("M", mapOf(
                "bld" to "dbms/MDC/STAT/standard/MDCSTAT00301",
                "locale" to "ko_KR",
                "indIdx" to "2", "indIdx2" to "001",
                "strtDd" to start, "endDd" to end,
                "share" to "2", "money" to "3", "csvxls_isNo" to "false"
            ))
            else -> return emptyList()
        }

        try {
            val formBuilder = FormBody.Builder()
            config.params.forEach { (k, v) -> formBuilder.add(k, v) }

            val request = Request.Builder()
                .url(KRX_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", "https://data.krx.co.kr")
                .header("Referer", "https://data.krx.co.kr/contents/MDC/MDI/outerLoader/index.cmd")
                .post(formBuilder.build())
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val rows = jsonObj["block1"]?.jsonArray
                ?: jsonObj["output"]?.jsonArray
                ?: return emptyList()

            return rows.mapNotNull { row ->
                val obj = row.jsonObject
                val date = obj["TRD_DD"]?.jsonPrimitive?.content?.let {
                    DateFormatter.formatFromYYYYMMDD(it.replace("/", "").replace("-", ""))
                } ?: return@mapNotNull null
                val value = obj["CLSPRC_IDX"]?.jsonPrimitive?.content?.let { parseNum(it) }
                    ?: return@mapNotNull null
                date to value
            }.sortedBy { it.first }
        } catch (e: Exception) {
            logger.e("Error fetching index $key", e)
            return emptyList()
        }
    }

    private fun parseNum(s: String): Double {
        return s.replace(",", "").replace("+", "").trim().toDoubleOrNull() ?: 0.0
    }

    // Inner data class for merged rows
    private data class MergedRow(
        val date: String,
        val call: Double?,
        val put: Double?,
        val bond5y: Double?,
        val bond10y: Double?,
        val vix: Double?,
        val kospiVal: Double?,
        val kosdaqVal: Double?
    )
}
