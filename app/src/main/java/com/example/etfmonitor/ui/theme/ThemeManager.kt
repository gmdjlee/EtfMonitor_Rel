package com.etfmonitor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * 개별 차트 색상 설정
 */
data class SingleChartColorSettings(
    val lineColor1: Int = ChartPrimary.toArgb(),      // 첫 번째 라인 색상
    val lineColor2: Int = ChartSecondary.toArgb(),    // 두 번째 라인 색상
    val lineColor3: Int = ChartTertiary.toArgb(),     // 세 번째 라인 색상 (MACD Signal 등)
    val positiveColor: Int = ChartGreen.toArgb(),     // 양수/상승 색상
    val negativeColor: Int = ChartRed.toArgb(),       // 음수/하락 색상
    val textColor: Int? = null,                        // null이면 테마 기본값 사용
    val legendColor: Int? = null                       // null이면 테마 기본값 사용
)

/**
 * 전체 차트 색상 설정
 */
data class ChartColorSettings(
    val marketCapOscillator: SingleChartColorSettings = SingleChartColorSettings(),
    val macd: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartPrimary.toArgb(),
        lineColor2 = ChartOrange.toArgb()  // Signal line
    ),
    val marketDeposit: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartPrimary.toArgb(),
        lineColor2 = ChartTertiary.toArgb()
    ),
    val fearGreed: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartOrange.toArgb(),     // Fear & Greed Oscillator
        lineColor2 = ChartSecondary.toArgb()   // KOSPI/KOSDAQ Index
    )
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

    // 차트 색상 설정
    private val _chartColorSettings = MutableStateFlow(ChartColorSettings())
    val chartColorSettings: StateFlow<ChartColorSettings> = _chartColorSettings.asStateFlow()

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

    // 차트 색상 설정 메서드들
    fun setChartColorSettings(settings: ChartColorSettings) {
        _chartColorSettings.value = settings
    }

    fun setMarketCapOscillatorColors(colors: SingleChartColorSettings) {
        _chartColorSettings.value = _chartColorSettings.value.copy(marketCapOscillator = colors)
    }

    fun setMacdColors(colors: SingleChartColorSettings) {
        _chartColorSettings.value = _chartColorSettings.value.copy(macd = colors)
    }

    fun setMarketDepositColors(colors: SingleChartColorSettings) {
        _chartColorSettings.value = _chartColorSettings.value.copy(marketDeposit = colors)
    }

    fun setFearGreedColors(colors: SingleChartColorSettings) {
        _chartColorSettings.value = _chartColorSettings.value.copy(fearGreed = colors)
    }
}
