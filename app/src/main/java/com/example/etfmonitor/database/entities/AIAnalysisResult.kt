package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 분석 결과 엔티티
 * AI가 상관관계 분석을 해석한 결과 저장
 */
@Entity(tableName = "ai_analysis_result")
data class AIAnalysisResult(
    @PrimaryKey
    val id: String, // UUID

    val market: String, // "KOSPI" 또는 "KOSDAQ"
    val analysisDate: String, // 분석 기준 날짜 "2025-01-15"

    // 연결된 상관관계 분석 ID
    val correlationResultId: String?,

    // AI 제공자 정보
    val aiProvider: String, // "CLAUDE" 또는 "GEMINI"
    val aiModel: String, // 사용된 모델 ID

    // 신호 결과
    val signal: String, // "STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"
    val confidence: Double, // 신뢰도 0.0 ~ 1.0
    val upProbability: Double, // 상승 확률 (%)
    val downProbability: Double, // 하락 확률 (%)
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH"

    // AI 분석 내용
    val reasoning: String, // AI의 분석 이유
    val keyFactors: String, // 주요 영향 요인 (JSON Array)
    val recommendation: String, // 투자 권장사항
    val alternativeScenarios: String?, // 대안 시나리오 (JSON Array)

    // 분석 메타데이터
    val promptUsed: String, // AI에게 전달한 프롬프트
    val rawResponse: String, // AI의 원본 응답

    // 처리 정보
    val processingTimeMs: Long, // 처리 시간 (밀리초)
    val createdAt: Long = System.currentTimeMillis()
)
