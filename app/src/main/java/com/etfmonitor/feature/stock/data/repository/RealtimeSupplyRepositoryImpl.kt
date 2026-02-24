package com.etfmonitor.feature.stock.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.network.kiwoom.KiwoomApiClient
import com.etfmonitor.core.network.kiwoom.KiwoomApiError
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.feature.stock.data.dto.RealtimeSupplyItemDto
import com.etfmonitor.feature.stock.data.dto.RealtimeSupplyResponse
import com.etfmonitor.feature.stock.domain.model.RealtimeSupplyData
import com.etfmonitor.feature.stock.domain.repository.RealtimeSupplyRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeSupplyRepositoryImpl @Inject constructor(
    private val apiClient: KiwoomApiClient,
    private val kiwoomApiKeyProvider: KiwoomApiKeyProvider,
    private val json: Json
) : RealtimeSupplyRepository {

    private val cache = ConcurrentHashMap<String, Pair<Long, RealtimeSupplyData>>()
    private val cacheTtlMs = 60_000L // 60 seconds

    private fun getApiConfig(): Triple<String, String, String> {
        val config = kiwoomApiKeyProvider.getConfig()
        if (!config.isValid()) {
            throw KiwoomApiError.NoApiKeyError()
        }
        return Triple(config.appKey, config.secretKey, config.getBaseUrl())
    }

    override suspend fun getRealtimeSupply(ticker: String, useCache: Boolean): Result<RealtimeSupplyData> {
        if (useCache) {
            cache[ticker]?.let { (timestamp, data) ->
                if (System.currentTimeMillis() - timestamp < cacheTtlMs) {
                    logger.d("Cache hit for ticker: $ticker")
                    return Result.success(data)
                }
            }
        }

        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()

            val stexTp = if (kiwoomApiKeyProvider.getConfig().investmentMode.name == "MOCK") "3" else "1"

            val body = mapOf(
                "stk_cd" to ticker,
                "mrkt_tp" to "000",
                "invsr" to "6",
                "stex_tp" to stexTp,
                "amt_qty_tp" to "1",
                "frgn_all" to "0",
                "smtm_netprps_tp" to "0"
            )

            apiClient.call(
                apiId = "ka10063",
                url = "/api/dostk/mrkcond",
                body = body,
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                parseRealtimeSupplyResponse(responseJson, ticker)
            }.also { result ->
                result.getOrNull()?.let { data ->
                    cache[ticker] = System.currentTimeMillis() to data
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        } catch (e: Exception) {
            logger.e("Unexpected error fetching realtime supply for $ticker: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun clearCache(ticker: String) {
        cache.remove(ticker)
        logger.d("Cache cleared for ticker: $ticker")
    }

    private fun parseRealtimeSupplyResponse(responseJson: String, ticker: String): RealtimeSupplyData {
        val items = findAndParseItemsArray(responseJson)
        val item = items.firstOrNull()
            ?: return emptySupplyData(ticker)

        return RealtimeSupplyData(
            ticker = item.stkCd?.trim()?.ifEmpty { ticker } ?: ticker,
            name = item.stkNm?.trim() ?: "",
            currentPrice = parseSignedLong(item.currentPrice),
            netBuyAmount = parseSignedLong(item.netBuyAmount),
            buyAmount = parseSignedLong(item.buyAmount),
            sellAmount = parseSignedLong(item.sellAmount),
            netBuyQuantity = parseSignedLong(item.netBuyQuantity),
            accumulatedVolume = parseSignedLong(item.accumulatedVolume),
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun findAndParseItemsArray(responseJson: String): List<RealtimeSupplyItemDto> {
        try {
            val rootObject = json.parseToJsonElement(responseJson).jsonObject
            val skipFields = setOf("return_code", "return_msg", "msg_cd", "msg1")

            for ((key, value) in rootObject.entries) {
                if (key in skipFields) continue

                if (value is JsonArray) {
                    if (value.isEmpty()) {
                        logger.d("Found data array in field: $key but it is empty")
                        return emptyList()
                    }

                    val firstElement = value.firstOrNull()
                    if (firstElement is JsonObject) {
                        logger.d("Found items array in field: $key with ${value.size} items")
                        return json.decodeFromJsonElement<List<RealtimeSupplyItemDto>>(value)
                    }
                }
            }

            // Fallback: try typed parsing with known field name
            val typedResponse = json.decodeFromString<RealtimeSupplyResponse>(responseJson)
            if (!typedResponse.items.isNullOrEmpty()) {
                logger.d("Parsed items via typed response: ${typedResponse.items.size} items")
                return typedResponse.items
            }

            logger.w("No data array field found in response. Available fields: ${rootObject.keys}")
            return emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e("Error parsing realtime supply items array: ${e.message}")
            return emptyList()
        }
    }

    private fun parseSignedLong(value: String?): Long =
        value?.replace(",", "")?.replace("+", "")?.trim()?.toLongOrNull() ?: 0L

    private fun emptySupplyData(ticker: String): RealtimeSupplyData = RealtimeSupplyData(
        ticker = ticker,
        name = "",
        currentPrice = 0L,
        netBuyAmount = 0L,
        buyAmount = 0L,
        sellAmount = 0L,
        netBuyQuantity = 0L,
        accumulatedVolume = 0L,
        fetchedAt = System.currentTimeMillis()
    )

    companion object {
        private val logger = AppLogger.getLogger("RealtimeSupplyRepositoryImpl")
    }
}
