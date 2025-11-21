package com.etfmonitor.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 폰트 스케일 설정 데이터 클래스
 */
data class FontScaleSettings(
    val displayScale: Float = 1.0f,
    val headlineScale: Float = 1.0f,
    val titleScale: Float = 1.0f,
    val bodyScale: Float = 1.0f,
    val labelScale: Float = 1.0f
)

/**
 * 앱 전역 테마 상태 관리
 * Singleton으로 MainActivity와 SettingsViewModel에서 공유
 */
@Singleton
class ThemeManager @Inject constructor() {

    // null = 시스템 설정 따름, true = 다크 모드, false = 라이트 모드
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    // 폰트 스케일 설정
    private val _fontScaleSettings = MutableStateFlow(FontScaleSettings())
    val fontScaleSettings: StateFlow<FontScaleSettings> = _fontScaleSettings.asStateFlow()

    fun setDarkTheme(isDark: Boolean?) {
        _isDarkTheme.value = isDark
    }

    fun setFontScaleSettings(settings: FontScaleSettings) {
        _fontScaleSettings.value = settings
    }

    fun setDisplayScale(scale: Float) {
        _fontScaleSettings.value = _fontScaleSettings.value.copy(displayScale = scale)
    }

    fun setHeadlineScale(scale: Float) {
        _fontScaleSettings.value = _fontScaleSettings.value.copy(headlineScale = scale)
    }

    fun setTitleScale(scale: Float) {
        _fontScaleSettings.value = _fontScaleSettings.value.copy(titleScale = scale)
    }

    fun setBodyScale(scale: Float) {
        _fontScaleSettings.value = _fontScaleSettings.value.copy(bodyScale = scale)
    }

    fun setLabelScale(scale: Float) {
        _fontScaleSettings.value = _fontScaleSettings.value.copy(labelScale = scale)
    }
}
