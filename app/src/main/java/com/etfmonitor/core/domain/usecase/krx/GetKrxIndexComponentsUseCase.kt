package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxMarketRepositoryImpl
import javax.inject.Inject

// Get Krx Index Components Use Case
//
// kotlin_krx를 통해 지수 구성 종목을 조회하는 유스케이스입니다.
// AD-003 proxy 사용: top-N 시가총액 종목을 지수 구성 종목으로 근사
//
// PHASE 3 ENABLEMENT: T-012 oscillator feature migration을 위한 기반 UseCase
// Phase 2에서는 OscillatorViewModel을 대체하지 않음 (여전히 OscillatorPyClient 사용)
//
// TECHNICAL DEBT (C2): 인터페이스 대신 concrete class 주입
// Rationale: Coexistence phase 단축. Clean Architecture 인터페이스는 Phase 3로 연기
class GetKrxIndexComponentsUseCase @Inject constructor(
    private val krxMarketRepository: KrxMarketRepositoryImpl
) {
    // 지수 구성 종목 조회 (시가총액 proxy 사용)
    suspend operator fun invoke(
        indexTicker: String,
        date: String,
        topN: Int = 200
    ): Result<List<String>> {
        return krxMarketRepository.getIndexComponents(indexTicker, date, topN)
    }
}
