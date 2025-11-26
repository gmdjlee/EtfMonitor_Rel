package com.etfmonitor.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
 * Google Gemini API 클라이언트
 * Gemini 1.5 Flash 모델을 통한 시장 분석
 *
 * 사용 전 API 키 설정 필요:
 * - Settings에서 GEMINI_API_KEY 저장
 */
@Singleton
class GeminiApiClient @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) : AIApiClient {

    override val provider: AIProvider = AIProvider.GEMINI

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-1.5-flash-latest" // Updated model name
        private const val MAX_OUTPUT_TOKENS = 2048
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
            val apiKey = apiKeyProvider.getApiKey(AIProvider.GEMINI)
            if (apiKey.isNullOrBlank()) {
                Log.e(TAG, "API key not configured")
                return@withContext Result.failure(Exception("Gemini API 키가 설정되지 않았습니다. 설정에서 API 키를 등록해주세요."))
            }

            Log.d(TAG, "Analyzing market with Gemini API...")

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val response = callGeminiApi(apiKey, prompt, temperature)
                val signal = parseResponse(response)
                Result.success(signal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Market analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Gemini API 호출
     */
    private suspend fun callGeminiApi(
        apiKey: String,
        prompt: String,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            })
        }

        val url = "$API_BASE_URL/$MODEL:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API call failed: ${response.code} - $errorBody")
                throw Exception("Gemini API 호출 실패: ${response.code} - $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from Gemini API")

            Log.d(TAG, "API response received: ${responseBody.take(200)}...")

            // Extract text from response
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() == 0) {
                throw Exception("No candidates in Gemini API response")
            }

            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) {
                throw Exception("No parts in Gemini API response")
            }

            parts.getJSONObject(0).getString("text")
        }
    }

    /**
     * Gemini 응답을 MarketSignal로 파싱
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
            Log.e(TAG, "Failed to parse Gemini response", e)
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
        val apiKey = apiKeyProvider.getApiKey(AIProvider.GEMINI)
        !apiKey.isNullOrBlank()
    }

    /**
     * API 키 유효성 테스트
     */
    override suspend fun testApiKey(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.GEMINI) ?: ""
            val testPrompt = "Hello, please respond with 'OK'"
            val response = callGeminiApi(apiKey, testPrompt, 0.0)
            Result.success(response.isNotBlank())
        } catch (e: Exception) {
            Log.e(TAG, "API key test failed", e)
            Result.failure(e)
        }
    }
}
