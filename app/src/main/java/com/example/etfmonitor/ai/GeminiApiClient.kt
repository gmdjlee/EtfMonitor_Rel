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
                val signal = AIResponseParser.parseToMarketSignal(response)
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
     * 채팅 메시지 전송
     */
    override suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.GEMINI)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Gemini API 키가 설정되지 않았습니다."))
            }

            var model = apiKeyProvider.getSelectedModel(AIProvider.GEMINI) ?: MODEL
            model = validateAndFixModelName(model)

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val response = callGeminiChatApi(apiKey, messages, systemPrompt, temperature, model)
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            Result.failure(e)
        }
    }

    /**
     * Gemini Chat API 호출
     */
    private suspend fun callGeminiChatApi(
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double,
        model: String
    ): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            // 시스템 프롬프트 설정 (Gemini는 systemInstruction 사용)
            if (!systemPrompt.isNullOrBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            }

            // 대화 이력 구성
            put("contents", JSONArray().apply {
                for (msg in messages) {
                    put(JSONObject().apply {
                        // Gemini는 "user"와 "model" 역할 사용
                        put("role", if (msg.role == "assistant") "model" else msg.role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", msg.content)
                            })
                        })
                    })
                }
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            })
        }

        val url = "$API_BASE_URL/$model:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Chat API call failed: ${response.code} - $errorBody")
                throw Exception("Gemini API 호출 실패: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from Gemini API")

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
