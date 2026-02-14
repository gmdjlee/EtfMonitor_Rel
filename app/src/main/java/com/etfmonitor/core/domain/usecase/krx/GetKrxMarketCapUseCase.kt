package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxStockRepositoryImpl
import com.krxkt.model.Market
import com.krxkt.model.MarketCap
import javax.inject.Inject

// Get Krx Market Cap Use Case
//
// kotlin_krx를 통해 시가총액 데이터를 조회하는 유스케이스입니다.
//
// Location rationale: core/* 위치 - 여러 feature에서 공유하는 기반 UseCase
// (T-011 ETF, T-012 Oscillator, T-013 Stock Analysis)
//
// PHASE 3 ENABLEMENT: Phase 3 feature migration을 위한 기반 UseCase
// Phase 2에서는 기존 ViewModel을 대체하지 않음 (coexistence)
//
// TECHNICAL DEBT (C2): 인터페이스 대신 concrete class 주입
// Rationale: Coexistence phase 단축. Clean Architecture 인터페이스는 Phase 3로 연기
class GetKrxMarketCapUseCase @Inject constructor(
    private val krxStockRepository: KrxStockRepositoryImpl
) {
    // 시가총액 데이터 조회
    suspend operator fun invoke(
        date: String,
        market: Market = Market.ALL
    ): Result<List<MarketCap>> {
        return krxStockRepository.getMarketCap(date, market)
    }
}
