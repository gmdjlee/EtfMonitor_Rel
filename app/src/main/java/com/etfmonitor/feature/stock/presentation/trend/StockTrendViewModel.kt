package com.etfmonitor.feature.stock.presentation.trend

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.EtfDao
import com.etfmonitor.core.network.python.OscillatorPyClient
import com.etfmonitor.feature.stock.domain.usecase.GetStockTrendUseCase
import com.etfmonitor.core.common.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stock Trend ViewModel
 *
 * ETF 내 특정 종목의 시계열 추이를 표시하는 화면의 ViewModel입니다.
 *
 * @property getStockTrendUseCase 종목 추이 조회 유스케이스
 * @property etfDao 설정 조회용 DAO
 * @property pyClient 차트 분석용 Python 클라이언트
 * @property savedStateHandle Navigation arguments
 */
@HiltViewModel
class StockTrendViewModel @Inject constructor(
    private val getStockTrendUseCase: GetStockTrendUseCase,
    private val etfDao: EtfDao,
    val pyClient: OscillatorPyClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private val logger = AppLogger.getLogger("StockTrendViewModel")
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"
    }

    private val etfTicker: String = savedStateHandle.get<String>("etfTicker")
        ?: throw IllegalArgumentException("etfTicker is required")

    val stockTicker: String = savedStateHandle.get<String>("stockTicker")
        ?: throw IllegalArgumentException("stockTicker is required")

    private val _state = MutableStateFlow<StockTrendState>(StockTrendState.Loading)
    val state: StateFlow<StockTrendState> = _state.asStateFlow()

    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    init {
        loadTrend()
        loadQuickChartAnalysisSetting()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            try {
                val trend = getStockTrendUseCase(etfTicker, stockTicker)
                _state.value = if (trend != null) {
                    StockTrendState.Success(trend)
                } else {
                    StockTrendState.Error("추이 데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                logger.e("Error loading trend for ETF: $etfTicker, Stock: $stockTicker", e)
                _state.value = StockTrendState.Error(e.message ?: "오류 발생")
            }
        }
    }

    private fun loadQuickChartAnalysisSetting() {
        viewModelScope.launch {
            try {
                val enabled = etfDao.getSetting(QUICK_CHART_ANALYSIS_KEY) == "true"
                _quickChartAnalysisEnabled.value = enabled
            } catch (e: Exception) {
                logger.e("Error loading quick chart analysis setting", e)
            }
        }
    }
}
