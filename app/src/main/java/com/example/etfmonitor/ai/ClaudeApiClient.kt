package com.etfmonitor.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude API 클라이언트
 * Anthropic Claude API를 통한 시장 분석
 *
 * 사용 전 API 키 설정 필요:
 * - Settings에서 CLAUDE_API_KEY 저장
 * - 또는 BuildConfig에 API_KEY 추가
 */
@Singleton
class ClaudeApiClient @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) : AIApiClient {

    override val provider: AIProvider = AIProvider.CLAUDE

    companion object {
        private const val TAG = "ClaudeApiClient"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODELS_API_URL = "https://api.anthropic.com/v1/models"
        private const val MODEL = "claude-3-5-sonnet-20241022" // Latest Sonnet model
        private const val MAX_TOKENS = 2048
        private const val TIMEOUT_SECONDS = 60L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 시장 분석 수행
     */
    override suspend fun analyzeMarket(
        prompt: String,
        temperature: Double
    ): Result<MarketSignal> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE)
            if (apiKey.isNullOrBlank()) {
                Log.e(TAG, "API key not configured")
                return@withContext Result.failure(Exception("Claude API 키가 설정되지 않았습니다. 설정에서 API 키를 등록해주세요."))
            }

            // 선택된 모델 가져오기 (없으면 기본 모델 사용)
            val model = apiKeyProvider.getSelectedModel(AIProvider.CLAUDE) ?: MODEL

            Log.d(TAG, "Analyzing market with Claude API using model: $model")

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val response = callClaudeApi(apiKey, prompt, temperature, model)
                val signal = parseResponse(response)
                Result.success(signal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Market analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Claude API 호출
     */
    private suspend fun callClaudeApi(
        apiKey: String,
        prompt: String,
        temperature: Double,
        model: String = MODEL
    ): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("temperature", temperature)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API call failed: ${response.code} - $errorBody")
                throw Exception("Claude API 호출 실패: ${response.code} - $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from Claude API")

            Log.d(TAG, "API response received: ${responseBody.take(200)}...")

            // Extract content from response
            val jsonResponse = JSONObject(responseBody)
            val content = jsonResponse.getJSONArray("content")
            if (content.length() == 0) {
                throw Exception("No content in Claude API response")
            }

            content.getJSONObject(0).getString("text")
        }
    }

    /**
     * Claude 응답을 MarketSignal로 파싱
     */
    private fun parseResponse(responseText: String): MarketSignal {
        try {
            // JSON 블록 추출 (```json ... ``` 또는 그냥 {...})
            val jsonText = extractJsonFromResponse(responseText)

            Log.d(TAG, "Parsing JSON: $jsonText")

            val jsonElement = json.parseToJsonElement(jsonText).jsonObject

            return MarketSignal(
                market = jsonElement["market"]?.jsonPrimitive?.content ?: "UNKNOWN",
                date = jsonElement["date"]?.jsonPrimitive?.content ?: "",
                signal = parseSignalType(jsonElement["signal"]?.jsonPrimitive?.content ?: "NEUTRAL"),
                confidence = jsonElement["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5,
                upProbability = jsonElement["upProbability"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 50.0,
                downProbability = jsonElement["downProbability"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 50.0,
                reasoning = jsonElement["reasoning"]?.jsonPrimitive?.content ?: "분석 데이터 부족",
                keyFactors = parseKeyFactors(jsonElement["keyFactors"]?.toString() ?: "[]"),
                recommendation = jsonElement["recommendation"]?.jsonPrimitive?.content ?: "추가 분석 필요",
                riskLevel = parseRiskLevel(jsonElement["riskLevel"]?.jsonPrimitive?.content ?: "MEDIUM")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Claude response", e)
            // 파싱 실패 시 기본 신호 반환
            return MarketSignal(
                market = "UNKNOWN",
                date = "",
                signal = SignalType.NEUTRAL,
                confidence = 0.0,
                upProbability = 50.0,
                downProbability = 50.0,
                reasoning = "AI 응답 파싱 실패: ${e.message}",
                keyFactors = listOf("응답 처리 오류"),
                recommendation = "수동 분석 필요",
                riskLevel = RiskLevel.MEDIUM
            )
        }
    }

    /**
     * 응답에서 JSON 블록 추출
     */
    private fun extractJsonFromResponse(text: String): String {
        // ```json ... ``` 블록 찾기
        val jsonBlockRegex = "```json\\s*([\\s\\S]*?)```".toRegex()
        val match = jsonBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // {...} 블록 찾기
        val jsonObjectRegex = "\\{[\\s\\S]*\\}".toRegex()
        val objectMatch = jsonObjectRegex.find(text)
        if (objectMatch != null) {
            return objectMatch.value.trim()
        }

        throw Exception("No JSON found in response")
    }

    /**
     * SignalType 파싱
     */
    private fun parseSignalType(value: String): SignalType {
        return when (value.uppercase()) {
            "STRONG_BUY", "강력매수", "강력 매수" -> SignalType.STRONG_BUY
            "BUY", "매수" -> SignalType.BUY
            "NEUTRAL", "중립" -> SignalType.NEUTRAL
            "SELL", "매도" -> SignalType.SELL
            "STRONG_SELL", "강력매도", "강력 매도" -> SignalType.STRONG_SELL
            else -> SignalType.NEUTRAL
        }
    }

    /**
     * RiskLevel 파싱
     */
    private fun parseRiskLevel(value: String): RiskLevel {
        return when (value.uppercase()) {
            "LOW", "낮음" -> RiskLevel.LOW
            "MEDIUM", "중간" -> RiskLevel.MEDIUM
            "HIGH", "높음" -> RiskLevel.HIGH
            else -> RiskLevel.MEDIUM
        }
    }

    /**
     * keyFactors 배열 파싱
     */
    private fun parseKeyFactors(jsonArray: String): List<String> {
        return try {
            val array = JSONArray(jsonArray)
            List(array.length()) { i ->
                array.getString(i)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse key factors", e)
            emptyList()
        }
    }

    /**
     * API 사용 가능 여부 확인
     */
    override suspend fun isApiAvailable(): Boolean = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE)
        !apiKey.isNullOrBlank()
    }

    /**
     * API 키 유효성 테스트
     */
    override suspend fun testApiKey(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testPrompt = "Hello, please respond with 'OK'"
            val response = callClaudeApi(apiKeyProvider.getApiKey(AIProvider.CLAUDE) ?: "", testPrompt, 0.0)
            Result.success(response.isNotBlank())
        } catch (e: Exception) {
            Log.e(TAG, "API key test failed", e)
            Result.failure(e)
        }
    }

    /**
     * 사용 가능한 Claude 모델 목록 조회
     */
    override suspend fun listModels(): Result<List<AIModel>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Claude API 키가 설정되지 않았습니다."))
            }

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val request = Request.Builder()
                    .url(MODELS_API_URL)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Models API call failed: ${response.code} - $errorBody")
                        throw Exception("Claude 모델 목록 조회 실패: ${response.code}")
                    }

                    val responseBody = response.body?.string()
                        ?: throw Exception("Empty response from Claude Models API")

                    Log.d(TAG, "Models API response: ${responseBody.take(200)}...")

                    val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
                    val data = jsonResponse["data"]?.jsonArray ?: throw Exception("No data in response")

                    val models = data.map { modelElement ->
                        val modelObj = modelElement.jsonObject
                        AIModel(
                            id = modelObj["id"]?.jsonPrimitive?.content ?: "",
                            name = modelObj["display_name"]?.jsonPrimitive?.content ?: modelObj["id"]?.jsonPrimitive?.content ?: "",
                            provider = AIProvider.CLAUDE,
                            description = modelObj["description"]?.jsonPrimitive?.content,
                            contextWindow = modelObj["context_window"]?.jsonPrimitive?.content?.toIntOrNull(),
                            maxOutputTokens = modelObj["max_output_tokens"]?.jsonPrimitive?.content?.toIntOrNull()
                        )
                    }

                    Result.success(models)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list models", e)
            Result.failure(e)
        }
    }
}
