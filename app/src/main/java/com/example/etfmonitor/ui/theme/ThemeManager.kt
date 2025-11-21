package com.etfmonitor.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 테마 상태 관리
 * Singleton으로 MainActivity와 SettingsViewModel에서 공유
 */
@Singleton
class ThemeManager @Inject constructor() {

    // null = 시스템 설정 따름, true = 다크 모드, false = 라이트 모드
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }
}
