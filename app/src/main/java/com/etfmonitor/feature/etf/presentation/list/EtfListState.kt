package com.etfmonitor.feature.etf.presentation.list

import com.etfmonitor.feature.etf.domain.model.Etf

/**
 * ETF List UI State
 */
sealed class EtfListState {
    data object Loading : EtfListState()
    data class Success(val etfs: List<Etf>) : EtfListState()
    data object Empty : EtfListState()
    data class Error(val message: String) : EtfListState()
}
