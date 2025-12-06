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
 * Claude API 클라이언트
 * Anthropic Claude API를 통한 시장 분석
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
        private const val MODEL = "claude-3-5-sonnet-20241022"
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
                val signal = AIResponseParser.parseToMarketSignal(response)
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
     * 채팅 메시지 전송
     */
    override suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey(AIProvider.CLAUDE)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Claude API 키가 설정되지 않았습니다."))
            }

            val model = apiKeyProvider.getSelectedModel(AIProvider.CLAUDE) ?: MODEL

            withTimeout(TIMEOUT_SECONDS * 1000) {
                val response = callClaudeChatApi(apiKey, messages, systemPrompt, temperature, model)
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            Result.failure(e)
        }
    }

    /**
     * Claude Chat API 호출
     */
    private suspend fun callClaudeChatApi(
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double,
        model: String
    ): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("temperature", temperature)

            // 시스템 프롬프트 추가 (있는 경우)
            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }

            // 메시지 배열 구성
            put("messages", JSONArray().apply {
                for (msg in messages) {
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
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
                Log.e(TAG, "Chat API call failed: ${response.code} - $errorBody")
                throw Exception("Claude API 호출 실패: ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Empty response from Claude API")

            val jsonResponse = JSONObject(responseBody)
            val content = jsonResponse.getJSONArray("content")
            if (content.length() == 0) {
                throw Exception("No content in Claude API response")
            }

            content.getJSONObject(0).getString("text")
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
