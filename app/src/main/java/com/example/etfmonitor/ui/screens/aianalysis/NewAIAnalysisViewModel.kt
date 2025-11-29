package com.etfmonitor.ui.screens.aianalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.ai.AIApiClientFactory
import com.etfmonitor.ai.AIProvider
import com.etfmonitor.ai.ApiKeyProvider
import com.etfmonitor.analysis.SignalType
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.AIChatSession
import com.etfmonitor.database.entities.AIAnalysisResult
import com.etfmonitor.database.entities.CorrelationAnalysisResult
import com.etfmonitor.repository.AIChatRepository
import com.etfmonitor.repository.CorrelationAnalysisRepository
import com.etfmonitor.repository.FullAnalysisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 새로운 AI 분석 화면 ViewModel
 * 상관관계 분석 + AI 해석 + 채팅 기능 통합
 */
@HiltViewModel
class NewAIAnalysisViewModel @Inject constructor(
    private val correlationAnalysisRepository: CorrelationAnalysisRepository,
    private val chatRepository: AIChatRepository,
    private val apiKeyProvider: ApiKeyProvider,
    private val aiApiClientFactory: AIApiClientFactory
) : ViewModel() {

    // ========== 상태 관리 ==========

    private val _state = MutableStateFlow<NewAIAnalysisState>(NewAIAnalysisState.Idle)
    val state: StateFlow<NewAIAnalysisState> = _state.asStateFlow()

    private val _selectedMarket = MutableStateFlow("KOSPI")
    val selectedMarket: StateFlow<String> = _selectedMarket.asStateFlow()

    private val _analysisResult = MutableStateFlow<FullAnalysisResult?>(null)
    val analysisResult: StateFlow<FullAnalysisResult?> = _analysisResult.asStateFlow()

    private val _isApiKeyConfigured = MutableStateFlow(false)
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    private val _selectedProvider = MutableStateFlow(AIProvider.CLAUDE)
    val selectedProvider: StateFlow<AIProvider> = _selectedProvider.asStateFlow()

    // 채팅 관련 상태
    private val _currentSession = MutableStateFlow<AIChatSession?>(null)
    val currentSession: StateFlow<AIChatSession?> = _currentSession.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<AIChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<AIChatMessage>> = _chatMessages.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    // 채팅 세션 목록
    val chatSessions: Flow<List<AIChatSession>> = chatRepository.getAllSessions()

    // 상관관계 분석 결과 목록
    val correlationResults: Flow<List<CorrelationAnalysisResult>> =
        _selectedMarket.flatMapLatest { market ->
            correlationAnalysisRepository.getCorrelationResults(market)
        }

    init {
        checkApiKey()
        loadSelectedProvider()
    }

    // ========== 설정 관련 ==========

    private fun checkApiKey() {
        viewModelScope.launch {
            val provider = apiKeyProvider.getSelectedProvider()
            val apiKey = apiKeyProvider.getApiKey(provider)
            _isApiKeyConfigured.value = !apiKey.isNullOrBlank()
            _selectedProvider.value = provider
        }
    }

    private fun loadSelectedProvider() {
        _selectedProvider.value = apiKeyProvider.getSelectedProvider()
    }

    fun selectMarket(market: String) {
        _selectedMarket.value = market
    }

    fun selectProvider(provider: AIProvider) {
        apiKeyProvider.setSelectedProvider(provider)
        _selectedProvider.value = provider
        checkApiKey()
    }

    fun getAvailableProviders(): List<AIProvider> {
        return aiApiClientFactory.getAvailableProviders()
    }

    // ========== 상관관계 분석 ==========

    /**
     * 상관관계 분석 실행 (로컬 계산만)
     */
    fun runCorrelationAnalysis(periodDays: Int = 30) {
        viewModelScope.launch {
            _state.value = NewAIAnalysisState.AnalyzingCorrelation

            val result = correlationAnalysisRepository.runLatestCorrelationAnalysis(
                market = _selectedMarket.value,
                periodDays = periodDays
            )

            if (result.isSuccess) {
                val correlation = result.getOrThrow()
                _analysisResult.value = FullAnalysisResult(
                    correlationResult = correlation,
                    aiResult = null,
                    errorMessage = null
                )
                _state.value = NewAIAnalysisState.CorrelationComplete(correlation)
            } else {
                _state.value = NewAIAnalysisState.Error(
                    result.exceptionOrNull()?.message ?: "상관관계 분석 실패"
                )
            }
        }
    }

    /**
     * 전체 분석 실행 (상관관계 + AI 해석)
     */
    fun runFullAnalysis(periodDays: Int = 30) {
        viewModelScope.launch {
            if (!_isApiKeyConfigured.value) {
                _state.value = NewAIAnalysisState.Error(
                    "API 키가 설정되지 않았습니다. 설정에서 ${_selectedProvider.value.name} API 키를 등록해주세요."
                )
                return@launch
            }

            _state.value = NewAIAnalysisState.AnalyzingFull

            val result = correlationAnalysisRepository.runFullAnalysis(
                market = _selectedMarket.value,
                periodDays = periodDays
            )

            if (result.isSuccess) {
                val fullResult = result.getOrThrow()
                _analysisResult.value = fullResult
                _state.value = NewAIAnalysisState.FullAnalysisComplete(fullResult)
            } else {
                _state.value = NewAIAnalysisState.Error(
                    result.exceptionOrNull()?.message ?: "분석 실패"
                )
            }
        }
    }

    /**
     * 기존 상관관계 분석에 AI 해석 추가
     */
    fun interpretWithAI(correlationResult: CorrelationAnalysisResult) {
        viewModelScope.launch {
            if (!_isApiKeyConfigured.value) {
                _state.value = NewAIAnalysisState.Error("API 키가 설정되지 않았습니다.")
                return@launch
            }

            _state.value = NewAIAnalysisState.InterpretingWithAI

            val result = correlationAnalysisRepository.interpretWithAI(correlationResult)

            if (result.isSuccess) {
                val aiResult = result.getOrThrow()
                _analysisResult.value = FullAnalysisResult(
                    correlationResult = correlationResult,
                    aiResult = aiResult,
                    errorMessage = null
                )
                _state.value = NewAIAnalysisState.AIInterpretationComplete(aiResult)
            } else {
                _state.value = NewAIAnalysisState.Error(
                    result.exceptionOrNull()?.message ?: "AI 해석 실패"
                )
            }
        }
    }

    // ========== 채팅 기능 ==========

    /**
     * 새 채팅 세션 시작
     */
    fun startNewChat() {
        viewModelScope.launch {
            val analysisResult = _analysisResult.value

            val session = if (analysisResult != null) {
                chatRepository.createSessionWithAnalysis(
                    correlationResult = analysisResult.correlationResult,
                    aiResult = analysisResult.aiResult
                )
            } else {
                chatRepository.createSession(
                    market = _selectedMarket.value,
                    title = "새 대화"
                )
            }

            _currentSession.value = session
            loadMessages(session.id)
            _state.value = NewAIAnalysisState.ChatActive(session)
        }
    }

    /**
     * 기존 세션 열기
     */
    fun openSession(sessionId: String) {
        viewModelScope.launch {
            val session = chatRepository.getSession(sessionId)
            if (session != null) {
                _currentSession.value = session
                loadMessages(session.id)
                _state.value = NewAIAnalysisState.ChatActive(session)
            }
        }
    }

    /**
     * 메시지 로드
     */
    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }

    /**
     * 메시지 전송
     */
    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return

        viewModelScope.launch {
            _isSendingMessage.value = true

            val result = chatRepository.sendMessage(session.id, content)

            _isSendingMessage.value = false

            if (result.isFailure) {
                _state.value = NewAIAnalysisState.ChatError(
                    result.exceptionOrNull()?.message ?: "메시지 전송 실패"
                )
            }
        }
    }

    /**
     * 세션 삭제
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSession.value?.id == sessionId) {
                _currentSession.value = null
                _chatMessages.value = emptyList()
                _state.value = NewAIAnalysisState.Idle
            }
        }
    }

    /**
     * 채팅 종료
     */
    fun closeChat() {
        _currentSession.value = null
        _chatMessages.value = emptyList()

        // 분석 결과가 있으면 해당 상태로, 없으면 Idle
        val result = _analysisResult.value
        _state.value = when {
            result?.aiResult != null -> NewAIAnalysisState.FullAnalysisComplete(result)
            result?.correlationResult != null -> NewAIAnalysisState.CorrelationComplete(result.correlationResult)
            else -> NewAIAnalysisState.Idle
        }
    }

    // ========== 유틸리티 ==========

    fun clearError() {
        val result = _analysisResult.value
        _state.value = when {
            result?.aiResult != null -> NewAIAnalysisState.FullAnalysisComplete(result)
            result?.correlationResult != null -> NewAIAnalysisState.CorrelationComplete(result.correlationResult)
            else -> NewAIAnalysisState.Idle
        }
    }

    fun refreshApiKeyStatus() {
        checkApiKey()
    }
}

/**
 * 새로운 AI 분석 화면 상태
 */
sealed class NewAIAnalysisState {
    object Idle : NewAIAnalysisState()

    // 분석 진행 중
    object AnalyzingCorrelation : NewAIAnalysisState()
    object AnalyzingFull : NewAIAnalysisState()
    object InterpretingWithAI : NewAIAnalysisState()

    // 분석 완료
    data class CorrelationComplete(val result: CorrelationAnalysisResult) : NewAIAnalysisState()
    data class FullAnalysisComplete(val result: FullAnalysisResult) : NewAIAnalysisState()
    data class AIInterpretationComplete(val result: AIAnalysisResult) : NewAIAnalysisState()

    // 채팅
    data class ChatActive(val session: AIChatSession) : NewAIAnalysisState()
    data class ChatError(val message: String) : NewAIAnalysisState()

    // 에러
    data class Error(val message: String) : NewAIAnalysisState()
}

/**
 * 신호 타입 확장 함수
 */
fun String.toSignalType(): SignalType {
    return try {
        SignalType.valueOf(this)
    } catch (e: Exception) {
        SignalType.NEUTRAL
    }
}
