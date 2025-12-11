package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 종목-지표 상관관계 AI 분석 결과 엔티티
 * 개별 종목과 시장 지표(Fear&Greed, Oscillator, 예탁금 등) 간의
 * 상관관계를 AI가 분석한 결과를 저장
 */
@Entity(
    tableName = "stock_indicator_ai_result",
    indices = [
        Index(value = ["ticker"]),
        Index(value = ["ticker", "analysisDate"]),
        Index(value = ["createdAt"])
    ]
)
data class StockIndicatorAIResult(
    @PrimaryKey
    val id: String, // UUID

    // 종목 정보
    val ticker: String,
    val stockName: String,
    val market: String, // "KOSPI" 또는 "KOSDAQ"

    // 분석 기간
    val analysisDate: String, // 분석 기준 날짜 "2025-01-15"
    val period: String, // "2024-10-15 ~ 2025-01-15"
    val periodDays: Int, // 분석 기간 일수

    // AI 제공자 정보
    val aiProvider: String, // "CLAUDE" 또는 "GEMINI"
    val aiModel: String, // 사용된 모델 ID

    // 신호 결과
    val signal: String, // "STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"
    val confidence: Double, // 신뢰도 0.0 ~ 1.0
    val upProbability: Double, // 상승 확률 (%)
    val downProbability: Double, // 하락 확률 (%)
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH"

    // 상관관계 요약
    val keyCorrelations: String, // 주요 상관관계 (JSON Array)
    val marketSentimentImpact: String, // 시장 심리 영향
    val fundFlowImpact: String, // 자금 흐름 영향
    val etfFlowImpact: String, // ETF 수급 영향

    // AI 분석 내용
    val reasoning: String, // AI의 분석 이유
    val recommendation: String, // 투자 권장사항

    // 처리 정보
    val createdAt: Long = System.currentTimeMillis()
)
