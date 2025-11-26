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
        private const val MODELS_API_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-2.0-flash-exp" // Default model - Gemini 2.0 Flash (experimental)
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

            // 선택된 모델 가져오기 (없으면 기본 모델 사용)
            var model = apiKeyProvider.getSelectedModel(AIProvider.GEMINI) ?: MODEL
            Log.d(TAG, "Retrieved model from settings: $model")

            // 잘못된 모델명 검증 및 수정
            model = validateAndFixModelName(model)
            Log.d(TAG, "Validated model name: $model")

            Log.d(TAG, "Analyzing market with Gemini API using model: $model")

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val response = callGeminiApi(apiKey, prompt, temperature, model)
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
        temperature: Double,
        model: String = MODEL
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

        val url = "$API_BASE_URL/$model:generateContent?key=$apiKey"
        Log.d(TAG, "Calling Gemini API with URL: $API_BASE_URL/$model:generateContent")

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API call failed for model '$model': ${response.code} - $errorBody")
                throw Exception("Gemini API 호출 실패 (모델: $model): ${response.code} - $errorBody")
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
     * 모델명 검증 및 수정
     * - 공백이나 잘못된 문자 처리
     * - '-latest' 접미사 제거
     * - 잘못된 모델명을 기본값으로 대체
     */
    private fun validateAndFixModelName(modelName: String): String {
        var fixedModel = modelName.trim()

        // 공백이 포함된 경우 (예: "gemini 2.5 pro")
        if (fixedModel.contains(" ")) {
            Log.w(TAG, "Invalid model name with spaces: '$fixedModel'")
            fixedModel = MODEL
            apiKeyProvider.setSelectedModel(AIProvider.GEMINI, MODEL)
            return fixedModel
        }

        // -latest 접미사 제거 (v1beta에서 지원 안됨)
        if (fixedModel.endsWith("-latest")) {
            Log.w(TAG, "Model name with '-latest' suffix: $fixedModel, removing suffix")
            fixedModel = fixedModel.removeSuffix("-latest")
            apiKeyProvider.setSelectedModel(AIProvider.GEMINI, fixedModel)
        }

        // 유효한 모델명 패턴 검증 (gemini-x.x-xxx 형식, -exp 접미사 허용)
        val validPattern = "^gemini-[0-9]+(\\.[0-9]+)?-[a-z]+(-[a-z]+)?(-exp)?$".toRegex()
        if (!validPattern.matches(fixedModel)) {
            Log.w(TAG, "Invalid model name format: '$fixedModel', using default: $MODEL")
            fixedModel = MODEL
            apiKeyProvider.setSelectedModel(AIProvider.GEMINI, MODEL)
        }

        return fixedModel
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

    /**
     * 사용 가능한 Gemini 모델 목록 조회
     */
    override suspend fun listModels(): Result<List<AIModel>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.GEMINI)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Gemini API 키가 설정되지 않았습니다."))
            }

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val url = "$MODELS_API_URL?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Models API call failed: ${response.code} - $errorBody")
                        throw Exception("Gemini 모델 목록 조회 실패: ${response.code}")
                    }

                    val responseBody = response.body?.string()
                        ?: throw Exception("Empty response from Gemini Models API")

                    Log.d(TAG, "Models API response: ${responseBody.take(200)}...")

                    val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
                    val modelsArray = jsonResponse["models"]?.jsonArray
                        ?: throw Exception("No models in response")

                    val models = modelsArray.mapNotNull { modelElement ->
                        val modelObj = modelElement.jsonObject
                        val modelName = modelObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null

                        // Extract model ID from "models/gemini-pro" format
                        val modelId = modelName.removePrefix("models/")

                        // Filter to only include generative models
                        val supportedGenerationMethods = modelObj["supportedGenerationMethods"]?.jsonArray
                        if (supportedGenerationMethods?.any {
                            it.jsonPrimitive.content == "generateContent"
                        } != true) {
                            Log.d(TAG, "Skipping model $modelId (doesn't support generateContent)")
                            return@mapNotNull null
                        }

                        val displayName = modelObj["displayName"]?.jsonPrimitive?.content ?: modelId
                        Log.d(TAG, "Found model - ID: $modelId, Name: $displayName")

                        AIModel(
                            id = modelId,
                            name = displayName,
                            provider = AIProvider.GEMINI,
                            description = modelObj["description"]?.jsonPrimitive?.content,
                            contextWindow = modelObj["inputTokenLimit"]?.jsonPrimitive?.content?.toIntOrNull(),
                            maxOutputTokens = modelObj["outputTokenLimit"]?.jsonPrimitive?.content?.toIntOrNull()
                        )
                    }

                    Log.d(TAG, "Successfully loaded ${models.size} Gemini models")
                    Result.success(models)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list models", e)
            Result.failure(e)
        }
    }
}
