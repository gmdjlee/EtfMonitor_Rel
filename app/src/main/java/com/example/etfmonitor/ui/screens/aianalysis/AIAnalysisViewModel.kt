package com.etfmonitor.ui.screens.aianalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.ai.*
import com.etfmonitor.analysis.Backtester
import com.etfmonitor.database.entities.StockPrediction
import com.etfmonitor.database.entities.TrainingResult
import com.etfmonitor.repository.AIAnalysisRepository
import com.etfmonitor.repository.StockPredictionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 분석 화면 ViewModel
 */
@HiltViewModel
class AIAnalysisViewModel @Inject constructor(
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val apiKeyProvider: ApiKeyProvider,
    private val backtester: Backtester,
    private val stockPredictionRepository: StockPredictionRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AIAnalysisState>(AIAnalysisState.Idle)
    val state: StateFlow<AIAnalysisState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _isApiKeyConfigured = MutableStateFlow(false)
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    init {
        checkApiKey()
    }

    /**
     * API 키 설정 여부 확인
     */
    private fun checkApiKey() {
        viewModelScope.launch {
            _isApiKeyConfigured.value = aiAnalysisRepository.isApiAvailable()
        }
    }

    /**
     * 시장 선택
     */
    fun selectMarket(market: String) {
        _selectedMarket.value = market
    }

    /**
     * 최신 시장 분석 수행
     */
    fun analyzeLatestMarket() {
        viewModelScope.launch {
            if (!_isApiKeyConfigured.value) {
                _state.value = AIAnalysisState.Error("API 키가 설정되지 않았습니다. 설정에서 Claude API 키를 등록해주세요.")
                return@launch
            }

            _state.value = AIAnalysisState.Loading

            val result = aiAnalysisRepository.analyzeLatestMarket(_selectedMarket.value)

            _state.value = if (result.isSuccess) {
                AIAnalysisState.Success(result.getOrThrow())
            } else {
                AIAnalysisState.Error(result.exceptionOrNull()?.message ?: "분석 실패")
            }
        }
    }

    /**
     * 빠른 신호 생성
     */
    fun generateQuickSignal() {
        viewModelScope.launch {
            if (!_isApiKeyConfigured.value) {
                _state.value = AIAnalysisState.Error("API 키가 설정되지 않았습니다.")
                return@launch
            }

            _state.value = AIAnalysisState.LoadingQuick

            // 최신 날짜 조회를 위한 분석 수행
            val result = aiAnalysisRepository.analyzeLatestMarket(_selectedMarket.value)

            _state.value = if (result.isSuccess) {
                AIAnalysisState.QuickSignal(result.getOrThrow().signal)
            } else {
                AIAnalysisState.Error(result.exceptionOrNull()?.message ?: "신호 생성 실패")
            }
        }
    }

    /**
     * API 키 테스트
     */
    fun testApiConnection() {
        viewModelScope.launch {
            _state.value = AIAnalysisState.Loading

            val result = aiAnalysisRepository.testApiConnection()

            _state.value = if (result.isSuccess) {
                checkApiKey() // 성공 시 상태 업데이트
                AIAnalysisState.ApiTestSuccess
            } else {
                AIAnalysisState.Error("API 연결 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * 백테스트 실행
     */
    fun runBacktest(startDate: String, endDate: String) {
        viewModelScope.launch {
            _state.value = AIAnalysisState.LoadingBacktest

            // 배치 신호 생성
            val signalsResult = aiAnalysisRepository.generateBatchSignals(
                market = _selectedMarket.value,
                startDate = startDate,
                endDate = endDate
            )

            if (signalsResult.isFailure) {
                _state.value = AIAnalysisState.Error(
                    "신호 생성 실패: ${signalsResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            val signals = signalsResult.getOrThrow()

            if (signals.isEmpty()) {
                _state.value = AIAnalysisState.Error("생성된 신호가 없습니다")
                return@launch
            }

            // 백테스트 실행
            val backtestResult = backtester.backtest(
                market = _selectedMarket.value,
                signals = signals,
                holdingPeriod = 5
            )

            _state.value = if (backtestResult.isSuccess) {
                AIAnalysisState.BacktestComplete(backtestResult.getOrThrow(), signals)
            } else {
                AIAnalysisState.Error("백테스트 실패: ${backtestResult.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * ML 모델 기반 주가 상승 예측 실행
     */
    fun runStockPrediction(
        daysAfter: Int = 5,
        priceThreshold: Double = 3.0,
        minConfidence: Double = 0.6
    ) {
        viewModelScope.launch {
            _state.value = AIAnalysisState.LoadingPrediction

            val response = stockPredictionRepository.runPrediction(
                daysAfter = daysAfter,
                priceThreshold = priceThreshold,
                minConfidence = minConfidence
            )

            _state.value = if (response.success) {
                AIAnalysisState.PredictionComplete(
                    predictions = response.predictions,
                    trainingResult = response.trainingResult,
                    totalAnalyzed = response.totalAnalyzed,
                    predictedCount = response.predictedCount
                )
            } else {
                AIAnalysisState.Error(response.errorMessage ?: "예측 실패")
            }
        }
    }

    /**
     * 저장된 최신 예측 결과 조회
     */
    fun loadLatestPredictions() {
        viewModelScope.launch {
            _state.value = AIAnalysisState.LoadingPrediction

            val predictions = stockPredictionRepository.getLatestPredictionsSuspend()

            _state.value = if (predictions.isNotEmpty()) {
                AIAnalysisState.PredictionComplete(
                    predictions = predictions,
                    trainingResult = null,
                    totalAnalyzed = predictions.size,
                    predictedCount = predictions.size
                )
            } else {
                AIAnalysisState.Error("저장된 예측 결과가 없습니다. 새로운 예측을 실행해주세요.")
            }
        }
    }

    /**
     * 에러 상태 초기화
     */
    fun clearError() {
        _state.value = AIAnalysisState.Idle
    }

    /**
     * API 키 재확인
     */
    fun refreshApiKeyStatus() {
        checkApiKey()
    }
}

/**
 * AI 분석 화면 상태
 */
sealed class AIAnalysisState {
    object Idle : AIAnalysisState()
    object Loading : AIAnalysisState()
    object LoadingQuick : AIAnalysisState()
    object LoadingBacktest : AIAnalysisState()
    object LoadingPrediction : AIAnalysisState()
    data class Success(val response: AIAnalysisResponse) : AIAnalysisState()
    data class QuickSignal(val signal: MarketSignal) : AIAnalysisState()
    data class BacktestComplete(
        val result: BacktestResult,
        val signals: List<SignalRecord>
    ) : AIAnalysisState()
    data class PredictionComplete(
        val predictions: List<StockPrediction>,
        val trainingResult: TrainingResult?,
        val totalAnalyzed: Int,
        val predictedCount: Int
    ) : AIAnalysisState()
    object ApiTestSuccess : AIAnalysisState()
    data class Error(val message: String) : AIAnalysisState()
}
