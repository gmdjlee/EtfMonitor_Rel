package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxIndexRepositoryImpl
import com.etfmonitor.core.database.entities.MarketIndex
import javax.inject.Inject

/**
 * kotlin_krx를 사용하여 시장 지수 데이터를 가져오는 UseCase
 *
 * @property repository KrxIndexRepositoryImpl - KrxIndex 클라이언트를 감싼 repository
 */
class GetKrxIndexDataUseCase @Inject constructor(
    private val repository: KrxIndexRepositoryImpl
) {
    /**
     * 지정된 기간 동안의 시장 지수 데이터 수집
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return Result<List<MarketIndex>> 성공 시 MarketIndex 리스트, 실패 시 Exception
     */
    suspend operator fun invoke(
        startDate: String,
        endDate: String,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): Result<List<MarketIndex>> {
        return repository.getMarketIndices(startDate, endDate, markets)
    }

    /**
     * 최근 N일의 시장 지수 데이터 수집
     *
     * @param days 수집할 일수
     * @param markets 수집할 시장 목록
     * @return Result<List<MarketIndex>> 성공 시 MarketIndex 리스트, 실패 시 Exception
     */
    suspend fun getRecentDays(
        days: Int,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): Result<List<MarketIndex>> {
        return repository.getRecentMarketIndices(days, markets)
    }
}
