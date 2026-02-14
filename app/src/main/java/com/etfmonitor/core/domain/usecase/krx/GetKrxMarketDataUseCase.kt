package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxStockRepositoryImpl
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import javax.inject.Inject

// Get Krx Market Data Use Case
//
// KOSPI/KOSDAQ 전체 시장 데이터를 집계 조회하는 유스케이스입니다.
//
// PHASE 3 ENABLEMENT: T-013 stock analysis feature migration을 위한 기반 UseCase
// Phase 2에서는 AggregatedStockTrendViewModel을 대체하지 않음 (여전히 OscillatorPyClient 사용)
//
// TECHNICAL DEBT (C2): 인터페이스 대신 concrete class 주입
// Rationale: Coexistence phase 단축. Clean Architecture 인터페이스는 Phase 3로 연기
//
// Error Handling Strategy (W1 fix): Fail-fast on first market error.
// 첫 번째 마켓 오류 시 즉시 실패 반환 (부분 결과 대신).
class GetKrxMarketDataUseCase @Inject constructor(
    private val krxStockRepository: KrxStockRepositoryImpl
) {
    // 여러 마켓의 시장 데이터 집계 조회
    // Fail-fast behavior: 첫 번째 마켓 오류 시 즉시 실패 반환
    suspend operator fun invoke(
        date: String,
        markets: List<Market> = listOf(Market.KOSPI, Market.KOSDAQ)
    ): Result<Map<Market, List<MarketCap>>> {
        val results = mutableMapOf<Market, List<MarketCap>>()

        for (market in markets) {
            val result = krxStockRepository.getMarketCap(date, market)
            result.onSuccess { data ->
                results[market] = data
            }.onFailure { error ->
                // W1 FIX: Fail-fast on first error instead of silently swallowing
                return Result.failure(error)
            }
        }

        return Result.success(results)
    }
}
