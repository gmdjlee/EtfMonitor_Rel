package com.etfmonitor.ai

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray

/**
 * AI 응답 파싱 유틸리티
 * Claude와 Gemini API 응답에서 MarketSignal을 추출하는 공통 로직
 */
object AIResponseParser {

    private const val TAG = "AIResponseParser"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * AI 응답 텍스트를 MarketSignal로 파싱
     */
    fun parseToMarketSignal(responseText: String): MarketSignal {
        try {
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
            Log.e(TAG, "Failed to parse AI response", e)
            return createDefaultSignal("AI 응답 파싱 실패: ${e.message}")
        }
    }

    /**
     * 파싱 실패 시 기본 MarketSignal 생성
     */
    fun createDefaultSignal(reason: String): MarketSignal {
        return MarketSignal(
            market = "UNKNOWN",
            date = "",
            signal = SignalType.NEUTRAL,
            confidence = 0.0,
            upProbability = 50.0,
            downProbability = 50.0,
            reasoning = reason,
            keyFactors = listOf("응답 처리 오류"),
            recommendation = "수동 분석 필요",
            riskLevel = RiskLevel.MEDIUM
        )
    }

    /**
     * 응답에서 JSON 블록 추출
     */
    fun extractJsonFromResponse(text: String): String {
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
    fun parseSignalType(value: String): SignalType {
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
    fun parseRiskLevel(value: String): RiskLevel {
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
    fun parseKeyFactors(jsonArray: String): List<String> {
        return try {
            val array = JSONArray(jsonArray)
            List(array.length()) { i -> array.getString(i) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse key factors", e)
            emptyList()
        }
    }
}
