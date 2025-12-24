package com.etfmonitor.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 데이터 수집 상태를 전역적으로 관리
 */
object CollectionState {
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()

    private val _currentProgress = MutableStateFlow(0)
    val currentProgress: StateFlow<Int> = _currentProgress.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    fun startCollection(isInitialize: Boolean, initialMessage: String = "준비 중...") {
        _isCollecting.value = true
        _isInitializing.value = isInitialize
        _currentMessage.value = initialMessage
        _currentProgress.value = 0
    }

    fun updateProgress(message: String, progress: Int) {
        _currentMessage.value = message
        _currentProgress.value = progress
    }

    fun complete(message: String) {
        _currentMessage.value = message
        _currentProgress.value = 100
        _isCollecting.value = false
    }

    fun error(message: String) {
        _currentMessage.value = message
        _isCollecting.value = false
    }

    fun reset() {
        _isCollecting.value = false
        _isInitializing.value = false
        _currentMessage.value = ""
        _currentProgress.value = 0
    }
}