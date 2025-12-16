package com.etfmonitor.ui.screens.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.database.entities.EnhancedPrediction
import com.etfmonitor.database.entities.EnhancedPredictionConfig
import com.etfmonitor.database.entities.EnhancedTrainingResult
import com.etfmonitor.repository.EnhancedPredictionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ML 기반 주가 상승 예측 화면 ViewModel
 * Enhanced 28-feature 앙상블 모델 사용
 *
 * 기능:
 * 1. 예측 실행 (학습 + 예측)
 * 2. 예측 결과 조회
 * 3. 예측 파라미터 설정
 */
@HiltViewModel
class PredictionViewModel @Inject constructor(
    private val predictionRepository: EnhancedPredictionRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_DAYS_AFTER = 5
        private const val DEFAULT_PRICE_THRESHOLD = 3.0
        private const val DEFAULT_MIN_CONFIDENCE = 0.6
    }

    // UI 상태
    private val _state = MutableStateFlow<PredictionState>(PredictionState.Initial)
    val state: StateFlow<PredictionState> = _state.asStateFlow()

    // 예측 결과 리스트
    private val _predictions = MutableStateFlow<List<EnhancedPrediction>>(emptyList())
    val predictions: StateFlow<List<EnhancedPrediction>> = _predictions.asStateFlow()

    // 학습 결과
    private val _trainingResult = MutableStateFlow<EnhancedTrainingResult?>(null)
    val trainingResult: StateFlow<EnhancedTrainingResult?> = _trainingResult.asStateFlow()

    // 예측 파라미터
    private val _daysAfter = MutableStateFlow(DEFAULT_DAYS_AFTER)
    val daysAfter: StateFlow<Int> = _daysAfter.asStateFlow()

    private val _priceThreshold = MutableStateFlow(DEFAULT_PRICE_THRESHOLD)
    val priceThreshold: StateFlow<Double> = _priceThreshold.asStateFlow()

    private val _minConfidence = MutableStateFlow(DEFAULT_MIN_CONFIDENCE)
    val minConfidence: StateFlow<Double> = _minConfidence.asStateFlow()

    // 모델 타입 (voting, xgboost, lightgbm, random_forest, gradient_boosting)
    private val _modelType = MutableStateFlow("voting")
    val modelType: StateFlow<String> = _modelType.asStateFlow()

    // CV 사용 여부
    private val _useCrossValidation = MutableStateFlow(true)
    val useCrossValidation: StateFlow<Boolean> = _useCrossValidation.asStateFlow()

    init {
        loadLatestPredictions()
    }

    /**
     * 최신 예측 결과 로드
     * 결과가 없으면 자동으로 예측 실행
     */
    private fun loadLatestPredictions() {
        viewModelScope.launch {
            try {
                val latestPredictions = predictionRepository.getLatestPredictions()
                    .flowOn(Dispatchers.IO)
                    .first()

                if (latestPredictions.isNotEmpty()) {
                    _predictions.value = latestPredictions
                    _state.value = PredictionState.HasPredictions(latestPredictions.size)
                } else {
                    // 예측 결과가 없으면 자동으로 예측 실행
                    runPrediction()
                }
            } catch (e: Exception) {
                _state.value = PredictionState.Error("예측 결과 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 예측 실행 (학습 + 예측)
     */
    fun runPrediction() {
        viewModelScope.launch {
            _state.value = PredictionState.Loading("ML 모델 학습 중... (28개 Feature)")

            try {
                val config = EnhancedPredictionConfig(
                    daysAfter = _daysAfter.value,
                    priceThreshold = _priceThreshold.value,
                    minConfidence = _minConfidence.value,
                    modelType = _modelType.value,
                    useCrossValidation = _useCrossValidation.value
                )

                val response = predictionRepository.runEnhancedPrediction(config)

                if (response.success) {
                    _predictions.value = response.predictions
                    _trainingResult.value = response.trainingResult

                    if (response.predictions.isNotEmpty()) {
                        _state.value = PredictionState.Success(
                            message = "예측 완료: ${response.predictions.size}개 종목 발견",
                            predictedCount = response.predictions.size,
                            accuracy = response.trainingResult?.cvAccuracy,
                            f1Score = response.trainingResult?.cvF1
                        )
                    } else {
                        _state.value = PredictionState.NoPredictions
                    }
                } else {
                    _state.value = PredictionState.Error(response.errorMessage ?: "예측 실패")
                }
            } catch (e: Exception) {
                _state.value = PredictionState.Error("예측 실행 중 오류: ${e.message}")
            }
        }
    }

    /**
     * 예측 기간 설정
     */
    fun setDaysAfter(days: Int) {
        _daysAfter.value = days.coerceIn(1, 30)
    }

    /**
     * 상승 판단 기준 설정
     */
    fun setPriceThreshold(threshold: Double) {
        _priceThreshold.value = threshold.coerceIn(1.0, 10.0)
    }

    /**
     * 최소 신뢰도 설정
     */
    fun setMinConfidence(confidence: Double) {
        _minConfidence.value = confidence.coerceIn(0.5, 0.95)
    }

    /**
     * 모델 타입 설정
     */
    fun setModelType(type: String) {
        if (type in listOf("voting", "xgboost", "lightgbm", "random_forest", "gradient_boosting")) {
            _modelType.value = type
        }
    }

    /**
     * CV 사용 여부 설정
     */
    fun setUseCrossValidation(use: Boolean) {
        _useCrossValidation.value = use
    }

    /**
     * 상태 초기화
     */
    fun clearState() {
        if (_predictions.value.isNotEmpty()) {
            _state.value = PredictionState.HasPredictions(_predictions.value.size)
        } else {
            _state.value = PredictionState.NoPredictions
        }
    }

    /**
     * 예측 새로고침
     */
    fun refresh() {
        loadLatestPredictions()
    }
}

/**
 * 예측 화면 UI 상태
 */
sealed class PredictionState {
    /** 초기 상태 (로딩 중) */
    object Initial : PredictionState()

    /** 예측 데이터 없음 */
    object NoPredictions : PredictionState()

    /** 예측 데이터 있음 */
    data class HasPredictions(val count: Int) : PredictionState()

    /** 로딩 중 */
    data class Loading(val message: String) : PredictionState()

    /** 예측 성공 */
    data class Success(
        val message: String,
        val predictedCount: Int,
        val accuracy: Double?,
        val f1Score: Double? = null
    ) : PredictionState()

    /** 오류 */
    data class Error(val message: String) : PredictionState()
}
