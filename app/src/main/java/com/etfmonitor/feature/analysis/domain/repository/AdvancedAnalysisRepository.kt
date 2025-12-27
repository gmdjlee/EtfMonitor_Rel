package com.etfmonitor.feature.analysis.domain.repository

import com.etfmonitor.feature.analysis.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 고급 분석 Repository 인터페이스
 *
 * 5가지 핵심 분석 기능 제공:
 * 1. 시총 가중 ETF 흐름 분석
 * 2. 외국인/기관 수급 Divergence 분석
 * 3. 예탁금/시총 비율 분석 (유동성)
 * 4. 섹터별 Fear & Greed 분석
 * 5. ETF 간 상관관계 분석
 */
interface AdvancedAnalysisRepository {

    // ==================== 1. 시총 가중 ETF 흐름 분석 ====================

    /**
     * 시총 가중 ETF 흐름 계산
     */
    suspend fun calculateMarketCapWeightedFlow(
        currentDate: String,
        previousDate: String,
        market: String = "ALL"
    ): MarketCapFlow


    // ==================== 2. 외국인/기관 수급 Divergence 분석 ====================

    /**
     * 수급 Divergence 분석
     */
    suspend fun analyzeSupplyDemandDivergence(
        date: String,
        market: String = "ALL"
    ): DivergenceAnalysis

    // ==================== 3. 예탁금/시총 비율 분석 (유동성) ====================

    /**
     * 유동성 분석 계산 및 저장
     */
    suspend fun calculateAndSaveLiquidityAnalysis(date: String): LiquidityAnalysisData?

    /**
     * 최신 유동성 분석 조회
     */
    suspend fun getLatestLiquidityAnalysis(): LiquidityAnalysisData?


    // ==================== 4. 섹터별 Fear & Greed 분석 ====================

    /**
     * 섹터별 Fear & Greed 분석 계산 및 저장
     */
    suspend fun calculateAndSaveSectorAnalysis(
        currentDate: String,
        previousDate: String
    ): List<SectorAnalysisData>

    /**
     * 특정 날짜의 섹터 분석 조회
     */
    suspend fun getSectorAnalysisByDate(date: String): List<SectorAnalysisData>

    /**
     * 섹터 분석 (Flow)
     */
    fun observeSectorAnalysis(date: String): Flow<List<SectorAnalysisData>>

    /**
     * 섹터 로테이션 신호 감지
     */
    suspend fun detectSectorRotation(
        currentDate: String,
        previousDate: String
    ): List<SectorRotation>

    // ==================== 5. ETF 간 상관관계 분석 ====================

    /**
     * ETF 쌍의 상관관계 계산 및 저장
     */
    suspend fun calculateAndSaveEtfCorrelation(
        etf1Ticker: String,
        etf2Ticker: String,
        date: String
    ): EtfCorrelation?

    /**
     * 모든 ETF 쌍의 상관관계 계산
     */
    suspend fun calculateAllEtfCorrelations(date: String): List<EtfCorrelation>

    /**
     * 높은 중복률 ETF 쌍 조회
     */
    suspend fun getHighOverlapEtfPairs(
        date: String,
        threshold: Double = 0.1
    ): List<EtfCorrelation>

}
