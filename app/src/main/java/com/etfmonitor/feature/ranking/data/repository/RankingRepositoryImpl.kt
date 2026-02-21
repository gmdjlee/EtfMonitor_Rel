package com.etfmonitor.feature.ranking.data.repository

import com.etfmonitor.core.common.util.AppLogger
import com.etfmonitor.core.network.kiwoom.KiwoomApiClient
import com.etfmonitor.core.network.kiwoom.KiwoomApiError
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.feature.ranking.data.dto.ForeignInstitutionTopResponse
import com.etfmonitor.feature.ranking.data.dto.RankingItemDto
import com.etfmonitor.feature.ranking.domain.model.*
import com.etfmonitor.feature.ranking.domain.repository.RankingRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RankingRepositoryImpl @Inject constructor(
    private val apiClient: KiwoomApiClient,
    private val kiwoomApiKeyProvider: KiwoomApiKeyProvider,
    private val json: Json
) : RankingRepository {

    private fun getApiConfig(): Triple<String, String, String> {
        val config = kiwoomApiKeyProvider.getConfig()
        if (!config.isValid()) {
            throw KiwoomApiError.NoApiKeyError()
        }
        return Triple(config.appKey, config.secretKey, config.getBaseUrl())
    }

    override suspend fun getOrderBookSurge(params: OrderBookSurgeParams): Result<RankingResult> {
        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()
            val orderBookDirection = if (params.tradeType == "1") {
                OrderBookDirection.BUY
            } else {
                OrderBookDirection.SELL
            }

            apiClient.call(
                apiId = "ka10021",
                url = "/api/dostk/rkinfo",
                body = params.toRequestBody(),
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                val items = findAndParseItemsArray(responseJson)
                RankingParsers.parseOrderBookSurgeItems(items, params, orderBookDirection)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        }
    }

    override suspend fun getVolumeSurge(params: VolumeSurgeParams): Result<RankingResult> {
        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()

            apiClient.call(
                apiId = "ka10023",
                url = "/api/dostk/rkinfo",
                body = params.toRequestBody(),
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                val items = findAndParseItemsArray(responseJson)
                RankingParsers.parseVolumeSurgeItems(items, params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        }
    }

    override suspend fun getDailyVolumeTop(params: DailyVolumeTopParams): Result<RankingResult> {
        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()

            apiClient.call(
                apiId = "ka10030",
                url = "/api/dostk/rkinfo",
                body = params.toRequestBody(),
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                val items = findAndParseItemsArray(responseJson)
                RankingParsers.parseDailyVolumeTopItems(items, params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        }
    }

    override suspend fun getCreditRatioTop(params: CreditRatioTopParams): Result<RankingResult> {
        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()

            apiClient.call(
                apiId = "ka10033",
                url = "/api/dostk/rkinfo",
                body = params.toRequestBody(),
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                val items = findAndParseItemsArray(responseJson)
                RankingParsers.parseCreditRatioTopItems(items, params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        }
    }

    override suspend fun getForeignInstitutionTop(params: ForeignInstitutionTopParams): Result<RankingResult> {
        return try {
            val (appKey, secretKey, baseUrl) = getApiConfig()

            apiClient.call(
                apiId = "ka90009",
                url = "/api/dostk/rkinfo",
                body = params.toRequestBody(),
                appKey = appKey,
                secretKey = secretKey,
                baseUrl = baseUrl
            ) { responseJson ->
                val response = json.decodeFromString<ForeignInstitutionTopResponse>(responseJson)
                RankingParsers.parseForeignInstitutionTopResponse(response, params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: KiwoomApiError) {
            Result.failure(e)
        }
    }

    private fun findAndParseItemsArray(responseJson: String): List<RankingItemDto> {
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
                        return json.decodeFromJsonElement<List<RankingItemDto>>(value)
                    }
                }
            }

            logger.w("No data array field found in response. Available fields: ${rootObject.keys}")
            return emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e("Error parsing items array: ${e.message}")
            return emptyList()
        }
    }

    companion object {
        private val logger = AppLogger.getLogger("RankingRepositoryImpl")
    }
}
