package com.etfmonitor.feature.analysis.presentation.advanced

import com.etfmonitor.feature.analysis.domain.model.*

/**
 * 고급 분석 대시보드 상태
 */
sealed class AdvancedDashboardState {
    object Loading : AdvancedDashboardState()
    data class Success(val data: AdvancedDashboard) : AdvancedDashboardState()
    data class Error(val message: String) : AdvancedDashboardState()
}
