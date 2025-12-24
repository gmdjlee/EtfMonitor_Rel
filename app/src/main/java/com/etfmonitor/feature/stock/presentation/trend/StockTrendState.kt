package com.etfmonitor.feature.stock.presentation.trend

import com.etfmonitor.feature.stock.domain.model.StockTrend

/**
 * Stock Trend Screen UI State
 */
sealed class StockTrendState {
    data object Loading : StockTrendState()
    data class Success(val trend: StockTrend) : StockTrendState()
    data class Error(val message: String) : StockTrendState()
}
