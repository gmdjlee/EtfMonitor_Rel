package com.etfmonitor.feature.stock.domain.usecase

import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Search Stocks Use Case
 *
 * 종목 검색 유스케이스입니다.
 * 종목명 또는 종목코드로 검색합니다.
 *
 * @property repository Stock Repository
 */
class SearchStocksUseCase @Inject constructor(
    private val repository: StockRepository
) {
    /**
     * 종목 검색
     *
     * @param query 검색어 (종목명 또는 종목코드)
     * @return 검색 결과 Flow
     */
    operator fun invoke(query: String): Flow<List<Stock>> {
        return repository.searchStocks(query)
    }
}
