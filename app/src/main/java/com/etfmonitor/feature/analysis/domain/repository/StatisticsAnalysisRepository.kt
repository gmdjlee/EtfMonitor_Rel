package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.core.database.entities.DailyEtfStatistics
import kotlinx.coroutines.flow.Flow

/**
 * 통계 분석 Repository 인터페이스
 */
interface StatisticsAnalysisRepository {

    /**
     * 일일 ETF 통계 계산 및 저장
     */
    suspend fun calculateAndStoreDailyStatistics(date: String): DailyEtfStatistics?

    /**
     * 날짜별 통계 조회
     */
    suspend fun getStatisticsByDate(date: String): DailyEtfStatistics?

    /**
     * 최신 날짜 조회
     */
    suspend fun getLatestDate(): String?

    /**
     * 모든 날짜 목록 조회
     */
    suspend fun getAllDates(): List<String>

    /**
     * 상관관계 계산 (Pearson)
     */
    suspend fun calculateCorrelation(
        market: String,
        startDate: String,
        endDate: String
    ): CorrelationData?
}

/**
 * 상관관계 데이터
 */
data class CorrelationData(
    val market: String,
    val period: String,
    val dataPoints: Int,
    val correlations: Map<String, Double>
)
