package com.etfmonitor.feature.etf.presentation.detail

import com.etfmonitor.feature.etf.domain.model.ComparisonResult

/**
 * ETF Detail UI State
 */
sealed class EtfDetailState {
    data object Loading : EtfDetailState()
    data class Success(val comparison: ComparisonResult) : EtfDetailState()
    data class Error(val message: String) : EtfDetailState()
}
