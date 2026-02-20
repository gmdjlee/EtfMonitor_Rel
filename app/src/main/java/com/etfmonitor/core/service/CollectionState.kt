package com.etfmonitor.core.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 데이터 수집 상태를 전역적으로 관리
 *
 * SharedPreferences를 통해 앱 프로세스 종료 후에도 수집 상태를 복원합니다.
 * 앱 재시작 시 isCollecting=true였던 경우를 interrupted(중단됨) 상태로 감지합니다.
 *
 * 초기화: Application.onCreate()에서 반드시 [init]을 호출해야 합니다.
 * 단위 테스트 환경에서는 [init] 없이 사용 가능합니다 (prefs=null safe).
 */
object CollectionState {

    // SharedPreferences keys
    private const val PREF_NAME = "collection_state"
    private const val KEY_IS_COLLECTING = "is_collecting"
    private const val KEY_MESSAGE = "message"
    private const val KEY_PROGRESS = "progress"

    /** null-safe: unit test contexts that never call init() won't crash */
    private var prefs: SharedPreferences? = null

    // --- Existing StateFlows (unchanged contract) ---

    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()

    private val _currentProgress = MutableStateFlow(0)
    val currentProgress: StateFlow<Int> = _currentProgress.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    // --- New StateFlows: interrupted-collection detection ---

    private val _wasInterrupted = MutableStateFlow(false)
    val wasInterrupted: StateFlow<Boolean> = _wasInterrupted.asStateFlow()

    private val _interruptedMessage = MutableStateFlow("")
    val interruptedMessage: StateFlow<String> = _interruptedMessage.asStateFlow()

    private val _interruptedProgress = MutableStateFlow(0)
    val interruptedProgress: StateFlow<Int> = _interruptedProgress.asStateFlow()

    /**
     * 앱 시작 시 Application.onCreate()에서 호출.
     *
     * SharedPreferences를 읽어 이전 프로세스가 수집 중이었는지 감지합니다.
     * isCollecting=true 상태로 앱이 죽었다면 wasInterrupted=true를 설정합니다.
     */
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val wasCollecting = prefs?.getBoolean(KEY_IS_COLLECTING, false) ?: false
        if (wasCollecting) {
            val savedMessage = prefs?.getString(KEY_MESSAGE, "") ?: ""
            val savedProgress = prefs?.getInt(KEY_PROGRESS, 0) ?: 0
            _wasInterrupted.value = true
            _interruptedMessage.value = savedMessage
            _interruptedProgress.value = savedProgress
        }
    }

    // --- Existing methods (backward compatible, now also persist to prefs) ---

    fun startCollection(isInitialize: Boolean, initialMessage: String = "준비 중...") {
        _isCollecting.value = true
        _isInitializing.value = isInitialize
        _currentMessage.value = initialMessage
        _currentProgress.value = 0
        prefs?.edit()
            ?.putBoolean(KEY_IS_COLLECTING, true)
            ?.putString(KEY_MESSAGE, initialMessage)
            ?.putInt(KEY_PROGRESS, 0)
            ?.apply()
    }

    fun updateProgress(message: String, progress: Int) {
        _currentMessage.value = message
        _currentProgress.value = progress
        prefs?.edit()
            ?.putString(KEY_MESSAGE, message)
            ?.putInt(KEY_PROGRESS, progress)
            ?.apply()
    }

    fun complete(message: String) {
        _currentMessage.value = message
        _currentProgress.value = 100
        _isCollecting.value = false
        clearPersistedState()
    }

    fun error(message: String) {
        _currentMessage.value = message
        _isCollecting.value = false
        clearPersistedState()
    }

    fun reset() {
        _isCollecting.value = false
        _isInitializing.value = false
        _currentMessage.value = ""
        _currentProgress.value = 0
        clearPersistedState()
    }

    // --- New methods ---

    /**
     * interrupted 상태 StateFlows를 초기화합니다.
     * 저장된 prefs는 건드리지 않습니다.
     */
    fun clearInterrupted() {
        _wasInterrupted.value = false
        _interruptedMessage.value = ""
        _interruptedProgress.value = 0
    }

    /**
     * 사용자가 중단 알림을 확인했을 때 호출합니다.
     * wasInterrupted StateFlow와 저장된 prefs를 모두 초기화합니다.
     */
    fun acknowledgeInterruption() {
        _wasInterrupted.value = false
        _interruptedMessage.value = ""
        _interruptedProgress.value = 0
        clearPersistedState()
    }

    // --- Private helpers ---

    private fun clearPersistedState() {
        prefs?.edit()
            ?.remove(KEY_IS_COLLECTING)
            ?.remove(KEY_MESSAGE)
            ?.remove(KEY_PROGRESS)
            ?.apply()
    }
}
