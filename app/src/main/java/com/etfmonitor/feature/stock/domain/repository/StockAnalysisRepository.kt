package com.etfmonitor.feature.stock.domain.repository

import com.etfmonitor.core.analysis.model.StockData

/**
 * Stock Analysis Repository Interface
 *
 * Domain 레이어에 정의된 종목 수급 분석 Repository 인터페이스입니다.
 * 구현체는 Data 레이어(StockAnalysisRepositoryImpl)에서 제공합니다.
 *
 * ## 주요 기능
 * - 종목 수급 분석 데이터 조회 (24h 캐싱)
 * - Python에서 새 데이터 수집
 * - 캐시 관리
 *
 * ## 캐싱 정책
 * - 데이터 만료 시간: 24시간
 * - 최신 날짜가 오늘이 아니면 업데이트
 * - 데이터가 요청 일수의 80% 미만이면 업데이트
 *
 * ## 스레드 안전성
 * - 모든 suspend 함수는 withContext(Dispatchers.IO)로 IO 스레드에서 실행됩니다.
 *
 * ## 참고
 * - StockData는 core.analysis.model에 정의되어 있습니다.
 * - OscillatorCalculator와 호환을 위해 core 모델을 직접 사용합니다.
 */
interface StockAnalysisRepository {

    /**
     * 종목 분석 데이터 가져오기 (DB 캐시 활용)
     *
     * stocks JOIN으로 종목명 조회합니다.
     * 캐시가 유효하면 캐시 데이터를 반환하고, 그렇지 않으면 Python에서 새 데이터를 수집합니다.
     *
     * @param ticker 종목코드
     * @param days 조회할 일수 (기본값: 180)
     * @return 분석 데이터 또는 null
     */
    suspend fun getStockAnalysis(ticker: String, days: Int = 180): StockData?

    /**
     * 캐시 삭제 (특정 종목)
     *
     * @param ticker 종목코드
     */
    suspend fun clearCache(ticker: String)

    /**
     * 캐시 전체 삭제
     */
    suspend fun clearAllCache()
}
