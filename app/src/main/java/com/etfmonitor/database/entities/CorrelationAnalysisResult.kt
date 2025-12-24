package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.etfmonitor.core.database.Converters

/**
 * 상관관계 분석 결과 엔티티
 * 각 지표와 시장 지수 간의 상관관계 계산 결과 저장
 */
@Entity(tableName = "correlation_analysis_result")
@TypeConverters(Converters::class)
data class CorrelationAnalysisResult(
    @PrimaryKey
    val id: String, // "KOSPI-2025-01-15" 형식

    val market: String, // "KOSPI" 또는 "KOSDAQ"
    val analysisDate: String, // 분석 기준 날짜 "2025-01-15"
    val periodDays: Int, // 분석 기간 (일)

    // ETF 통계와 지수의 상관관계
    val etfNewStockCorrelation: Double, // 신규 편입 종목 수 vs 지수 등락률
    val etfRemovedStockCorrelation: Double, // 제외 종목 수 vs 지수 등락률
    val etfIncreasedCorrelation: Double, // 비중 증가 종목 수 vs 지수 등락률
    val etfDecreasedCorrelation: Double, // 비중 감소 종목 수 vs 지수 등락률
    val etfNetFlowCorrelation: Double, // 순 편입(신규-제외) vs 지수 등락률
    val cashDepositCorrelation: Double, // 원화예금 변화 vs 지수 등락률

    // 증시 자금 동향과 지수의 상관관계
    val marketDepositCorrelation: Double?, // 고객예탁금 변화 vs 지수 등락률
    val creditBalanceCorrelation: Double?, // 신용잔고 변화 vs 지수 등락률

    // Fear & Greed와 지수의 상관관계
    val fearGreedCorrelation: Double?, // Fear & Greed 값 vs 지수 등락률
    val fearGreedLeadCorrelation: Double?, // Fear & Greed (1일 선행) vs 지수 등락률

    // Oscillator와 지수의 상관관계
    val oscillatorCorrelation: Double?, // Oscillator vs 지수 등락률
    val oscillatorLeadCorrelation: Double?, // Oscillator (1일 선행) vs 지수 등락률

    // 종합 상관관계 점수 (가중 평균)
    val compositeScore: Double, // -1.0 ~ 1.0

    // 신호 생성 결과
    val signal: String, // "STRONG_BUY", "BUY", "NEUTRAL", "SELL", "STRONG_SELL"
    val confidence: Double, // 신뢰도 0.0 ~ 1.0
    val upProbability: Double, // 상승 확률 (%)
    val downProbability: Double, // 하락 확률 (%)

    // 분석 컨텍스트 (JSON)
    val analysisContext: String, // 분석에 사용된 원본 데이터 JSON

    val createdAt: Long = System.currentTimeMillis()
)
