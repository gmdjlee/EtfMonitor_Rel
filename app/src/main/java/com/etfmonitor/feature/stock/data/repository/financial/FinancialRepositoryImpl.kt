package com.etfmonitor.feature.stock.data.repository.financial

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.database.FinancialCacheDao
import com.etfmonitor.core.database.entities.FinancialCache
import com.etfmonitor.core.network.kis.KisApiKeyProvider
import com.etfmonitor.feature.stock.data.dto.*
import com.etfmonitor.feature.stock.domain.model.financial.*
import com.etfmonitor.feature.stock.domain.repository.FinancialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialRepositoryImpl @Inject constructor(
    private val financialCacheDao: FinancialCacheDao,
    private val kisApiKeyProvider: KisApiKeyProvider,
    private val json: Json,
    private val httpClient: OkHttpClient
) : FinancialRepository {

    // OAuth2 token cache
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0
    private var tokenBaseUrl: String? = null
    private val tokenMutex = Mutex()

    override suspend fun getFinancialData(
        ticker: String,
        name: String,
        useCache: Boolean
    ): Result<FinancialData> = withContext(Dispatchers.IO) {
        try {
            val config = kisApiKeyProvider.getConfig()
            if (!config.isValid()) {
                return@withContext Result.failure(
                    IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
                )
            }

            if (useCache) {
                val cached = financialCacheDao.get(ticker)
                if (cached != null && !isCacheExpired(cached.cachedAt)) {
                    try {
                        val cacheData = json.decodeFromString<FinancialDataCache>(cached.data)
                        return@withContext Result.success(cacheData.toData().copy(name = name))
                    } catch (e: Exception) {
                        logger.w("Failed to parse cached data for $ticker, fetching from API", e)
                    }
                }
            }

            refreshFinancialData(ticker, name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshFinancialData(
        ticker: String,
        name: String
    ): Result<FinancialData> = withContext(Dispatchers.IO) {
        try {
            val config = kisApiKeyProvider.getConfig()
            if (!config.isValid()) {
                return@withContext Result.failure(
                    IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
                )
            }

            val baseUrl = config.getBaseUrl()
            val accessToken = getAccessToken(config.appKey, config.appSecret, baseUrl)

            val (balanceSheets, incomeStatements, profitRatios, stabilityRatios, growthRatios) =
                coroutineScope {
                    val bs = async { fetchBalanceSheet(ticker, baseUrl, accessToken, config) }
                    val is_ = async { fetchIncomeStatement(ticker, baseUrl, accessToken, config) }
                    val pr = async { fetchProfitabilityRatios(ticker, baseUrl, accessToken, config) }
                    val sr = async { fetchStabilityRatios(ticker, baseUrl, accessToken, config) }
                    val gr = async { fetchGrowthRatios(ticker, baseUrl, accessToken, config) }
                    FetchResults(bs.await(), is_.await(), pr.await(), sr.await(), gr.await())
                }

            val data = mergeFinancialData(
                ticker, name, balanceSheets, incomeStatements,
                profitRatios, stabilityRatios, growthRatios
            )

            // Cache the result
            val cacheEntity = FinancialCache(
                ticker = ticker,
                name = name,
                data = json.encodeToString(FinancialDataCache.serializer(), data.toCache())
            )
            financialCacheDao.insert(cacheEntity)

            Result.success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Failed to refresh financial data for $ticker", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache(ticker: String) = withContext(Dispatchers.IO) {
        financialCacheDao.delete(ticker)
    }

    override suspend fun clearExpiredCache() = withContext(Dispatchers.IO) {
        financialCacheDao.deleteExpired(System.currentTimeMillis() - CACHE_TTL_MS)
    }

    // ========== OAuth2 Token Management ==========

    private suspend fun getAccessToken(
        appKey: String,
        appSecret: String,
        baseUrl: String
    ): String = tokenMutex.withLock {
        if (cachedToken != null &&
            tokenBaseUrl == baseUrl &&
            System.currentTimeMillis() < tokenExpiresAt - 60_000
        ) {
            return cachedToken!!
        }

        val requestBody = json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(),
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to appKey,
                "appsecret" to appSecret
            )
        )

        val request = Request.Builder()
            .url("$baseUrl/oauth2/tokenP")
            .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val (isSuccessful, code, body) = httpClient.newCall(request).execute().use { response ->
            Triple(response.isSuccessful, response.code, response.body?.string() ?: "")
        }

        if (body.isEmpty()) throw Exception("Empty token response")
        if (!isSuccessful) {
            throw Exception("Token request failed: $code - $body")
        }

        val tokenResponse = json.decodeFromString<KisTokenResponse>(body)
        cachedToken = tokenResponse.accessToken
        tokenExpiresAt = System.currentTimeMillis() + TOKEN_CACHE_DURATION_MS
        tokenBaseUrl = baseUrl

        tokenResponse.accessToken
    }

    // ========== API Fetch Helpers ==========

    private suspend fun fetchBalanceSheet(
        ticker: String,
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig
    ): List<BalanceSheet> = fetchFinancialData(
        baseUrl, accessToken, config,
        "/uapi/domestic-stock/v1/finance/balance-sheet",
        TR_BALANCE_SHEET, ticker, "BalanceSheet"
    ) { mapToBalanceSheet(it) }

    private suspend fun fetchIncomeStatement(
        ticker: String,
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig
    ): List<IncomeStatement> = fetchFinancialData(
        baseUrl, accessToken, config,
        "/uapi/domestic-stock/v1/finance/income-statement",
        TR_INCOME_STATEMENT, ticker, "IncomeStatement"
    ) { mapToIncomeStatement(it) }

    private suspend fun fetchProfitabilityRatios(
        ticker: String,
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig
    ): List<ProfitabilityRatios> = fetchFinancialData(
        baseUrl, accessToken, config,
        "/uapi/domestic-stock/v1/finance/profit-ratio",
        TR_PROFITABILITY, ticker, "ProfitabilityRatios"
    ) { mapToProfitabilityRatios(it) }

    private suspend fun fetchStabilityRatios(
        ticker: String,
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig
    ): List<StabilityRatios> = fetchFinancialData(
        baseUrl, accessToken, config,
        "/uapi/domestic-stock/v1/finance/stability-ratio",
        TR_STABILITY, ticker, "StabilityRatios"
    ) { mapToStabilityRatios(it) }

    private suspend fun fetchGrowthRatios(
        ticker: String,
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig
    ): List<GrowthRatios> = fetchFinancialData(
        baseUrl, accessToken, config,
        "/uapi/domestic-stock/v1/finance/growth-ratio",
        TR_GROWTH, ticker, "GrowthRatios"
    ) { mapToGrowthRatios(it) }

    private suspend fun <T> fetchFinancialData(
        baseUrl: String,
        accessToken: String,
        config: com.etfmonitor.core.network.kis.KisApiKeyConfig,
        endpoint: String,
        trId: String,
        ticker: String,
        dataTypeLabel: String,
        mapper: (Map<String, String?>) -> T?
    ): List<T> {
        try {
            val url = "$baseUrl$endpoint" +
                "?FID_DIV_CLS_CODE=1" +
                "&FID_COND_MRKT_DIV_CODE=J" +
                "&FID_INPUT_ISCD=$ticker"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("content-type", "application/json; charset=utf-8")
                .addHeader("authorization", "Bearer $accessToken")
                .addHeader("appkey", config.appKey)
                .addHeader("appsecret", config.appSecret)
                .addHeader("tr_id", trId)
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                response.body?.string() ?: throw Exception("Empty response for $dataTypeLabel")
            }

            val apiResponse = json.decodeFromString<KisApiResponse>(body)

            if (apiResponse.rtCd != "0") {
                throw Exception("API error: ${apiResponse.msgCd} - ${apiResponse.msg1}")
            }

            val output = apiResponse.actualOutput ?: return emptyList()
            return output.mapNotNull { mapper(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("Failed to fetch $dataTypeLabel for $ticker", e)
            return emptyList()
        }
    }

    // ========== Data Merging ==========

    private fun mergeFinancialData(
        ticker: String,
        name: String,
        balanceSheets: List<BalanceSheet>,
        incomeStatements: List<IncomeStatement>,
        profitRatios: List<ProfitabilityRatios>,
        stabilityRatios: List<StabilityRatios>,
        growthRatios: List<GrowthRatios>
    ): FinancialData {
        val allPeriods = mutableSetOf<String>()
        balanceSheets.forEach { allPeriods.add(it.period.yearMonth) }
        incomeStatements.forEach { allPeriods.add(it.period.yearMonth) }
        profitRatios.forEach { allPeriods.add(it.period.yearMonth) }
        stabilityRatios.forEach { allPeriods.add(it.period.yearMonth) }
        growthRatios.forEach { allPeriods.add(it.period.yearMonth) }

        return FinancialData(
            ticker = ticker,
            name = name,
            periods = allPeriods.sorted(),
            balanceSheets = balanceSheets.associateBy { it.period.yearMonth },
            incomeStatements = incomeStatements.associateBy { it.period.yearMonth },
            profitabilityRatios = profitRatios.associateBy { it.period.yearMonth },
            stabilityRatios = stabilityRatios.associateBy { it.period.yearMonth },
            growthRatios = growthRatios.associateBy { it.period.yearMonth }
        )
    }

    private fun isCacheExpired(cachedAt: Long): Boolean {
        return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS
    }

    private data class FetchResults(
        val balanceSheets: List<BalanceSheet>,
        val incomeStatements: List<IncomeStatement>,
        val profitabilityRatios: List<ProfitabilityRatios>,
        val stabilityRatios: List<StabilityRatios>,
        val growthRatios: List<GrowthRatios>
    )

    companion object {
        private val logger = AppLogger.getLogger("FinancialRepoImpl")
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val TOKEN_CACHE_DURATION_MS = 23 * 60 * 60 * 1000L // 23 hours

        private const val TR_BALANCE_SHEET = "FHKST66430100"
        private const val TR_INCOME_STATEMENT = "FHKST66430200"
        private const val TR_PROFITABILITY = "FHKST66430400"
        private const val TR_STABILITY = "FHKST66430600"
        private const val TR_GROWTH = "FHKST66430800"
    }
}
