package com.etfmonitor.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.repository.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: DataRepository
) : ViewModel() {

    private val _themes = MutableStateFlow<List<String>>(emptyList())
    val themes: StateFlow<List<String>> = _themes.asStateFlow()

    private val _exclusions = MutableStateFlow<List<String>>(emptyList())
    val exclusions: StateFlow<List<String>> = _exclusions.asStateFlow()

    private val _defaultDays = MutableStateFlow(25)  // ✅ 추가
    val defaultDays: StateFlow<Int> = _defaultDays.asStateFlow()  // ✅ 추가

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _themes.value = repository.getThemes()
            _exclusions.value = repository.getExclusions()
            _defaultDays.value = repository.getDefaultDays()  // ✅ 추가
        }
    }

    // ✅ 기본 수집 기간 설정 메서드 추가
    fun setDefaultDays(days: Int) {
        viewModelScope.launch {
            repository.setDefaultDays(days)
            _defaultDays.value = days
            _message.value = "기본 수집 기간이 ${days}일로 설정되었습니다"
        }
    }

    fun addTheme(theme: String) {
        if (theme.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 추가됨: $theme"
        }
    }

    fun removeTheme(theme: String) {
        viewModelScope.launch {
            repository.removeTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 제거됨: $theme"
        }
    }

    fun addExclusion(keyword: String) {
        if (keyword.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 추가됨: $keyword"
        }
    }

    fun removeExclusion(keyword: String) {
        viewModelScope.launch {
            repository.removeExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 제거됨: $keyword"
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.resetDatabase()
            _message.value = "데이터베이스가 초기화되었습니다"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    EtfMonitorApp.instance.repository
                ) as T
            }
        }
    }
}