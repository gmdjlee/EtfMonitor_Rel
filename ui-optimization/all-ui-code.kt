# EtfMonitor UI Code Extraction
# Generated: Sat Jan 10 13:48:16 UTC 2026

// ====== CORE UI COMPONENTS ======

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/BottomNavigationBar.kt
package com.etfmonitor.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom Navigation Bar - Moss Green Nature Theme
 *
 * New menu structure:
 * - 시장 지표: Fear & Greed, 과매수/과매도, 증시 자금 동향
 * - ETF: ETF 목록, ETF 통계
 * - 홈: Home (center button)
 * - 종목: 종목 수급 분석
 * - 분석: AI 분석, ML 예측, 고급 분석
 */

enum class MainNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MARKET_INDICATOR("market_indicator", "시장 지표", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    ETF("etf_hub", "ETF", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    HOME("home", "홈", Icons.Filled.Home, Icons.Outlined.Home),
    STOCKS("stocks", "종목", Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart),
    ANALYSIS("analysis", "분석", Icons.Filled.Analytics, Icons.Outlined.Analytics)
}

@Composable
fun MainBottomNavigationBar(
    currentRoute: String,
    onNavigate: (MainNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = MainNavItem.entries.toList()
    // Strip query parameters for route comparison (e.g., "etf_hub?stockTicker={stockTicker}" -> "etf_hub")
    val baseRoute = currentRoute.substringBefore("?")

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                if (item == MainNavItem.HOME) {
                    // Center Home button (elevated)
                    CenterHomeButton(
                        isSelected = baseRoute == item.route,
                        onClick = { onNavigate(item) }
                    )
                } else {
                    // Regular nav item
                    MainNavItemButton(
                        item = item,
                        isSelected = baseRoute == item.route,
                        onClick = { onNavigate(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainNavItemButton(
    item: MainNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        },
        label = "iconColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = iconColor
        )
    }
}

@Composable
private fun CenterHomeButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-12).dp)
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "홈",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "홈",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
            modifier = Modifier.offset(y = (-8).dp)
        )
    }
}

/**
 * Tab Navigation Bar for sub-screens
 * Used within consolidated menu screens (시장 지표, ETF, 분석)
 */
@Composable
fun TabNavigationBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 16.dp,
        divider = {}
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/ChartUtils.kt
package com.etfmonitor.core.ui.component

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet
import com.github.mikephil.charting.renderer.scatter.IShapeRenderer
import com.github.mikephil.charting.utils.ViewPortHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Chart components utility classes and shared composables
 */

internal const val CHART_TAG = "ChartComponents"

/**
 * 역삼각형 Shape Renderer (꼭지점이 아래를 향함)
 * 매도 시그널 표시에 사용
 */
class InvertedTriangleShapeRenderer : IShapeRenderer {
    override fun renderShape(
        c: Canvas,
        dataSet: IScatterDataSet,
        viewPortHandler: ViewPortHandler,
        posX: Float,
        posY: Float,
        renderPaint: Paint
    ) {
        val shapeSize = dataSet.scatterShapeSize
        val halfSize = shapeSize / 2f

        renderPaint.style = Paint.Style.FILL

        val path = android.graphics.Path()
        // 역삼각형: 위쪽 두 점, 아래쪽 한 점
        path.moveTo(posX - halfSize, posY - halfSize)  // 좌상단
        path.lineTo(posX + halfSize, posY - halfSize)  // 우상단
        path.lineTo(posX, posY + halfSize)              // 하단 중앙 (꼭지점)
        path.close()

        c.drawPath(path, renderPaint)
    }
}

/**
 * 차트 색상 제공을 위한 ViewModel
 */
@HiltViewModel
class ChartColorViewModel @Inject constructor(
    private val themeManager: ThemeManager
) : ViewModel() {
    val chartColorSettings = themeManager.chartColorSettings
}

/**
 * Modern chart card container with enhanced styling
 * Uses a lighter background in dark mode for better chart readability
 */
@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val chartCardBackground = if (isDark) ChartCardBackgroundDark else ChartCardBackgroundLight
    // Always use black color for chart titles for maximum readability
    val titleColor = ComposeColor.Black
    val subtitleColor = ComposeColor.Black.copy(alpha = 0.7f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        colors = CardDefaults.cardColors(
            containerColor = chartCardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 1.dp,
            hoveredElevation = 5.dp
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor
                )

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                }
            }

            content()
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/CustomMarkerView.kt
package com.etfmonitor.core.ui.component

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.etfmonitor.R
import com.etfmonitor.core.common.util.AppLogger

private val logger = AppLogger.getLogger("CustomMarkerView")

/**
 * 차트 값의 타입
 */
enum class ValueType {
    CURRENCY,      // 화폐 (원, 억원, 조원)
    RATIO,         // 비율 (단위 없음)
    PERCENTAGE,    // 퍼센트 (%)
    NUMBER         // 일반 숫자
}

/**
 * 범용 마커 뷰 - 값 포맷터를 받아서 사용
 */
class CustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>,
    private val formatter: (Float) -> String
) : MarkerView(context, layoutResource) {

    private var tvContent: TextView? = null

    init {
        try {
            tvContent = findViewById(R.id.tvContent)
            if (tvContent == null) {
                logger.e("CustomMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            logger.e("CustomMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                logger.w("CustomMarkerView: Entry is null")
                return
            }

            val index = e.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else "N/A"

            val formattedValue = try {
                formatter(e.y)
            } catch (ex: Exception) {
                logger.e("CustomMarkerView: Formatter error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$formattedValue"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            logger.e("CustomMarkerView refreshContent error", e)
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

/**
 * 시가총액 차트 전용 마커 뷰
 */
class MarketCapMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>,
    private val valueType: ValueType = ValueType.CURRENCY
) : MarkerView(context, layoutResource) {

    private var tvContent: TextView? = null

    init {
        try {
            tvContent = findViewById(R.id.tvContent)
            if (tvContent == null) {
                logger.e("MarketCapMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            logger.e("MarketCapMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                logger.w("MarketCapMarkerView: Entry is null")
                return
            }

            val index = e.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else "N/A"

            // dataSetIndex로 시가총액과 오실레이터 구분
            val value = e.y
            val formattedValue = try {
                when (highlight?.dataSetIndex) {
                    0 -> formatCurrency(value)  // 시가총액 (첫 번째 데이터셋)
                    1 -> formatRatio(value)     // 오실레이터 (두 번째 데이터셋)
                    else -> formatCurrency(value)
                }
            } catch (ex: Exception) {
                logger.e("MarketCapMarkerView: Format error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$formattedValue"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            logger.e("MarketCapMarkerView refreshContent error", e)
        }
    }

    /**
     * 화폐 값 포맷팅
     */
    private fun formatCurrency(value: Float): String {
        return try {
            val absValue = kotlin.math.abs(value.toDouble())
            when {
                absValue >= 1_000_000_000_000 -> {
                    val trillion = value / 1_000_000_000_000
                    String.format("%.2f조원", trillion)
                }
                absValue >= 100_000_000 -> {
                    val hundredMillion = value / 100_000_000
                    String.format("%.2f억원", hundredMillion)
                }
                absValue >= 10_000 -> {
                    val tenThousand = value / 10_000
                    String.format("%.2f만원", tenThousand)
                }
                else -> {
                    String.format("%.0f원", value)
                }
            }
        } catch (e: Exception) {
            logger.e("formatCurrency error", e)
            "Error"
        }
    }

    /**
     * 비율 값 포맷팅 (단위 없음)
     */
    private fun formatRatio(value: Float): String {
        return try {
            val absValue = kotlin.math.abs(value)
            when {
                absValue < 0.001 -> String.format("%.6f", value)
                absValue < 1 -> String.format("%.4f", value)
                absValue < 100 -> String.format("%.2f", value)
                else -> String.format("%.0f", value)
            }
        } catch (e: Exception) {
            logger.e("formatRatio error", e)
            "Error"
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

/**
 * MACD 차트 전용 마커 뷰
 */
class MacdMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>,
    private val macdValues: List<Double>,
    private val signalValues: List<Double>
) : MarkerView(context, layoutResource) {

    private var tvContent: TextView? = null

    init {
        try {
            tvContent = findViewById(R.id.tvContent)
            if (tvContent == null) {
                logger.e("MacdMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            logger.e("MacdMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                logger.w("MacdMarkerView: Entry is null")
                return
            }

            val index = e.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else "N/A"

            // MACD 값과 Signal 값 표시
            val macdText = try {
                if (index >= 0 && index < macdValues.size && index < signalValues.size) {
                    val macd = macdValues[index]
                    val signal = signalValues[index]
                    "MACD: ${String.format("%.3f", macd)}\nSignal: ${String.format("%.3f", signal)}"
                } else {
                    logger.w("MacdMarkerView: Index out of range - index=$index, macd.size=${macdValues.size}, signal.size=${signalValues.size}")
                    "N/A"
                }
            } catch (ex: Exception) {
                logger.e("MacdMarkerView: Format error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$macdText"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            logger.e("MacdMarkerView refreshContent error", e)
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/DateRangeSelector.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 차트 X축 날짜 범위 옵션
 *
 * @property label UI에 표시될 레이블
 * @property days 범위에 해당하는 일수 (-1은 전체 데이터)
 */
enum class DateRangeOption(val label: String, val days: Int) {
    WEEK("1주", 7),
    MONTH("1개월", 30),
    THREE_MONTHS("3개월", 90),
    SIX_MONTHS("6개월", 180),
    YEAR("1년", 365),
    THREE_YEARS("3년", 1095),
    FIVE_YEARS("5년", 1825),
    SEVEN_YEARS("7년", 2555),
    ALL("전체", -1);

    companion object {
        /**
         * 기본 선택 옵션
         */
        val DEFAULT = YEAR
    }
}

/**
 * 날짜 범위 선택 UI 컴포넌트
 *
 * FilterChip을 사용하여 사용자가 차트의 X축 날짜 범위를 선택할 수 있게 합니다.
 *
 * @param selectedRange 현재 선택된 범위
 * @param onRangeSelected 범위가 선택되었을 때 호출되는 콜백
 * @param modifier Modifier
 * @param availableOptions 표시할 옵션 목록 (기본값: 전체 옵션)
 */
@Composable
fun DateRangeSelector(
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier,
    availableOptions: List<DateRangeOption> = DateRangeOption.entries
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availableOptions.forEach { option ->
            FilterChip(
                selected = selectedRange == option,
                onClick = { onRangeSelected(option) },
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * 차트 라벨 및 날짜 범위 계산 유틸리티
 */
object ChartLabelCalculator {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 데이터 포인트 수에 따른 최적 라벨 개수 계산
     *
     * 라벨이 너무 많으면 겹치고, 너무 적으면 정보가 부족합니다.
     * 이 함수는 데이터 포인트 수에 따라 적절한 라벨 개수를 반환합니다.
     *
     * @param dataPoints 차트에 표시될 데이터 포인트 수
     * @return 최적의 라벨 개수
     */
    fun calculateOptimalLabelCount(dataPoints: Int): Int {
        return when {
            dataPoints <= 7 -> dataPoints.coerceAtLeast(2)   // 1주: 매일 표시
            dataPoints <= 14 -> 7                             // 2주: 2일마다
            dataPoints <= 30 -> 10                            // 1개월: 3일마다
            dataPoints <= 90 -> 10                            // 3개월: 9일마다
            dataPoints <= 180 -> 8                            // 6개월: 22일마다
            dataPoints <= 365 -> 8                            // 1년: 45일마다 (increased from 6)
            dataPoints <= 730 -> 10                           // 2년: 73일마다
            else -> 12                                        // 2년 이상: more labels for visibility
        }
    }

    /**
     * DateRangeOption에 따른 시작/종료 날짜 계산
     *
     * @param option 선택된 날짜 범위 옵션
     * @param endDate 종료일 (기본값: 오늘)
     * @return Pair(시작일, 종료일) - yyyy-MM-dd 형식
     */
    fun calculateDateRange(
        option: DateRangeOption,
        endDate: LocalDate = LocalDate.now()
    ): Pair<String, String> {
        val startDate = when (option) {
            DateRangeOption.ALL -> LocalDate.of(2020, 1, 1)  // 가장 이른 날짜
            else -> endDate.minusDays(option.days.toLong())
        }
        return Pair(
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        )
    }

    /**
     * 날짜 문자열 목록에서 시작/종료 날짜 추출
     *
     * @param dates 날짜 문자열 목록 (yyyy-MM-dd 형식, 정렬되어 있다고 가정)
     * @return Pair(시작일, 종료일) 또는 빈 목록이면 null
     */
    fun extractDateRange(dates: List<String>): Pair<String, String>? {
        if (dates.isEmpty()) return null
        return Pair(dates.first(), dates.last())
    }

    /**
     * 날짜 범위 옵션에 해당하는 예상 데이터 포인트 수 반환
     * (영업일 기준 대략적인 추정)
     *
     * @param option 날짜 범위 옵션
     * @return 예상 데이터 포인트 수
     */
    fun estimatedDataPoints(option: DateRangeOption): Int {
        return when (option) {
            DateRangeOption.WEEK -> 5          // 주 5일 영업
            DateRangeOption.MONTH -> 22        // 월 ~22일 영업
            DateRangeOption.THREE_MONTHS -> 66
            DateRangeOption.SIX_MONTHS -> 132
            DateRangeOption.YEAR -> 252        // 연 ~252일 영업
            DateRangeOption.THREE_YEARS -> 756 // 3년 ~756일 영업
            DateRangeOption.FIVE_YEARS -> 1260 // 5년 ~1260일 영업
            DateRangeOption.SEVEN_YEARS -> 1764 // 7년 ~1764일 영업
            DateRangeOption.ALL -> 2000        // 약 8년치
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/DesignSystemComponents.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Design System Components
 * Shared UI components for ETF Monitor
 */

// ============================================
// Filter Chip Row Component
// ============================================

@Composable
fun FilterChipRow(
    filters: List<String>,
    selectedIndex: Int,
    onFilterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEachIndexed { index, filter ->
            val isSelected = index == selectedIndex

            Surface(
                modifier = Modifier.clickable { onFilterSelected(index) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = if (!isSelected) {
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                } else null
            ) {
                Text(
                    text = filter,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ============================================
// Stat Box Component (for Fear & Greed detail)
// ============================================

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/ErrorBoundary.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.common.util.AppLogger

/**
 * Error state holder for ErrorBoundary.
 * Provides a way to capture and manage error states in Compose.
 */
class ErrorBoundaryState {
    private var _error: Throwable? by mutableStateOf(null)
    val error: Throwable? get() = _error

    fun setError(throwable: Throwable) {
        _error = throwable
    }

    fun clearError() {
        _error = null
    }

    fun hasError(): Boolean = _error != null
}

/**
 * Remember an ErrorBoundaryState instance.
 */
@Composable
fun rememberErrorBoundaryState(): ErrorBoundaryState {
    return remember { ErrorBoundaryState() }
}

/**
 * Error Boundary wrapper for Compose content.
 *
 * Since Compose doesn't support try-catch around composables directly,
 * this component provides a state-based error handling mechanism.
 *
 * Usage:
 * ```kotlin
 * val errorState = rememberErrorBoundaryState()
 *
 * ErrorBoundary(
 *     state = errorState,
 *     onRetry = { viewModel.retry() }
 * ) {
 *     // Your content that might cause errors
 *     MyScreen(
 *         onError = { errorState.setError(it) }
 *     )
 * }
 * ```
 *
 * For ViewModel integration:
 * ```kotlin
 * LaunchedEffect(uiState) {
 *     if (uiState is UiState.Error) {
 *         errorState.setError(uiState.exception)
 *     }
 * }
 * ```
 *
 * @param state ErrorBoundaryState to track error state
 * @param fallback Custom fallback composable (optional)
 * @param onRetry Callback when retry button is clicked (optional)
 * @param content The content to display when there's no error
 */
@Composable
fun ErrorBoundary(
    state: ErrorBoundaryState,
    modifier: Modifier = Modifier,
    fallback: (@Composable (Throwable, () -> Unit) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val logger = remember { AppLogger.getLogger("ErrorBoundary") }

    if (state.hasError()) {
        val error = state.error!!
        logger.e("Error boundary triggered", error)

        val retryAction: () -> Unit = {
            state.clearError()
            onRetry?.invoke()
        }

        if (fallback != null) {
            fallback(error, retryAction)
        } else {
            DefaultErrorFallback(
                error = error,
                modifier = modifier,
                onRetry = if (onRetry != null) retryAction else null
            )
        }
    } else {
        content()
    }
}

/**
 * Default error fallback UI.
 */
@Composable
fun DefaultErrorFallback(
    error: Throwable,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = stringResource(R.string.cd_error_icon),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.error_data_load),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message ?: error.javaClass.simpleName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_retry))
            }
        }
    }
}

/**
 * Compact error fallback for inline usage (e.g., within cards or lists).
 */
@Composable
fun CompactErrorFallback(
    error: Throwable,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = stringResource(R.string.cd_error_icon),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.error_data_load),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error.message?.take(50) ?: error.javaClass.simpleName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (onRetry != null) {
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_button),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/HubComponents.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared Hub Header Component
 * Used by all hub screens (시장 지표, ETF, 종목, 분석)
 */

@Composable
fun HubHeader(
    title: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Theme toggle button
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "라이트 모드" else "다크 모드",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Settings button
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/MarketCharts.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import com.etfmonitor.core.common.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.core.analysis.model.OscillatorResult
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Market-related chart components
 * - MarketCapOscillatorChart: 시가총액 + 수급 오실레이터 복합 차트
 * - MarketDepositChart: 증시 자금 동향 차트
 */

private val logger = AppLogger.getLogger("MarketCharts")

/**
 * 시가총액 + 수급 오실레이터 복합 차트
 */
@Composable
fun MarketCapOscillatorChart(
    result: OscillatorResult,
    marketCap: List<Long>,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 데이터 검증
    if (result.dates.isEmpty() || marketCap.isEmpty()) {
        logger.w("Empty data for MarketCapOscillatorChart")
        return
    }

    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val primaryColor = colorSettings.lineColor1  // 시가총액 (기본값: Black)
    val tertiaryColor = colorSettings.lineColor2  // 오실레이터
    val textColor = colorSettings.textColor      // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor  // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "시가총액 & 수급 오실레이터",
        subtitle = latestDate?.let { "최신 데이터: $it" },
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                try {
                    CombinedChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        isDragEnabled = true
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        setExtraBottomOffset(10f)  // Extra padding for rotated labels
                        setDrawOrder(arrayOf(
                            CombinedChart.DrawOrder.LINE,
                            CombinedChart.DrawOrder.LINE
                        ))

                        // 마커 뷰 설정
                        try {
                            val markerView = MarketCapMarkerView(
                                context,
                                R.layout.marker_view,
                                result.dates
                            )
                            marker = markerView
                        } catch (e: Exception) {
                            logger.e("Error creating marker", e)
                        }

                        // X축 설정
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            setGridColor(gridColor)
                            enableGridDashedLine(10f, 5f, 0f)
                            setTextColor(textColor)
                            granularity = 1f
                            labelRotationAngle = -45f
                            setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                            // labelCount and valueFormatter are set in update block
                        }

                        // 왼쪽 Y축 (시가총액)
                        axisLeft.apply {
                            setDrawGridLines(true)
                            gridLineWidth = 1f
                            setGridColor(gridColor)
                            enableGridDashedLine(10f, 5f, 0f)
                            setTextColor(textColor)
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    val billions = (value / 100_000_000).toInt()
                                    return when {
                                        billions >= 10000 -> "${billions / 10000}조"
                                        billions >= 1000 -> String.format("%.1f조", billions / 10000f)
                                        else -> "${billions}억"
                                    }
                                }
                            }
                        }

                        // 오른쪽 Y축 (오실레이터)
                        axisRight.apply {
                            isEnabled = true
                            setDrawGridLines(false)
                            setTextColor(textColor)
                            setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        }

                        legend.apply {
                            isEnabled = true
                            textSize = 12f
                            setTextColor(legendColor)
                        }
                    }
                } catch (e: Exception) {
                    logger.e("Error creating chart", e)
                    CombinedChart(context)
                }
            },
            update = { chart ->
                try {
                    val dataCount = result.dates.size

                    // Update x-axis with dynamic label count and smart date formatting
                    chart.xAxis.apply {
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < result.dates.size) {
                                    DateFormatter.formatForChartByDataCount(result.dates[index], dataCount)
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // 시가총액 라인
                    val marketCapEntries = marketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = primaryColor
                        lineWidth = 2.5f
                        setCircleColor(primaryColor)
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = primaryColor
                    }

                    // 오실레이터 라인
                    val oscEntries = result.oscillator.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val oscDataSet = LineDataSet(oscEntries, "오실레이터").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = tertiaryColor
                        lineWidth = 2.5f
                        setCircleColor(tertiaryColor)
                        circleRadius = 2f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = tertiaryColor
                    }

                    val lineData = LineData(marketCapDataSet, oscDataSet)
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating chart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

/**
 * 증시 자금 동향 차트
 */
@Composable
fun MarketDepositChart(
    data: MarketDepositData,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketDeposit

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val depositColor = colorSettings.lineColor1   // 고객예탁금 (기본값: Black)
    val creditColor = colorSettings.lineColor2    // 신용잔고
    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "증시 자금 동향 (고객예탁금 & 신용잔고)",
        subtitle = latestDate?.let { "최신 데이터: $it" },
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setExtraBottomOffset(10f)  // Extra padding for rotated labels

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        data.dates
                    ) { value ->
                        "${value.toInt()}억원"
                    }
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                        // labelCount and valueFormatter are set in update block
                    }

                    // 왼쪽 Y축 (고객예탁금)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${value.toInt()}억"
                            }
                        }
                    }

                    // 오른쪽 Y축 (신용잔고)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${value.toInt()}억"
                            }
                        }
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                val dataCount = data.dates.size

                // Update x-axis with dynamic label count and smart date formatting
                chart.xAxis.apply {
                    setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < data.dates.size) {
                                DateFormatter.formatForChartByDataCount(data.dates[index], dataCount)
                            } else {
                                ""
                            }
                        }
                    }
                }

                // 고객예탁금
                val depositEntries = data.depositAmounts.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val depositDataSet = LineDataSet(depositEntries, "고객예탁금").apply {
                    axisDependency = YAxis.AxisDependency.LEFT
                    color = depositColor
                    lineWidth = 2.5f
                    setCircleColor(depositColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    highLightColor = depositColor
                }

                // 신용잔고
                val creditEntries = data.creditAmounts.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val creditDataSet = LineDataSet(creditEntries, "신용잔고").apply {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    color = creditColor
                    lineWidth = 2.5f
                    setCircleColor(creditColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    highLightColor = creditColor
                }

                val lineData = LineData(depositDataSet, creditDataSet)
                val combinedData = CombinedData().apply {
                    setData(lineData)
                }

                chart.data = combinedData
                chart.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/StateCards.kt
package com.etfmonitor.core.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.ui.theme.elevation
import com.etfmonitor.core.ui.theme.extendedShapes
import com.etfmonitor.core.ui.theme.spacing

/**
 * Material Design 3 State Card Components
 * Professional, production-ready state indicators with:
 * - Consistent elevation and shape
 * - Smooth animations
 * - Accessible color contrast
 * - Modern look & feel
 */

/**
 * Loading state card with smooth pulsing animation
 * Professional loading indicator for data-heavy operations
 */
@Composable
fun LoadingCard(
    message: String = "데이터 분석 중...",
    modifier: Modifier = Modifier
) {
    // Smooth breathing animation for text
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = MaterialTheme.elevation.level2
        ),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(alpha)
                )
            }
        }
    }
}

/**
 * Error state card with prominent, accessible styling
 * Clear visual hierarchy with icon and message
 *
 * @param message Error message to display
 * @param modifier Modifier for the card
 * @param onDismiss Optional callback for dismiss action. When provided, shows a close button.
 */
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = MaterialTheme.elevation.level2
        ),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * Idle state card with subtle, inviting styling
 * Gentle design to indicate available actions
 */
@Composable
fun IdleCard(
    message: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/TechnicalCharts.kt
package com.etfmonitor.core.ui.component

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import com.etfmonitor.core.common.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.ImpulseState
import com.etfmonitor.core.analysis.model.OscillatorResult
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Technical analysis chart components
 * - MacdChart: MACD 차트
 * - TrendSignalChart: 추세 시그널 차트
 * - ElderImpulseChart: Elder Impulse System 차트
 * - DemarkTDChart: DeMark TD Setup 차트
 */

private val logger = AppLogger.getLogger("TechnicalCharts")

/**
 * MACD 차트
 */
@Composable
fun MacdChart(
    result: OscillatorResult,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.macd

    // Modern theme colors
    val isDark = isSystemInDarkTheme()
    val macdColor = colorSettings.lineColor1      // MACD 라인 (기본값: Black)
    val signalColor = colorSettings.lineColor2    // Signal 라인
    val positiveColor = colorSettings.positiveColor   // Histogram 양수
    val negativeColor = colorSettings.negativeColor     // Histogram 음수
    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "MACD",
        subtitle = latestDate?.let { "최신 데이터: $it" },
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setDrawOrder(arrayOf(
                        CombinedChart.DrawOrder.BAR,
                        CombinedChart.DrawOrder.LINE
                    ))

                    // MACD 전용 마커 뷰
                    val markerView = MacdMarkerView(
                        context,
                        R.layout.marker_view,
                        result.dates,
                        result.macd,
                        result.signal
                    )
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(result.dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < result.dates.size) {
                                    result.dates[index]
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // Y축 설정
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    }
                    axisRight.isEnabled = false

                    legend.apply {
                        isEnabled = true
                        textSize = 12f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                // Histogram
                val barEntries = result.histogram.mapIndexed { index, value ->
                    BarEntry(index.toFloat(), value.toFloat())
                }
                val barDataSet = BarDataSet(barEntries, "").apply {
                    colors = result.histogram.map { value ->
                        if (value >= 0) positiveColor
                        else negativeColor
                    }
                    setDrawValues(false)
                    isHighlightEnabled = false
                }
                val barData = BarData(barDataSet).apply {
                    barWidth = 0.8f
                }

                // MACD 라인
                val macdEntries = result.macd.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val macdDataSet = LineDataSet(macdEntries, "MACD").apply {
                    color = macdColor
                    lineWidth = 2f
                    setCircleColor(macdColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    highLightColor = macdColor
                }

                // Signal 라인
                val signalEntries = result.signal.mapIndexed { index, value ->
                    Entry(index.toFloat(), value.toFloat())
                }
                val signalDataSet = LineDataSet(signalEntries, "Signal").apply {
                    color = signalColor
                    lineWidth = 2f
                    setCircleColor(signalColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    enableDashedLine(10f, 5f, 0f)
                    highLightColor = signalColor
                }

                val lineData = LineData(macdDataSet, signalDataSet)
                val combinedData = CombinedData().apply {
                    setData(barData)
                    setData(lineData)
                }

                chart.data = combinedData
                chart.legend.isEnabled = true
                chart.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

/**
 * 추세 시그널 차트 (가격 + MA + 매수/매도 시그널 + Fear & Greed)
 *
 * 시그널 색상:
 * - 매수/보조매수: 빨간색 (한국 주식시장 관례)
 * - 매도/보조매도: 파란색
 *
 * 시그널 마커: 종가 라인 위에 표시
 */
@Composable
fun TrendSignalChart(
    data: TrendSignalData,
    latestDate: String? = null,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    // 데이터 검증
    if (data.dates.isEmpty() || data.close.isEmpty()) {
        logger.w("Empty data for TrendSignalChart")
        return
    }

    // 차트 색상 설정 가져오기
    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.macd  // MACD 색상 재활용

    // Theme colors
    val isDark = isSystemInDarkTheme()
    val priceColor = colorSettings.lineColor1           // 종가
    val maColor = colorSettings.lineColor2              // MA

    // 매수: 빨간색, 매도: 파란색 (한국 주식시장 관례)
    val buyColor = Color.rgb(244, 67, 54)               // 매수 (빨간색)
    val auxBuyColor = Color.rgb(255, 138, 128)          // 보조매수 (연한 빨간색)
    val sellColor = Color.rgb(33, 150, 243)             // 매도 (파란색)
    val auxSellColor = Color.rgb(130, 177, 255)         // 보조매도 (연한 파란색)

    // Fear & Greed 라인 색상
    val fearGreedColor = Color.rgb(156, 39, 176)        // 보라색

    val textColor = colorSettings.textColor       // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor   // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    ChartCard(
        title = "추세 시그널 (MA/CMF/Fear&Greed)",
        subtitle = latestDate?.let { "최신 데이터: $it (${data.interval})" },
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)
                    setDrawOrder(arrayOf(
                        CombinedChart.DrawOrder.LINE,
                        CombinedChart.DrawOrder.SCATTER
                    ))

                    // 마커 뷰
                    val markerView = CustomMarkerView(
                        context,
                        R.layout.marker_view,
                        data.dates
                    ) { value ->
                        String.format("%,.0f", value)
                    }
                    marker = markerView

                    // X축 설정
                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(data.dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index >= 0 && index < data.dates.size) {
                                    data.dates[index]
                                } else {
                                    ""
                                }
                            }
                        }
                    }

                    // 왼쪽 Y축 (가격)
                    axisLeft.apply {
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        enableGridDashedLine(10f, 5f, 0f)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return when {
                                    value >= 1_000_000 -> String.format("%.1f만", value / 10_000f)
                                    value >= 10_000 -> String.format("%.0f", value)
                                    else -> String.format("%.0f", value)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축 (Fear & Greed: -1 ~ +1)
                    axisRight.apply {
                        isEnabled = true
                        setDrawGridLines(false)
                        setTextColor(textColor)
                        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                        axisMinimum = -1.2f
                        axisMaximum = 1.2f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return when {
                                    value > 0.6f -> "탐욕"
                                    value > 0.2f -> "+"
                                    value > -0.2f -> "중립"
                                    value > -0.6f -> "-"
                                    else -> "공포"
                                }
                            }
                        }
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 1. 종가 라인
                    val closeEntries = data.close.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val closeDataSet = LineDataSet(closeEntries, "종가").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = priceColor
                        lineWidth = 2.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = priceColor
                    }
                    lineDataSets.add(closeDataSet)

                    // 2. MA 라인
                    val maEntries = data.ma.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val maDataSet = LineDataSet(maEntries, "MA").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = maColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                        enableDashedLine(10f, 5f, 0f)
                        highLightColor = maColor
                    }
                    lineDataSets.add(maDataSet)

                    // 3. Fear & Greed 라인 차트 (오른쪽 Y축)
                    val fearGreedEntries = data.fearGreed.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val fearGreedDataSet = LineDataSet(fearGreedEntries, "F&G").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = fearGreedColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        highLightColor = fearGreedColor
                    }
                    lineDataSets.add(fearGreedDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // 4. 매수/매도 시그널 (Scatter) - 종가 라인 위에 표시
                    val buyEntries = mutableListOf<Entry>()
                    val auxBuyEntries = mutableListOf<Entry>()
                    val sellEntries = mutableListOf<Entry>()
                    val auxSellEntries = mutableListOf<Entry>()

                    // 매수 시그널 (종가에 표시)
                    data.buySignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            buyEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 보조매수 시그널 (종가에 표시)
                    data.auxBuySignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            auxBuyEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 매도 시그널 (종가에 표시)
                    data.sellSignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            sellEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    // 보조매도 시그널 (종가에 표시)
                    data.auxSellSignal.forEachIndexed { index, signal ->
                        if (signal == 1) {
                            auxSellEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    val scatterDataSets = mutableListOf<ScatterDataSet>()

                    // 매수 (빨간색, 큰 삼각형 마커)
                    if (buyEntries.isNotEmpty()) {
                        val buyDataSet = ScatterDataSet(buyEntries, "매수").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = buyColor
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE)
                            scatterShapeSize = 24f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(buyDataSet)
                    }

                    // 보조매수 (연한 빨간색, 작은 삼각형 마커)
                    if (auxBuyEntries.isNotEmpty()) {
                        val auxBuyDataSet = ScatterDataSet(auxBuyEntries, "보조매수").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = auxBuyColor
                            setScatterShape(ScatterChart.ScatterShape.TRIANGLE)
                            scatterShapeSize = 18f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(auxBuyDataSet)
                    }

                    // 매도 (파란색, 큰 역삼각형 마커)
                    if (sellEntries.isNotEmpty()) {
                        val sellDataSet = ScatterDataSet(sellEntries, "매도").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = sellColor
                            shapeRenderer = InvertedTriangleShapeRenderer()
                            scatterShapeSize = 24f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(sellDataSet)
                    }

                    // 보조매도 (연한 파란색, 작은 역삼각형 마커)
                    if (auxSellEntries.isNotEmpty()) {
                        val auxSellDataSet = ScatterDataSet(auxSellEntries, "보조매도").apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            color = auxSellColor
                            shapeRenderer = InvertedTriangleShapeRenderer()
                            scatterShapeSize = 18f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(auxSellDataSet)
                    }

                    // CombinedData 조립
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                        if (scatterDataSets.isNotEmpty()) {
                            setData(ScatterData(scatterDataSets.toList()))
                        }
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating TrendSignalChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        )
    }
}

/**
 * 시가총액 + Elder Impulse System 복합 차트 (주봉)
 */
@Composable
fun ElderImpulseChart(
    data: ElderImpulseData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dates.isEmpty()) {
        logger.w("Empty data for ElderImpulseChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val marketCapColor = colorSettings.lineColor1  // 시가총액 (기본값: Black)
    val emaColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor        // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor    // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()
    val bullColor = ChartGreen.toArgb()
    val bearColor = ChartRed.toArgb()
    val neutralColor = ChartTextLight.toArgb()

    // 현재 Impulse 상태
    val currentImpulse = data.impulse.lastOrNull()?.let { ImpulseState.fromValue(it) } ?: ImpulseState.NEUTRAL

    ChartCard(
        title = "Elder Impulse System (주봉)",
        subtitle = "현재 상태: ${currentImpulse.displayName}",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(data.dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val idx = value.toInt()
                                return if (idx in data.dates.indices) {
                                    val date = data.dates[idx]
                                    if (date.length >= 7) date.substring(5) else date
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축: 시가총액
                    axisLeft.apply {
                        setTextColor(textColor)
                        setDrawGridLines(true)
                        gridLineWidth = 0.5f
                        setGridColor(gridColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value >= 1_000_000_000_000) {
                                    String.format("%.1f조", value / 1_000_000_000_000)
                                } else {
                                    String.format("%.0f억", value / 100_000_000)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축: 종가/EMA
                    axisRight.apply {
                        isEnabled = true
                        setTextColor(textColor)
                        setDrawGridLines(false)
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 시가총액 라인
                    val marketCapEntries = data.marketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = marketCapColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(marketCapDataSet)

                    // EMA13 라인 - Grey dashed line
                    val emaEntries = data.ema.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val emaDataSet = LineDataSet(emaEntries, "EMA13").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = Color.GRAY
                        lineWidth = 1.5f
                        enableDashedLine(10f, 5f, 0f)  // lineLength, spaceLength, phase
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(emaDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // Impulse 상태를 Circle Scatter로 표시 (Bullish, Bearish, Neutral 모두)
                    val bullEntries = mutableListOf<Entry>()
                    val bearEntries = mutableListOf<Entry>()
                    val neutralEntries = mutableListOf<Entry>()

                    data.impulse.forEachIndexed { index, value ->
                        when (value) {
                            1 -> bullEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                            -1 -> bearEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                            else -> neutralEntries.add(Entry(index.toFloat(), data.close[index].toFloat()))
                        }
                    }

                    val scatterDataSets = mutableListOf<ScatterDataSet>()

                    if (bullEntries.isNotEmpty()) {
                        val bullDataSet = ScatterDataSet(bullEntries, "Bullish").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = bullColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(bullDataSet)
                    }

                    if (bearEntries.isNotEmpty()) {
                        val bearDataSet = ScatterDataSet(bearEntries, "Bearish").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = bearColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(bearDataSet)
                    }

                    if (neutralEntries.isNotEmpty()) {
                        val neutralDataSet = ScatterDataSet(neutralEntries, "Neutral").apply {
                            axisDependency = YAxis.AxisDependency.RIGHT
                            color = neutralColor
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 12f
                            setDrawValues(false)
                        }
                        scatterDataSets.add(neutralDataSet)
                    }

                    val combinedData = CombinedData().apply {
                        setData(lineData)
                        if (scatterDataSets.isNotEmpty()) {
                            setData(ScatterData(scatterDataSets.toList()))
                        }
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating ElderImpulseChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

/**
 * 시가총액 + DeMark TD Setup 복합 차트
 */
@Composable
fun DemarkTDChart(
    data: DemarkTDData,
    modifier: Modifier = Modifier,
    chartColorViewModel: ChartColorViewModel = hiltViewModel()
) {
    if (data.dates.isEmpty()) {
        logger.w("Empty data for DemarkTDChart")
        return
    }

    val chartColors by chartColorViewModel.chartColorSettings.collectAsState()
    val colorSettings = chartColors.marketCapOscillator

    val isDark = isSystemInDarkTheme()
    val marketCapColor = colorSettings.lineColor1   // 시가총액 (기본값: Black)
    val closeColor = colorSettings.lineColor2
    val textColor = colorSettings.textColor         // 축 라벨/틱 색상 (기본값: Black)
    val legendColor = colorSettings.legendColor     // 범례 색상 (기본값: Black)
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()
    val sellFatigueColor = ChartRed.toArgb()
    val buyFatigueColor = ChartGreen.toArgb()

    // 현재 TD 상태
    val currentTdSell = data.tdSell.lastOrNull() ?: 0
    val currentTdBuy = data.tdBuy.lastOrNull() ?: 0
    val statusText = when {
        currentTdSell >= 9 -> "매도 피로 ($currentTdSell) - 하락 전환 가능"
        currentTdBuy >= 9 -> "매수 피로 ($currentTdBuy) - 상승 전환 가능"
        currentTdSell > 0 -> "상승 지속 ($currentTdSell)"
        currentTdBuy > 0 -> "하락 지속 ($currentTdBuy)"
        else -> "중립"
    }

    ChartCard(
        title = "DeMark TD Setup (${data.intervalName})",
        subtitle = "현재 상태: $statusText",
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                CombinedChart(context).apply {
                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)
                    setDrawGridBackground(false)

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        gridLineWidth = 1f
                        setGridColor(gridColor)
                        setTextColor(textColor)
                        granularity = 1f
                        labelRotationAngle = -45f
                        setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(data.dates.size), false)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val idx = value.toInt()
                                return if (idx in data.dates.indices) {
                                    val date = data.dates[idx]
                                    if (date.length >= 7) date.substring(5) else date
                                } else ""
                            }
                        }
                    }

                    // 왼쪽 Y축: 시가총액
                    axisLeft.apply {
                        setTextColor(textColor)
                        setDrawGridLines(true)
                        gridLineWidth = 0.5f
                        setGridColor(gridColor)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (value >= 1_000_000_000_000) {
                                    String.format("%.1f조", value / 1_000_000_000_000)
                                } else {
                                    String.format("%.0f억", value / 100_000_000)
                                }
                            }
                        }
                    }

                    // 오른쪽 Y축: TD 카운트
                    axisRight.apply {
                        isEnabled = true
                        setTextColor(textColor)
                        setDrawGridLines(false)
                        axisMinimum = -15f
                        axisMaximum = 15f
                    }

                    legend.apply {
                        isEnabled = true
                        textSize = 10f
                        setTextColor(legendColor)
                    }
                }
            },
            update = { chart ->
                try {
                    val lineDataSets = mutableListOf<LineDataSet>()

                    // 시가총액 라인
                    val marketCapEntries = data.marketCap.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val marketCapDataSet = LineDataSet(marketCapEntries, "시가총액").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = marketCapColor
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    lineDataSets.add(marketCapDataSet)

                    // TD Sell 라인 (양수: 상승 피로)
                    val tdSellEntries = data.tdSell.mapIndexed { index, value ->
                        Entry(index.toFloat(), value.toFloat())
                    }
                    val tdSellDataSet = LineDataSet(tdSellEntries, "매도피로").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = sellFatigueColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = sellFatigueColor
                        fillAlpha = 50
                    }
                    lineDataSets.add(tdSellDataSet)

                    // TD Buy 라인 (음수로 표시: 하락 피로)
                    val tdBuyEntries = data.tdBuy.mapIndexed { index, value ->
                        Entry(index.toFloat(), -value.toFloat())
                    }
                    val tdBuyDataSet = LineDataSet(tdBuyEntries, "매수피로").apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                        color = buyFatigueColor
                        lineWidth = 1.5f
                        setDrawCircles(false)
                        setDrawValues(false)
                        setDrawFilled(true)
                        fillColor = buyFatigueColor
                        fillAlpha = 50
                    }
                    lineDataSets.add(tdBuyDataSet)

                    val lineData = LineData(lineDataSets.toList())

                    // 마커 제거 - 라인 차트만 표시
                    val combinedData = CombinedData().apply {
                        setData(lineData)
                    }

                    chart.data = combinedData
                    chart.invalidate()
                } catch (e: Exception) {
                    logger.e("Error updating DemarkTDChart", e)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/UnifiedStockSearchField.kt
package com.etfmonitor.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.ui.theme.*

/**
 * 종목 검색 결과 데이터
 */
data class StockSearchItem(
    val ticker: String,
    val name: String,
    val market: String = ""
)

/**
 * 통합 종목 검색 텍스트 필드
 *
 * ETF 통계탭 분석, 종목 메뉴, AI 분석 종목-지표 탭에서 사용하는
 * 통일된 디자인의 종목 검색 필드입니다.
 *
 * @param searchQuery 현재 검색어
 * @param onSearchQueryChange 검색어 변경 콜백
 * @param searchResults 검색 결과 목록
 * @param searchHistory 검색 히스토리 목록
 * @param isSearching 검색 중 여부 (로딩 표시)
 * @param placeholder 플레이스홀더 텍스트
 * @param onSelectStock 종목 선택 콜백 (ticker, name)
 * @param onSelectFromHistory 히스토리에서 선택 콜백 (기본: onSelectStock과 동일)
 * @param modifier Modifier
 */
@Composable
fun UnifiedStockSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<StockSearchItem>,
    searchHistory: List<SearchHistory>,
    isSearching: Boolean = false,
    placeholder: String = "종목명 또는 티커 검색...",
    onSelectStock: (ticker: String, name: String) -> Unit,
    onSelectFromHistory: ((ticker: String, name: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 외부 searchQuery가 변경되면 내부 상태도 업데이트
    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty() && textFieldValue.isNotEmpty()) {
            // 외부에서 클리어된 경우
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 검색 필드
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onSearchQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search_button),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History 버튼
                    if (searchHistory.isNotEmpty() && textFieldValue.isEmpty()) {
                        IconButton(onClick = { showHistoryDialog = true }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "검색 히스토리",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Clear 버튼
                    if (textFieldValue.isNotEmpty()) {
                        IconButton(onClick = {
                            textFieldValue = ""
                            onSearchQueryChange("")
                        }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "지우기",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.extendedShapes.searchBar,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        // 자동완성 드롭다운 - 오버레이
        if (searchResults.isNotEmpty() && textFieldValue.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .heightIn(max = 300.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.extendedShapes.cardLarge
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(searchResults, key = { it.ticker }) { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            supportingContent = {
                                Text(
                                    if (result.market.isNotEmpty()) {
                                        "${result.ticker} • ${result.market}"
                                    } else {
                                        result.ticker
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.clickable {
                                textFieldValue = result.name
                                onSearchQueryChange("")
                                onSelectStock(result.ticker, result.name)
                            }
                        )
                        if (result != searchResults.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // 검색 히스토리 다이얼로그
    if (showHistoryDialog && searchHistory.isNotEmpty()) {
        StockSearchHistoryDialog(
            searchHistory = searchHistory,
            onDismiss = { showHistoryDialog = false },
            onSelectStock = { ticker, name ->
                textFieldValue = name
                val selectCallback = onSelectFromHistory ?: onSelectStock
                selectCallback(ticker, name)
                showHistoryDialog = false
            }
        )
    }
}

/**
 * 검색 히스토리 다이얼로그
 */
@Composable
fun StockSearchHistoryDialog(
    searchHistory: List<SearchHistory>,
    onDismiss: () -> Unit,
    onSelectStock: (ticker: String, name: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("최근 검색")
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (searchHistory.isEmpty()) {
                    Text(
                        "검색 기록이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory, key = { it.id }) { history ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(history.name) },
                                    supportingContent = {
                                        Text(
                                            "${history.ticker} • ${history.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onSelectStock(history.ticker, history.name)
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/component/statistics/SortController.kt
package com.etfmonitor.core.ui.component.statistics

import kotlinx.coroutines.flow.StateFlow

/**
 * Sort order enumeration for table column sorting.
 * Supports three-state cycling: NONE -> DESCENDING -> ASCENDING -> NONE
 */
enum class SortOrder {
    NONE,       // Default (no sorting)
    ASCENDING,  // Ascending order
    DESCENDING; // Descending order

    /**
     * Cycles to the next sort order.
     * NONE -> DESCENDING -> ASCENDING -> NONE
     */
    fun next(): SortOrder = when (this) {
        NONE -> DESCENDING
        DESCENDING -> ASCENDING
        ASCENDING -> NONE
    }
}

/**
 * Columns available for sorting in amount ranking table.
 */
enum class SortColumn {
    STOCK_NAME,           // Stock name
    TOTAL_AMOUNT,         // Total amount
    ETF_COUNT,            // ETF count
    NEW_ETF_COUNT,        // New ETF count
    INCREASED_ETF_COUNT,  // Increased ETF count
    DECREASED_ETF_COUNT,  // Decreased ETF count
    REMOVED_ETF_COUNT     // Removed ETF count
}

/**
 * Represents a single sort criterion with column and order.
 */
data class SortCriterion(
    val column: SortColumn,
    val order: SortOrder
)

/**
 * Interface for controlling table sorting functionality.
 * Decouples UI components from specific ViewModel implementations,
 * enabling shared components to be used across different feature modules.
 */
interface SortController {
    /**
     * Current list of active sort criteria.
     * Multiple criteria can be active for multi-column sorting.
     */
    val sortCriteria: StateFlow<List<SortCriterion>>

    /**
     * Gets the current sort order for a specific column.
     * @param column The column to check
     * @return The current sort order (NONE if not sorted)
     */
    fun getSortOrder(column: SortColumn): SortOrder

    /**
     * Gets the sort priority (1-based) for a specific column in multi-column sorting.
     * @param column The column to check
     * @return Priority number (1 = first, 2 = second, etc.) or 0 if not in sort criteria
     */
    fun getSortPriority(column: SortColumn): Int

    /**
     * Toggles or applies sorting for the specified column.
     * Cycles through: NONE -> DESCENDING -> ASCENDING -> NONE
     * @param column The column to sort by
     */
    fun sortAmountRankingBy(column: SortColumn)

    /**
     * Clears all active sort criteria, resetting to default order.
     */
    fun clearAllSorting()
}

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Color.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Moss Green Nature Design Theme - Material Design 3
 * Clean, professional color palette inspired by natural moss green tones
 * Theme Name: Moss Green Nature
 * Base Source Color: Moss Green (#4C6C43)
 * Version: 3.0
 */

// ============================================
// Light Theme Colors - Moss Green Nature
// ============================================

// Primary - Moss Green
val primaryLight = Color(0xFF4C6C43)  // Moss green
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFCDEDA3)  // Light moss green
val onPrimaryContainerLight = Color(0xFF102000)

// Secondary - Olive Green
val secondaryLight = Color(0xFF586249)  // Olive green
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDCE7C8)  // Light olive
val onSecondaryContainerLight = Color(0xFF161E0B)

// Tertiary - Teal Green (for accents)
val tertiaryLight = Color(0xFF396663)  // Teal green
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBBEBEB)  // Light teal
val onTertiaryContainerLight = Color(0xFF002020)

// Error - Coral Red
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)

// Surface Colors - Warm Off-White
val backgroundLight = Color(0xFFFEFCF4)  // Warm off-white
val onBackgroundLight = Color(0xFF1B1C18)
val surfaceLight = Color(0xFFFEFCF4)
val onSurfaceLight = Color(0xFF1B1C18)
val surfaceVariantLight = Color(0xFFE1E4D5)  // Light gray-green
val onSurfaceVariantLight = Color(0xFF44483D)
val outlineLight = Color(0xFF75796C)
val outlineVariantLight = Color(0xFFC5C8BA)

// Surface Container Colors (for cards and elevated surfaces)
val surfaceDimLight = Color(0xFFDFDCD4)
val surfaceBrightLight = Color(0xFFFEFCF4)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF8F6EE)
val surfaceContainerLight = Color(0xFFF2F0E8)
val surfaceContainerHighLight = Color(0xFFECEAE2)
val surfaceContainerHighestLight = Color(0xFFE6E4DC)

// Inverse Colors
val inverseSurfaceLight = Color(0xFF30312C)
val inverseOnSurfaceLight = Color(0xFFF1EFEA)
val inversePrimaryLight = Color(0xFFB1D18A)

// Scrim and Shadow
val scrimLight = Color(0xFF000000)

// ============================================
// Dark Theme Colors - Moss Green Night
// ============================================

// Primary - Light Moss Green
val primaryDark = Color(0xFFB1D18A)  // Light moss for dark mode
val onPrimaryDark = Color(0xFF1F3701)
val primaryContainerDark = Color(0xFF354E16)  // Dark moss container
val onPrimaryContainerDark = Color(0xFFCDEDA3)

// Secondary - Light Olive
val secondaryDark = Color(0xFFBFCBAD)  // Light olive
val onSecondaryDark = Color(0xFF2A331E)
val secondaryContainerDark = Color(0xFF404A33)  // Dark olive
val onSecondaryContainerDark = Color(0xFFDCE7C8)

// Tertiary - Light Teal
val tertiaryDark = Color(0xFFA0CFCF)  // Light teal
val onTertiaryDark = Color(0xFF003738)
val tertiaryContainerDark = Color(0xFF1F4E4D)  // Dark teal
val onTertiaryContainerDark = Color(0xFFBBEBEB)

// Error
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)

// Surface Colors - Dark Mode
val backgroundDark = Color(0xFF1A1C18)  // Dark greenish gray
val onBackgroundDark = Color(0xFFE3E3DC)
val surfaceDark = Color(0xFF1A1C18)
val onSurfaceDark = Color(0xFFE3E3DC)
val surfaceVariantDark = Color(0xFF44483D)  // Dark gray-green
val onSurfaceVariantDark = Color(0xFFC5C8BA)
val outlineDark = Color(0xFF8F9285)
val outlineVariantDark = Color(0xFF44483D)

// Surface Container Colors - Dark Mode
val surfaceDimDark = Color(0xFF1A1C18)
val surfaceBrightDark = Color(0xFF40423D)
val surfaceContainerLowestDark = Color(0xFF151713)
val surfaceContainerLowDark = Color(0xFF1B1C18)
val surfaceContainerDark = Color(0xFF202120)
val surfaceContainerHighDark = Color(0xFF2A2C28)
val surfaceContainerHighestDark = Color(0xFF353733)

// Inverse Colors - Dark Mode
val inverseSurfaceDark = Color(0xFFE3E3DC)
val inverseOnSurfaceDark = Color(0xFF30312C)
val inversePrimaryDark = Color(0xFF4C6C43)

// Scrim
val scrimDark = Color(0xFF000000)

// ============================================
// Status Colors for Financial Data
// ============================================
val StatusNew = Color(0xFF4C6C43)      // Moss green - new holdings
val StatusIncrease = Color(0xFF2E7D5A)  // Teal green - increased weight
val StatusDecrease = Color(0xFFBA1A1A)  // Error red - decreased weight
val StatusRemoved = Color(0xFF8F9285)   // Gray - removed
val StatusMaintain = Color(0xFF586249)  // Olive - maintained

// ============================================
// Chart Colors - Professional Palette
// ============================================
val ChartPrimary = Color(0xFF4C6C43)    // Main chart line - moss green
val ChartSecondary = Color(0xFF396663)  // Secondary line - teal
val ChartTertiary = Color(0xFF586249)   // Tertiary line - olive
val ChartGreen = Color(0xFF2E7D5A)      // Bullish/positive - teal green
val ChartRed = Color(0xFFBA1A1A)        // Bearish/negative - error red
val ChartBlue = Color(0xFF396663)       // Neutral/info - teal
val ChartPurple = Color(0xFF8E7CC3)     // Accent - muted purple
val ChartOrange = Color(0xFFE0A050)     // Warning - warm orange
val ChartCyan = Color(0xFFA0CFCF)       // Highlight - light teal
val ChartPink = Color(0xFFD4A5A5)       // Special - dusty pink

// ============================================
// Gradient Colors for Modern UI Effects
// ============================================
val GradientStart = Color(0xFF4C6C43)   // Moss green
val GradientMiddle = Color(0xFF5A8A6A)  // Mid green
val GradientEnd = Color(0xFFB1D18A)     // Light moss

// ============================================
// Surface Elevation Colors for Layered UI
// ============================================
val SurfaceElevation1Light = Color(0xFFF8F6EE)  // Very light
val SurfaceElevation2Light = Color(0xFFF2F0E8)  // Light
val SurfaceElevation3Light = Color(0xFFECEAE2)  // Medium light
val SurfaceElevation1Dark = Color(0xFF202120)   // Dark elevation 1
val SurfaceElevation2Dark = Color(0xFF2A2C28)   // Dark elevation 2
val SurfaceElevation3Dark = Color(0xFF353733)   // Dark elevation 3

// ============================================
// Chart Grid and Text Colors
// ============================================
val ChartGridLight = Color(0xFFE1E4D5)  // Light grid
val ChartGridDark = Color(0xFF353733)   // Dark grid
val ChartTextLight = Color(0xFF1B1C18)  // Dark text on light
val ChartTextDark = Color(0xFFE3E3DC)   // Light text on dark

// ============================================
// Shimmer Effect Colors for Loading States
// ============================================
val ShimmerColorLight = Color(0xFFE1E4D5)      // Light shimmer base
val ShimmerHighlightLight = Color(0xFFFEFCF4)  // Light shimmer highlight
val ShimmerColorDark = Color(0xFF353733)       // Dark shimmer base
val ShimmerHighlightDark = Color(0xFF44483D)   // Dark shimmer highlight

// ============================================
// Chart Card Background Colors
// ============================================
val ChartCardBackgroundLight = Color(0xFFFFFFFF)  // Pure white for charts
val ChartCardBackgroundDark = Color(0xFFF5F7F5)   // Light for readability in dark mode

// ============================================
// Semantic Colors for Production Apps
// ============================================
val SuccessLight = Color(0xFF2E7D5A)    // Success - teal green
val SuccessDark = Color(0xFF81C995)     // Success - light green
val SuccessContainerLight = Color(0xFFC4EED0)  // Success container
val SuccessContainerDark = Color(0xFF0F5223)   // Success container dark
val OnSuccessContainerLight = Color(0xFF073315)
val OnSuccessContainerDark = Color(0xFFC4EED0)
val WarningLight = Color(0xFFE0A050)    // Warning - warm orange
val WarningDark = Color(0xFFFFCC80)     // Warning - light orange
val InfoLight = Color(0xFF396663)       // Info - teal
val InfoDark = Color(0xFFA0CFCF)        // Info - light teal

// ============================================
// Interactive State Colors
// ============================================
val RippleLight = Color(0xFF4C6C43).copy(alpha = 0.12f)
val RippleDark = Color(0xFFB1D18A).copy(alpha = 0.16f)
val HoverLight = Color(0xFF4C6C43).copy(alpha = 0.06f)
val HoverDark = Color(0xFFB1D18A).copy(alpha = 0.08f)

// ============================================
// Special Accent Colors (for stars, badges, etc.)
// ============================================
val AccentStar = Color(0xFF4C6C43)      // Star/favorite icon color
val AccentBadge = Color(0xFF4C6C43)     // Badge background
val AccentHighlight = Color(0xFFCDEDA3) // Highlight background

// ============================================
// AI Insights Feature Colors
// ============================================
val AIInsightsBackground = Color(0xFF2D4438)  // Dark green for AI card background
val AIInsightsAccent = Color(0xFFCDEDA3)      // Light moss accent for AI elements
val AIInsightsText = Color(0xFFFFFFFF)         // White text on AI card
val AIInsightsSubtext = Color(0xFFFFFFFF).copy(alpha = 0.8f)  // Semi-transparent white

// ============================================
// Featured Card Colors
// ============================================
val FeaturedCardOverlay = Color(0xFFFFFFFF).copy(alpha = 0.1f)  // White overlay for effects
val FeaturedCardBlur = Color(0xFFFFFFFF).copy(alpha = 0.2f)     // Blur background effect

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Elevation.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Elevation System
 * Professional elevation levels for consistent depth hierarchy
 *
 * Usage:
 * - Level 0: No elevation (flat surfaces)
 * - Level 1: Cards, chips at rest
 * - Level 2: Floating action buttons, cards on hover
 * - Level 3: Dialogs, pickers
 * - Level 4: Navigation drawers, modal bottom sheets
 * - Level 5: App bars, top app bars
 */
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)

/**
 * Local composition for elevation values
 * Access via MaterialTheme.elevation
 */
val LocalElevation = staticCompositionLocalOf { Elevation() }

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Motion.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material Design 3 Motion System
 * Professional animation specifications for smooth, natural interactions
 *
 * Easing curves based on Material Design 3 guidelines:
 * - Emphasized: Dynamic content changes (entering/exiting screens)
 * - Standard: Moderate emphasis (cards, buttons)
 * - Decelerated: Elements entering the screen
 * - Accelerated: Elements leaving the screen
 */

/**
 * Material Design 3 Easing Functions
 */
object MaterialEasing {
    // Emphasized easing for dynamic, expressive motion
    val emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Standard easing for most UI transitions
    val standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val standardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val standardAccelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

/**
 * Motion duration tokens (in milliseconds)
 */
object MotionDuration {
    const val short1 = 50
    const val short2 = 100
    const val short3 = 150
    const val short4 = 200
    const val medium1 = 250
    const val medium2 = 300
    const val medium3 = 350
    const val medium4 = 400
    const val long1 = 450
    const val long2 = 500
    const val long3 = 550
    const val long4 = 600
    const val extraLong1 = 700
    const val extraLong2 = 800
    const val extraLong3 = 900
    const val extraLong4 = 1000
}

/**
 * Pre-configured animation specs for common use cases
 */
data class MotionScheme(
    // Quick interactions (ripples, state changes)
    val quick: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.short4,
        easing = MaterialEasing.standard
    ),

    // Standard UI transitions (most common)
    val default: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.medium2,
        easing = MaterialEasing.standard
    ),

    // Emphasized transitions (screen changes, important actions)
    val emphasized: AnimationSpec<Float> = tween(
        durationMillis = MotionDuration.medium4,
        easing = MaterialEasing.emphasized
    ),

    // Smooth spring animations (for natural, bouncy motion)
    val spring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),

    // Expressive spring (more bouncy, for playful interactions)
    val expressiveSpring: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

/**
 * Local composition for motion values
 * Access via MaterialTheme.motion
 */
val LocalMotion = staticCompositionLocalOf { MotionScheme() }

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Shape.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Shape System - Moss Green Nature Theme
 * Professional, modern rounded corners for production-level apps
 *
 * Following Material Design 3 guidelines with enhanced rounding:
 * - Extra Small: 4dp - Chips, small buttons
 * - Small: 8dp - Cards, text fields
 * - Medium: 16dp - Dialogs, bottom sheets
 * - Large: 24dp - FABs, large cards
 * - Extra Large: 32dp - Hero sections, special components (2rem in design guide)
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Extended shape tokens for specialized use cases
 * Updated for Moss Green Nature theme - Enhanced rounded corners (32dp for cards)
 */
data class ExtendedShapes(
    val card: Shape = RoundedCornerShape(32.dp),  // Standard card corners (2rem from design guide)
    val cardLarge: Shape = RoundedCornerShape(32.dp),  // Large cards
    val cardMedium: Shape = RoundedCornerShape(24.dp),  // Medium cards
    val cardSmall: Shape = RoundedCornerShape(16.dp),  // Small cards
    val button: Shape = RoundedCornerShape(100.dp),  // Fully rounded buttons
    val buttonOutlined: Shape = RoundedCornerShape(100.dp),  // Fully rounded for outlined buttons
    val buttonLarge: Shape = RoundedCornerShape(100.dp),  // Fully rounded for prominence
    val dialog: Shape = RoundedCornerShape(28.dp),  // Dialogs
    val bottomSheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),  // Enhanced bottom sheet
    val chip: Shape = RoundedCornerShape(100.dp),  // Fully rounded chips
    val fab: Shape = RoundedCornerShape(16.dp),  // FAB corners
    val fabExtended: Shape = RoundedCornerShape(100.dp),  // Extended FAB (pill shape)
    val searchBar: Shape = RoundedCornerShape(100.dp),  // Fully rounded search bars
    val badge: Shape = RoundedCornerShape(8.dp),  // Status badges with gentle rounding
    val statusChip: Shape = RoundedCornerShape(8.dp),  // Status chips (from design guide)
    val filterChip: Shape = RoundedCornerShape(100.dp),  // Filter chips (pill shape)
    val listItem: Shape = RoundedCornerShape(16.dp),  // List item backgrounds
    val aiInsightsCard: Shape = RoundedCornerShape(32.dp),  // AI insights featured card
    val iconContainer: Shape = RoundedCornerShape(16.dp),  // Icon containers in cards
    val circle: Shape = CircleShape
)

val LocalExtendedShapes = staticCompositionLocalOf { ExtendedShapes() }

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Spacing.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Spacing System
 * Consistent spacing scale based on 4dp baseline grid
 *
 * Professional spacing for production-level apps:
 * - extraSmall: 4dp - Compact spacing, icons, badges
 * - small: 8dp - Text line spacing, small gaps
 * - medium: 16dp - Standard padding, card content
 * - large: 24dp - Section spacing, large gaps
 * - extraLarge: 32dp - Screen margins, major sections
 * - extraExtraLarge: 48dp - Hero sections, major separators
 */
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val extraExtraLarge: Dp = 48.dp
)

/**
 * Local composition for spacing values
 * Access via MaterialTheme.spacing
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Theme.kt
package com.etfmonitor.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Moss Green Nature Theme - Material Design 3
 * Clean, professional design with moss green nature-inspired accents
 * Features:
 * - Material You dynamic color support (Android 12+)
 * - Custom professional color palette fallback
 * - Enhanced surface elevation system
 * - Full surface container colors support
 * - AI Insights accent colors
 */

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    // Surface container colors
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    // Inverse colors
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    scrim = scrimLight
)

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    // Surface container colors
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    // Inverse colors
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    scrim = scrimDark
)

/**
 * Extended theme colors for financial data visualization
 */
data class ExtendedColors(
    val statusNew: androidx.compose.ui.graphics.Color,
    val statusIncrease: androidx.compose.ui.graphics.Color,
    val statusDecrease: androidx.compose.ui.graphics.Color,
    val statusRemoved: androidx.compose.ui.graphics.Color,
    val statusMaintain: androidx.compose.ui.graphics.Color,
    val chartPrimary: androidx.compose.ui.graphics.Color,
    val chartSecondary: androidx.compose.ui.graphics.Color,
    val chartTertiary: androidx.compose.ui.graphics.Color,
    val chartGreen: androidx.compose.ui.graphics.Color,
    val chartRed: androidx.compose.ui.graphics.Color,
    val chartBlue: androidx.compose.ui.graphics.Color,
    val surfaceElevation1: androidx.compose.ui.graphics.Color,
    val surfaceElevation2: androidx.compose.ui.graphics.Color,
    val surfaceElevation3: androidx.compose.ui.graphics.Color,
    // Semantic colors
    val success: androidx.compose.ui.graphics.Color,
    val successContainer: androidx.compose.ui.graphics.Color,
    val onSuccessContainer: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val accentStar: androidx.compose.ui.graphics.Color,
    val accentBadge: androidx.compose.ui.graphics.Color,
    val accentHighlight: androidx.compose.ui.graphics.Color,
    // AI Insights colors
    val aiInsightsBackground: androidx.compose.ui.graphics.Color,
    val aiInsightsAccent: androidx.compose.ui.graphics.Color,
    val aiInsightsText: androidx.compose.ui.graphics.Color,
    val aiInsightsSubtext: androidx.compose.ui.graphics.Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        statusNew = StatusNew,
        statusIncrease = StatusIncrease,
        statusDecrease = StatusDecrease,
        statusRemoved = StatusRemoved,
        statusMaintain = StatusMaintain,
        chartPrimary = ChartPrimary,
        chartSecondary = ChartSecondary,
        chartTertiary = ChartTertiary,
        chartGreen = ChartGreen,
        chartRed = ChartRed,
        chartBlue = ChartBlue,
        surfaceElevation1 = SurfaceElevation1Light,
        surfaceElevation2 = SurfaceElevation2Light,
        surfaceElevation3 = SurfaceElevation3Light,
        success = SuccessLight,
        successContainer = SuccessContainerLight,
        onSuccessContainer = OnSuccessContainerLight,
        warning = WarningLight,
        info = InfoLight,
        accentStar = AccentStar,
        accentBadge = AccentBadge,
        accentHighlight = AccentHighlight,
        aiInsightsBackground = AIInsightsBackground,
        aiInsightsAccent = AIInsightsAccent,
        aiInsightsText = AIInsightsText,
        aiInsightsSubtext = AIInsightsSubtext
    )
}

@Composable
fun EtfMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color by default to use custom Moss Green Nature theme
    dynamicColor: Boolean = false,
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Extended colors for financial data (not affected by dynamic color)
    val extendedColors = ExtendedColors(
        statusNew = StatusNew,
        statusIncrease = StatusIncrease,
        statusDecrease = StatusDecrease,
        statusRemoved = StatusRemoved,
        statusMaintain = StatusMaintain,
        chartPrimary = ChartPrimary,
        chartSecondary = ChartSecondary,
        chartTertiary = ChartTertiary,
        chartGreen = ChartGreen,
        chartRed = ChartRed,
        chartBlue = ChartBlue,
        surfaceElevation1 = if (darkTheme) SurfaceElevation1Dark else SurfaceElevation1Light,
        surfaceElevation2 = if (darkTheme) SurfaceElevation2Dark else SurfaceElevation2Light,
        surfaceElevation3 = if (darkTheme) SurfaceElevation3Dark else SurfaceElevation3Light,
        success = if (darkTheme) SuccessDark else SuccessLight,
        successContainer = if (darkTheme) SuccessContainerDark else SuccessContainerLight,
        onSuccessContainer = if (darkTheme) OnSuccessContainerDark else OnSuccessContainerLight,
        warning = if (darkTheme) WarningDark else WarningLight,
        info = if (darkTheme) InfoDark else InfoLight,
        accentStar = AccentStar,
        accentBadge = AccentBadge,
        accentHighlight = AccentHighlight,
        aiInsightsBackground = AIInsightsBackground,
        aiInsightsAccent = AIInsightsAccent,
        aiInsightsText = AIInsightsText,
        aiInsightsSubtext = AIInsightsSubtext
    )

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalElevation provides Elevation(),
        LocalSpacing provides Spacing(),
        LocalMotion provides MotionScheme(),
        LocalExtendedShapes provides ExtendedShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}

/**
 * Access extended theme colors
 * Usage: MaterialTheme.extendedColors.statusNew
 */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

/**
 * Access elevation system
 * Usage: MaterialTheme.elevation.level2
 */
val MaterialTheme.elevation: Elevation
    @Composable
    get() = LocalElevation.current

/**
 * Access spacing system
 * Usage: MaterialTheme.spacing.medium
 */
val MaterialTheme.spacing: Spacing
    @Composable
    get() = LocalSpacing.current

/**
 * Access motion system
 * Usage: MaterialTheme.motion.emphasized
 */
val MaterialTheme.motion: MotionScheme
    @Composable
    get() = LocalMotion.current

/**
 * Access extended shapes
 * Usage: MaterialTheme.extendedShapes.card
 */
val MaterialTheme.extendedShapes: ExtendedShapes
    @Composable
    get() = LocalExtendedShapes.current

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/ThemeManager.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// 차트 기본 색상 상수 - Black을 기본값으로 사용
val ChartDefaultBlack = Color(0xFF000000)

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
 * 기본값: 시가총액 라인, 축 라벨/틱, 범례 색상 = Black
 */
data class SingleChartColorSettings(
    val lineColor1: Int = ChartDefaultBlack.toArgb(), // 첫 번째 라인 색상 (시가총액) = Black
    val lineColor2: Int = ChartSecondary.toArgb(),    // 두 번째 라인 색상
    val lineColor3: Int = ChartTertiary.toArgb(),     // 세 번째 라인 색상 (MACD Signal 등)
    val positiveColor: Int = ChartGreen.toArgb(),     // 양수/상승 색상
    val negativeColor: Int = ChartRed.toArgb(),       // 음수/하락 색상
    val textColor: Int = ChartDefaultBlack.toArgb(),  // 축 라벨/틱 색상 = Black
    val legendColor: Int = ChartDefaultBlack.toArgb() // 범례 색상 = Black
)

/**
 * 전체 차트 색상 설정
 * 기본값: 시가총액 라인, 축 라벨/틱, 범례 색상 = Black
 */
data class ChartColorSettings(
    val marketCapOscillator: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartDefaultBlack.toArgb(),  // 시가총액 라인 = Black
        lineColor2 = ChartSecondary.toArgb(),     // Oscillator 라인
        textColor = ChartDefaultBlack.toArgb(),   // 축 라벨/틱 = Black
        legendColor = ChartDefaultBlack.toArgb()  // 범례 = Black
    ),
    val macd: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartDefaultBlack.toArgb(),  // MACD 라인 = Black
        lineColor2 = ChartOrange.toArgb(),        // Signal line
        textColor = ChartDefaultBlack.toArgb(),   // 축 라벨/틱 = Black
        legendColor = ChartDefaultBlack.toArgb()  // 범례 = Black
    ),
    val marketDeposit: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartDefaultBlack.toArgb(),  // 예탁금 라인 = Black
        lineColor2 = ChartTertiary.toArgb(),      // 신용잔고 라인
        textColor = ChartDefaultBlack.toArgb(),   // 축 라벨/틱 = Black
        legendColor = ChartDefaultBlack.toArgb()  // 범례 = Black
    ),
    val fearGreed: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartOrange.toArgb(),        // Fear & Greed Oscillator
        lineColor2 = ChartDefaultBlack.toArgb(),  // KOSPI/KOSDAQ Index = Black
        textColor = ChartDefaultBlack.toArgb(),   // 축 라벨/틱 = Black
        legendColor = ChartDefaultBlack.toArgb()  // 범례 = Black
    ),
    val bloodIndicator: SingleChartColorSettings = SingleChartColorSettings(
        lineColor1 = ChartRed.toArgb(),           // BLOOD 라인 (Red for blood theme)
        lineColor2 = ChartSecondary.toArgb(),     // S&P 500 Index
        textColor = ChartDefaultBlack.toArgb(),   // 축 라벨/틱 = Black
        legendColor = ChartDefaultBlack.toArgb()  // 범례 = Black
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

// FILE: app/src/main/java/com/etfmonitor/core/ui/theme/Type.kt
package com.etfmonitor.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.etfmonitor.R

/**
 * Modern ETF Monitor Typography
 * Using Montserrat font family for professional, financial app aesthetic
 * Montserrat: Clean, modern, highly readable - perfect for data-heavy interfaces
 */

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

val Typography = Typography(
    // Display styles - Bold, impactful headers
    displayLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    // Headline styles - Section headers
    headlineLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Title styles - Card/component titles
    titleLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Body styles - Main content text
    bodyLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // Label styles - Button text, tags, captions
    labelLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 동적 Typography 생성 함수
 * 각 스타일 그룹별로 폰트 스케일 적용
 */
fun createScaledTypography(
    displayScale: Float = 1.0f,
    headlineScale: Float = 1.0f,
    titleScale: Float = 1.0f,
    bodyScale: Float = 1.0f,
    labelScale: Float = 1.0f
): Typography = Typography(
    // Display styles
    displayLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (57 * displayScale).sp,
        lineHeight = (64 * displayScale).sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (45 * displayScale).sp,
        lineHeight = (52 * displayScale).sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (36 * displayScale).sp,
        lineHeight = (44 * displayScale).sp,
        letterSpacing = 0.sp
    ),
    // Headline styles
    headlineLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (32 * headlineScale).sp,
        lineHeight = (40 * headlineScale).sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (28 * headlineScale).sp,
        lineHeight = (36 * headlineScale).sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (24 * headlineScale).sp,
        lineHeight = (32 * headlineScale).sp,
        letterSpacing = 0.sp
    ),
    // Title styles
    titleLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (22 * titleScale).sp,
        lineHeight = (28 * titleScale).sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (16 * titleScale).sp,
        lineHeight = (24 * titleScale).sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * titleScale).sp,
        lineHeight = (20 * titleScale).sp,
        letterSpacing = 0.1.sp
    ),
    // Body styles
    bodyLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * bodyScale).sp,
        lineHeight = (24 * bodyScale).sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (14 * bodyScale).sp,
        lineHeight = (20 * bodyScale).sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (12 * bodyScale).sp,
        lineHeight = (16 * bodyScale).sp,
        letterSpacing = 0.4.sp
    ),
    // Label styles
    labelLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (14 * labelScale).sp,
        lineHeight = (20 * labelScale).sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (12 * labelScale).sp,
        lineHeight = (16 * labelScale).sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (11 * labelScale).sp,
        lineHeight = (16 * labelScale).sp,
        letterSpacing = 0.5.sp
    )
)

// ====== FEATURE SCREENS ======

// FILE: app/src/main/java/com/etfmonitor/feature/analysis/presentation/AnalysisScreens.kt
package com.etfmonitor.feature.analysis.presentation

/**
 * Analysis Feature Module - Presentation Layer
 *
 * 현재 단계에서는 기존 화면과 ViewModel을 ui/screens/ 위치에 유지하면서
 * feature 모듈의 Domain/Data Layer를 통해 Clean Architecture 패턴을 적용합니다.
 *
 * ## 마이그레이션 전략
 *
 * Phase 6에서는 다음 구조를 구축했습니다:
 * - feature/analysis/domain/model/ - 도메인 모델
 * - feature/analysis/domain/repository/ - Repository 인터페이스
 * - feature/analysis/domain/usecase/ - UseCase 클래스
 * - feature/analysis/data/mapper/ - Entity <-> Domain 변환
 * - feature/analysis/data/repository/ - Repository 구현체
 * - feature/analysis/di/ - DI 모듈
 *
 * ## 기존 화면 위치 (유지)
 *
 * - ui/screens/aianalysis/
 *   - NewAIAnalysisScreen.kt
 *   - NewAIAnalysisViewModel.kt
 *
 * - ui/screens/advanced/
 *   - AdvancedDashboardScreen.kt
 *   - AdvancedDashboardViewModel.kt
 *   - (Tab 컴포넌트들)
 *
 * - ui/screens/hub/
 *   - AnalysisHubScreen.kt
 *
 * ## 향후 마이그레이션 계획
 *
 * Phase 7 또는 별도의 리팩토링에서:
 * 1. ViewModel을 UseCase 의존성으로 전환
 * 2. Screen 파일들을 feature/analysis/presentation/으로 이동
 * 3. 기존 Repository 의존성을 완전히 제거
 *
 * @see com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisScreen
 * @see com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisViewModel
 * @see com.etfmonitor.ui.screens.advanced.AdvancedDashboardScreen
 * @see com.etfmonitor.ui.screens.advanced.AdvancedDashboardViewModel
 * @see com.etfmonitor.ui.screens.hub.AnalysisHubScreen
 */
object AnalysisScreens {
    // Re-exports for feature module access
    // 향후 UseCase 기반 ViewModel 마이그레이션 시 사용

    const val AI_ANALYSIS_ROUTE = "ai_analysis"
    const val ADVANCED_DASHBOARD_ROUTE = "advanced_dashboard"
    const val ANALYSIS_HUB_ROUTE = "analysis_hub"
}

// FILE: app/src/main/java/com/etfmonitor/feature/analysis/presentation/advanced/AdvancedDashboardScreen.kt
package com.etfmonitor.feature.analysis.presentation.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.R
import kotlinx.coroutines.launch

/**
 * Advanced Dashboard Screen - Main Entry Point
 * Provides comprehensive advanced market analysis across multiple tabs:
 * - 시총가중 (Market Cap Flow): Market cap weighted ETF flow analysis
 * - 유동성 (Liquidity): Market liquidity analysis
 * - 섹터심리 (Sector Fear & Greed): Sector-level sentiment analysis
 * - ETF상관 (ETF Correlation): ETF overlap and correlation analysis
 *
 * Tab components are split into separate files:
 * - CommonComponents.kt: Colors, utilities, shared composables
 * - MarketCapFlowTab.kt: Market cap flow analysis
 * - LiquidityTab.kt: Liquidity analysis
 * - SectorFearGreedTab.kt: Sector sentiment
 * - EtfCorrelationTab.kt: ETF correlation
 * - HistoryCharts.kt: History chart components
 * - PredictionAccuracyUI.kt: Prediction accuracy UI
 */

// 탭 정의
private enum class AdvancedTab(val titleResId: Int, val icon: ImageVector) {
    MARKET_CAP_FLOW(R.string.advanced_tab_market_cap, Icons.AutoMirrored.Filled.TrendingUp),
    LIQUIDITY(R.string.advanced_tab_liquidity, Icons.Default.AccountBalance),
    SECTOR_FG(R.string.advanced_tab_sector, Icons.Default.PieChart),
    ETF_CORRELATION(R.string.advanced_tab_etf_correlation, Icons.Default.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDashboardScreen(
    navController: NavHostController,
    viewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pagerState = rememberPagerState(pageCount = { AdvancedTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    // 일반 새로고침
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.nav_refresh))
                    }
                    // 강제 재계산 (캐시 무시)
                    IconButton(onClick = { viewModel.forceRefresh() }) {
                        Icon(Icons.Default.Sync, contentDescription = "재계산")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 바
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp,
                divider = {}
            ) {
                AdvancedTab.entries.forEachIndexed { index, tab ->
                    val tabTitle = stringResource(tab.titleResId)
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tabTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(tab.icon, contentDescription = tabTitle, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // 컨텐츠
            Box(modifier = Modifier.fillMaxSize()) {
                when (val currentState = state) {
                    is AdvancedDashboardState.Loading -> LoadingContent()
                    is AdvancedDashboardState.Error -> ErrorContent(currentState.message) { viewModel.loadDashboard() }
                    is AdvancedDashboardState.Success -> {
                        // 히스토리 데이터 수집
                        val marketCapFlowHistory by viewModel.marketCapFlowHistory.collectAsState()
                        val liquidityHistory by viewModel.liquidityHistory.collectAsState()
                        val sectorHistory by viewModel.sectorHistory.collectAsState()

                        // 예측 정확도 데이터 수집
                        val marketCapFlowAccuracy by viewModel.marketCapFlowAccuracy.collectAsState()
                        val liquidityAccuracy by viewModel.liquidityAccuracy.collectAsState()

                        // ETF 상관관계 계산 상태
                        val isCalculatingCorrelation by viewModel.isCalculatingCorrelation.collectAsState()

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (AdvancedTab.entries[page]) {
                                AdvancedTab.MARKET_CAP_FLOW -> MarketCapFlowTab(
                                    data = currentState.data,
                                    history = marketCapFlowHistory,
                                    accuracy = marketCapFlowAccuracy
                                )
                                AdvancedTab.LIQUIDITY -> LiquidityTab(
                                    data = currentState.data,
                                    history = liquidityHistory,
                                    accuracy = liquidityAccuracy
                                )
                                AdvancedTab.SECTOR_FG -> SectorFearGreedTab(currentState.data, sectorHistory)
                                AdvancedTab.ETF_CORRELATION -> EtfCorrelationTab(
                                    data = currentState.data,
                                    isCalculating = isCalculatingCorrelation,
                                    onCalculate = { viewModel.calculateEtfCorrelation() }
                                )
                            }
                        }
                    }
                }

                // 로딩 오버레이
                if (isRefreshing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/analysis/presentation/aianalysis/NewAIAnalysisScreen.kt
package com.etfmonitor.feature.analysis.presentation.aianalysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.etfmonitor.core.database.entities.SearchHistory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullStockIndicatorAnalysis

/**
 * 분석 탭 종류
 */
enum class AnalysisTab(val title: String) {
    CORRELATION("상관관계"),
    STOCK_INDICATOR("종목-지표")
}

/**
 * 새로운 AI 분석 화면
 * 상관관계 분석 + 종목-지표 상관관계 분석 + AI 해석 + 채팅 기능 통합
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAIAnalysisScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOscillator: ((String) -> Unit)? = null,
    viewModel: NewAIAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val stockIndicatorCorrelationResult by viewModel.stockIndicatorCorrelationResult.collectAsState()
    val analysisPeriod by viewModel.analysisPeriod.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val stockSearchResults by viewModel.stockSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState(initial = emptyList())
    val stockIndicatorAIHistory by viewModel.stockIndicatorAIHistory.collectAsState(initial = emptyList())
    val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

    var showProviderDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showStockIndicatorHistorySheet by remember { mutableStateOf(false) }
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val selectedTab = AnalysisTab.entries[selectedTabIndex]

    // 화면 진입 시 API 키 상태 새로고침 (설정에서 돌아왔을 때 반영)
    LaunchedEffect(Unit) {
        viewModel.refreshApiKeyState()
    }

    // FAB 표시 조건: 종목-지표 탭에서 종목이 선택되고 분석 결과가 있을 때
    val showFab by remember(
        quickChartAnalysisEnabled,
        onNavigateToOscillator,
        selectedTab,
        selectedStock,
        stockIndicatorCorrelationResult,
        currentSession
    ) {
        derivedStateOf {
            quickChartAnalysisEnabled &&
                    onNavigateToOscillator != null &&
                    selectedTab == AnalysisTab.STOCK_INDICATOR &&
                    selectedStock != null &&
                    stockIndicatorCorrelationResult?.correlationResult != null &&
                    currentSession == null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 분석") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSession != null) {
                            viewModel.closeChat()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    // 캐시 초기화 및 재분석 버튼
                    IconButton(onClick = { viewModel.clearCacheAndRefresh() }) {
                        Icon(Icons.Default.Refresh, "새로고침")
                    }

                    // AI 제공자 선택
                    TextButton(onClick = { showProviderDialog = true }) {
                        Text(
                            selectedProvider.name,
                            color = if (isApiKeyConfigured)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    // 대화 이력
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.History, "대화 이력")
                    }
                }
            )
        },
        floatingActionButton = {
            selectedStock?.let { stock ->
                if (showFab) {
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToOscillator?.invoke(stock.first) },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                        text = { Text("차트 분석") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                currentSession != null -> {
                    // 채팅 화면
                    ChatScreen(
                        messages = chatMessages,
                        isSending = isSendingMessage,
                        onSendMessage = { viewModel.sendMessage(it) },
                        state = state
                    )
                }
                else -> {
                    Column {
                        // 탭 선택
                        TabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnalysisTab.entries.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab.ordinal) },
                                    text = { Text(tab.title) }
                                )
                            }
                        }

                        // 탭 내용
                        when (selectedTab) {
                            AnalysisTab.CORRELATION -> {
                                CorrelationAnalysisContent(
                                    state = state,
                                    selectedMarket = selectedMarket,
                                    isApiKeyConfigured = isApiKeyConfigured,
                                    analysisResult = analysisResult,
                                    onMarketSelect = { viewModel.selectMarket(it) },
                                    onRunCorrelation = { viewModel.runCorrelationAnalysis() },
                                    onRunFullAnalysis = { viewModel.runFullAnalysis() },
                                    onInterpretWithAI = { viewModel.interpretWithAI(it) },
                                    onStartChat = { viewModel.startNewChat() },
                                    onClearError = { viewModel.clearError() }
                                )
                            }
                            AnalysisTab.STOCK_INDICATOR -> {
                                StockIndicatorCorrelationContent(
                                    state = state,
                                    analysisPeriod = analysisPeriod,
                                    isApiKeyConfigured = isApiKeyConfigured,
                                    selectedStock = selectedStock,
                                    stockIndicatorCorrelationResult = stockIndicatorCorrelationResult,
                                    stockSearchResults = stockSearchResults,
                                    isSearching = isSearching,
                                    historyCount = stockIndicatorAIHistory.size,
                                    searchHistory = searchHistory,
                                    onPeriodChange = { viewModel.setAnalysisPeriod(it) },
                                    onSearchStock = { viewModel.searchStock(it) },
                                    onSelectStock = { ticker, name -> viewModel.selectStock(ticker, name) },
                                    onClearStock = { viewModel.clearSelectedStock() },
                                    onRunAnalysis = { viewModel.analyzeStockIndicatorCorrelation() },
                                    onRunFullAnalysis = { viewModel.runFullStockIndicatorCorrelationAnalysis() },
                                    onInterpretWithAI = { viewModel.interpretStockIndicatorCorrelationWithAI() },
                                    onStartChat = { viewModel.startNewChat() },
                                    onClearError = { viewModel.clearError() },
                                    onShowHistory = { showStockIndicatorHistorySheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // AI 제공자 선택 다이얼로그
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("AI 제공자 선택") },
            text = {
                Column {
                    viewModel.getAvailableProviders().forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = provider == selectedProvider,
                                onClick = {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(provider.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 대화 이력 바텀 시트
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "대화 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (chatSessions.isEmpty()) {
                    Text(
                        "저장된 대화가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(chatSessions, key = { it.id }) { session ->
                            SessionItem(
                                session = session,
                                onClick = {
                                    viewModel.openSession(session.id)
                                    showHistorySheet = false
                                },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 종목-지표 분석 이력 바텀 시트
    if (showStockIndicatorHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showStockIndicatorHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI 분석 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (stockIndicatorAIHistory.isEmpty()) {
                    Text(
                        "저장된 분석 결과가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(stockIndicatorAIHistory, key = { it.id }) { historyItem ->
                            StockIndicatorAIHistoryItemCard(
                                item = historyItem,
                                onClick = {
                                    viewModel.loadFromHistory(historyItem)
                                    showStockIndicatorHistorySheet = false
                                },
                                onDelete = { viewModel.deleteHistoryItem(historyItem.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 상관관계 분석 화면 콘텐츠
 */
@Composable
private fun CorrelationAnalysisContent(
    state: NewAIAnalysisState,
    selectedMarket: String,
    isApiKeyConfigured: Boolean,
    analysisResult: FullAnalysis?,
    onMarketSelect: (String) -> Unit,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: (CorrelationAnalysis) -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시장 선택
        item {
            AnalysisMarketSelector(
                selectedMarket = selectedMarket,
                onMarketSelect = onMarketSelect
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            AnalysisButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasCorrelationResult = analysisResult?.correlationResult != null,
                onRunCorrelation = onRunCorrelation,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = {
                    analysisResult?.correlationResult?.let { onInterpretWithAI(it) }
                }
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    ErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 분석 결과
        analysisResult?.let { result ->
            item {
                CorrelationResultCard(
                    result = result.correlationResult,
                    aiResult = result.aiResult
                )
            }

            // AI 해석 결과
            result.aiResult?.let { aiResult ->
                item {
                    AIInterpretationCard(
                        signal = aiResult.signal,
                        confidence = aiResult.confidence,
                        upProbability = aiResult.upProbability,
                        downProbability = aiResult.downProbability,
                        reasoning = aiResult.reasoning,
                        recommendation = aiResult.recommendation,
                        riskLevel = aiResult.riskLevel
                    )
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is NewAIAnalysisState.AnalyzingCorrelation &&
                            state !is NewAIAnalysisState.AnalyzingFull &&
                            state !is NewAIAnalysisState.InterpretingWithAI
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("분석 결과로 대화하기")
                }
            }
        }
    }
}

/**
 * 종목-지표 상관관계 분석 화면 콘텐츠
 * 시장은 종목 티커에 따라 자동 감지됨 (KOSPI: 0,1,2,3으로 시작, 나머지: KOSDAQ)
 */
@Composable
private fun StockIndicatorCorrelationContent(
    state: NewAIAnalysisState,
    analysisPeriod: Int,
    isApiKeyConfigured: Boolean,
    selectedStock: Pair<String, String>?,
    stockIndicatorCorrelationResult: FullStockIndicatorAnalysis?,
    stockSearchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    historyCount: Int,
    searchHistory: List<SearchHistory>,
    onPeriodChange: (Int) -> Unit,
    onSearchStock: (String) -> Unit,
    onSelectStock: (String, String) -> Unit,
    onClearStock: () -> Unit,
    onRunAnalysis: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit,
    onShowHistory: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation ||
            state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull ||
            state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation

    var searchQuery by remember { mutableStateOf("") }

    // 선택된 종목의 시장 자동 감지
    val detectedMarket = selectedStock?.let { Stock.inferMarket(it.first) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 종목 검색
        item {
            StockSearchSection(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    onSearchStock(it)
                },
                searchResults = stockSearchResults,
                isSearching = isSearching,
                selectedStock = selectedStock,
                detectedMarket = detectedMarket,
                searchHistory = searchHistory,
                onSelectStock = { ticker, name ->
                    onSelectStock(ticker, name)
                    searchQuery = ""
                },
                onClearStock = onClearStock
            )
        }

        // 분석 이력 버튼
        item {
            OutlinedCard(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "AI 분석 이력",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (historyCount > 0) "저장된 분석 결과 ${historyCount}개" else "저장된 분석 결과 없음",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 분석 기간 선택
        item {
            TimeSeriesPeriodSelector(
                period = analysisPeriod,
                onPeriodChange = onPeriodChange
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            StockIndicatorCorrelationButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasSelectedStock = selectedStock != null,
                hasCorrelationResult = stockIndicatorCorrelationResult?.correlationResult != null,
                onRunAnalysis = onRunAnalysis,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = onInterpretWithAI
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    ErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 상관관계 분석 결과
        stockIndicatorCorrelationResult?.correlationResult?.let { result ->
            // 종목 정보 요약 카드
            item {
                StockIndicatorSummaryCard(result = result)
            }

            // Fear & Greed 상관관계 차트
            if (result.fearGreedCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "심리 지표 상관관계",
                        subtitle = "Fear & Greed, RSI, 모멘텀",
                        icon = Icons.Default.Psychology,
                        correlations = result.fearGreedCorrelations,
                        color = Color(0xFF6750A4)
                    )
                }
            }

            // Oscillator 상관관계 차트
            if (result.oscillatorCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "기술 지표 상관관계",
                        subtitle = "시장 과매수/과매도",
                        icon = Icons.Default.TrendingUp,
                        correlations = result.oscillatorCorrelations,
                        color = Color(0xFF1976D2)
                    )
                }
            }

            // 예탁금/신용 상관관계 차트
            if (result.depositCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "자금 동향 상관관계",
                        subtitle = "고객예탁금, 신용잔고",
                        icon = Icons.Default.AccountBalance,
                        correlations = result.depositCorrelations,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            // ETF 수급 상관관계 차트
            if (result.etfCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "ETF 수급 상관관계",
                        subtitle = "ETF 편입/편출, 비중 변화",
                        icon = Icons.Default.ShowChart,
                        correlations = result.etfCorrelations,
                        color = Color(0xFFE64A19)
                    )
                }
            }

            // Top 상관관계 요약
            if (result.topPositiveCorrelations.isNotEmpty() || result.topNegativeCorrelations.isNotEmpty()) {
                item {
                    TopCorrelationsCard(
                        topPositive = result.topPositiveCorrelations,
                        topNegative = result.topNegativeCorrelations
                    )
                }
            }

            // AI 해석 결과
            stockIndicatorCorrelationResult.aiInterpretation?.let { aiResult ->
                item {
                    StockIndicatorAIInterpretationCard(interpretation = aiResult)
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("상관관계 분석 결과로 대화하기")
                }
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/analysis/presentation/hub/AnalysisHubScreen.kt
package com.etfmonitor.feature.analysis.presentation.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.etfmonitor.R
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.database.entities.Stock
import com.etfmonitor.feature.analysis.domain.model.CorrelationAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullAnalysis
import com.etfmonitor.feature.analysis.domain.model.FullStockIndicatorAnalysis
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.core.ui.component.TabNavigationBar
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.feature.analysis.presentation.aianalysis.AnalysisTab
import com.etfmonitor.feature.analysis.presentation.aianalysis.NewAIAnalysisViewModel
import com.etfmonitor.feature.analysis.presentation.aianalysis.NewAIAnalysisState
import com.etfmonitor.feature.analysis.presentation.aianalysis.*
import com.etfmonitor.feature.analysis.presentation.advanced.AdvancedDashboardViewModel
import com.etfmonitor.feature.analysis.presentation.advanced.AdvancedDashboardState
import com.etfmonitor.feature.analysis.presentation.advanced.MarketCapFlowTab
import com.etfmonitor.feature.analysis.presentation.advanced.LiquidityTab
import com.etfmonitor.feature.analysis.presentation.advanced.SectorFearGreedTab
import com.etfmonitor.feature.analysis.presentation.advanced.EtfCorrelationTab
import kotlinx.coroutines.launch

/**
 * Analysis Hub Screen - 분석
 *
 * Consolidates:
 * - AI 시장 분석
 * - 고급 분석
 */

private val ANALYSIS_TABS = listOf("AI 분석", "고급 분석")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisHubScreen(
    navController: NavHostController,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStocks: (String) -> Unit,
    aiAnalysisViewModel: NewAIAnalysisViewModel = hiltViewModel(),
    advancedDashboardViewModel: AdvancedDashboardViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { ANALYSIS_TABS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "분석",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = ANALYSIS_TABS,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> AIAnalysisHubContent(
                    viewModel = aiAnalysisViewModel,
                    onNavigateToStocks = onNavigateToStocks
                )
                1 -> AdvancedDashboardHubContent(
                    viewModel = advancedDashboardViewModel,
                    navController = navController
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIAnalysisHubContent(
    viewModel: NewAIAnalysisViewModel,
    onNavigateToStocks: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isApiKeyConfigured by viewModel.isApiKeyConfigured.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val stockIndicatorCorrelationResult by viewModel.stockIndicatorCorrelationResult.collectAsState()
    val analysisPeriod by viewModel.analysisPeriod.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val stockSearchResults by viewModel.stockSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState(initial = emptyList())
    val stockIndicatorAIHistory by viewModel.stockIndicatorAIHistory.collectAsState(initial = emptyList())
    val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

    var showProviderDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showStockIndicatorHistorySheet by remember { mutableStateOf(false) }
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val selectedTab = AnalysisTab.entries[selectedTabIndex]

    // 화면 진입 시 API 키 상태 새로고침
    LaunchedEffect(Unit) {
        viewModel.refreshApiKeyState()
    }

    // FAB 표시 조건
    val showFab = quickChartAnalysisEnabled &&
            selectedTab == AnalysisTab.STOCK_INDICATOR &&
            selectedStock != null &&
            stockIndicatorCorrelationResult?.correlationResult != null &&
            currentSession == null

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            currentSession != null -> {
                // 채팅 화면
                Column(modifier = Modifier.fillMaxSize()) {
                    // 채팅 헤더
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.closeChat() }) {
                                Icon(Icons.Default.Close, "채팅 닫기")
                            }
                            Text(
                                "AI 대화",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    ChatScreen(
                        messages = chatMessages,
                        isSending = isSendingMessage,
                        onSendMessage = { viewModel.sendMessage(it) },
                        state = state
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 상단 액션 바
                    Surface(tonalElevation = 1.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            // 캐시 초기화 및 재분석 버튼
                            IconButton(onClick = { viewModel.clearCacheAndRefresh() }) {
                                Icon(Icons.Default.Refresh, "새로고침")
                            }

                            // AI 제공자 선택
                            TextButton(onClick = { showProviderDialog = true }) {
                                Text(
                                    selectedProvider.name,
                                    color = if (isApiKeyConfigured)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                            }

                            // 대화 이력
                            IconButton(onClick = { showHistorySheet = true }) {
                                Icon(Icons.Default.History, "대화 이력")
                            }
                        }
                    }

                    // 탭 선택
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AnalysisTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { viewModel.selectTab(tab.ordinal) },
                                text = { Text(tab.title) }
                            )
                        }
                    }

                    // 탭 내용
                    when (selectedTab) {
                        AnalysisTab.CORRELATION -> {
                            HubCorrelationAnalysisContent(
                                state = state,
                                selectedMarket = selectedMarket,
                                isApiKeyConfigured = isApiKeyConfigured,
                                analysisResult = analysisResult,
                                onMarketSelect = { viewModel.selectMarket(it) },
                                onRunCorrelation = { viewModel.runCorrelationAnalysis() },
                                onRunFullAnalysis = { viewModel.runFullAnalysis() },
                                onInterpretWithAI = { viewModel.interpretWithAI(it) },
                                onStartChat = { viewModel.startNewChat() },
                                onClearError = { viewModel.clearError() }
                            )
                        }
                        AnalysisTab.STOCK_INDICATOR -> {
                            HubStockIndicatorCorrelationContent(
                                state = state,
                                analysisPeriod = analysisPeriod,
                                isApiKeyConfigured = isApiKeyConfigured,
                                selectedStock = selectedStock,
                                stockIndicatorCorrelationResult = stockIndicatorCorrelationResult,
                                stockSearchResults = stockSearchResults,
                                isSearching = isSearching,
                                historyCount = stockIndicatorAIHistory.size,
                                searchHistory = searchHistory,
                                onPeriodChange = { viewModel.setAnalysisPeriod(it) },
                                onSearchStock = { viewModel.searchStock(it) },
                                onSelectStock = { ticker, name -> viewModel.selectStock(ticker, name) },
                                onClearStock = { viewModel.clearSelectedStock() },
                                onRunAnalysis = { viewModel.analyzeStockIndicatorCorrelation() },
                                onRunFullAnalysis = { viewModel.runFullStockIndicatorCorrelationAnalysis() },
                                onInterpretWithAI = { viewModel.interpretStockIndicatorCorrelationWithAI() },
                                onStartChat = { viewModel.startNewChat() },
                                onClearError = { viewModel.clearError() },
                                onShowHistory = { showStockIndicatorHistorySheet = true }
                            )
                        }
                    }
                }

                // FAB
                selectedStock?.let { stock ->
                    if (showFab) {
                        ExtendedFloatingActionButton(
                            onClick = { onNavigateToStocks(stock.first) },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                        text = { Text(stringResource(R.string.fab_stock_analysis)) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    // AI 제공자 선택 다이얼로그
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text("AI 제공자 선택") },
            text = {
                Column {
                    viewModel.getAvailableProviders().forEach { provider ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = provider == selectedProvider,
                                onClick = {
                                    viewModel.selectProvider(provider)
                                    showProviderDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(provider.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 대화 이력 바텀 시트
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "대화 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (chatSessions.isEmpty()) {
                    Text(
                        "저장된 대화가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(chatSessions, key = { it.id }) { session ->
                            SessionItem(
                                session = session,
                                onClick = {
                                    viewModel.openSession(session.id)
                                    showHistorySheet = false
                                },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 종목-지표 분석 이력 바텀 시트
    if (showStockIndicatorHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showStockIndicatorHistorySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "AI 분석 이력",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (stockIndicatorAIHistory.isEmpty()) {
                    Text(
                        "저장된 분석 결과가 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(stockIndicatorAIHistory, key = { it.id }) { historyItem ->
                            StockIndicatorAIHistoryItemCard(
                                item = historyItem,
                                onClick = {
                                    viewModel.loadFromHistory(historyItem)
                                    showStockIndicatorHistorySheet = false
                                },
                                onDelete = { viewModel.deleteHistoryItem(historyItem.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Hub 상관관계 분석 화면 콘텐츠
 */
@Composable
private fun HubCorrelationAnalysisContent(
    state: NewAIAnalysisState,
    selectedMarket: String,
    isApiKeyConfigured: Boolean,
    analysisResult: FullAnalysis?,
    onMarketSelect: (String) -> Unit,
    onRunCorrelation: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: (CorrelationAnalysis) -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시장 선택
        item {
            AnalysisMarketSelector(
                selectedMarket = selectedMarket,
                onMarketSelect = onMarketSelect
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            AnalysisButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasCorrelationResult = analysisResult?.correlationResult != null,
                onRunCorrelation = onRunCorrelation,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = {
                    analysisResult?.correlationResult?.let { onInterpretWithAI(it) }
                }
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    ErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 분석 결과
        analysisResult?.let { result ->
            item {
                CorrelationResultCard(
                    result = result.correlationResult,
                    aiResult = result.aiResult
                )
            }

            // AI 해석 결과
            result.aiResult?.let { aiResult ->
                item {
                    AIInterpretationCard(
                        signal = aiResult.signal,
                        confidence = aiResult.confidence,
                        upProbability = aiResult.upProbability,
                        downProbability = aiResult.downProbability,
                        reasoning = aiResult.reasoning,
                        recommendation = aiResult.recommendation,
                        riskLevel = aiResult.riskLevel
                    )
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state !is NewAIAnalysisState.AnalyzingCorrelation &&
                            state !is NewAIAnalysisState.AnalyzingFull &&
                            state !is NewAIAnalysisState.InterpretingWithAI
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("분석 결과로 대화하기")
                }
            }
        }
    }
}

/**
 * Hub 종목-지표 상관관계 분석 화면 콘텐츠
 */
@Composable
private fun HubStockIndicatorCorrelationContent(
    state: NewAIAnalysisState,
    analysisPeriod: Int,
    isApiKeyConfigured: Boolean,
    selectedStock: Pair<String, String>?,
    stockIndicatorCorrelationResult: FullStockIndicatorAnalysis?,
    stockSearchResults: List<Pair<String, String>>,
    isSearching: Boolean,
    historyCount: Int,
    searchHistory: List<SearchHistory>,
    onPeriodChange: (Int) -> Unit,
    onSearchStock: (String) -> Unit,
    onSelectStock: (String, String) -> Unit,
    onClearStock: () -> Unit,
    onRunAnalysis: () -> Unit,
    onRunFullAnalysis: () -> Unit,
    onInterpretWithAI: () -> Unit,
    onStartChat: () -> Unit,
    onClearError: () -> Unit,
    onShowHistory: () -> Unit
) {
    val isLoading = state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelation ||
            state is NewAIAnalysisState.AnalyzingStockIndicatorCorrelationFull ||
            state is NewAIAnalysisState.InterpretingStockIndicatorCorrelation

    var searchQuery by remember { mutableStateOf("") }

    // 선택된 종목의 시장 자동 감지
    val detectedMarket = selectedStock?.let { Stock.inferMarket(it.first) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 종목 검색
        item {
            StockSearchSection(
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    onSearchStock(it)
                },
                searchResults = stockSearchResults,
                isSearching = isSearching,
                selectedStock = selectedStock,
                detectedMarket = detectedMarket,
                searchHistory = searchHistory,
                onSelectStock = { ticker, name ->
                    onSelectStock(ticker, name)
                    searchQuery = ""
                },
                onClearStock = onClearStock
            )
        }

        // 분석 이력 버튼
        item {
            OutlinedCard(
                onClick = onShowHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "AI 분석 이력",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (historyCount > 0) "저장된 분석 결과 ${historyCount}개" else "저장된 분석 결과 없음",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 분석 기간 선택
        item {
            TimeSeriesPeriodSelector(
                period = analysisPeriod,
                onPeriodChange = onPeriodChange
            )
        }

        // API 키 경고
        if (!isApiKeyConfigured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI 분석을 위해 설정에서 API 키를 등록해주세요",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 분석 버튼
        item {
            StockIndicatorCorrelationButtons(
                state = state,
                isApiKeyConfigured = isApiKeyConfigured,
                hasSelectedStock = selectedStock != null,
                hasCorrelationResult = stockIndicatorCorrelationResult?.correlationResult != null,
                onRunAnalysis = onRunAnalysis,
                onRunFullAnalysis = onRunFullAnalysis,
                onInterpretWithAI = onInterpretWithAI
            )
        }

        // 에러 표시
        when (state) {
            is NewAIAnalysisState.Error -> {
                item {
                    ErrorCard(message = state.message, onDismiss = onClearError)
                }
            }
            else -> {}
        }

        // 상관관계 분석 결과
        stockIndicatorCorrelationResult?.correlationResult?.let { result ->
            // 종목 정보 요약 카드
            item {
                StockIndicatorSummaryCard(result = result)
            }

            // Fear & Greed 상관관계 차트
            if (result.fearGreedCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "심리 지표 상관관계",
                        subtitle = "Fear & Greed, RSI, 모멘텀",
                        icon = Icons.Default.Psychology,
                        correlations = result.fearGreedCorrelations,
                        color = Color(0xFF6750A4)
                    )
                }
            }

            // Oscillator 상관관계 차트
            if (result.oscillatorCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "기술 지표 상관관계",
                        subtitle = "시장 과매수/과매도",
                        icon = Icons.Default.TrendingUp,
                        correlations = result.oscillatorCorrelations,
                        color = Color(0xFF1976D2)
                    )
                }
            }

            // 예탁금/신용 상관관계 차트
            if (result.depositCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "자금 동향 상관관계",
                        subtitle = "고객예탁금, 신용잔고",
                        icon = Icons.Default.AccountBalance,
                        correlations = result.depositCorrelations,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            // ETF 수급 상관관계 차트
            if (result.etfCorrelations.isNotEmpty()) {
                item {
                    CorrelationCategoryCard(
                        title = "ETF 수급 상관관계",
                        subtitle = "ETF 편입/편출, 비중 변화",
                        icon = Icons.Default.ShowChart,
                        correlations = result.etfCorrelations,
                        color = Color(0xFFE64A19)
                    )
                }
            }

            // Top 상관관계 요약
            if (result.topPositiveCorrelations.isNotEmpty() || result.topNegativeCorrelations.isNotEmpty()) {
                item {
                    TopCorrelationsCard(
                        topPositive = result.topPositiveCorrelations,
                        topNegative = result.topNegativeCorrelations
                    )
                }
            }

            // AI 해석 결과
            stockIndicatorCorrelationResult.aiInterpretation?.let { aiResult ->
                item {
                    StockIndicatorAIInterpretationCard(interpretation = aiResult)
                }
            }

            // 채팅 시작 버튼
            item {
                Button(
                    onClick = onStartChat,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("상관관계 분석 결과로 대화하기")
                }
            }
        }
    }
}

/**
 * 고급 분석 대시보드 콘텐츠
 */
@Composable
private fun AdvancedDashboardHubContent(
    viewModel: AdvancedDashboardViewModel,
    navController: NavHostController
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        is AdvancedDashboardState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "분석 데이터 로딩 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is AdvancedDashboardState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        currentState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadDashboard() }) {
                        Text("다시 시도")
                    }
                }
            }
        }
        is AdvancedDashboardState.Success -> {
            val data = currentState.data

            // 고급 분석 서브탭
            var selectedSubTab by remember { mutableIntStateOf(0) }
            val subTabs = listOf("시총가중", "유동성", "섹터심리", "ETF상관")

            Column(modifier = Modifier.fillMaxSize()) {
                // 서브탭 네비게이션
                ScrollableTabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp
                ) {
                    subTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSubTab == index,
                            onClick = { selectedSubTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }

                // 탭 내용
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (selectedSubTab) {
                        0 -> MarketCapFlowTab(data = data)
                        1 -> LiquidityTab(data = data)
                        2 -> SectorFearGreedTab(data = data)
                        3 -> EtfCorrelationTab(data = data)
                    }
                }
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/backup/presentation/screen/BackupScreen.kt
package com.etfmonitor.feature.backup.presentation.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.feature.backup.domain.model.*
import com.etfmonitor.feature.backup.presentation.state.*
import com.etfmonitor.feature.backup.presentation.viewmodel.BackupViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Embedded backup content for use in Settings tab
 * Contains all backup functionality without the Scaffold wrapper
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTabContent(
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val createBackupState by viewModel.createBackupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val backupDetailState by viewModel.backupDetailState.collectAsState()
    val deleteConfirmState by viewModel.deleteConfirmState.collectAsState()
    val googleDriveState by viewModel.googleDriveState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.handleGoogleSignInResult(account, null)
        } catch (e: ApiException) {
            viewModel.handleGoogleSignInResult(null, e.statusCode)
        }
    }

    // File picker for restore
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.validateBackupFile(it) }
    }

    // File saver for export
    var pendingExportBackupId by remember { mutableStateOf<String?>(null) }
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { destinationUri ->
            pendingExportBackupId?.let { backupId ->
                viewModel.exportBackup(backupId, destinationUri)
            }
        }
        pendingExportBackupId = null
    }

    // Collect snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val currentState = state) {
            is BackupState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is BackupState.Error -> {
                ErrorContent(
                    message = currentState.message,
                    onRetry = { viewModel.loadData() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is BackupState.Idle -> {
                BackupContentWithFab(
                    localBackups = currentState.localBackups,
                    entityCounts = currentState.entityCounts,
                    dateRange = currentState.dateRange,
                    estimatedSize = currentState.estimatedSize,
                    googleDriveState = googleDriveState,
                    onBackupClick = { viewModel.showBackupDetail(it) },
                    onRestoreClick = { viewModel.showRestoreFromLocalBackup(it) },
                    onDeleteClick = { viewModel.showDeleteConfirmation(it) },
                    onExportClick = { backupInfo ->
                        pendingExportBackupId = backupInfo.id
                        fileSaverLauncher.launch("etfmonitor_backup_${backupInfo.id}.etfbackup")
                    },
                    onUploadClick = { viewModel.uploadToGoogleDrive(it.id) },
                    onRestoreFromFile = {
                        viewModel.showRestoreFromFileDialog()
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onGoogleSignIn = {
                        googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                    },
                    onGoogleSignOut = { viewModel.signOutFromGoogleDrive() },
                    onLoadDriveBackups = { viewModel.loadGoogleDriveBackups() },
                    onDownloadFromDrive = { viewModel.downloadFromGoogleDrive(it) },
                    onDeleteFromDrive = { viewModel.deleteFromGoogleDrive(it) },
                    onCreateBackup = { viewModel.showCreateBackupDialog() },
                    onRefresh = { viewModel.loadData() }
                )
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Dialogs
    CreateBackupDialog(
        state = createBackupState,
        onDismiss = { viewModel.hideCreateBackupDialog() },
        onUpdateOptions = { entities, compress, startDate, endDate ->
            viewModel.updateCreateBackupOptions(entities, compress, startDate, endDate)
        },
        onConfirm = { viewModel.createBackup() }
    )

    RestoreDialog(
        state = restoreState,
        onDismiss = { viewModel.hideRestoreDialog() },
        onUpdateOptions = { entities -> viewModel.updateRestoreOptions(entities) },
        onConfirm = { viewModel.startRestore() }
    )

    BackupDetailDialog(
        state = backupDetailState,
        onDismiss = { viewModel.hideBackupDetail() }
    )

    DeleteConfirmDialog(
        state = deleteConfirmState,
        onDismiss = { viewModel.hideDeleteConfirmation() },
        onConfirm = { viewModel.confirmDelete() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val createBackupState by viewModel.createBackupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val backupDetailState by viewModel.backupDetailState.collectAsState()
    val deleteConfirmState by viewModel.deleteConfirmState.collectAsState()
    val googleDriveState by viewModel.googleDriveState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.handleGoogleSignInResult(account, null)
        } catch (e: ApiException) {
            viewModel.handleGoogleSignInResult(null, e.statusCode)
        }
    }

    // File picker for restore
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.validateBackupFile(it) }
    }

    // File saver for export
    var pendingExportBackupId by remember { mutableStateOf<String?>(null) }
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { destinationUri ->
            pendingExportBackupId?.let { backupId ->
                viewModel.exportBackup(backupId, destinationUri)
            }
        }
        pendingExportBackupId = null
    }

    // Collect snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("백업 및 복구") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is BackupState.Idle) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateBackupDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "백업 생성")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = state) {
                is BackupState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is BackupState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        onRetry = { viewModel.loadData() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is BackupState.Idle -> {
                    BackupContent(
                        localBackups = currentState.localBackups,
                        entityCounts = currentState.entityCounts,
                        dateRange = currentState.dateRange,
                        estimatedSize = currentState.estimatedSize,
                        googleDriveState = googleDriveState,
                        onBackupClick = { viewModel.showBackupDetail(it) },
                        onRestoreClick = { viewModel.showRestoreFromLocalBackup(it) },
                        onDeleteClick = { viewModel.showDeleteConfirmation(it) },
                        onExportClick = { backupInfo ->
                            pendingExportBackupId = backupInfo.id
                            fileSaverLauncher.launch("etfmonitor_backup_${backupInfo.id}.etfbackup")
                        },
                        onUploadClick = { viewModel.uploadToGoogleDrive(it.id) },
                        onRestoreFromFile = {
                            viewModel.showRestoreFromFileDialog()
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onGoogleSignIn = {
                            googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                        },
                        onGoogleSignOut = { viewModel.signOutFromGoogleDrive() },
                        onLoadDriveBackups = { viewModel.loadGoogleDriveBackups() },
                        onDownloadFromDrive = { viewModel.downloadFromGoogleDrive(it) },
                        onDeleteFromDrive = { viewModel.deleteFromGoogleDrive(it) }
                    )
                }
            }
        }
    }

    // Dialogs
    CreateBackupDialog(
        state = createBackupState,
        onDismiss = { viewModel.hideCreateBackupDialog() },
        onUpdateOptions = { entities, compress, startDate, endDate ->
            viewModel.updateCreateBackupOptions(entities, compress, startDate, endDate)
        },
        onConfirm = { viewModel.createBackup() }
    )

    RestoreDialog(
        state = restoreState,
        onDismiss = { viewModel.hideRestoreDialog() },
        onUpdateOptions = { entities -> viewModel.updateRestoreOptions(entities) },
        onConfirm = { viewModel.startRestore() }
    )

    BackupDetailDialog(
        state = backupDetailState,
        onDismiss = { viewModel.hideBackupDetail() }
    )

    DeleteConfirmDialog(
        state = deleteConfirmState,
        onDismiss = { viewModel.hideDeleteConfirmation() },
        onConfirm = { viewModel.confirmDelete() }
    )
}

/**
 * Backup content with embedded FAB for use in Settings tab
 */
@Composable
private fun BackupContentWithFab(
    localBackups: List<BackupInfo>,
    entityCounts: Map<EntityType, Int>,
    dateRange: DateRange?,
    estimatedSize: Long,
    googleDriveState: GoogleDriveState,
    onBackupClick: (BackupInfo) -> Unit,
    onRestoreClick: (BackupInfo) -> Unit,
    onDeleteClick: (BackupInfo) -> Unit,
    onExportClick: (BackupInfo) -> Unit,
    onUploadClick: (BackupInfo) -> Unit,
    onRestoreFromFile: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onLoadDriveBackups: () -> Unit,
    onDownloadFromDrive: (String) -> Unit,
    onDeleteFromDrive: (String) -> Unit,
    onCreateBackup: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action buttons at top
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCreateBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("백업 생성")
                    }
                    OutlinedButton(
                        onClick = onRefresh
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            }

            // Database Status Card
            item {
                DatabaseStatusCard(
                    entityCounts = entityCounts,
                    dateRange = dateRange,
                    estimatedSize = estimatedSize
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(
                    onRestoreFromFile = onRestoreFromFile
                )
            }

            // Google Drive Section
            item {
                GoogleDriveCard(
                    state = googleDriveState,
                    onSignIn = onGoogleSignIn,
                    onSignOut = onGoogleSignOut,
                    onLoadBackups = onLoadDriveBackups,
                    onDownload = onDownloadFromDrive,
                    onDelete = onDeleteFromDrive
                )
            }

            // Local Backups Section
            item {
                Text(
                    text = "로컬 백업 (${localBackups.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (localBackups.isEmpty()) {
                item {
                    EmptyBackupsCard()
                }
            } else {
                items(localBackups, key = { it.id }) { backup ->
                    BackupCard(
                        backupInfo = backup,
                        onClick = { onBackupClick(backup) },
                        onRestore = { onRestoreClick(backup) },
                        onDelete = { onDeleteClick(backup) },
                        onExport = { onExportClick(backup) },
                        onUpload = { onUploadClick(backup) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupContent(
    localBackups: List<BackupInfo>,
    entityCounts: Map<EntityType, Int>,
    dateRange: DateRange?,
    estimatedSize: Long,
    googleDriveState: GoogleDriveState,
    onBackupClick: (BackupInfo) -> Unit,
    onRestoreClick: (BackupInfo) -> Unit,
    onDeleteClick: (BackupInfo) -> Unit,
    onExportClick: (BackupInfo) -> Unit,
    onUploadClick: (BackupInfo) -> Unit,
    onRestoreFromFile: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onLoadDriveBackups: () -> Unit,
    onDownloadFromDrive: (String) -> Unit,
    onDeleteFromDrive: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Database Status Card
        item {
            DatabaseStatusCard(
                entityCounts = entityCounts,
                dateRange = dateRange,
                estimatedSize = estimatedSize
            )
        }

        // Quick Actions
        item {
            QuickActionsCard(
                onRestoreFromFile = onRestoreFromFile
            )
        }

        // Google Drive Section
        item {
            GoogleDriveCard(
                state = googleDriveState,
                onSignIn = onGoogleSignIn,
                onSignOut = onGoogleSignOut,
                onLoadBackups = onLoadDriveBackups,
                onDownload = onDownloadFromDrive,
                onDelete = onDeleteFromDrive
            )
        }

        // Local Backups Section
        item {
            Text(
                text = "로컬 백업 (${localBackups.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (localBackups.isEmpty()) {
            item {
                EmptyBackupsCard()
            }
        } else {
            items(localBackups, key = { it.id }) { backup ->
                BackupCard(
                    backupInfo = backup,
                    onClick = { onBackupClick(backup) },
                    onRestore = { onRestoreClick(backup) },
                    onDelete = { onDeleteClick(backup) },
                    onExport = { onExportClick(backup) },
                    onUpload = { onUploadClick(backup) }
                )
            }
        }
    }
}

@Composable
private fun DatabaseStatusCard(
    entityCounts: Map<EntityType, Int>,
    dateRange: DateRange?,
    estimatedSize: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "현재 데이터베이스",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val totalRecords = entityCounts.values.sum()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "총 레코드", value = formatNumber(totalRecords))
                StatItem(label = "예상 크기", value = formatFileSize(estimatedSize))
            }

            if (dateRange != null) {
                Text(
                    text = "데이터 기간: ${dateRange.startDate} ~ ${dateRange.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun QuickActionsCard(onRestoreFromFile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                icon = Icons.Default.FileOpen,
                label = "파일에서 복구",
                onClick = onRestoreFromFile
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyBackupsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "백업이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "우측 하단 버튼을 눌러 첫 백업을 생성하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GoogleDriveCard(
    state: GoogleDriveState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onLoadBackups: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )

                when (state) {
                    is GoogleDriveState.NotSignedIn -> {
                        FilledTonalButton(onClick = onSignIn) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("로그인")
                        }
                    }
                    is GoogleDriveState.SignedIn, is GoogleDriveState.Backups -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onLoadBackups) {
                                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.Default.Logout, contentDescription = "로그아웃")
                            }
                        }
                    }
                    else -> {}
                }
            }

            when (state) {
                is GoogleDriveState.NotSignedIn -> {
                    Text(
                        text = "Google Drive에 로그인하여 백업을 클라우드에 저장하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                is GoogleDriveState.Loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "로딩 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is GoogleDriveState.Uploading -> {
                    Column {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is GoogleDriveState.Downloading -> {
                    Column {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is GoogleDriveState.SignedIn -> {
                    Text(
                        text = "연결됨. 백업 목록을 불러오려면 새로고침을 클릭하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                is GoogleDriveState.Backups -> {
                    if (state.backups.isEmpty()) {
                        Text(
                            text = "클라우드에 저장된 백업이 없습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "클라우드 백업 (${state.backups.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            state.backups.forEach { backup ->
                                DriveBackupItem(
                                    backupInfo = backup,
                                    onDownload = { onDownload(backup.id) },
                                    onDelete = { onDelete(backup.id) }
                                )
                            }
                        }
                    }
                }
                is GoogleDriveState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveBackupItem(
    backupInfo: BackupInfo,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backupInfo.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTimestamp(backupInfo.createdAt)} • ${formatFileSize(backupInfo.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("다운로드") },
                        leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    backupInfo: BackupInfo,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onUpload: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTimestamp(backupInfo.createdAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${backupInfo.schemaVersion} • ${formatFileSize(backupInfo.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("복구") },
                            leadingIcon = { Icon(Icons.Default.Restore, null) },
                            onClick = {
                                showMenu = false
                                onRestore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("내보내기") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = {
                                showMenu = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Google Drive 업로드") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                            onClick = {
                                showMenu = false
                                onUpload()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entity summary
            val entitySummary = backupInfo.entityCounts.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(" • ") { (key, count) ->
                    val displayName = EntityType.fromTableName(key)?.displayName ?: key
                    "$displayName: ${formatNumber(count)}"
                }

            Text(
                text = entitySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

// ==================== Dialogs ====================

@Composable
private fun CreateBackupDialog(
    state: CreateBackupState,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>?, Boolean?, String?, String?) -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is CreateBackupState.Hidden -> {}
        is CreateBackupState.Visible -> {
            CreateBackupConfigDialog(
                selectedEntities = state.selectedEntities,
                useCompression = state.useCompression,
                startDate = state.startDate,
                endDate = state.endDate,
                dateRange = state.dateRange,
                onDismiss = onDismiss,
                onUpdateOptions = onUpdateOptions,
                onConfirm = onConfirm
            )
        }
        is CreateBackupState.InProgress -> {
            ProgressDialog(
                title = "백업 생성 중",
                message = state.message,
                progress = state.progress,
                processedItems = state.processedEntities,
                totalItems = state.totalEntities
            )
        }
        is CreateBackupState.Success -> {
            SuccessDialog(
                title = "백업 완료",
                message = "백업이 성공적으로 생성되었습니다.\n크기: ${formatFileSize(state.backupInfo.fileSize)}",
                onDismiss = onDismiss
            )
        }
        is CreateBackupState.Error -> {
            ErrorDialog(
                title = "백업 실패",
                message = state.message,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun CreateBackupConfigDialog(
    selectedEntities: Set<EntityType>,
    useCompression: Boolean,
    startDate: String?,
    endDate: String?,
    dateRange: DateRange?,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>?, Boolean?, String?, String?) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 생성") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Entity selection
                Text(
                    text = "백업할 데이터 선택",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                EntityType.entries.groupBy { it.category }.forEach { (category, entities) ->
                    Text(
                        text = when (category) {
                            EntityCategory.MASTER -> "마스터 데이터"
                            EntityCategory.TIME_SERIES -> "시계열 데이터"
                            EntityCategory.ANALYSIS -> "분석 결과"
                            EntityCategory.USER_DATA -> "사용자 데이터"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    entities.forEach { entityType ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entityType in selectedEntities,
                                onCheckedChange = { checked ->
                                    val newSelection = if (checked) {
                                        selectedEntities + entityType
                                    } else {
                                        selectedEntities - entityType
                                    }
                                    onUpdateOptions(newSelection, null, null, null)
                                }
                            )
                            Text(
                                text = entityType.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Compression option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useCompression,
                        onCheckedChange = { onUpdateOptions(null, it, null, null) }
                    )
                    Text("파일 압축 (권장)")
                }

                // Date range (only show if time-series entities are selected)
                val hasTimeSeries = selectedEntities.any { it.category == EntityCategory.TIME_SERIES }
                if (hasTimeSeries && dateRange != null) {
                    HorizontalDivider()
                    Text(
                        text = "날짜 범위 (시계열 데이터)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "가능한 범위: ${dateRange.startDate} ~ ${dateRange.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Simple date inputs (in production, use DatePicker)
                    OutlinedTextField(
                        value = startDate ?: "",
                        onValueChange = { onUpdateOptions(null, null, it.takeIf { it.isNotBlank() }, null) },
                        label = { Text("시작일 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate ?: "",
                        onValueChange = { onUpdateOptions(null, null, null, it.takeIf { it.isNotBlank() }) },
                        label = { Text("종료일 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedEntities.isNotEmpty()
            ) {
                Text("백업 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun RestoreDialog(
    state: RestoreState,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>) -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is RestoreState.Hidden -> {}
        is RestoreState.SelectFile -> {
            if (state.isValidating) {
                ProgressDialog(
                    title = "백업 파일 확인 중",
                    message = "파일을 분석하고 있습니다...",
                    progress = -1,
                    processedItems = 0,
                    totalItems = 0
                )
            } else if (state.validationError != null) {
                ErrorDialog(
                    title = "유효하지 않은 파일",
                    message = state.validationError,
                    onDismiss = onDismiss
                )
            }
        }
        is RestoreState.Configure -> {
            RestoreConfigDialog(
                metadata = state.metadata,
                selectedEntities = state.selectedEntities,
                onDismiss = onDismiss,
                onUpdateOptions = onUpdateOptions,
                onConfirm = onConfirm
            )
        }
        is RestoreState.InProgress -> {
            ProgressDialog(
                title = "복구 중",
                message = state.message,
                progress = state.progress,
                processedItems = state.processedEntities,
                totalItems = state.totalEntities
            )
        }
        is RestoreState.Success -> {
            val result = state.result
            SuccessDialog(
                title = "복구 완료",
                message = buildString {
                    appendLine("추가된 항목: ${result.imported}")
                    appendLine("건너뛴 항목: ${result.skipped}")
                    if (result.errors > 0) {
                        appendLine("실패한 항목: ${result.errors}")
                    }
                },
                onDismiss = onDismiss
            )
        }
        is RestoreState.Error -> {
            ErrorDialog(
                title = "복구 실패",
                message = state.message,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun RestoreConfigDialog(
    metadata: BackupMetadata,
    selectedEntities: Set<EntityType>,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("복구 설정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Backup info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "백업 정보",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("생성일: ${formatTimestamp(metadata.createdAt)}")
                        Text("스키마 버전: ${metadata.schemaVersion}")
                        if (metadata.dateRange != null) {
                            Text("데이터 기간: ${metadata.dateRange.startDate} ~ ${metadata.dateRange.endDate}")
                        }
                    }
                }

                Text(
                    text = "복구할 데이터 선택",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "※ 현재 데이터에 없는 항목만 추가됩니다 (병합 모드)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Only show entities that exist in backup
                val availableEntities = metadata.entityCounts
                    .filter { it.value > 0 }
                    .mapNotNull { (tableName, count) ->
                        EntityType.fromTableName(tableName)?.let { it to count }
                    }

                availableEntities.forEach { (entityType, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = entityType in selectedEntities,
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedEntities + entityType
                                } else {
                                    selectedEntities - entityType
                                }
                                onUpdateOptions(newSelection)
                            }
                        )
                        Text("${entityType.displayName} ($count)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedEntities.isNotEmpty()
            ) {
                Text("복구 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun BackupDetailDialog(
    state: BackupDetailState,
    onDismiss: () -> Unit
) {
    if (state is BackupDetailState.Visible) {
        val backup = state.backupInfo
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("백업 상세 정보") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("백업 ID", backup.id)
                    DetailRow("생성일시", formatTimestamp(backup.createdAt))
                    DetailRow("스키마 버전", backup.schemaVersion.toString())
                    DetailRow("파일 크기", formatFileSize(backup.fileSize))
                    DetailRow("백업 타입", if (backup.backupType == BackupType.FULL) "전체" else "선택")

                    if (backup.dateRange != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "데이터 범위",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        DetailRow("시작일", backup.dateRange.startDate)
                        DetailRow("종료일", backup.dateRange.endDate)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "포함된 데이터",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    backup.entityCounts
                        .filter { it.value > 0 }
                        .forEach { (tableName, count) ->
                            val displayName = EntityType.fromTableName(tableName)?.displayName ?: tableName
                            DetailRow(displayName, formatNumber(count))
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    state: DeleteConfirmState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is DeleteConfirmState.Hidden -> {}
        is DeleteConfirmState.Visible -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("백업 삭제") },
                text = {
                    Text("이 백업을 삭제하시겠습니까?\n\n${formatTimestamp(state.backupInfo.createdAt)}\n${formatFileSize(state.backupInfo.fileSize)}")
                },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                }
            )
        }
        is DeleteConfirmState.Deleting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("삭제 중...") },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("백업을 삭제하고 있습니다...")
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
private fun ProgressDialog(
    title: String,
    message: String,
    progress: Int,
    processedItems: Int,
    totalItems: Int
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(message)
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$processedItems / $totalItems",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SuccessDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

// ==================== Utility Functions ====================

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun formatNumber(number: Int): String {
    return DecimalFormat("#,###").format(number)
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// FILE: app/src/main/java/com/etfmonitor/feature/etf/presentation/detail/EtfDetailScreen.kt
package com.etfmonitor.feature.etf.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.core.common.util.AmountFormatter

/**
 * ETF Detail Screen - Moss Green Nature Theme
 * Shows holding comparisons with status badges
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfDetailScreen(
    etfTicker: String,
    onNavigateBack: () -> Unit,
    onStockClick: (String) -> Unit,
    viewModel: EtfDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val etfName by viewModel.etfName.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            etfName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state is EtfDetailState.Success) {
                            val comparison = (state as EtfDetailState.Success).comparison
                            Text(
                                "$etfTicker | 비교기간: ${comparison.previousDate} ~ ${comparison.currentDate}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text(
                                etfTicker,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 기간 선택 UI
            DateRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.updateDateRange(it) },
                availableOptions = listOf(
                    DateRangeOption.WEEK,
                    DateRangeOption.MONTH,
                    DateRangeOption.THREE_MONTHS,
                    DateRangeOption.SIX_MONTHS,
                    DateRangeOption.YEAR,
                    DateRangeOption.ALL
                )
            )

            when (val s = state) {
                is EtfDetailState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                    }
                }
                is EtfDetailState.Success -> {
                    ComparisonList(
                        items = s.comparison.items,
                        onStockClick = onStockClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is EtfDetailState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(MaterialTheme.spacing.large),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.extendedShapes.cardLarge,
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.spacing.large),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    s.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonList(
    items: List<HoldingWithComparison>,
    onStockClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        items(items, key = { it.stockTicker }) { item ->
            ComparisonCard(
                item = item,
                onClick = { onStockClick(item.stockTicker) }
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    item: HoldingWithComparison,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            // Header: Stock name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.stockName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        item.stockTicker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(item.status)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Weight comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeightInfo("이전", item.previousWeight, Modifier.weight(1f))
                WeightInfo("현재", item.currentWeight, Modifier.weight(1f))
                ChangeInfo(item.change, Modifier.weight(1f))
            }

            // Amount display
            if (item.currentAmount > 0) {
                Text(
                    "평가금액: ${AmountFormatter.format(item.currentAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: HoldingStatus) {
    val (text, color) = when (status) {
        HoldingStatus.NEW -> "신규" to MaterialTheme.colorScheme.primary
        HoldingStatus.INCREASE -> "증가" to MaterialTheme.colorScheme.tertiary
        HoldingStatus.DECREASE -> "감소" to MaterialTheme.colorScheme.error
        HoldingStatus.MAINTAIN -> "유지" to MaterialTheme.colorScheme.outline
        HoldingStatus.REMOVED -> "제외" to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.extendedShapes.badge,
        tonalElevation = 1.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun WeightInfo(label: String, weight: Float, modifier: Modifier = Modifier) {
    val formattedWeight = remember(weight) { String.format("%.2f%%", weight) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formattedWeight,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChangeInfo(change: Float, modifier: Modifier = Modifier) {
    val formattedChange = remember(change) { String.format("%+.2f%%", change) }
    val changeColor = when {
        change > 0.01f -> MaterialTheme.colorScheme.tertiary
        change < -0.01f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "변동",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formattedChange,
            style = MaterialTheme.typography.bodyMedium,
            color = changeColor
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/etf/presentation/hub/EtfHubScreen.kt
package com.etfmonitor.feature.etf.presentation.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.core.database.entities.HoldingStatus
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.component.TabNavigationBar
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.feature.etf.presentation.list.EtfListViewModel
import com.etfmonitor.feature.etf.presentation.list.EtfListState
import com.etfmonitor.feature.stock.presentation.statistics.StatisticsViewModel
import com.etfmonitor.feature.stock.presentation.statistics.AmountRankingTab
import com.etfmonitor.feature.stock.presentation.statistics.StockChangeTab
import com.etfmonitor.feature.stock.presentation.statistics.CashDepositTrendTab
import com.etfmonitor.feature.stock.presentation.statistics.StockAnalysisTab
import kotlinx.coroutines.launch

/**
 * ETF Hub Screen
 *
 * Consolidates:
 * - ETF 테마 목록
 * - ETF 전체 통계
 */

private val ETF_TABS = listOf("테마 목록", "통계")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEtfClick: (String) -> Unit,
    onStockClick: (String) -> Unit,
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null,
    listViewModel: EtfListViewModel = hiltViewModel(),
    statisticsViewModel: StatisticsViewModel = hiltViewModel()
) {
    // Start on Statistics tab (1) if initialStockTicker is provided
    val initialPage = if (initialStockTicker != null) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { ETF_TABS.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Trigger stock analysis and navigate to Statistics tab when initialStockTicker is provided
    LaunchedEffect(initialStockTicker) {
        if (initialStockTicker != null) {
            // Navigate to Statistics tab (page 1)
            pagerState.scrollToPage(1)
            // Trigger analysis (skip history save when navigating via FAB)
            statisticsViewModel.analyzeStock(initialStockTicker, saveHistory = false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "ETF",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = ETF_TABS,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> EtfListHubContent(
                    viewModel = listViewModel,
                    onEtfClick = onEtfClick
                )
                1 -> StatisticsHubContent(
                    viewModel = statisticsViewModel,
                    onStockClick = onStockClick,
                    onNavigateToStocks = onNavigateToStocks,
                    initialStockTicker = initialStockTicker
                )
            }
        }
    }
}

@Composable
private fun EtfListHubContent(
    viewModel: EtfListViewModel,
    onEtfClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 검색 필드
        EtfSearchField(
            searchQuery = searchQuery,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onClearSearch = viewModel::onClearSearch,
            onSearchDone = { keyboardController?.hide() }
        )

        when (val s = state) {
            is EtfListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is EtfListState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(s.etfs, key = { _, etf -> etf.ticker }) { index, etf ->
                        EtfListItemCompact(
                            rank = index + 1,
                            etf = etf,
                            onClick = { onEtfClick(etf.ticker) }
                        )
                    }
                }
            }
            is EtfListState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "검색 결과가 없습니다" else "ETF 데이터가 없습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is EtfListState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EtfSearchField(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchDone: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = {
            Text(
                "ETF 검색...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "지우기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchDone() })
    )
}

@Composable
private fun EtfListItemCompact(
    rank: Int,
    etf: Etf,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (rank <= 3) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (rank <= 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // ETF info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = etf.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = etf.ticker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatisticsHubContent(
    viewModel: StatisticsViewModel,
    onStockClick: (String) -> Unit,
    onNavigateToStocks: (String) -> Unit,
    initialStockTicker: String? = null
) {
    // ViewModel states
    val amountRanking by viewModel.amountRanking.collectAsState()
    val newStocks by viewModel.newStocks.collectAsState()
    val removedStocks by viewModel.removedStocks.collectAsState()
    val increasedStocks by viewModel.increasedStocks.collectAsState()
    val decreasedStocks by viewModel.decreasedStocks.collectAsState()
    val cashDepositTrend by viewModel.cashDepositTrend.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Analysis tab states
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())

    // Date range states
    val selectedRange by viewModel.selectedRange.collectAsState()
    val dates by viewModel.dates.collectAsState()

    // Start on Analysis tab (6) if initialStockTicker is provided
    var selectedTab by remember { mutableIntStateOf(if (initialStockTicker != null) 6 else 0) }

    // Force navigate to Analysis tab (6) when initialStockTicker changes
    LaunchedEffect(initialStockTicker) {
        if (initialStockTicker != null) {
            selectedTab = 6
        }
    }

    val tabs = listOf(
        stringResource(R.string.statistics_tab_amount_ranking),
        stringResource(R.string.statistics_tab_new),
        stringResource(R.string.statistics_tab_removed),
        stringResource(R.string.statistics_tab_increased),
        stringResource(R.string.statistics_tab_decreased),
        stringResource(R.string.statistics_tab_cash_deposit),
        stringResource(R.string.statistics_tab_analysis)
    )

    // FAB 표시 조건: 분석 탭에서 분석 결과가 있을 때
    val showFab = selectedTab == 6 && analysisResult != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-tab navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            // Date Range Selector (except for Analysis tab which has its own search)
            if (selectedTab != 6) {
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    availableOptions = listOf(
                        DateRangeOption.WEEK,
                        DateRangeOption.MONTH,
                        DateRangeOption.THREE_MONTHS,
                        DateRangeOption.SIX_MONTHS,
                        DateRangeOption.YEAR,
                        DateRangeOption.ALL
                    )
                )

                // Show comparison dates
                dates?.let { (currentDate, previousDate) ->
                    Text(
                        text = "$previousDate ~ $currentDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> AmountRankingTab(amountRanking, viewModel, onStockClick)
                    1 -> StockChangeTab(newStocks, HoldingStatus.NEW, onStockClick)
                    2 -> StockChangeTab(removedStocks, HoldingStatus.REMOVED, onStockClick)
                    3 -> StockChangeTab(increasedStocks, HoldingStatus.INCREASE, onStockClick)
                    4 -> StockChangeTab(decreasedStocks, HoldingStatus.DECREASE, onStockClick)
                    5 -> CashDepositTrendTab(cashDepositTrend)
                    6 -> StockAnalysisTab(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        analysisResult = analysisResult,
                        isAnalyzing = isAnalyzing,
                        searchHistory = searchHistory,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearchAndAnalyze = { viewModel.searchAndAnalyze(it) },
                        onStockSelect = { viewModel.analyzeStock(it) },
                        onClearAnalysis = { viewModel.clearAnalysis() },
                        onStockClick = onStockClick
                    )
                }
            }
        }

        // Floating Action Button for navigating to stock analysis
        analysisResult?.let { result ->
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToStocks(result.stockTicker) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        Icon(Icons.Default.ShowChart, contentDescription = null)
                    },
                    text = {
                        Text(stringResource(R.string.fab_stock_analysis))
                    }
                )
            }
        }
    }
}


// FILE: app/src/main/java/com/etfmonitor/feature/etf/presentation/list/EtfListScreen.kt
package com.etfmonitor.feature.etf.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.core.ui.component.FilterChipRow
import com.etfmonitor.core.ui.theme.*

/**
 * ETF List Screen - Moss Green Nature Theme
 * Clean, modern list with search and filter functionality
 */

private val ETF_CATEGORIES = listOf("전체", "반도체", "바이오", "2차전지", "금융", "에너지", "IT")

@Composable
fun EtfListScreen(
    onNavigateBack: () -> Unit,
    onEtfClick: (String) -> Unit,
    viewModel: EtfListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var showSearchField by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom Header
            EtfListHeader(
                onNavigateBack = onNavigateBack,
                showSearchField = showSearchField,
                searchQuery = searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onSearchToggle = { showSearchField = !showSearchField },
                onClearSearch = {
                    viewModel.onClearSearch()
                    showSearchField = false
                },
                onSearchDone = { keyboardController?.hide() }
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            ) {
                FilterChipRow(
                    filters = ETF_CATEGORIES,
                    selectedIndex = selectedFilterIndex,
                    onFilterSelected = { index ->
                        selectedFilterIndex = index
                        // Apply filter based on category
                        if (index == 0) {
                            viewModel.onClearSearch()
                        } else {
                            viewModel.onSearchQueryChanged(ETF_CATEGORIES[index])
                        }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )

            // Content
            when (val s = state) {
                is EtfListState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
                is EtfListState.Success -> {
                    EtfListContent(etfs = s.etfs, onEtfClick = onEtfClick)
                }
                is EtfListState.Empty -> {
                    EmptyStateCard()
                }
                is EtfListState.Error -> {
                    ErrorStateCard(message = s.message)
                }
            }
        }
    }
}

@Composable
private fun EtfListHeader(
    onNavigateBack: () -> Unit,
    showSearchField: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onClearSearch: () -> Unit,
    onSearchDone: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Title
                if (!showSearchField) {
                    Text(
                        text = "ETF 목록",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        placeholder = {
                            Text(
                                "ETF 검색...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchDone() }),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = onClearSearch) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "지우기",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }

                // Search toggle button
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "검색",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EtfListContent(etfs: List<Etf>, onEtfClick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(etfs, key = { _, etf -> etf.ticker }) { index, etf ->
            EtfListItem(
                rank = index + 1,
                etf = etf,
                onClick = { onEtfClick(etf.ticker) }
            )
        }
    }
}

@Composable
private fun EtfListItem(
    rank: Int,
    etf: Etf,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.03f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Rank and info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Rank badge
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = when {
                        rank <= 3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                rank <= 3 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        )
                    }
                }

                // Name and code
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = etf.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = etf.ticker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Right: Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.extraLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "검색 결과가 없습니다",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "다른 검색어를 입력해보세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorStateCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.extraLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = "오류가 발생했습니다",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/home/presentation/screen/HomeScreen.kt
package com.etfmonitor.feature.home.presentation.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.home.domain.model.HomeState
import com.etfmonitor.feature.home.domain.model.HomeSummary
import com.etfmonitor.feature.home.presentation.component.DaysSelectionDialog
import com.etfmonitor.feature.home.presentation.component.UnifiedInitializationDialog
import com.etfmonitor.feature.home.presentation.viewmodel.HomeViewModel

/**
 * Home Screen - Summary Dashboard
 * Shows summary cards for each menu section
 *
 * Menu Structure:
 * - 시장 지표: Fear & Greed, 과매수/과매도, 증시 자금 동향
 * - ETF: ETF 목록, ETF 통계
 * - 종목: 종목 수급 분석
 * - 분석: AI 분석, ML 예측, 고급 분석
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMarketIndicator: () -> Unit,
    onNavigateToEtf: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val showUnifiedInitDialog by viewModel.showUnifiedInitDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastDate = (state as? HomeState.Idle)?.lastDate

    var showDaysDialog by remember { mutableStateOf(false) }
    var showUnifiedDialog by remember { mutableStateOf(false) }

    // Dialog handlers
    LaunchedEffect(showUnifiedInitDialog) {
        if (showUnifiedInitDialog) showUnifiedDialog = true
    }

    LaunchedEffect(showFirstRunDialog) {
        if (showFirstRunDialog) showDaysDialog = true
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is HomeState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            is HomeState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearMessage()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val s = state) {
            is HomeState.Initializing -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            is HomeState.Updating -> {
                LoadingScreen(
                    modifier = Modifier.padding(padding),
                    message = s.message,
                    progress = s.progress
                )
            }
            else -> {
                HomeContent(
                    modifier = Modifier.padding(padding),
                    state = s,
                    lastDate = lastDate,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToMarketIndicator = onNavigateToMarketIndicator,
                    onNavigateToEtf = onNavigateToEtf,
                    onNavigateToStocks = onNavigateToStocks,
                    onNavigateToAnalysis = onNavigateToAnalysis
                )
            }
        }
    }

    // Dialogs
    if (showDaysDialog) {
        DaysSelectionDialog(
            onDismiss = {
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            },
            onConfirm = { days ->
                viewModel.initialize(days)
                showDaysDialog = false
                if (showFirstRunDialog) viewModel.onFirstRunDialogShown()
            }
        )
    }

    if (showUnifiedDialog) {
        UnifiedInitializationDialog(
            onDismiss = {
                showUnifiedDialog = false
                viewModel.onUnifiedInitDialogDismiss()
            },
            onConfirm = { etfDays, depositPages, fearGreedDays, oscillatorDays, marketIndexDays, bloodIndicatorDays ->
                showUnifiedDialog = false
                viewModel.initializeAll(etfDays, depositPages, fearGreedDays, oscillatorDays, marketIndexDays, bloodIndicatorDays)
            }
        )
    }
}

@Composable
private fun LoadingScreen(
    modifier: Modifier = Modifier,
    message: String,
    progress: Int
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(MaterialTheme.spacing.large),
            shape = MaterialTheme.extendedShapes.card,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = MaterialTheme.elevation.level3
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.extendedShapes.circle),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeState,
    lastDate: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMarketIndicator: () -> Unit,
    onNavigateToEtf: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToAnalysis: () -> Unit
) {
    val hasData = (state as? HomeState.Idle)?.hasData ?: false
    val summary = (state as? HomeState.Idle)?.summary
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
    ) {
        // Header with theme toggle
        HomeHeader(
            lastDate = lastDate,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Market Indicator Summary Card
            SummaryCard(
                title = "시장 지표",
                description = "Fear & Greed, 과매수/과매도, 증시 자금",
                icon = Icons.Default.BarChart,
                onClick = onNavigateToMarketIndicator,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                summaryContent = {
                    if (summary != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryItem(
                                label = "F&G",
                                value = summary.kospiFearGreed?.let {
                                    "${((it + 100) / 2).toInt()}"
                                } ?: "—"
                            )
                            SummaryItem(
                                label = "과매수/과매도",
                                value = summary.kospiStatus ?: "—"
                            )
                        }
                    }
                }
            )

            // ETF Summary Card
            SummaryCard(
                title = "ETF",
                description = "테마별 ETF 목록 및 통계",
                icon = Icons.Default.PieChart,
                onClick = onNavigateToEtf,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                summaryContent = {
                    if (hasData) {
                        Text(
                            text = "ETF 데이터 수집 완료",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "데이터를 수집해주세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            )

            // Stocks Summary Card
            SummaryCard(
                title = "종목",
                description = "종목 수급 분석 및 추세 신호",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                onClick = onNavigateToStocks,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                summaryContent = {
                    Text(
                        text = "종목 검색 및 분석",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            )

            // Analysis Summary Card
            SummaryCard(
                title = "분석",
                description = "AI 시장 분석, ML 주가 예측, 고급 분석",
                icon = Icons.Default.Analytics,
                onClick = onNavigateToAnalysis,
                backgroundColor = AIInsightsBackground,
                contentColor = AIInsightsAccent,
                summaryContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AnalysisItem(icon = Icons.Default.AutoAwesome, label = "AI")
                        AnalysisItem(icon = Icons.Default.Psychology, label = "ML")
                        AnalysisItem(icon = Icons.Default.Dashboard, label = "고급")
                    }
                }
            )

            // Bottom padding
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    lastDate: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            lastDate?.let {
                Text(
                    text = stringResource(R.string.home_last_update_short, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Theme toggle
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "라이트 모드" else "다크 모드",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            // Settings
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    summaryContent: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = contentColor.copy(alpha = 0.2f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = contentColor
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary content
            summaryContent()
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AnalysisItem(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AIInsightsAccent,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AIInsightsAccent.copy(alpha = 0.8f)
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/market/presentation/blood/BloodIndicatorScreen.kt
package com.etfmonitor.feature.market.presentation.blood

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.core.ui.component.*
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.model.BloodSignalType
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Blood Indicator Screen - US Market Health Monitor (v2.0)
 *
 * BLOOD = US03MY (3M T-Bill) / BAMLH0A0HYM2 (High Yield Spread from FRED)
 * - Above 100-week SMA = Risk On (Market healthy, Green)
 * - Below 100-week SMA = Risk Off (Market stress, Red)
 *
 * Data Sources:
 * - US03MY: Yahoo Finance (^IRX)
 * - BAMLH0A0HYM2: FRED API (free API key required)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodIndicatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: BloodIndicatorViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BloodIndicatorHeader(onNavigateBack = onNavigateBack)
            BloodIndicatorContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Blood Indicator content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun BloodIndicatorContent(
    viewModel: BloodIndicatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val bloodData by viewModel.bloodData.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }

    // Get chart colors from settings
    val settingsViewModel: com.etfmonitor.feature.settings.presentation.SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    // First run dialog
    if (showFirstRunDialog) {
        BloodInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // Manual dialog
    if (showManualDialog) {
        BloodInitializeDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { days ->
                showManualDialog = false
                viewModel.initialize(days)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is BloodIndicatorState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is BloodIndicatorState.Initializing -> {
                    InitializingCard(
                        message = currentState.message,
                        progress = currentState.progress
                    )
                }
                is BloodIndicatorState.Updating -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(currentState.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is BloodIndicatorState.Success -> {
                    SuccessInfoCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is BloodIndicatorState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is BloodIndicatorState.Idle -> {
                    if (!currentState.hasData) {
                        NoDataCard(onCollectClick = { showManualDialog = true })
                    }
                }
            }

            // Main Content
            if (bloodData.isNotEmpty()) {
                // Data is sorted ASC (oldest first), so last is the most recent
                val latest = bloodData.lastOrNull()
                if (latest != null) {
                    // Current Value Display
                    BloodValueSection(latest = latest)

                    // Components Breakdown
                    ComponentsCard(latest = latest)

                    // Date Range Selector (Blood Indicator specific options)
                    DateRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { viewModel.updateDateRange(it) },
                        availableOptions = listOf(
                            DateRangeOption.SIX_MONTHS,
                            DateRangeOption.YEAR,
                            DateRangeOption.THREE_YEARS,
                            DateRangeOption.FIVE_YEARS,
                            DateRangeOption.SEVEN_YEARS,
                            DateRangeOption.ALL
                        )
                    )

                    // Dual-Axis Chart (BLOOD + SPY)
                    BloodChartSection(
                        data = bloodData,
                        chartColors = chartColorSettings.bloodIndicator
                    )

                    // Explanation
                    ExplanationCard()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BloodIndicatorHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "Blood Indicator",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BloodValueSection(latest: BloodIndicator) {
    val (statusText, statusColor, icon) = when (latest.signalType) {
        BloodSignalType.RISK_ON -> Triple(
            "Risk On - 상승 추세",
            MaterialTheme.extendedColors.chartGreen,
            Icons.Default.TrendingUp
        )
        BloodSignalType.RISK_OFF -> Triple(
            "Risk Off - 하락 추세",
            MaterialTheme.extendedColors.chartRed,
            Icons.Default.TrendingDown
        )
        BloodSignalType.NEUTRAL -> Triple(
            "Neutral - 중립",
            MaterialTheme.colorScheme.onSurface,
            Icons.Default.TrendingFlat
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blood Value
        Text(
            text = String.format("%.4f", latest.bloodValue),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
            color = statusColor
        )

        // Status Chip
        Surface(
            shape = RoundedCornerShape(50),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
        }

        // Date
        Text(
            text = "기준일: ${latest.date}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ComponentsCard(latest: BloodIndicator) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "구성 요소",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            HorizontalDivider()

            ComponentRow("US03MY (3M T-Bill)", "${String.format("%.2f", latest.us03my)}%")
            ComponentRow("High Yield Spread", "${String.format("%.2f", latest.highYieldSpread)}%")

            HorizontalDivider()

            ComponentRow(
                "100주 SMA",
                String.format("%.4f", latest.bloodSma),
                MaterialTheme.colorScheme.primary
            )
            ComponentRow(
                "SMA 대비",
                if (latest.isAboveSma()) "상향 돌파" else "하향 돌파",
                if (latest.isAboveSma()) MaterialTheme.extendedColors.chartGreen
                else MaterialTheme.extendedColors.chartRed
            )

            latest.spyClose?.let { spy ->
                HorizontalDivider()
                ComponentRow("S&P 500", String.format("$%.2f", spy))
            }
        }
    }
}

@Composable
private fun ComponentRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun BloodChartSection(
    data: List<BloodIndicator>,
    chartColors: SingleChartColorSettings
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "BLOOD vs 100주 SMA & S&P 500",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            BloodDualAxisChart(
                data = data,
                chartColors = chartColors,
                modifier = Modifier.fillMaxWidth().height(300.dp)
            )

            // Legend
            BloodChartLegend()
        }
    }
}

@Composable
private fun BloodChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LegendItem(color = Color(0xFFE53935), label = "BLOOD")
        LegendItem(color = Color(0xFF2196F3), label = "100주 SMA", isDashed = true)
        LegendItem(color = Color.Black, label = "S&P 500")
    }
}

@Composable
private fun LegendItem(color: Color, label: String, isDashed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isDashed) {
            // Dashed line indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 3.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 3.dp)
                        .background(color, RoundedCornerShape(1.dp))
                )
            }
        } else {
            // Solid line indicator
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 3.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun BloodDualAxisChart(
    data: List<BloodIndicator>,
    chartColors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bloodColor = chartColors.lineColor1
    val spyColor = chartColors.lineColor2
    val textColor = chartColors.textColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    // 100-week SMA color (Blue)
    val smaColor = Color(0xFF2196F3).toArgb()

    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setLabelCount(8, false)
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(bloodColor)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.2f", value)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(spyColor)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f", value)
                        }
                    }
                }

                legend.apply {
                    isEnabled = false  // Using custom legend
                }
            }
        },
        update = { chart ->
            // Data is already sorted ASC (oldest first) from DAO - no need to reverse
            val chartData = data

            // Blood line (Red)
            val bloodEntries = chartData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.bloodValue.toFloat())
            }
            val bloodDataSet = LineDataSet(bloodEntries, "BLOOD").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = bloodColor
                lineWidth = 2.5f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }

            // 100-week SMA line (Blue, Dashed) - from data, not calculated
            val smaEntries = chartData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.bloodSma.toFloat())
            }
            val smaDataSet = LineDataSet(smaEntries, "100W SMA").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = smaColor
                lineWidth = 1.5f
                setDrawCircles(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                enableDashedLine(10f, 5f, 0f)
            }

            // SPY line (if available) - Black solid line
            val spyEntries = chartData.mapIndexedNotNull { index, item ->
                item.spyClose?.let { Entry(index.toFloat(), it.toFloat()) }
            }
            val spyDataSet = if (spyEntries.isNotEmpty()) {
                LineDataSet(spyEntries, "S&P 500").apply {
                    axisDependency = YAxis.AxisDependency.RIGHT
                    color = Color.Black.toArgb()
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    // Solid line (no dashing)
                }
            } else null

            val dataSets = mutableListOf<LineDataSet>()
            dataSets.add(bloodDataSet)
            dataSets.add(smaDataSet)
            spyDataSet?.let { dataSets.add(it) }

            val lineData = LineData(dataSets as List<LineDataSet>)
            val combinedData = CombinedData().apply { setData(lineData) }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                private var lastDisplayedYearMonth = ""

                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    if (index < 0 || index >= chartData.size) return ""

                    val dateStr = chartData[index].date // "YYYY-MM-DD"
                    val shortYear = dateStr.substring(2, 4) // "YY"
                    val month = dateStr.substring(5, 7) // "MM"
                    val yearMonth = "$shortYear/$month"

                    // 동일한 년/월이 이미 표시되었으면 중복 방지
                    return if (yearMonth != lastDisplayedYearMonth) {
                        lastDisplayedYearMonth = yearMonth
                        yearMonth
                    } else {
                        ""
                    }
                }
            }

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * Calculate Simple Moving Average for a list of values
 * Returns null for indices where MA cannot be calculated (not enough data)
 */
private fun calculateMovingAverage(values: List<Float>, period: Int): List<Float?> {
    if (values.isEmpty()) return emptyList()

    return values.mapIndexed { index, _ ->
        if (index < period - 1) {
            null
        } else {
            val sum = (0 until period).sumOf { values[index - it].toDouble() }
            (sum / period).toFloat()
        }
    }
}

@Composable
private fun ExplanationCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Blood Indicator 해석 (v2.0)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = """
                    • BLOOD = US03MY / High Yield Spread (FRED)
                    • 100주 SMA 상향 돌파 (Risk On): 시장이 건강하고 위험 자산 선호
                    • 100주 SMA 하향 돌파 (Risk Off): 시장 스트레스, 안전 자산 선호
                    • TradingView Pine Script Blood Indicator와 동일한 계산 방식
                    • 데이터 출처: Yahoo Finance (US03MY), FRED API (BAMLH0A0HYM2)
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun InitializingCard(message: String, progress: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(progress = { progress / 100f })
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$progress%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun SuccessInfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extendedColors.successContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSuccessContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ErrorInfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun NoDataCard(onCollectClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Blood Indicator 데이터가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCollectClick, shape = RoundedCornerShape(50)) {
                Text("데이터 수집")
            }
        }
    }
}

@Composable
private fun BloodInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        BloodPeriodOption(365, "1년", "약 52주"),
        BloodPeriodOption(1095, "3년 (권장)", "약 156주, 100주 SMA 표시"),
        BloodPeriodOption(1825, "5년", "약 260주"),
        BloodPeriodOption(2555, "7년", "약 364주"),
        BloodPeriodOption(3650, "10년", "약 520주")
    )
    var selectedDays by remember { mutableStateOf(1095) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Blood Indicator 데이터 수집 (v2.0)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "US 국채(Yahoo Finance) 및 High Yield Spread(FRED API) 데이터를 수집합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDays == option.days,
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "⚠️ FRED API 키가 필요합니다.\n설정 > API 키에서 등록해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에 약 1-2분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) { Text("수집 시작") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        }
    )
}

private data class BloodPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

// FILE: app/src/main/java/com/etfmonitor/feature/market/presentation/deposit/MarketDepositScreen.kt
package com.etfmonitor.feature.market.presentation.deposit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.settings.presentation.SettingsViewModel
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Market Deposit Screen - Fear & Greed 스타일로 재설계
 */
@Composable
fun MarketDepositScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketDepositViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom Header (FearGreedScreen 스타일)
            MarketDepositHeader(onNavigateBack = onNavigateBack)

            // Content
            MarketDepositContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Market Deposit content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun MarketDepositContent(
    viewModel: MarketDepositViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val depositData by viewModel.depositData.collectAsState()

    // Get chart colors from settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is MarketDepositState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MarketDepositState.Success -> {
                    SuccessCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is MarketDepositState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is MarketDepositState.Idle -> {
                    // Auto-load, no action needed
                }
            }

            // Main Content (if data available)
            if (depositData.dates.isNotEmpty()) {
                val lastIdx = depositData.dates.size - 1

                // Summary Section (게이지 대신 요약 정보)
                DepositSummarySection(
                    depositAmount = depositData.depositAmounts[lastIdx],
                    depositChange = depositData.depositChanges[lastIdx],
                    creditAmount = depositData.creditAmounts[lastIdx],
                    creditChange = depositData.creditChanges[lastIdx]
                )

                // Stats Row
                StatsRow(data = depositData)

                // Date Range Selector
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    availableOptions = listOf(
                        DateRangeOption.WEEK,
                        DateRangeOption.MONTH,
                        DateRangeOption.THREE_MONTHS,
                        DateRangeOption.SIX_MONTHS,
                        DateRangeOption.YEAR,
                        DateRangeOption.ALL
                    )
                )

                // Chart
                ChartSection(
                    data = depositData,
                    chartColors = chartColorSettings.marketDeposit
                )

                // Disclaimer
                DisclaimerText()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MarketDepositHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.market_deposit_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DepositSummarySection(
    depositAmount: Double,
    depositChange: Double,
    creditAmount: Double,
    creditChange: Double
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 고객예탁금 메인 표시
        Text(
            text = String.format("%.0f", depositAmount / 10000),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "조원",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 변화량 칩
        val depositChangeColor = if (depositChange > 0)
            MaterialTheme.extendedColors.chartGreen
        else
            MaterialTheme.extendedColors.chartRed

        Surface(
            shape = RoundedCornerShape(50),
            color = depositChangeColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = String.format("전일 대비 %+.0f억원", depositChange),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = depositChangeColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 신용잔고
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "신용잔고:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.0f억원", creditAmount),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            val creditChangeColor = if (creditChange > 0)
                MaterialTheme.extendedColors.chartGreen
            else
                MaterialTheme.extendedColors.chartRed

            Text(
                text = String.format("(%+.0f)", creditChange),
                style = MaterialTheme.typography.bodyMedium,
                color = creditChangeColor
            )
        }
    }
}

@Composable
private fun StatsRow(data: MarketDepositData) {
    val lastIdx = data.dates.size - 1
    val yesterday = if (lastIdx >= 1) data.depositAmounts[lastIdx - 1] else null
    val weekAgo = if (lastIdx >= 5) data.depositAmounts[lastIdx - 5] else null
    val monthAgo = if (lastIdx >= 20) data.depositAmounts[lastIdx - 20] else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatBox(
            label = "어제",
            value = yesterday?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1주일 전",
            value = weekAgo?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1달 전",
            value = monthAgo?.let { String.format("%.0f", it / 10000) } ?: "—",
            unit = "조",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (value != "—") {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartSection(
    data: MarketDepositData,
    chartColors: SingleChartColorSettings
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.market_deposit_chart_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            MarketDepositChartView(
                data = data,
                chartColors = chartColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

@Composable
private fun MarketDepositChartView(
    data: MarketDepositData,
    chartColors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val depositColor = chartColors.lineColor1
    val creditColor = chartColors.lineColor2
    val textColor = chartColors.textColor
    val legendColor = chartColors.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setExtraBottomOffset(10f)  // Extra padding for rotated labels
                setDrawOrder(arrayOf(
                    CombinedChart.DrawOrder.LINE,
                    CombinedChart.DrawOrder.LINE
                ))

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                    // labelCount and valueFormatter are set in update block
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(depositColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f조", value / 10000)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(creditColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f조", value / 10000)
                        }
                    }
                }

                legend.apply {
                    isEnabled = true
                    textSize = 12f
                    setTextColor(legendColor)
                }
            }
        },
        update = { chart ->
            val dataCount = data.dates.size

            // Update x-axis with dynamic label count and smart date formatting
            chart.xAxis.apply {
                setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < data.dates.size) {
                            DateFormatter.formatForChartByDataCount(data.dates[index], dataCount)
                        } else {
                            ""
                        }
                    }
                }
            }

            val depositEntries = data.depositAmounts.mapIndexed { index, value ->
                Entry(index.toFloat(), value.toFloat())
            }
            val depositDataSet = LineDataSet(depositEntries, "고객예탁금").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = depositColor
                lineWidth = 2.5f
                setCircleColor(depositColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = depositColor
            }

            val creditEntries = data.creditAmounts.mapIndexed { index, value ->
                Entry(index.toFloat(), value.toFloat())
            }
            val creditDataSet = LineDataSet(creditEntries, "신용잔고").apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                color = creditColor
                lineWidth = 2.5f
                setCircleColor(creditColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = creditColor
            }

            val lineData = LineData(depositDataSet, creditDataSet)
            val combinedData = CombinedData().apply {
                setData(lineData)
            }

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun DisclaimerText() {
    Text(
        text = "* 증시 자금 동향은 시장 유동성 및 투자심리를 파악하는\n보조 지표로 활용하는 것이 좋습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 8.dp),
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
    )
}

@Composable
private fun SuccessCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extendedColors.successContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSuccessContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ErrorInfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/market/presentation/feargreed/FearGreedScreen.kt
package com.etfmonitor.feature.market.presentation.feargreed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import com.etfmonitor.R
import com.etfmonitor.core.common.util.DateFormatter
import com.etfmonitor.core.ui.component.*
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.theme.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Fear & Greed Screen - Moss Green Nature Theme
 * Modern detail screen design matching the React design guide
 *
 * Layout:
 * - Back arrow header with title
 * - Gauge visual (semi-circle)
 * - Stats row (yesterday, 1 week ago, 1 month ago)
 * - Chart with bars
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FearGreedScreen(
    onNavigateBack: () -> Unit,
    viewModel: FearGreedViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom Header
            FearGreedHeader(onNavigateBack = onNavigateBack)

            // Content
            FearGreedContent(viewModel = viewModel)
        }
    }
}

/**
 * Reusable Fear & Greed content without header
 * Used in standalone screen and hub screen
 */
@Composable
fun FearGreedContent(
    viewModel: FearGreedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val fearGreedData by viewModel.fearGreedData.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    var showManualPeriodDialog by remember { mutableStateOf(false) }

    // Get chart colors from settings
    val settingsViewModel: com.etfmonitor.feature.settings.presentation.SettingsViewModel = hiltViewModel()
    val chartColorSettings by settingsViewModel.chartColorSettings.collectAsState()

    // First run dialog
    if (showFirstRunDialog) {
        FearGreedInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // Manual data collection dialog
    if (showManualPeriodDialog) {
        FearGreedInitializeDialog(
            onDismiss = { showManualPeriodDialog = false },
            onConfirm = { days ->
                showManualPeriodDialog = false
                viewModel.initialize(days)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // State Display
            when (val currentState = state) {
                is FearGreedState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is FearGreedState.Initializing -> {
                    InitializingCard(
                        message = currentState.message,
                        progress = currentState.progress
                    )
                }
                is FearGreedState.Updating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                currentState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is FearGreedState.Success -> {
                    SuccessCard(message = currentState.message)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is FearGreedState.Error -> {
                    ErrorInfoCard(message = currentState.message)
                }
                is FearGreedState.Idle -> {
                    if (!currentState.hasData) {
                        NoDataCard(onCollectClick = { showManualPeriodDialog = true })
                    }
                }
            }

            // Market Selection Chips
            MarketSelectionChips(
                selectedMarket = selectedMarket,
                onMarketSelected = { viewModel.onSelectedMarketChanged(it) }
            )

            // Main Content (if data available)
            if (fearGreedData.isNotEmpty()) {
                val latest = fearGreedData.firstOrNull()
                if (latest != null) {
                    // Gauge Visual
                    FearGreedGaugeSection(
                        value = (latest.fearGreedValue * 100).toFloat(),
                        oscillator = latest.oscillator
                    )

                    // Stats Row
                    StatsRow(data = fearGreedData)

                    // Date Range Selector
                    DateRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { viewModel.updateDateRange(it) },
                        availableOptions = listOf(
                            DateRangeOption.WEEK,
                            DateRangeOption.MONTH,
                            DateRangeOption.THREE_MONTHS,
                            DateRangeOption.SIX_MONTHS,
                            DateRangeOption.YEAR,
                            DateRangeOption.ALL
                        )
                    )

                    // Chart
                    ChartSection(
                        data = fearGreedData,
                        selectedMarket = selectedMarket,
                        chartColors = chartColorSettings.fearGreed
                    )

                    // Disclaimer
                    DisclaimerText()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FearGreedHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.fear_greed_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MarketSelectionChips(
    selectedMarket: String,
    onMarketSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedMarket == "KOSPI",
            onClick = { onMarketSelected("KOSPI") },
            label = { Text("KOSPI") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = selectedMarket == "KOSDAQ",
            onClick = { onMarketSelected("KOSDAQ") },
            label = { Text("KOSDAQ") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@Composable
private fun FearGreedGaugeSection(
    value: Float,
    oscillator: Double
) {
    val (statusText, statusColor) = when {
        value >= 70 -> "Greed (탐욕)" to MaterialTheme.extendedColors.chartGreen
        value <= 30 -> "Fear (공포)" to MaterialTheme.extendedColors.chartRed
        else -> "Neutral (중립)" to MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gauge visual (simplified semi-circle representation)
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(100.dp)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background arc
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            // Filled portion (simplified)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value / 100f).coerceIn(0f, 1f))
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            )
        }

        // Value display
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black
            ),
            color = MaterialTheme.colorScheme.primary
        )

        // Status chip
        Surface(
            shape = RoundedCornerShape(50),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = statusColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Oscillator value
        Text(
            text = "Oscillator: ${String.format("%.3f", oscillator)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (oscillator > 0) MaterialTheme.extendedColors.chartGreen
                    else MaterialTheme.extendedColors.chartRed
        )
    }
}

@Composable
private fun StatsRow(data: List<com.etfmonitor.feature.market.domain.model.FearGreedIndex>) {
    val latest = data.firstOrNull()
    val yesterday = data.getOrNull(1)
    val weekAgo = data.getOrNull(5)
    val monthAgo = data.getOrNull(20)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatBox(
            label = "어제",
            value = yesterday?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1주일 전",
            value = weekAgo?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = "1달 전",
            value = monthAgo?.let { (it.fearGreedValue * 100).toInt().toString() } ?: "—",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChartSection(
    data: List<com.etfmonitor.feature.market.domain.model.FearGreedIndex>,
    selectedMarket: String,
    chartColors: SingleChartColorSettings
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "$selectedMarket vs Index",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            FearGreedChart(
                data = data,
                chartColors = chartColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }
    }
}

@Composable
private fun DisclaimerText() {
    Text(
        text = "* Fear & Greed 지수는 시장의 과열 및 침체 정도를 나타내며,\n투자 판단의 보조 지표로 활용하는 것이 좋습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 8.dp),
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.5f
    )
}

@Composable
private fun InitializingCard(message: String, progress: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SuccessCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.extendedColors.successContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSuccessContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ErrorInfoCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun NoDataCard(onCollectClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.fear_greed_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCollectClick,
                shape = RoundedCornerShape(50)
            ) {
                Text(stringResource(R.string.action_collect_data))
            }
        }
    }
}

@Composable
fun FearGreedChart(
    data: List<com.etfmonitor.feature.market.domain.model.FearGreedIndex>,
    chartColors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val fearGreedColor = chartColors.lineColor1
    val indexColor = chartColors.lineColor2
    val textColor = chartColors.textColor
    val legendColor = chartColors.legendColor
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setExtraBottomOffset(10f)  // Extra padding for rotated labels
                setDrawOrder(arrayOf(
                    CombinedChart.DrawOrder.LINE,
                    CombinedChart.DrawOrder.LINE
                ))

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                    // labelCount and valueFormatter are set in update block
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(fearGreedColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.3f", value)
                        }
                    }
                }

                axisRight.apply {
                    isEnabled = true
                    setDrawGridLines(false)
                    setTextColor(indexColor)
                    setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return String.format("%.0f", value)
                        }
                    }
                }

                legend.apply {
                    isEnabled = true
                    textSize = 12f
                    setTextColor(legendColor)
                }
            }
        },
        update = { chart ->
            // Data comes in descending order (newest first), so we need to reverse for chart
            // Chart should display: oldest on left (index 0) -> newest on right (index n-1)
            val chartData = data.sortedBy { it.date }
            val dataCount = chartData.size

            // Update x-axis with current data
            chart.xAxis.apply {
                setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < chartData.size) {
                            DateFormatter.formatForChartByDataCount(chartData[index].date, dataCount)
                        } else {
                            ""
                        }
                    }
                }
            }

            val oscillatorEntries = chartData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.oscillator.toFloat())
            }
            val oscillatorDataSet = LineDataSet(oscillatorEntries, "Oscillator").apply {
                axisDependency = YAxis.AxisDependency.LEFT
                color = fearGreedColor
                lineWidth = 2.5f
                setCircleColor(fearGreedColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = fearGreedColor
            }

            val indexEntries = chartData.mapIndexed { index, item ->
                Entry(index.toFloat(), item.indexValue.toFloat())
            }
            val indexDataSet = LineDataSet(indexEntries, "${chartData.lastOrNull()?.market ?: ""} 지수").apply {
                axisDependency = YAxis.AxisDependency.RIGHT
                color = indexColor
                lineWidth = 2.5f
                setCircleColor(indexColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = indexColor
            }

            val lineData = LineData(oscillatorDataSet, indexDataSet)
            val combinedData = CombinedData().apply {
                setData(lineData)
            }

            chart.data = combinedData
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun FearGreedInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        FearGreedPeriodOption(180, "6개월", "약 180일"),
        FearGreedPeriodOption(365, "12개월 (권장)", "약 365일"),
        FearGreedPeriodOption(540, "18개월", "약 540일"),
        FearGreedPeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.fear_greed_init_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.fear_greed_init_desc),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        stringResource(R.string.dialog_fear_greed_time_estimate),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        }
    )
}

private data class FearGreedPeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

// FILE: app/src/main/java/com/etfmonitor/feature/market/presentation/hub/MarketIndicatorHubScreen.kt
package com.etfmonitor.feature.market.presentation.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.core.ui.component.TabNavigationBar
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.feature.market.presentation.feargreed.FearGreedContent
import com.etfmonitor.feature.market.presentation.feargreed.FearGreedViewModel
import com.etfmonitor.feature.market.presentation.oscillator.MarketOscillatorViewModel
import com.etfmonitor.feature.market.presentation.oscillator.MarketOscillatorState
import com.etfmonitor.feature.market.presentation.deposit.MarketDepositViewModel
import com.etfmonitor.feature.market.presentation.deposit.MarketDepositContent
import com.etfmonitor.feature.market.presentation.blood.BloodIndicatorViewModel
import com.etfmonitor.feature.market.presentation.blood.BloodIndicatorContent
import kotlinx.coroutines.launch

/**
 * Market Indicator Hub Screen - 시장 지표
 *
 * Consolidates:
 * - Fear & Greed Index
 * - 시장 과매수/과매도
 * - 증시 자금 동향
 * - Blood Indicator (US Treasury-based market health)
 */

private val MARKET_INDICATOR_TABS = listOf("Fear & Greed", "과매수/과매도", "자금 동향", "Blood")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketIndicatorHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    fearGreedViewModel: FearGreedViewModel = hiltViewModel(),
    marketOscillatorViewModel: MarketOscillatorViewModel = hiltViewModel(),
    marketDepositViewModel: MarketDepositViewModel = hiltViewModel(),
    bloodIndicatorViewModel: BloodIndicatorViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { MARKET_INDICATOR_TABS.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "시장 지표",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // Tab Navigation
        TabNavigationBar(
            tabs = MARKET_INDICATOR_TABS,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            }
        )

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> FearGreedContent(viewModel = fearGreedViewModel)
                1 -> MarketOscillatorHubContent(viewModel = marketOscillatorViewModel)
                2 -> MarketDepositContent(viewModel = marketDepositViewModel)
                3 -> BloodIndicatorContent(viewModel = bloodIndicatorViewModel)
            }
        }
    }
}

@Composable
private fun MarketOscillatorHubContent(
    viewModel: MarketOscillatorViewModel
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val marketData by viewModel.marketData.collectAsState()
    val overboughtThreshold by viewModel.overboughtThreshold.collectAsState()
    val oversoldThreshold by viewModel.oversoldThreshold.collectAsState()
    val bodyScale by viewModel.bodyScale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Market selection chips
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.market_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMarket == "KOSPI",
                        onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                        label = { Text(stringResource(R.string.market_kospi)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMarket == "KOSDAQ",
                        onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                        label = { Text(stringResource(R.string.market_kosdaq)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // State handling
        when (val currentState = state) {
            is MarketOscillatorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MarketOscillatorState.Initializing -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(currentState.message)
                        Text(
                            stringResource(R.string.progress_percent, currentState.progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            is MarketOscillatorState.Updating -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentState.message)
                    }
                }
            }
            is MarketOscillatorState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            is MarketOscillatorState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is MarketOscillatorState.Idle -> {
                if (!currentState.hasData) {
                    NoDataCard(message = stringResource(R.string.market_oscillator_no_data))
                }
            }
        }

        // Show latest data and table if available
        if (marketData.isNotEmpty()) {
            val latest = marketData.firstOrNull()
            if (latest != null) {
                OscillatorLatestDataCard(
                    latest = latest,
                    overboughtThreshold = overboughtThreshold,
                    oversoldThreshold = oversoldThreshold
                )
            }

            // Data Table
            OscillatorDataTable(
                data = marketData,
                overboughtThreshold = overboughtThreshold,
                oversoldThreshold = oversoldThreshold,
                bodyScale = bodyScale
            )
        }
    }
}

@Composable
private fun NoDataCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OscillatorLatestDataCard(
    latest: MarketOscillator,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    val cardBackground = Color(0xFFFFFBFE)
    val textColor = Color(0xFF1C1B1F)
    val dividerColor = Color(0xFFCAC4D0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "최신 데이터 (${latest.date})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            HorizontalDivider(color = dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("지수", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f", latest.indexValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Oscillator", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f%%", latest.oscillator),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> textColor
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("상태", style = MaterialTheme.typography.bodyMedium, color = textColor)
                val status = when {
                    latest.oscillator >= overboughtThreshold -> "과매수"
                    latest.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    latest.oscillator >= overboughtThreshold -> Color.Red
                    latest.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun OscillatorDataTable(
    data: List<MarketOscillator>,
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    bodyScale: Float
) {
    val cardBackground = Color(0xFFFFFBFE)
    val textColor = Color(0xFF1C1B1F)
    val secondaryTextColor = Color(0xFF49454F)
    val headerBackground = Color(0xFFE7E0EC)
    val dividerColor = Color(0xFFCAC4D0)

    val dateFontSize = (11 * bodyScale).sp
    val valueFontSize = (11 * bodyScale).sp
    val statusFontSize = (10 * bodyScale).sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "과매수/과매도 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Text(
                "표시 기간: 최근 ${data.size}일",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            HorizontalDivider(color = dividerColor)

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "날짜",
                    modifier = Modifier.weight(0.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                Text(
                    "지수",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "Oscillator",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "상태",
                    modifier = Modifier.weight(0.25f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
            }

            // Table Rows
            data.forEach { item ->
                val status = when {
                    item.oscillator >= overboughtThreshold -> "과매수"
                    item.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    item.oscillator >= overboughtThreshold -> Color.Red
                    item.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.date,
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = dateFontSize,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                    Text(
                        String.format("%.0f", item.indexValue),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        textAlign = TextAlign.End,
                        color = textColor
                    )
                    Text(
                        String.format("%.1f%%", item.oscillator),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        fontWeight = if (status != "중립") FontWeight.Bold else FontWeight.Normal,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                    Text(
                        status,
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }

                if (item != data.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                }
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/market/presentation/oscillator/MarketOscillatorScreen.kt
package com.etfmonitor.feature.market.presentation.oscillator

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import com.etfmonitor.core.ui.component.LoadingCard
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.core.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketOscillatorScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketOscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val marketData by viewModel.marketData.collectAsState()
    val overboughtThreshold by viewModel.overboughtThreshold.collectAsState()
    val oversoldThreshold by viewModel.oversoldThreshold.collectAsState()
    val showFirstRunDialog by viewModel.showFirstRunDialog.collectAsState()
    val bodyScale by viewModel.bodyScale.collectAsState()

    var showManualPeriodDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 첫 실행 다이얼로그
    if (showFirstRunDialog) {
        MarketOscillatorInitializeDialog(
            onDismiss = { viewModel.onFirstRunDialogShown() },
            onConfirm = { days ->
                viewModel.onFirstRunDialogConfirmed()
                viewModel.initialize(days)
            }
        )
    }

    // 수동 데이터 수집 다이얼로그
    if (showManualPeriodDialog) {
        MarketOscillatorInitializeDialog(
            onDismiss = { showManualPeriodDialog = false },
            onConfirm = { days ->
                showManualPeriodDialog = false
                viewModel.initialize(days)
            }
        )
    }

    // 설정 다이얼로그
    if (showSettingsDialog) {
        ThresholdSettingsDialog(
            overboughtThreshold = overboughtThreshold,
            oversoldThreshold = oversoldThreshold,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { overbought, oversold ->
                viewModel.onOverboughtThresholdChanged(overbought)
                viewModel.onOversoldThresholdChanged(oversold)
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_oscillator_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
                    }
                    val currentState = state
                    if (currentState is MarketOscillatorState.Idle && currentState.hasData) {
                        IconButton(onClick = { viewModel.update() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.nav_update))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Market Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.market_select),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMarket == "KOSPI",
                            onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                            label = { Text(stringResource(R.string.market_kospi)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMarket == "KOSDAQ",
                            onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                            label = { Text(stringResource(R.string.market_kosdaq)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Date Range Selector
            DateRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.updateDateRange(it) },
                availableOptions = listOf(
                    DateRangeOption.WEEK,
                    DateRangeOption.MONTH,
                    DateRangeOption.THREE_MONTHS,
                    DateRangeOption.SIX_MONTHS,
                    DateRangeOption.YEAR,
                    DateRangeOption.ALL
                )
            )

            // State Display
            when (val currentState = state) {
                is MarketOscillatorState.Loading -> LoadingCard(stringResource(R.string.data_loading))
                is MarketOscillatorState.Initializing -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Text(currentState.message)
                            Text(stringResource(R.string.progress_percent, currentState.progress), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is MarketOscillatorState.Updating -> LoadingCard(currentState.message)
                is MarketOscillatorState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            currentState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.clearMessage()
                    }
                }
                is MarketOscillatorState.Error -> ErrorCard(currentState.message)
                is MarketOscillatorState.Idle -> {
                    if (!currentState.hasData) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.market_oscillator_no_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showManualPeriodDialog = true }) {
                                    Text(stringResource(R.string.action_collect_data))
                                }
                            }
                        }
                    }
                }
            }

            // Latest Data Card
            if (marketData.isNotEmpty()) {
                val latest = marketData.firstOrNull()
                if (latest != null) {
                    LatestDataCard(
                        latest = latest,
                        overboughtThreshold = overboughtThreshold,
                        oversoldThreshold = oversoldThreshold
                    )
                }
            }

            // Data Table
            if (marketData.isNotEmpty()) {
                DataTable(
                    data = marketData,
                    overboughtThreshold = overboughtThreshold,
                    oversoldThreshold = oversoldThreshold,
                    bodyScale = bodyScale
                )
            }
        }
    }
}

@Composable
private fun LatestDataCard(
    latest: MarketOscillator,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    // 라이트 모드 색상 강제 적용
    val cardBackground = Color(0xFFFFFBFE) // Surface light
    val textColor = Color(0xFF1C1B1F) // OnSurface light
    val dividerColor = Color(0xFFCAC4D0) // OutlineVariant light

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "최신 데이터 (${latest.date})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            HorizontalDivider(color = dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("지수", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f", latest.indexValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Oscillator", style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    String.format("%.2f%%", latest.oscillator),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> textColor
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("상태", style = MaterialTheme.typography.bodyMedium, color = textColor)
                val status = when {
                    latest.oscillator >= overboughtThreshold -> "과매수"
                    latest.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    latest.oscillator >= overboughtThreshold -> Color.Red
                    latest.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun DataTable(
    data: List<MarketOscillator>,
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    bodyScale: Float
) {
    // 라이트 모드 색상 강제 적용
    val cardBackground = Color(0xFFFFFBFE) // Surface light
    val textColor = Color(0xFF1C1B1F) // OnSurface light
    val secondaryTextColor = Color(0xFF49454F) // OnSurfaceVariant light
    val headerBackground = Color(0xFFE7E0EC) // SurfaceVariant light
    val dividerColor = Color(0xFFCAC4D0) // OutlineVariant light

    // 스케일이 적용된 폰트 크기
    val dateFontSize = (11 * bodyScale).sp
    val valueFontSize = (11 * bodyScale).sp
    val statusFontSize = (10 * bodyScale).sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "과매수/과매도 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Text(
                "표시 기간: 최근 ${data.size}일",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )

            HorizontalDivider(color = dividerColor)

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "날짜",
                    modifier = Modifier.weight(0.4f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
                Text(
                    "지수",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "Oscillator",
                    modifier = Modifier.weight(0.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = textColor
                )
                Text(
                    "상태",
                    modifier = Modifier.weight(0.25f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor
                )
            }

            // Table Rows
            data.forEach { item ->
                val status = when {
                    item.oscillator >= overboughtThreshold -> "과매수"
                    item.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    item.oscillator >= overboughtThreshold -> Color.Red
                    item.oscillator <= oversoldThreshold -> Color.Blue
                    else -> textColor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.date,
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = dateFontSize,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                    Text(
                        String.format("%.0f", item.indexValue),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        textAlign = TextAlign.End,
                        color = textColor
                    )
                    Text(
                        String.format("%.1f%%", item.oscillator),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = valueFontSize,
                        fontWeight = if (status != "중립") FontWeight.Bold else FontWeight.Normal,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                    Text(
                        status,
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }

                if (item != data.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                }
            }
        }
    }
}

@Composable
private fun MarketOscillatorInitializeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(180, "6개월", "약 180일"),
        PeriodOption(365, "12개월 (권장)", "약 365일"),
        PeriodOption(540, "18개월", "약 540일"),
        PeriodOption(730, "24개월", "약 730일")
    )

    var selectedDays by remember { mutableStateOf(365) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시장 과매수/과매도 초기화") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "코스피/코스닥 데이터 수집 기간을 선택하세요.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedDays == option.days),
                                onClick = { selectedDays = option.days }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "데이터 수집에는 선택한 기간에 따라 2-5분 정도 소요됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays) }) {
                Text("수집 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        }
    )
}

@Composable
private fun ThresholdSettingsDialog(
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var overbought by remember { mutableStateOf(overboughtThreshold.toString()) }
    var oversold by remember { mutableStateOf(oversoldThreshold.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("임계값 설정") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overbought Threshold
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매수 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = overbought,
                        onValueChange = { overbought = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "예: 80",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                // Oversold Threshold
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매도 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = oversold,
                        onValueChange = { oversold = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "예: -80",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "• 과매수: Oscillator가 설정값 이상\n• 과매도: Oscillator가 설정값 이하",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val overboughtVal = overbought.toDoubleOrNull() ?: overboughtThreshold
                val oversoldVal = oversold.toDoubleOrNull() ?: oversoldThreshold
                onConfirm(overboughtVal, oversoldVal)
            }) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private data class PeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/SettingsScreen.kt
package com.etfmonitor.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.feature.backup.presentation.screen.BackupTabContent
import com.etfmonitor.feature.backup.presentation.viewmodel.BackupViewModel
import com.etfmonitor.feature.settings.presentation.component.*

/**
 * Settings Screen - Main Entry Point
 * Provides comprehensive app configuration across multiple tabs:
 * - 일반 (General): Theme, AI API keys, Font settings
 * - 키워드 (Keywords): Include/Exclude keywords for ETF filtering
 * - 데이터 업데이트 (Data Update): Auto-update schedules, manual update controls
 * - 수집 기간 (Data Period): Default collection days, Fear & Greed period, etc.
 * - 차트 (Chart): Chart color customization
 *
 * Component files in settings/components/:
 * - GeneralCards.kt: ThemeSettingCard, AIApiKeyCard, FontScaleCard
 * - KeywordCards.kt: ThemeCard, ExclusionCard
 * - DataCards.kt: DataManagementCard, DefaultDaysCard, SearchHistoryLimitCard, DatabaseCard
 * - PeriodCards.kt: FearGreedPeriodCard, MarketOscillatorPeriodCard
 * - UpdateCards.kt: StockUpdateCard, MarketDepositUpdateCard, FearGreedUpdateCard, etc.
 * - ChartColorCards.kt: MarketCapOscillatorColorCard, MacdColorCard, etc.
 * - ColorPickerComponents.kt: Color picker UI components
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val themes by viewModel.themes.collectAsState()
    val exclusions by viewModel.exclusions.collectAsState()
    val defaultDays by viewModel.defaultDays.collectAsState()
    val searchHistoryLimit by viewModel.searchHistoryLimit.collectAsState()
    val fearGreedPeriodDays by viewModel.fearGreedPeriodDays.collectAsState()
    val marketOscillatorPeriodDays by viewModel.marketOscillatorPeriodDays.collectAsState()
    val stockUpdateSettings by viewModel.stockUpdateSettings.collectAsState()
    val marketDepositUpdateSettings by viewModel.marketDepositUpdateSettings.collectAsState()
    val fearGreedUpdateSettings by viewModel.fearGreedUpdateSettings.collectAsState()
    val marketOscillatorUpdateSettings by viewModel.marketOscillatorUpdateSettings.collectAsState()
    val marketIndexUpdateSettings by viewModel.marketIndexUpdateSettings.collectAsState()
    val etfUpdateSettings by viewModel.etfUpdateSettings.collectAsState()
    val bloodIndicatorUpdateSettings by viewModel.bloodIndicatorUpdateSettings.collectAsState()
    val marketIndexPeriodDays by viewModel.marketIndexPeriodDays.collectAsState()
    val bloodIndicatorPeriodDays by viewModel.bloodIndicatorPeriodDays.collectAsState()
    val message by viewModel.message.collectAsState()

    // General settings
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val fontScaleSettings by viewModel.fontScaleSettings.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()

    // Chart color settings
    val chartColorSettings by viewModel.chartColorSettings.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.settings_tab_general),
        stringResource(R.string.settings_tab_keyword),
        stringResource(R.string.settings_tab_data_update),
        stringResource(R.string.settings_tab_period),
        stringResource(R.string.settings_tab_chart),
        stringResource(R.string.settings_tab_backup)
    )

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings_tab))
                                1 -> Icon(Icons.Default.Label, contentDescription = stringResource(R.string.cd_keyword_tab))
                                2 -> Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.cd_download_tab))
                                3 -> Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.cd_period_tab))
                                4 -> Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.cd_palette_tab))
                                5 -> Icon(Icons.Default.Backup, contentDescription = stringResource(R.string.cd_backup_tab))
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> GeneralTab(
                    isDarkTheme = isDarkTheme,
                    fontScaleSettings = fontScaleSettings,
                    quickChartAnalysisEnabled = quickChartAnalysisEnabled,
                    viewModel = viewModel
                )
                1 -> KeywordTab(
                    themes = themes,
                    exclusions = exclusions,
                    viewModel = viewModel
                )
                2 -> DataUpdateTab(
                    stockUpdateSettings = stockUpdateSettings,
                    marketDepositUpdateSettings = marketDepositUpdateSettings,
                    fearGreedUpdateSettings = fearGreedUpdateSettings,
                    marketOscillatorUpdateSettings = marketOscillatorUpdateSettings,
                    marketIndexUpdateSettings = marketIndexUpdateSettings,
                    etfUpdateSettings = etfUpdateSettings,
                    bloodIndicatorUpdateSettings = bloodIndicatorUpdateSettings,
                    viewModel = viewModel
                )
                3 -> DataPeriodTab(
                    defaultDays = defaultDays,
                    searchHistoryLimit = searchHistoryLimit,
                    fearGreedPeriodDays = fearGreedPeriodDays,
                    marketOscillatorPeriodDays = marketOscillatorPeriodDays,
                    marketIndexPeriodDays = marketIndexPeriodDays,
                    bloodIndicatorPeriodDays = bloodIndicatorPeriodDays,
                    viewModel = viewModel
                )
                4 -> ChartTab(
                    chartColorSettings = chartColorSettings,
                    viewModel = viewModel
                )
                5 -> BackupTab(
                    backupViewModel = backupViewModel
                )
            }
        }
    }
}

// ==================== General Tab ====================
@Composable
private fun GeneralTab(
    isDarkTheme: Boolean?,
    fontScaleSettings: com.etfmonitor.core.ui.theme.FontScaleSettings,
    quickChartAnalysisEnabled: Boolean,
    viewModel: SettingsViewModel
) {
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isClaudeConfigured by viewModel.isClaudeApiKeyConfigured.collectAsState()
    val isGeminiConfigured by viewModel.isGeminiApiKeyConfigured.collectAsState()
    val apiKeyTestState by viewModel.apiKeyTestState.collectAsState()

    val claudeModels by viewModel.claudeModels.collectAsState()
    val geminiModels by viewModel.geminiModels.collectAsState()
    val selectedClaudeModel by viewModel.selectedClaudeModel.collectAsState()
    val selectedGeminiModel by viewModel.selectedGeminiModel.collectAsState()
    val isLoadingClaudeModels by viewModel.isLoadingClaudeModels.collectAsState()
    val isLoadingGeminiModels by viewModel.isLoadingGeminiModels.collectAsState()
    val isFredApiKeyConfigured by viewModel.isFredApiKeyConfigured.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 테마 설정
        item {
            ThemeSettingCard(
                isDarkTheme = isDarkTheme,
                onThemeChange = { viewModel.setDarkTheme(it) }
            )
        }

        // 빠른 차트 분석 설정
        item {
            QuickChartAnalysisCard(
                isEnabled = quickChartAnalysisEnabled,
                onEnabledChange = { viewModel.setQuickChartAnalysisEnabled(it) }
            )
        }

        // AI API 키 설정
        item {
            AIApiKeyCard(
                selectedProvider = selectedProvider,
                isClaudeConfigured = isClaudeConfigured,
                isGeminiConfigured = isGeminiConfigured,
                testState = apiKeyTestState,
                claudeModels = claudeModels,
                geminiModels = geminiModels,
                selectedClaudeModel = selectedClaudeModel,
                selectedGeminiModel = selectedGeminiModel,
                isLoadingClaudeModels = isLoadingClaudeModels,
                isLoadingGeminiModels = isLoadingGeminiModels,
                onProviderSelected = { viewModel.setSelectedProvider(it) },
                onSetClaudeApiKey = { viewModel.setClaudeApiKey(it) },
                onSetGeminiApiKey = { viewModel.setGeminiApiKey(it) },
                onClearClaudeApiKey = { viewModel.clearClaudeApiKey() },
                onClearGeminiApiKey = { viewModel.clearGeminiApiKey() },
                onTestConnection = { viewModel.testApiConnection() },
                onClearTestState = { viewModel.clearApiTestState() },
                onLoadClaudeModels = { viewModel.loadClaudeModels() },
                onLoadGeminiModels = { viewModel.loadGeminiModels() },
                onSelectClaudeModel = { viewModel.setClaudeModel(it) },
                onSelectGeminiModel = { viewModel.setGeminiModel(it) }
            )
        }

        // FRED API 키 설정 (Blood Indicator 용)
        item {
            FredApiKeyCard(
                isConfigured = isFredApiKeyConfigured,
                onSetApiKey = { viewModel.setFredApiKey(it) },
                onClearApiKey = { viewModel.clearFredApiKey() }
            )
        }

        // 폰트 크기 설정
        item {
            FontScaleCard(
                fontScaleSettings = fontScaleSettings,
                onDisplayScaleChange = { viewModel.setDisplayScale(it) },
                onHeadlineScaleChange = { viewModel.setHeadlineScale(it) },
                onTitleScaleChange = { viewModel.setTitleScale(it) },
                onBodyScaleChange = { viewModel.setBodyScale(it) },
                onLabelScaleChange = { viewModel.setLabelScale(it) }
            )
        }
    }
}

// ==================== Backup Tab ====================
@Composable
private fun BackupTab(
    backupViewModel: BackupViewModel
) {
    BackupTabContent(viewModel = backupViewModel)
}

// ==================== Keyword Tab ====================
@Composable
private fun KeywordTab(
    themes: List<String>,
    exclusions: List<String>,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 포함 테마 설정
        item {
            ThemeCard(
                themes = themes,
                onAddTheme = { viewModel.addTheme(it) },
                onRemoveTheme = { viewModel.removeTheme(it) }
            )
        }

        // 제외 키워드 설정
        item {
            ExclusionCard(
                exclusions = exclusions,
                onAddExclusion = { viewModel.addExclusion(it) },
                onRemoveExclusion = { viewModel.removeExclusion(it) }
            )
        }
    }
}

// ==================== Data Update Tab ====================
@Composable
private fun DataUpdateTab(
    stockUpdateSettings: StockUpdateSettings,
    marketDepositUpdateSettings: MarketDepositUpdateSettings,
    fearGreedUpdateSettings: FearGreedUpdateSettings,
    marketOscillatorUpdateSettings: MarketOscillatorUpdateSettings,
    marketIndexUpdateSettings: MarketIndexUpdateSettings,
    etfUpdateSettings: EtfUpdateSettings,
    bloodIndicatorUpdateSettings: BloodIndicatorUpdateSettings,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ETF 데이터 자동 업데이트 설정
        // 참고: ETF 데이터 초기화는 하단의 DatabaseCard에서 지원됨
        item {
            EtfDataManagementCard(
                settings = etfUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setEtfUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateEtfNow() }
            )
        }

        // 종목 DB 자동 업데이트 설정
        item {
            StockUpdateCard(
                settings = stockUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateStocksNow() }
            )
        }

        // 증시 자금 DB 자동 업데이트 설정
        item {
            MarketDepositUpdateCard(
                settings = marketDepositUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketDepositUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketDepositsNow() }
            )
        }

        // Fear & Greed Index DB 자동 업데이트 설정
        item {
            FearGreedUpdateCard(
                settings = fearGreedUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setFearGreedUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateFearGreedNow() }
            )
        }

        // 과매수/과매도 DB 자동 업데이트 설정
        item {
            MarketOscillatorUpdateCard(
                settings = marketOscillatorUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketOscillatorUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketOscillatorsNow() }
            )
        }

        // 시장 지수 DB 자동 업데이트 설정
        item {
            MarketIndexUpdateCard(
                settings = marketIndexUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setMarketIndexUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateMarketIndexNow() }
            )
        }

        // Blood Indicator DB 자동 업데이트 설정
        item {
            BloodIndicatorUpdateCard(
                settings = bloodIndicatorUpdateSettings,
                onTimeChange = { hour, minute -> viewModel.setBloodIndicatorUpdateTime(hour, minute) },
                onUpdateNow = { viewModel.updateBloodIndicatorNow() }
            )
        }

        // 데이터베이스 초기화
        item {
            DatabaseCard(
                onReset = { viewModel.resetDatabase() }
            )
        }
    }
}

// ==================== Data Period Tab ====================
@Composable
private fun DataPeriodTab(
    defaultDays: Int,
    searchHistoryLimit: Int,
    fearGreedPeriodDays: Int,
    marketOscillatorPeriodDays: Int,
    marketIndexPeriodDays: Int,
    bloodIndicatorPeriodDays: Int,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ETF 수집 기간 설정
        item {
            DefaultDaysCard(
                currentDays = defaultDays,
                onDaysChange = { days, reinitialize -> viewModel.setDefaultDays(days, reinitialize) }
            )
        }

        // Fear & Greed Index 데이터 수집 기간 설정
        item {
            FearGreedPeriodCard(
                currentDays = fearGreedPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setFearGreedPeriodDays(days, reinitialize) }
            )
        }

        // 과매수/과매도 데이터 수집 기간 설정
        item {
            MarketOscillatorPeriodCard(
                currentDays = marketOscillatorPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setMarketOscillatorPeriodDays(days, reinitialize) }
            )
        }

        // 시장 지수 데이터 수집 기간 설정
        item {
            MarketIndexPeriodCard(
                currentDays = marketIndexPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setMarketIndexPeriodDays(days, reinitialize) }
            )
        }

        // Blood Indicator 데이터 수집 기간 설정
        item {
            BloodIndicatorPeriodCard(
                currentDays = bloodIndicatorPeriodDays,
                onDaysChange = { days, reinitialize -> viewModel.setBloodIndicatorPeriodDays(days, reinitialize) }
            )
        }

        // 검색 히스토리 개수 설정
        item {
            SearchHistoryLimitCard(
                currentLimit = searchHistoryLimit,
                onLimitChange = { viewModel.setSearchHistoryLimit(it) }
            )
        }
    }
}

// ==================== Chart Tab ====================
@Composable
private fun ChartTab(
    chartColorSettings: com.etfmonitor.core.ui.theme.ChartColorSettings,
    viewModel: SettingsViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 시가총액 & 오실레이터 차트 색상
        item {
            MarketCapOscillatorColorCard(
                colors = chartColorSettings.marketCapOscillator,
                onLineColor1Changed = { viewModel.setMarketCapOscillatorLineColor1(it) },
                onLineColor2Changed = { viewModel.setMarketCapOscillatorLineColor2(it) },
                onTextColorChanged = { viewModel.setMarketCapOscillatorTextColor(it) },
                onTextColorReset = { viewModel.setMarketCapOscillatorTextColor(null) },
                onLegendColorChanged = { viewModel.setMarketCapOscillatorLegendColor(it) },
                onLegendColorReset = { viewModel.setMarketCapOscillatorLegendColor(null) }
            )
        }

        // MACD 차트 색상
        item {
            MacdColorCard(
                colors = chartColorSettings.macd,
                onLineColor1Changed = { viewModel.setMacdLineColor1(it) },
                onLineColor2Changed = { viewModel.setMacdLineColor2(it) },
                onPositiveColorChanged = { viewModel.setMacdPositiveColor(it) },
                onNegativeColorChanged = { viewModel.setMacdNegativeColor(it) },
                onTextColorChanged = { viewModel.setMacdTextColor(it) },
                onTextColorReset = { viewModel.setMacdTextColor(null) },
                onLegendColorChanged = { viewModel.setMacdLegendColor(it) },
                onLegendColorReset = { viewModel.setMacdLegendColor(null) }
            )
        }

        // 증시 자금 동향 차트 색상
        item {
            MarketDepositColorCard(
                colors = chartColorSettings.marketDeposit,
                onLineColor1Changed = { viewModel.setMarketDepositLineColor1(it) },
                onLineColor2Changed = { viewModel.setMarketDepositLineColor2(it) },
                onTextColorChanged = { viewModel.setMarketDepositTextColor(it) },
                onTextColorReset = { viewModel.setMarketDepositTextColor(null) },
                onLegendColorChanged = { viewModel.setMarketDepositLegendColor(it) },
                onLegendColorReset = { viewModel.setMarketDepositLegendColor(null) }
            )
        }

        // Fear & Greed Index 차트 색상
        item {
            FearGreedColorCard(
                colors = chartColorSettings.fearGreed,
                onLineColor1Changed = { viewModel.setFearGreedLineColor1(it) },
                onLineColor2Changed = { viewModel.setFearGreedLineColor2(it) },
                onTextColorChanged = { viewModel.setFearGreedTextColor(it) },
                onTextColorReset = { viewModel.setFearGreedTextColor(null) },
                onLegendColorChanged = { viewModel.setFearGreedLegendColor(it) },
                onLegendColorReset = { viewModel.setFearGreedLegendColor(null) }
            )
        }

        // 초기화 버튼
        item {
            ResetChartColorsCard(
                onReset = { viewModel.resetChartColors() }
            )
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/SettingsScreens.kt
package com.etfmonitor.feature.settings.presentation

/**
 * Settings Feature Screen Routes
 *
 * Navigation routes for settings screens.
 * The actual screens remain in ui/screens/settings/ for now,
 * following the gradual migration approach from Phase 6.
 */
object SettingsScreens {
    const val SETTINGS = "settings"
}

// FILE: app/src/main/java/com/etfmonitor/feature/stock/presentation/hub/StocksHubScreen.kt
package com.etfmonitor.feature.stock.presentation.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.analysis.model.FearGreedState
import com.etfmonitor.core.analysis.model.TrendSignalAnalysis
import com.etfmonitor.core.analysis.model.TrendTradeSignal
import com.etfmonitor.core.ui.component.MarketCapOscillatorChart
import com.etfmonitor.core.ui.component.MacdChart
import com.etfmonitor.core.ui.component.TrendSignalChart
import com.etfmonitor.core.ui.component.ElderImpulseChart
import com.etfmonitor.core.ui.component.DemarkTDChart
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.database.entities.SearchHistory
import com.etfmonitor.feature.stock.presentation.oscillator.OscillatorViewModel
import com.etfmonitor.feature.stock.presentation.oscillator.OscillatorState
import com.etfmonitor.core.ui.component.HubHeader
import com.etfmonitor.core.ui.component.StockSearchItem
import com.etfmonitor.core.ui.component.UnifiedStockSearchField

/**
 * Stocks Hub Screen - 종목
 *
 * Renamed from 종목 수급 분석 to 종목
 * Shows stock supply/demand analysis (Oscillator)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksHubScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: (String) -> Unit,
    initialTicker: String? = null,
    viewModel: OscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val demarkTDInterval by viewModel.demarkTDInterval.collectAsState()
    val trendSignalInterval by viewModel.trendSignalInterval.collectAsState()
    val elderImpulseInterval by viewModel.elderImpulseInterval.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val currentTicker by viewModel.currentTicker.collectAsState()

    // Set initial ticker if provided (skip history save when navigating via FAB)
    LaunchedEffect(initialTicker) {
        initialTicker?.let { ticker ->
            viewModel.analyzeStock(ticker, saveHistory = false)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        HubHeader(
            title = "종목",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSettingsClick = onNavigateToSettings
        )

        // 통합 검색 필드
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            UnifiedStockSearchField(
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    viewModel.onSearchQueryChanged(query)
                },
                searchResults = suggestions.map { stock ->
                    StockSearchItem(
                        ticker = stock.ticker,
                        name = stock.name,
                        market = stock.market
                    )
                },
                searchHistory = searchHistory,
                isSearching = false,
                placeholder = stringResource(R.string.search_hint),
                onSelectStock = { ticker, _ ->
                    viewModel.onClearSuggestions()
                    viewModel.analyzeStock(ticker)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 기간 선택 (분석된 종목이 있을 때 표시)
        if (currentTicker != null) {
            DateRangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.updateDateRange(it) },
                availableOptions = listOf(
                    DateRangeOption.WEEK,
                    DateRangeOption.MONTH,
                    DateRangeOption.THREE_MONTHS,
                    DateRangeOption.SIX_MONTHS,
                    DateRangeOption.YEAR,
                    DateRangeOption.ALL
                )
            )
        }

        // Content
        when (val currentState = state) {
            is OscillatorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.data_analyzing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is OscillatorState.Success -> {
                // Build chart pages
                val chartPages = buildStockChartPages(
                    currentState = currentState,
                    demarkTDInterval = demarkTDInterval,
                    onDemarkIntervalChange = { viewModel.changeDemarkTDInterval(it) },
                    trendSignalInterval = trendSignalInterval,
                    onTrendSignalIntervalChange = { viewModel.changeTrendSignalInterval(it) },
                    elderImpulseInterval = elderImpulseInterval,
                    onElderImpulseIntervalChange = { viewModel.changeElderImpulseInterval(it) }
                )

                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { chartPages.size }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Stock Info Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        currentState.stockData.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        currentState.stockData.ticker,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (currentState.oscillatorResult.dates.isNotEmpty()) {
                                        Text(
                                            currentState.oscillatorResult.dates.last(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${currentState.oscillatorResult.dates.size}개 데이터",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Page Indicators + Chart Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chartPages.getOrNull(pagerState.currentPage)?.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Page Indicators
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                chartPages.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                                            .background(
                                                color = if (index == pagerState.currentPage)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Pager for Charts
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                chartPages[page].content()
                            }
                        }

                        // Swipe hint
                        Text(
                            text = stringResource(R.string.oscillator_swipe_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    // Floating Action Button
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToStatistics(currentState.stockData.ticker) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            Icon(Icons.Default.Analytics, contentDescription = null)
                        },
                        text = {
                            Text(stringResource(R.string.fab_etf_analysis))
                        }
                    )
                }
            }

            is OscillatorState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            is OscillatorState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (suggestions.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.search_results),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        suggestions.forEach { stock ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.analyzeStock(stock.ticker) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = stock.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = "${stock.ticker} • ${stock.market}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (searchQuery.isEmpty()) {
                        // Empty state - prompt to search
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.oscillator_idle_message),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "수급 분석, 추세 신호, MACD 등을 확인할 수 있습니다",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chart page data class
 */
private data class StockChartPage(
    val title: String,
    val content: @Composable () -> Unit
)

/**
 * Build chart pages list
 */
@Composable
private fun buildStockChartPages(
    currentState: OscillatorState.Success,
    demarkTDInterval: String,
    onDemarkIntervalChange: (String) -> Unit,
    trendSignalInterval: String,
    onTrendSignalIntervalChange: (String) -> Unit,
    elderImpulseInterval: String,
    onElderImpulseIntervalChange: (String) -> Unit
): List<StockChartPage> {
    val pages = mutableListOf<StockChartPage>()

    // 1. 시가총액 & 수급 오실레이터 차트
    pages.add(
        StockChartPage(
            title = stringResource(R.string.oscillator_chart_marketcap)
        ) {
            MarketCapOscillatorChart(
                result = currentState.oscillatorResult,
                marketCap = currentState.stockData.marketCap,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    // 2. DeMark TD 차트
    currentState.demarkTDData?.let { demarkData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_demark)
            ) {
                StockDemarkTDChartWithSelector(
                    data = demarkData,
                    currentInterval = demarkTDInterval,
                    onIntervalChange = onDemarkIntervalChange
                )
            }
        )
    }

    // 3. 추세 시그널 차트 + 분석 카드
    currentState.trendSignalData?.let { trendData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_trend)
            ) {
                StockTrendSignalChartWithSelector(
                    data = trendData,
                    analysis = currentState.trendSignalAnalysis,
                    currentInterval = trendSignalInterval,
                    onIntervalChange = onTrendSignalIntervalChange
                )
            }
        )
    }

    // 4. Elder Impulse 차트
    currentState.elderImpulseData?.let { elderData ->
        pages.add(
            StockChartPage(
                title = stringResource(R.string.oscillator_chart_elder)
            ) {
                StockElderImpulseChartWithSelector(
                    data = elderData,
                    currentInterval = elderImpulseInterval,
                    onIntervalChange = onElderImpulseIntervalChange
                )
            }
        )
    }

    // 5. MACD 차트
    pages.add(
        StockChartPage(
            title = stringResource(R.string.oscillator_chart_macd)
        ) {
            MacdChart(
                result = currentState.oscillatorResult,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    return pages
}

/**
 * DeMark TD Chart with interval selector
 */
@Composable
private fun StockDemarkTDChartWithSelector(
    data: DemarkTDData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Interval selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockIntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_monthly),
                selected = currentInterval == "m",
                onClick = { onIntervalChange("m") },
                modifier = Modifier.weight(1f)
            )
        }

        DemarkTDChart(data = data, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StockIntervalButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

/**
 * Trend Signal Chart with interval selector
 */
@Composable
private fun StockTrendSignalChartWithSelector(
    data: com.etfmonitor.core.analysis.model.TrendSignalData,
    analysis: TrendSignalAnalysis?,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Interval selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockIntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        TrendSignalChart(data = data, latestDate = data.dates.lastOrNull())

        analysis?.let {
            StockTrendSignalAnalysisCard(it)
        }
    }
}

/**
 * Elder Impulse Chart with interval selector
 */
@Composable
private fun StockElderImpulseChartWithSelector(
    data: com.etfmonitor.core.analysis.model.ElderImpulseData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Interval selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockIntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            StockIntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        ElderImpulseChart(data = data, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Trend Signal Analysis Card
 */
@Composable
private fun StockTrendSignalAnalysisCard(analysis: TrendSignalAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + Signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.oscillator_trend_analysis),
                    style = MaterialTheme.typography.titleMedium
                )

                val (signalText, signalColor) = when (analysis.signal) {
                    TrendTradeSignal.STRONG_BUY -> stringResource(R.string.signal_strong_buy) to Color(0xFF4CAF50)
                    TrendTradeSignal.BUY -> stringResource(R.string.signal_buy) to Color(0xFF8BC34A)
                    TrendTradeSignal.NEUTRAL -> stringResource(R.string.signal_neutral) to Color(0xFF9E9E9E)
                    TrendTradeSignal.SELL -> stringResource(R.string.signal_sell) to Color(0xFFFF9800)
                    TrendTradeSignal.STRONG_SELL -> stringResource(R.string.signal_strong_sell) to Color(0xFFF44336)
                }

                Surface(
                    color = signalColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = signalText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                }
            }

            HorizontalDivider()

            // Trend description
            Text(
                analysis.trendDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Indicator values
            StockDataRow(stringResource(R.string.label_current_price), String.format("%,.0f", analysis.currentPrice))
            StockDataRow(stringResource(R.string.label_ma), String.format("%,.0f", analysis.maPrice))
            StockDataRow(stringResource(R.string.label_cmf), String.format("%.3f", analysis.cmfValue))

            // Fear & Greed state
            val fearGreedState = FearGreedState.fromValue(analysis.fearGreedValue)
            val fearGreedColor = when (fearGreedState) {
                FearGreedState.EXTREME_FEAR -> Color(0xFFF44336)
                FearGreedState.FEAR -> Color(0xFFFF9800)
                FearGreedState.NEUTRAL -> Color(0xFF9E9E9E)
                FearGreedState.GREED -> Color(0xFF8BC34A)
                FearGreedState.EXTREME_GREED -> Color(0xFF4CAF50)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.label_fear_greed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format("%.2f", analysis.fearGreedValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = fearGreedColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = fearGreedState.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = fearGreedColor
                        )
                    }
                }
            }

            // Signal counts
            StockDataRow(stringResource(R.string.label_recent_buy_signals), "${analysis.recentBuyCount}회")
            StockDataRow(stringResource(R.string.label_recent_sell_signals), "${analysis.recentSellCount}회")

            HorizontalDivider()

            // Recommendation
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = analysis.recommendation,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StockDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}


// FILE: app/src/main/java/com/etfmonitor/feature/stock/presentation/oscillator/OscillatorScreen.kt
package com.etfmonitor.feature.stock.presentation.oscillator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.component.MarketCapOscillatorChart
import com.etfmonitor.core.ui.component.MacdChart
import com.etfmonitor.core.ui.component.TrendSignalChart
import com.etfmonitor.core.ui.component.LoadingCard
import com.etfmonitor.core.ui.component.ErrorCard
import com.etfmonitor.core.ui.component.IdleCard
import com.etfmonitor.core.ui.component.ElderImpulseChart
import com.etfmonitor.core.ui.component.DemarkTDChart
import com.etfmonitor.core.analysis.model.TrendSignalAnalysis
import com.etfmonitor.core.analysis.model.TrendSignalData
import com.etfmonitor.core.analysis.model.TrendTradeSignal
import com.etfmonitor.core.analysis.model.FearGreedState
import com.etfmonitor.core.analysis.model.ElderImpulseData
import com.etfmonitor.core.analysis.model.DemarkTDData
import com.etfmonitor.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscillatorScreen(
    onNavigateBack: () -> Unit,
    initialTicker: String? = null,
    onNavigateToStatistics: ((String) -> Unit)? = null,
    viewModel: OscillatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val demarkTDInterval by viewModel.demarkTDInterval.collectAsState()
    val trendSignalInterval by viewModel.trendSignalInterval.collectAsState()
    val elderImpulseInterval by viewModel.elderImpulseInterval.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val currentTicker by viewModel.currentTicker.collectAsState()

    // FAB 표시 조건: 설정이 활성화되어 있고, Success 상태일 때
    val showFab by remember(quickChartAnalysisEnabled, onNavigateToStatistics, state) {
        derivedStateOf {
            quickChartAnalysisEnabled &&
                    onNavigateToStatistics != null &&
                    state is OscillatorState.Success
        }
    }

    // Auto-analyze if initialTicker is provided
    LaunchedEffect(initialTicker) {
        if (initialTicker != null && state is OscillatorState.Idle) {
            viewModel.analyzeStock(initialTicker)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oscillator_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (showFab && state is OscillatorState.Success) {
                val successState = state as OscillatorState.Success
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToStatistics?.invoke(successState.stockData.ticker) },
                    icon = {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.fab_etf_analysis)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        var textFieldValue by remember { mutableStateOf("") }
        var showHistoryDialog by remember { mutableStateOf(false) }
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field with Autocomplete - Wrapped in Box for overlay
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search TextField - Matches EtfListScreen design
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.onSearchQueryChanged(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // History 버튼
                                if (searchHistory.isNotEmpty() && textFieldValue.isEmpty()) {
                                    IconButton(onClick = { showHistoryDialog = true }) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = stringResource(R.string.search_history),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Clear 버튼
                                if (textFieldValue.isNotEmpty()) {
                                    IconButton(onClick = {
                                        textFieldValue = ""
                                        viewModel.onClearSuggestions()
                                    }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.extendedShapes.searchBar,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )
                }

                // Autocomplete Dropdown - Overlay below TextField
                if (suggestions.isNotEmpty() && textFieldValue.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaterialTheme.spacing.medium,
                                end = MaterialTheme.spacing.medium,
                                top = 72.dp
                            )
                            .heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 8.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestions, key = { it.ticker }) { stock ->
                                ListItem(
                                    headlineContent = { Text(stock.name) },
                                    supportingContent = {
                                        Text(
                                            "${stock.ticker} • ${stock.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        textFieldValue = stock.name
                                        viewModel.onClearSuggestions()
                                        viewModel.analyzeStock(stock.ticker)
                                    }
                                )
                                if (stock != suggestions.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Date Range Selector (분석된 종목이 있을 때 표시 - Loading 상태에서도 유지)
            if (currentTicker != null) {
                DateRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    availableOptions = listOf(
                        DateRangeOption.WEEK,
                        DateRangeOption.MONTH,
                        DateRangeOption.THREE_MONTHS,
                        DateRangeOption.SIX_MONTHS,
                        DateRangeOption.YEAR,
                        DateRangeOption.ALL
                    )
                )
            }

            // State Content
            when (val currentState = state) {
                is OscillatorState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingCard(message = stringResource(R.string.data_analyzing))
                    }
                }

                is OscillatorState.Success -> {
                    // 차트 페이지 목록 구성
                    val chartPages = buildChartPages(
                        currentState = currentState,
                        demarkTDInterval = demarkTDInterval,
                        onDemarkIntervalChange = { viewModel.changeDemarkTDInterval(it) },
                        trendSignalInterval = trendSignalInterval,
                        onTrendSignalIntervalChange = { viewModel.changeTrendSignalInterval(it) },
                        elderImpulseInterval = elderImpulseInterval,
                        onElderImpulseIntervalChange = { viewModel.changeElderImpulseInterval(it) }
                    )

                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { chartPages.size }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Stock Info Card (고정)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 종목명 & 종목코드 (왼쪽)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        currentState.stockData.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        currentState.stockData.ticker,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 최근 데이터 날짜 & 데이터 포인트 (오른쪽)
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (currentState.oscillatorResult.dates.isNotEmpty()) {
                                        Text(
                                            currentState.oscillatorResult.dates.last(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${currentState.oscillatorResult.dates.size}개 데이터",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Page Indicators + Chart Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chartPages.getOrNull(pagerState.currentPage)?.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Page Indicators
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                chartPages.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                                            .background(
                                                color = if (index == pagerState.currentPage)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Pager for Charts
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                chartPages[page].content()
                            }
                        }

                        // Swipe hint
                        Text(
                            text = stringResource(R.string.oscillator_swipe_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                is OscillatorState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorCard(message = currentState.message)
                    }
                }

                is OscillatorState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IdleCard(message = stringResource(R.string.oscillator_idle_message))
                    }
                }
            }
        }

        // Search History Dialog
        if (showHistoryDialog) {
            SearchHistoryDialog(
                searchHistory = searchHistory,
                onDismiss = { showHistoryDialog = false },
                onSelectStock = { ticker ->
                    showHistoryDialog = false
                    viewModel.analyzeStock(ticker)
                }
            )
        }
    }
}

/**
 * 차트 페이지 데이터 클래스
 */
private data class ChartPage(
    val title: String,
    val content: @Composable () -> Unit
)

/**
 * 차트 페이지 목록 빌드
 */
@Composable
private fun buildChartPages(
    currentState: OscillatorState.Success,
    demarkTDInterval: String,
    onDemarkIntervalChange: (String) -> Unit,
    trendSignalInterval: String,
    onTrendSignalIntervalChange: (String) -> Unit,
    elderImpulseInterval: String,
    onElderImpulseIntervalChange: (String) -> Unit
): List<ChartPage> {
    val pages = mutableListOf<ChartPage>()

    // 1. 시가총액 & 수급 오실레이터 차트
    pages.add(
        ChartPage(
            title = stringResource(R.string.oscillator_chart_marketcap)
        ) {
            MarketCapOscillatorChart(
                result = currentState.oscillatorResult,
                marketCap = currentState.stockData.marketCap,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    // 2. DeMark TD 차트
    currentState.demarkTDData?.let { demarkData ->
        pages.add(
            ChartPage(
                title = stringResource(R.string.oscillator_chart_demark)
            ) {
                DemarkTDChartWithSelector(
                    data = demarkData,
                    currentInterval = demarkTDInterval,
                    onIntervalChange = onDemarkIntervalChange
                )
            }
        )
    }

    // 3. 추세 시그널 차트 + 분석 카드
    currentState.trendSignalData?.let { trendData ->
        pages.add(
            ChartPage(
                title = stringResource(R.string.oscillator_chart_trend)
            ) {
                TrendSignalChartWithSelector(
                    data = trendData,
                    analysis = currentState.trendSignalAnalysis,
                    currentInterval = trendSignalInterval,
                    onIntervalChange = onTrendSignalIntervalChange
                )
            }
        )
    }

    // 4. Elder Impulse 차트
    currentState.elderImpulseData?.let { elderData ->
        pages.add(
            ChartPage(
                title = stringResource(R.string.oscillator_chart_elder)
            ) {
                ElderImpulseChartWithSelector(
                    data = elderData,
                    currentInterval = elderImpulseInterval,
                    onIntervalChange = onElderImpulseIntervalChange
                )
            }
        )
    }

    // 5. MACD 차트
    pages.add(
        ChartPage(
            title = stringResource(R.string.oscillator_chart_macd)
        ) {
            MacdChart(
                result = currentState.oscillatorResult,
                latestDate = currentState.stockData.dates.lastOrNull()
            )
        }
    )

    return pages
}

/**
 * DeMark TD 차트 + 인터벌 선택 버튼
 */
@Composable
private fun DemarkTDChartWithSelector(
    data: DemarkTDData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_monthly),
                selected = currentInterval == "m",
                onClick = { onIntervalChange("m") },
                modifier = Modifier.weight(1f)
            )
        }

        // DeMark TD 차트
        DemarkTDChart(
            data = data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 추세 시그널 차트 + 인터벌 선택 버튼
 */
@Composable
private fun TrendSignalChartWithSelector(
    data: TrendSignalData,
    analysis: TrendSignalAnalysis?,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼 (일봉/주봉만 지원)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        // 추세 시그널 차트
        TrendSignalChart(
            data = data,
            latestDate = data.dates.lastOrNull()
        )

        // 분석 카드
        analysis?.let { TrendSignalAnalysisCard(it) }
    }
}

/**
 * Elder Impulse 차트 + 인터벌 선택 버튼
 */
@Composable
private fun ElderImpulseChartWithSelector(
    data: ElderImpulseData,
    currentInterval: String,
    onIntervalChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 인터벌 선택 버튼 (일봉/주봉만 지원)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntervalButton(
                text = stringResource(R.string.interval_daily),
                selected = currentInterval == "d",
                onClick = { onIntervalChange("d") },
                modifier = Modifier.weight(1f)
            )
            IntervalButton(
                text = stringResource(R.string.interval_weekly),
                selected = currentInterval == "w",
                onClick = { onIntervalChange("w") },
                modifier = Modifier.weight(1f)
            )
        }

        // Elder Impulse 차트
        ElderImpulseChart(
            data = data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 인터벌 선택 버튼
 */
@Composable
private fun IntervalButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text)
        }
    }
}

@Composable
private fun SearchHistoryDialog(
    searchHistory: List<com.etfmonitor.core.database.entities.SearchHistory>,
    onDismiss: () -> Unit,
    onSelectStock: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.recent_search))
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (searchHistory.isEmpty()) {
                    Text(
                        stringResource(R.string.search_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchHistory, key = { it.id }) { history ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(history.name) },
                                    supportingContent = {
                                        Text(
                                            "${history.ticker} • ${history.market}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        onSelectStock(history.ticker)
                                    }
                                )
                                if (history != searchHistory.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 추세 시그널 분석 카드
 */
@Composable
private fun TrendSignalAnalysisCard(analysis: TrendSignalAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 제목 + 신호
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.oscillator_trend_analysis),
                    style = MaterialTheme.typography.titleMedium
                )

                // 신호 배지
                val (signalText, signalColor) = when (analysis.signal) {
                    TrendTradeSignal.STRONG_BUY -> stringResource(R.string.signal_strong_buy) to Color(0xFF4CAF50)
                    TrendTradeSignal.BUY -> stringResource(R.string.signal_buy) to Color(0xFF8BC34A)
                    TrendTradeSignal.NEUTRAL -> stringResource(R.string.signal_neutral) to Color(0xFF9E9E9E)
                    TrendTradeSignal.SELL -> stringResource(R.string.signal_sell) to Color(0xFFFF9800)
                    TrendTradeSignal.STRONG_SELL -> stringResource(R.string.signal_strong_sell) to Color(0xFFF44336)
                }

                Surface(
                    color = signalColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = signalText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                }
            }

            HorizontalDivider()

            // 추세 설명
            Text(
                analysis.trendDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 지표 값
            DataRow(stringResource(R.string.label_current_price), String.format("%,.0f", analysis.currentPrice))
            DataRow(stringResource(R.string.label_ma), String.format("%,.0f", analysis.maPrice))
            DataRow(stringResource(R.string.label_cmf), String.format("%.3f", analysis.cmfValue))

            // Fear & Greed 상태
            val fearGreedState = FearGreedState.fromValue(analysis.fearGreedValue)
            val fearGreedColor = when (fearGreedState) {
                FearGreedState.EXTREME_FEAR -> Color(0xFFF44336)
                FearGreedState.FEAR -> Color(0xFFFF9800)
                FearGreedState.NEUTRAL -> Color(0xFF9E9E9E)
                FearGreedState.GREED -> Color(0xFF8BC34A)
                FearGreedState.EXTREME_GREED -> Color(0xFF4CAF50)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.label_fear_greed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format("%.2f", analysis.fearGreedValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = fearGreedColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = fearGreedState.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = fearGreedColor
                        )
                    }
                }
            }

            // 시그널 카운트
            DataRow(stringResource(R.string.label_recent_buy_signals), "${analysis.recentBuyCount}회")
            DataRow(stringResource(R.string.label_recent_sell_signals), "${analysis.recentSellCount}회")

            HorizontalDivider()

            // 투자 권고
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = analysis.recommendation,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/stock/presentation/statistics/AggregatedStockTrendScreen.kt
package com.etfmonitor.feature.stock.presentation.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etfmonitor.R
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTimePoint
import com.etfmonitor.feature.stock.domain.model.StockAggregatedTrend
import com.etfmonitor.feature.stock.domain.repository.StockStatisticsRepository
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.theme.ChartGridDark
import com.etfmonitor.core.ui.theme.ChartGridLight
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedStockTrendScreen(
    stockTicker: String,
    onNavigateBack: () -> Unit,
    onNavigateToOscillator: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val factory = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AggregatedStockTrendViewModelFactoryProvider::class.java
    ).aggregatedStockTrendViewModelFactory()

    val viewModel: AggregatedStockTrendViewModel = viewModel(
        factory = AggregatedStockTrendViewModel.provideFactory(
            assistedFactory = factory,
            stockTicker = stockTicker
        )
    )
    val state by viewModel.state.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (val s = state) {
                                is AggregatedTrendState.Success -> s.trend.stockName
                                else -> "종목 통합 추이"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stockTicker,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (quickChartAnalysisEnabled && onNavigateToOscillator != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToOscillator(viewModel.stockTicker) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    text = { Text(stringResource(R.string.go_to_oscillator_analysis)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        when (val s = state) {
            is AggregatedTrendState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AggregatedTrendState.Success -> {
                AggregatedTrendContent(
                    trend = s.trend,
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    modifier = Modifier.padding(padding)
                )
            }
            is AggregatedTrendState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AggregatedTrendContent(
    trend: StockAggregatedTrend,
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 기간 선택
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected,
            availableOptions = listOf(
                DateRangeOption.WEEK,
                DateRangeOption.MONTH,
                DateRangeOption.THREE_MONTHS,
                DateRangeOption.SIX_MONTHS,
                DateRangeOption.YEAR,
                DateRangeOption.ALL
            )
        )

        AggregatedSummaryCard(trend.timeSeries)

        AggregatedChartSection(
            title = "총 평가금액 추이",
            data = trend.timeSeries,
            valueExtractor = { it.totalAmount / 100_000_000 },
            chartColor = 0
        )
        AggregatedChartSection(
            title = "최대 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.maxWeight },
            chartColor = 1
        )
        AggregatedChartSection(
            title = "평균 비중 추이 (%)",
            data = trend.timeSeries,
            valueExtractor = { it.avgWeight },
            chartColor = 2
        )
        AggregatedDataTable(trend.timeSeries)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ✅ 1. AggregatedSummaryCard 개선
@Composable
private fun AggregatedSummaryCard(timeSeries: List<StockAggregatedTimePoint>) {
    if (timeSeries.isEmpty()) return

    val first = timeSeries.first()
    val last = timeSeries.last()
    val amountChange = last.totalAmount - first.totalAmount

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("요약 (전체 ETF 통합)", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("데이터 기간", style = MaterialTheme.typography.labelSmall)
                    Text("${first.date} ~ ${last.date}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "현재 총액",
                    value = AmountFormatter.format(last.totalAmount)  // ✅ 개선
                )
                SummaryItem(
                    label = "금액 변화",
                    value = AmountFormatter.formatChange(amountChange)  // ✅ 개선
                )
                SummaryItem(
                    label = "보유 ETF",
                    value = "${last.etfCount}개"
                )
            }
        }
    }
}

// SummaryItem is defined in CashDepositTab.kt (internal visibility)

/**
 * Fear & Greed 스타일 차트 섹션
 * Surface with RoundedCornerShape, BorderStroke, chart title styling
 */
@Composable
private fun AggregatedChartSection(
    title: String,
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float,
    chartColor: Int // 0: primary, 1: secondary, 2: tertiary
) {
    val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
    val isAmountChart = title.contains("금액")
    val chartTitle = if (isAmountChart) {
        val unit = AmountFormatter.getChartUnit(maxValue)
        "총 평가금액 추이 ($unit)"
    } else {
        title
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = chartTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "데이터 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AggregatedLineChart(
                    data = data,
                    valueExtractor = valueExtractor,
                    colorIndex = chartColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }
        }
    }
}

/**
 * MPAndroidChart 기반 라인 차트 (Fear & Greed 스타일)
 */
@Composable
private fun AggregatedLineChart(
    data: List<StockAggregatedTimePoint>,
    valueExtractor: (StockAggregatedTimePoint) -> Float,
    colorIndex: Int,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val lineColor = when (colorIndex) {
        0 -> MaterialTheme.colorScheme.primary.toArgb()
        1 -> MaterialTheme.colorScheme.secondary.toArgb()
        else -> MaterialTheme.colorScheme.tertiary.toArgb()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                legend.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setLabelCount(6, false)
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, item ->
                Entry(index.toFloat(), valueExtractor(item))
            }

            val dataSet = LineDataSet(entries, "").apply {
                color = lineColor
                lineWidth = 2.5f
                setCircleColor(lineColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = lineColor
            }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return if (index >= 0 && index < data.size) {
                        formatDateForChart(data[index].date)
                    } else {
                        ""
                    }
                }
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// formatDateForChart is defined in CashDepositTab.kt (internal visibility)

// ✅ 3. AggregatedDataTable 개선
@Composable
private fun AggregatedDataTable(timeSeries: List<StockAggregatedTimePoint>) {
    // 최대 금액 계산
    val maxAmount = timeSeries.maxOfOrNull { it.totalAmount } ?: 0f
    val amountHeader = AmountFormatter.getTableHeader(maxAmount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text(amountHeader, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)  // ✅ 개선
                Text("ETF수", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("최대%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                Text("평균%", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        AmountFormatter.formatForTable(item.totalAmount, maxAmount),  // ✅ 개선
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${item.etfCount}",
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        String.format("%.2f", item.maxWeight),
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        String.format("%.2f", item.avgWeight),
                        Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Production-ready ViewModel using Hilt Assisted Injection
 *
 * 최적화:
 * - @AssistedInject: 런타임 파라미터(stockTicker)와 Hilt 의존성(repository)을 모두 지원
 * - AssistedFactory: 타입 안전한 팩토리 패턴
 * - EtfMonitorApp.instance 제거: 메모리 누수 위험 제거
 */
class AggregatedStockTrendViewModel @AssistedInject constructor(
    @Assisted val stockTicker: String,
    private val stockStatisticsRepository: StockStatisticsRepository,
    private val etfDao: com.etfmonitor.core.database.EtfDao,
    val pyClient: com.etfmonitor.core.network.python.OscillatorPyClient
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(stockTicker: String): AggregatedStockTrendViewModel
    }

    companion object {
        private const val QUICK_CHART_ANALYSIS_KEY = "quick_chart_analysis_enabled"

        fun provideFactory(
            assistedFactory: Factory,
            stockTicker: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(stockTicker) as T
            }
        }
    }

    private val _state = MutableStateFlow<AggregatedTrendState>(AggregatedTrendState.Loading)
    val state: StateFlow<AggregatedTrendState> = _state.asStateFlow()

    private val _quickChartAnalysisEnabled = MutableStateFlow(false)
    val quickChartAnalysisEnabled: StateFlow<Boolean> = _quickChartAnalysisEnabled.asStateFlow()

    // 날짜 범위 선택 상태
    private val _selectedRange = MutableStateFlow(DateRangeOption.YEAR)
    val selectedRange: StateFlow<DateRangeOption> = _selectedRange.asStateFlow()

    // 전체 데이터 캐시
    private var fullTrend: StockAggregatedTrend? = null

    init {
        loadTrend()
        loadQuickChartAnalysisSetting()
    }

    /**
     * 날짜 범위 업데이트
     */
    fun updateDateRange(option: DateRangeOption) {
        if (option == _selectedRange.value) return
        _selectedRange.value = option
        applyDateRangeFilter()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            try {
                val trend = stockStatisticsRepository.getStockAggregatedTrend(stockTicker)
                fullTrend = trend
                if (trend != null) {
                    applyDateRangeFilter()
                } else {
                    _state.value = AggregatedTrendState.Error("데이터를 찾을 수 없습니다")
                }
            } catch (e: Exception) {
                _state.value = AggregatedTrendState.Error(e.message ?: "오류 발생")
            }
        }
    }

    private fun applyDateRangeFilter() {
        val trend = fullTrend ?: return

        val filteredTimeSeries = if (_selectedRange.value == DateRangeOption.ALL) {
            trend.timeSeries
        } else {
            val cutoffDate = LocalDate.now().minusDays(_selectedRange.value.days.toLong())
            trend.timeSeries.filter { point ->
                try {
                    LocalDate.parse(point.date) >= cutoffDate
                } catch (e: Exception) {
                    true // 파싱 실패 시 포함
                }
            }
        }

        val filteredTrend = trend.copy(timeSeries = filteredTimeSeries)
        _state.value = AggregatedTrendState.Success(filteredTrend)
    }

    private fun loadQuickChartAnalysisSetting() {
        viewModelScope.launch {
            try {
                val enabled = etfDao.getSetting(QUICK_CHART_ANALYSIS_KEY) == "true"
                _quickChartAnalysisEnabled.value = enabled
            } catch (e: Exception) {
                // Ignore error, keep default value
            }
        }
    }
}

sealed class AggregatedTrendState {
    object Loading : AggregatedTrendState()
    data class Success(val trend: StockAggregatedTrend) : AggregatedTrendState()
    data class Error(val message: String) : AggregatedTrendState()
}

/**
 * Hilt EntryPoint to access AssistedFactory from Composable
 * EntryPoint는 Factory 타입을 제공하는 메서드를 가져야 함
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AggregatedStockTrendViewModelFactoryProvider {
    fun aggregatedStockTrendViewModelFactory(): AggregatedStockTrendViewModel.Factory
}
// FILE: app/src/main/java/com/etfmonitor/feature/stock/presentation/trend/StockTrendScreen.kt
package com.etfmonitor.feature.stock.presentation.trend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.core.ui.component.ChartLabelCalculator
import com.etfmonitor.core.ui.component.DateRangeOption
import com.etfmonitor.core.ui.component.DateRangeSelector
import com.etfmonitor.core.ui.theme.ChartGridDark
import com.etfmonitor.core.ui.theme.ChartGridLight
import com.etfmonitor.feature.stock.domain.model.StockTrend
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.core.common.util.AmountFormatter
import com.etfmonitor.core.common.util.DateFormatter
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTrendScreen(
    etfTicker: String,
    stockTicker: String,
    onNavigateBack: () -> Unit,
    onNavigateToOscillator: ((String) -> Unit)? = null,
    viewModel: StockTrendViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val quickChartAnalysisEnabled by viewModel.quickChartAnalysisEnabled.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (val s = state) {
                                is StockTrendState.Success -> s.trend.stockName
                                else -> stringResource(R.string.stock_trend_title)
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "$etfTicker - $stockTicker",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (quickChartAnalysisEnabled && onNavigateToOscillator != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToOscillator(viewModel.stockTicker) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    text = { Text(stringResource(R.string.go_to_oscillator_analysis)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { padding ->
        when (val s = state) {
            is StockTrendState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StockTrendState.Success -> {
                TrendContent(
                    trend = s.trend,
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.updateDateRange(it) },
                    modifier = Modifier.padding(padding)
                )
            }
            is StockTrendState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    Alignment.Center
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TrendContent(
    trend: StockTrend,
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 기간 선택
        DateRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected,
            availableOptions = listOf(
                DateRangeOption.WEEK,
                DateRangeOption.MONTH,
                DateRangeOption.THREE_MONTHS,
                DateRangeOption.SIX_MONTHS,
                DateRangeOption.YEAR,
                DateRangeOption.ALL
            )
        )

        SummaryCard(trend.timeSeries)

        StockTrendChartSection(
            title = stringResource(R.string.stock_trend_weight_chart),
            data = trend.timeSeries,
            valueExtractor = { it.weight },
            valueFormatter = { String.format("%.2f%%", it) },
            isPrimary = true
        )
        StockTrendChartSection(
            title = stringResource(R.string.stock_trend_amount_chart),
            data = trend.timeSeries,
            valueExtractor = { it.amount / 100_000_000 },
            valueFormatter = { AmountFormatter.format(it * 100_000_000) },
            isPrimary = false
        )
        DataTable(trend.timeSeries)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SummaryCard(timeSeries: List<HoldingTimeSeries>) {
    if (timeSeries.isEmpty()) return

    val first = timeSeries.first()
    val last = timeSeries.last()
    val weightChange = last.weight - first.weight
    val amountChange = last.amount - first.amount

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.stock_trend_summary), style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.label_data_period), style = MaterialTheme.typography.labelSmall)
                    Text("${first.date} ~ ${last.date}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = stringResource(R.string.label_weight_change),
                    value = String.format("%+.2f%%", weightChange)
                )
                SummaryItem(
                    label = stringResource(R.string.label_amount_change),
                    value = AmountFormatter.formatChange(amountChange)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Fear & Greed 스타일 차트 섹션
 * Surface with RoundedCornerShape, BorderStroke, chart title styling
 */
@Composable
private fun StockTrendChartSection(
    title: String,
    data: List<HoldingTimeSeries>,
    valueExtractor: (HoldingTimeSeries) -> Float,
    valueFormatter: (Float) -> String,
    isPrimary: Boolean
) {
    val maxValue = data.maxOfOrNull { valueExtractor(it) } ?: 0f
    val isAmountChart = title.contains("금액")
    val chartTitle = if (isAmountChart) {
        val unit = AmountFormatter.getChartUnit(maxValue)
        "평가금액 추이 ($unit)"
    } else {
        title
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = chartTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "데이터 없음",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                StockTrendLineChart(
                    data = data,
                    valueExtractor = valueExtractor,
                    isPrimary = isPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
            }
        }
    }
}

/**
 * MPAndroidChart 기반 라인 차트 (Fear & Greed 스타일)
 */
@Composable
private fun StockTrendLineChart(
    data: List<HoldingTimeSeries>,
    valueExtractor: (HoldingTimeSeries) -> Float,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val lineColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary.toArgb()
    } else {
        MaterialTheme.colorScheme.secondary.toArgb()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = if (isDark) ChartGridDark.toArgb() else ChartGridLight.toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                legend.isEnabled = false
                setExtraBottomOffset(10f)  // Extra padding for rotated labels

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                    granularity = 1f
                    labelRotationAngle = -45f
                    setAvoidFirstLastClipping(true)  // Prevent edge label clipping
                    // labelCount and valueFormatter are set in update block
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridLineWidth = 1f
                    setGridColor(gridColor)
                    enableGridDashedLine(10f, 5f, 0f)
                    setTextColor(textColor)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val dataCount = data.size
            val entries = data.mapIndexed { index, item ->
                Entry(index.toFloat(), valueExtractor(item))
            }

            val dataSet = LineDataSet(entries, "").apply {
                color = lineColor
                lineWidth = 2.5f
                setCircleColor(lineColor)
                circleRadius = 2f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                highLightColor = lineColor
            }

            // Update x-axis with dynamic label count and smart date formatting
            chart.xAxis.apply {
                setLabelCount(ChartLabelCalculator.calculateOptimalLabelCount(dataCount), false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < data.size) {
                            DateFormatter.formatForChartByDataCount(data[index].date, dataCount)
                        } else {
                            ""
                        }
                    }
                }
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun DataTable(timeSeries: List<HoldingTimeSeries>) {
    val maxAmount = timeSeries.maxOfOrNull { it.amount } ?: 0f
    val amountHeader = AmountFormatter.getTableHeader(maxAmount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("상세 데이터", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("날짜", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                Text("비중", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text(amountHeader, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            timeSeries.reversed().take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(item.date, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        String.format("%.2f%%", item.weight),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        AmountFormatter.formatForTable(item.amount, maxAmount),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ====== UI COMPONENTS ======

// FILE: app/src/main/java/com/etfmonitor/feature/home/presentation/component/HomeDialogs.kt
package com.etfmonitor.feature.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*

/**
 * Home Screen Dialogs
 * - DaysSelectionDialog: ETF 데이터 수집 기간 선택
 * - MarketDepositPagesSelectionDialog: 증시 자금 동향 페이지 수 선택
 * - FearGreedPeriodSelectionDialog: Fear & Greed 기간 선택
 * - MarketOscillatorPeriodSelectionDialog: 과매수/과매도 기간 선택
 * - UnifiedInitializationDialog: 통합 초기화 다이얼로그
 */

internal data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)

internal data class SelectionOption(
    val value: Int,
    val label: String,
    val description: String
)

@Composable
internal fun DaysSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc)),
        DaysOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
        DaysOption(40, stringResource(R.string.option_days_40), stringResource(R.string.option_days_40_desc)),
        DaysOption(50, stringResource(R.string.option_days_50), stringResource(R.string.option_days_50_desc))
    )

    var selectedOption by remember { mutableStateOf(options[4]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_init_data_collection)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.dialog_etf_collection_period),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option == selectedOption),
                                onClick = { selectedOption = option }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == selectedOption),
                            onClick = { selectedOption = option }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                stringResource(R.string.label_fear_greed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.dialog_fear_greed_auto_collect),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.dialog_notes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dialog_collection_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedOption.days) },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * 통합 옵션 선택 다이얼로그
 */
@Composable
internal fun OptionsSelectionDialog(
    title: String,
    description: String,
    options: List<SelectionOption>,
    defaultValue: Int,
    infoText: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedValue by remember { mutableStateOf(defaultValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))

                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedValue == option.value),
                                onClick = { selectedValue = option.value }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedValue == option.value),
                            onClick = { selectedValue = option.value }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        infoText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(selectedValue) },
                shape = MaterialTheme.extendedShapes.button
            ) { Text(stringResource(R.string.action_start_collection)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

@Composable
internal fun MarketDepositPagesSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = stringResource(R.string.dialog_market_deposit_init),
        description = stringResource(R.string.dialog_market_deposit_desc),
        options = listOf(
            SelectionOption(5, stringResource(R.string.option_pages_5), stringResource(R.string.option_pages_5_desc)),
            SelectionOption(10, stringResource(R.string.option_pages_10), stringResource(R.string.option_pages_10_desc)),
            SelectionOption(15, stringResource(R.string.option_pages_15), stringResource(R.string.option_pages_15_desc)),
            SelectionOption(20, stringResource(R.string.option_pages_20), stringResource(R.string.option_pages_20_desc)),
            SelectionOption(30, stringResource(R.string.option_pages_30), stringResource(R.string.option_pages_30_desc))
        ),
        defaultValue = 10,
        infoText = stringResource(R.string.dialog_deposit_time_estimate),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun FearGreedPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = stringResource(R.string.dialog_fear_greed_init),
        description = stringResource(R.string.dialog_fear_greed_desc),
        options = listOf(
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
            SelectionOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
            SelectionOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
            SelectionOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
        ),
        defaultValue = 365,
        infoText = stringResource(R.string.dialog_fear_greed_time_estimate),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun MarketOscillatorPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = stringResource(R.string.dialog_oscillator_init),
        description = stringResource(R.string.dialog_oscillator_desc),
        options = listOf(
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
            SelectionOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
            SelectionOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
            SelectionOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
        ),
        defaultValue = 365,
        infoText = stringResource(R.string.dialog_oscillator_time_estimate),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
internal fun MarketIndexPeriodSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    OptionsSelectionDialog(
        title = stringResource(R.string.dialog_market_index_init),
        description = stringResource(R.string.dialog_market_index_desc),
        options = listOf(
            SelectionOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
            SelectionOption(60, "60일", "약 3개월"),
            SelectionOption(90, "90일", "약 4.5개월"),
            SelectionOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc))
        ),
        defaultValue = 30,
        infoText = stringResource(R.string.dialog_market_index_time_estimate),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * 통합 초기화 다이얼로그
 * 앱 첫 실행 시 모든 데이터 수집 옵션을 한 번에 선택
 */
@Composable
internal fun UnifiedInitializationDialog(
    onDismiss: () -> Unit,
    onConfirm: (etfDays: Int, depositPages: Int?, fearGreedDays: Int?, oscillatorDays: Int?, marketIndexDays: Int?, bloodIndicatorDays: Int?) -> Unit
) {
    // ETF 수집 기간
    val etfOptions = listOf(
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc))
    )
    var selectedEtfDays by remember { mutableStateOf(25) }

    // 증시 자금 동향 수집 여부
    var collectDeposit by remember { mutableStateOf(true) }
    var selectedDepositPages by remember { mutableStateOf(10) }

    // Fear & Greed Index 수집 여부
    var collectFearGreed by remember { mutableStateOf(true) }
    var selectedFearGreedDays by remember { mutableStateOf(365) }

    // 과매수/과매도 수집 여부
    var collectOscillator by remember { mutableStateOf(true) }
    var selectedOscillatorDays by remember { mutableStateOf(365) }

    // 시장 지수 수집 여부
    var collectMarketIndex by remember { mutableStateOf(true) }
    var selectedMarketIndexDays by remember { mutableStateOf(30) }

    // Blood Indicator 수집 여부
    var collectBloodIndicator by remember { mutableStateOf(true) }
    var selectedBloodIndicatorDays by remember { mutableStateOf(1825) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.dialog_init_data_collection),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.dialog_select_data_to_collect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. ETF 데이터 수집 기간 (필수)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.dialog_etf_data_required),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        etfOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedEtfDays == option.days),
                                        onClick = { selectedEtfDays = option.days }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedEtfDays == option.days),
                                    onClick = { selectedEtfDays = option.days }
                                )
                                Text(
                                    "${option.label} - ${option.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 증시 자금 동향 (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.menu_market_fund),
                    enabled = collectDeposit,
                    onEnabledChange = { collectDeposit = it },
                    options = listOf(
                        stringResource(R.string.option_pages_5_desc) to 5,
                        stringResource(R.string.option_pages_10) to 10,
                        stringResource(R.string.option_pages_20_desc) to 20
                    ),
                    selectedValue = selectedDepositPages,
                    onValueChange = { selectedDepositPages = it }
                )

                // 3. Fear & Greed Index (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.label_fear_greed),
                    enabled = collectFearGreed,
                    onEnabledChange = { collectFearGreed = it },
                    options = listOf(
                        stringResource(R.string.option_months_6) to 180,
                        stringResource(R.string.option_months_12) to 365,
                        stringResource(R.string.option_months_18) to 540
                    ),
                    selectedValue = selectedFearGreedDays,
                    onValueChange = { selectedFearGreedDays = it }
                )

                // 4. 과매수/과매도 지표 (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.menu_market_overbought),
                    enabled = collectOscillator,
                    onEnabledChange = { collectOscillator = it },
                    options = listOf(
                        stringResource(R.string.option_months_6) to 180,
                        stringResource(R.string.option_months_12) to 365,
                        stringResource(R.string.option_months_18) to 540
                    ),
                    selectedValue = selectedOscillatorDays,
                    onValueChange = { selectedOscillatorDays = it }
                )

                // 5. 시장 지수 (선택)
                UnifiedOptionSection(
                    title = stringResource(R.string.menu_market_index),
                    enabled = collectMarketIndex,
                    onEnabledChange = { collectMarketIndex = it },
                    options = listOf(
                        "30일" to 30,
                        "60일" to 60,
                        "90일" to 90
                    ),
                    selectedValue = selectedMarketIndexDays,
                    onValueChange = { selectedMarketIndexDays = it }
                )

                // 6. Blood Indicator (선택)
                UnifiedOptionSection(
                    title = "Blood Indicator (US)",
                    enabled = collectBloodIndicator,
                    onEnabledChange = { collectBloodIndicator = it },
                    options = listOf(
                        "1년" to 365,
                        "5년" to 1825,
                        "10년" to 3650
                    ),
                    selectedValue = selectedBloodIndicatorDays,
                    onValueChange = { selectedBloodIndicatorDays = it }
                )

                // 안내 문구
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        stringResource(R.string.dialog_unified_time_estimate),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        selectedEtfDays,
                        if (collectDeposit) selectedDepositPages else null,
                        if (collectFearGreed) selectedFearGreedDays else null,
                        if (collectOscillator) selectedOscillatorDays else null,
                        if (collectMarketIndex) selectedMarketIndexDays else null,
                        if (collectBloodIndicator) selectedBloodIndicatorDays else null
                    )
                },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(stringResource(R.string.action_start_collection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

/**
 * 통합 다이얼로그의 선택 옵션 섹션
 */
@Composable
internal fun UnifiedOptionSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit
) {
    Surface(
        color = if (enabled)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.extendedShapes.card
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (label, value) ->
                        FilterChip(
                            selected = selectedValue == value,
                            onClick = { onValueChange(value) },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/home/presentation/component/HomeSummaryCard.kt
package com.etfmonitor.feature.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*
import com.etfmonitor.feature.home.domain.model.HomeSummary

/**
 * Home Screen Summary Card
 * Displays market summary including deposit changes, Fear & Greed index, and market status
 */

@Composable
internal fun SummaryCard(summary: HomeSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.card,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Text(
                stringResource(R.string.home_market_status),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.small))

            // 증시 자금 동향
            if (summary.depositChange != null || summary.creditChange != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_market_fund),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.depositChange?.let {
                            Text(
                                stringResource(R.string.home_deposit_format, formatChange(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                        summary.creditChange?.let {
                            Text(
                                stringResource(R.string.home_credit_format, formatChange(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getChangeColor(it)
                            )
                        }
                    }
                }
            }

            // Fear & Greed Index
            if (summary.kospiFearGreed != null || summary.kosdaqFearGreed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.label_fear_greed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiFearGreed?.let {
                            Text(
                                stringResource(R.string.home_kospi_format, String.format("%.2f", it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                        summary.kosdaqFearGreed?.let {
                            Text(
                                stringResource(R.string.home_kosdaq_format, String.format("%.2f", it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getFearGreedColor(it)
                            )
                        }
                    }
                }
            }

            // 시장 과매수/과매도
            if (summary.kospiStatus != null || summary.kosdaqStatus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_market_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        summary.kospiStatus?.let { status ->
                            Text(
                                stringResource(R.string.home_kospi_format, getStatusText(status)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                        summary.kosdaqStatus?.let { status ->
                            Text(
                                stringResource(R.string.home_kosdaq_format, getStatusText(status)),
                                style = MaterialTheme.typography.bodySmall,
                                color = getStatusColor(status)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun formatChange(value: Double): String {
    val sign = if (value > 0) "+" else ""
    // 데이터가 이미 억원 단위이므로 그대로 사용
    return "$sign${String.format("%.0f", value)}억"
}

@Composable
internal fun getChangeColor(value: Double): Color {
    return when {
        value > 0 -> MaterialTheme.colorScheme.error  // 증가 = 빨강
        value < 0 -> MaterialTheme.colorScheme.primary  // 감소 = 파랑
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun getFearGreedColor(value: Double): Color {
    // Oscillator 값 기준 (-100 ~ 100 범위)
    return when {
        value >= 20 -> MaterialTheme.colorScheme.error  // Greed (상승 모멘텀)
        value <= -20 -> MaterialTheme.colorScheme.primary  // Fear (하락 모멘텀)
        else -> MaterialTheme.colorScheme.onSurface  // Neutral
    }
}

@Composable
internal fun getStatusText(status: String): String {
    // 이미 한국어로 제공되므로 그대로 반환
    return status
}

@Composable
internal fun getStatusColor(status: String): Color {
    return when (status) {
        "과매수" -> MaterialTheme.colorScheme.error
        "과매도" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/ChartColorCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.SingleChartColorSettings

/**
 * 색상 항목 설정
 */
data class ColorItemConfig(
    val label: String,
    val getCurrentColor: (SingleChartColorSettings) -> Int,
    val onColorSelected: (Int) -> Unit
)

/**
 * 선택적 색상 항목 설정
 */
data class OptionalColorItemConfig(
    val label: String,
    val getCurrentColor: (SingleChartColorSettings) -> Int?,
    val onColorSelected: (Int) -> Unit,
    val onReset: () -> Unit
)

/**
 * 색상 섹션 설정
 */
data class ColorSectionConfig(
    val title: String,
    val items: List<ColorItemConfig> = emptyList(),
    val optionalItems: List<OptionalColorItemConfig> = emptyList()
)

/**
 * 차트 색상 카드 설정
 */
data class ChartColorCardConfig(
    val title: String,
    val icon: ImageVector,
    val sections: List<ColorSectionConfig>
)

/**
 * 공통 차트 색상 카드 컴포넌트
 */
@Composable
fun ChartColorCard(
    config: ChartColorCardConfig,
    colors: SingleChartColorSettings,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(config.title, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 각 섹션 렌더링
            config.sections.forEachIndexed { index, section ->
                // 섹션 제목
                Text(
                    section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 필수 색상 항목들
                section.items.forEach { item ->
                    ColorPickerRow(
                        label = item.label,
                        currentColor = item.getCurrentColor(colors),
                        onColorSelected = item.onColorSelected
                    )
                }

                // 선택적 색상 항목들
                section.optionalItems.forEach { item ->
                    OptionalColorPickerRow(
                        label = item.label,
                        currentColor = item.getCurrentColor(colors),
                        onColorSelected = item.onColorSelected,
                        onReset = item.onReset
                    )
                }

                // 마지막 섹션이 아니면 구분선 추가
                if (index < config.sections.size - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 시가총액 & 오실레이터 차트 색상 카드
 */
@Composable
fun MarketCapOscillatorColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_marketcap),
        icon = Icons.Default.ShowChart,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_marketcap_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_oscillator_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * MACD 차트 색상 카드
 */
@Composable
fun MacdColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onPositiveColorChanged: (Int) -> Unit,
    onNegativeColorChanged: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_macd),
        icon = Icons.Default.BarChart,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_macd_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_signal_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_histogram),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_positive),
                        getCurrentColor = { it.positiveColor },
                        onColorSelected = onPositiveColorChanged
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_negative),
                        getCurrentColor = { it.negativeColor },
                        onColorSelected = onNegativeColorChanged
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 증시 자금 동향 차트 색상 카드
 */
@Composable
fun MarketDepositColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_deposit),
        icon = Icons.Default.TrendingUp,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_customer_deposit),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_credit),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 공포 탐욕 지수 차트 색상 카드
 */
@Composable
fun FearGreedColorCard(
    colors: SingleChartColorSettings,
    onLineColor1Changed: (Int) -> Unit,
    onLineColor2Changed: (Int) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onTextColorReset: () -> Unit,
    onLegendColorChanged: (Int) -> Unit,
    onLegendColorReset: () -> Unit
) {
    val config = ChartColorCardConfig(
        title = stringResource(R.string.settings_chart_feargreed),
        icon = Icons.Default.Psychology,
        sections = listOf(
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_line_color),
                items = listOf(
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_fg_line),
                        getCurrentColor = { it.lineColor1 },
                        onColorSelected = onLineColor1Changed
                    ),
                    ColorItemConfig(
                        label = stringResource(R.string.settings_chart_index_line),
                        getCurrentColor = { it.lineColor2 },
                        onColorSelected = onLineColor2Changed
                    )
                )
            ),
            ColorSectionConfig(
                title = stringResource(R.string.settings_chart_text_legend),
                optionalItems = listOf(
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_axis_color),
                        getCurrentColor = { it.textColor },
                        onColorSelected = onTextColorChanged,
                        onReset = onTextColorReset
                    ),
                    OptionalColorItemConfig(
                        label = stringResource(R.string.settings_chart_legend_color),
                        getCurrentColor = { it.legendColor },
                        onColorSelected = onLegendColorChanged,
                        onReset = onLegendColorReset
                    )
                )
            )
        )
    )

    ChartColorCard(config = config, colors = colors)
}

/**
 * 차트 색상 초기화 카드
 */
@Composable
fun ResetChartColorsCard(
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.settings_chart_reset), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_chart_reset_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_chart_reset_all))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_chart_reset)) },
            text = { Text(stringResource(R.string.settings_chart_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/ColorPickerComponents.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.ui.theme.*
import kotlin.math.roundToInt

// 확장된 색상 팔레트 - Windows 스타일 색상 선택기와 유사
// Row 1: 밝은 색상
private val colorPaletteRow1 = listOf(
    Color(0xFFFF8080), Color(0xFFFFFF80), Color(0xFF80FF80), Color(0xFF00FF80),
    Color(0xFF80FFFF), Color(0xFF0080FF), Color(0xFFFF80C0), Color(0xFFFF80FF)
)
// Row 2: 기본 밝은 색상
private val colorPaletteRow2 = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF80FF00), Color(0xFF00FF40),
    Color(0xFF00FFFF), Color(0xFF0080C0), Color(0xFF8080C0), Color(0xFFFF00FF)
)
// Row 3: 중간 색상
private val colorPaletteRow3 = listOf(
    Color(0xFF804040), Color(0xFFFF8040), Color(0xFF00FF00), Color(0xFF008080),
    Color(0xFF004080), Color(0xFF8080FF), Color(0xFF800040), Color(0xFFFF0080)
)
// Row 4: 어두운 색상
private val colorPaletteRow4 = listOf(
    Color(0xFF800000), Color(0xFFFF8000), Color(0xFF008000), Color(0xFF008040),
    Color(0xFF0000FF), Color(0xFF0000A0), Color(0xFF800080), Color(0xFF8000FF)
)
// Row 5: 진한 색상
private val colorPaletteRow5 = listOf(
    Color(0xFF400000), Color(0xFF804000), Color(0xFF004000), Color(0xFF004040),
    Color(0xFF000080), Color(0xFF000040), Color(0xFF400040), Color(0xFF400080)
)
// Row 6: 그레이스케일 + 추가 색상
private val colorPaletteRow6 = listOf(
    Color(0xFF000000), Color(0xFF404040), Color(0xFF808080), Color(0xFFA0A0A0),
    Color(0xFFC0C0C0), Color(0xFFD4D4D4), Color(0xFFE8E8E8), Color(0xFFFFFFFF)
)

// 차트 기본 색상 (빠른 선택용)
private val chartDefaultColors = listOf(
    ChartPrimary,
    ChartSecondary,
    ChartTertiary,
    ChartGreen,
    ChartRed,
    ChartBlue,
    ChartOrange,
    ChartPurple,
    ChartCyan,
    ChartPink
)

/**
 * 필수 색상 선택 행
 * 현재 색상을 표시하고 클릭하면 색상 선택 다이얼로그를 엽니다.
 */
@Composable
fun ColorPickerRow(
    label: String,
    currentColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 현재 색상 표시
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(currentColor))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showColorPicker = true }
            )

            IconButton(onClick = { showColorPicker = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "색상 변경",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = currentColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            }
        )
    }
}

/**
 * 선택적 색상 선택 행
 * null일 경우 테마 기본값을 사용하고, 초기화 버튼을 제공합니다.
 */
@Composable
fun OptionalColorPickerRow(
    label: String,
    currentColor: Int?,
    onColorSelected: (Int) -> Unit,
    onReset: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentColor != null) {
                // 현재 색상 표시
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { showColorPicker = true }
                )

                // 초기화 버튼
                IconButton(onClick = onReset) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "기본값으로",
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // 기본값 사용 중
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "테마 기본값",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(onClick = { showColorPicker = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "색상 변경",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = currentColor ?: ChartTextLight.toArgb(),
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            }
        )
    }
}

/**
 * 색상 선택 다이얼로그
 * Windows 스타일의 확장된 색상 팔레트, RGB 슬라이더, Hex 입력을 제공합니다.
 */
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    var redValue by remember { mutableFloatStateOf(((currentColor shr 16) and 0xFF).toFloat()) }
    var greenValue by remember { mutableFloatStateOf(((currentColor shr 8) and 0xFF).toFloat()) }
    var blueValue by remember { mutableFloatStateOf((currentColor and 0xFF).toFloat()) }
    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentColor and 0xFFFFFF))
    }
    var isHexInputError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // RGB 값이 변경되면 selectedColor 업데이트
    LaunchedEffect(redValue, greenValue, blueValue) {
        val newColor = (0xFF shl 24) or
                      (redValue.roundToInt() shl 16) or
                      (greenValue.roundToInt() shl 8) or
                      blueValue.roundToInt()
        selectedColor = newColor
        hexInput = String.format("%06X", newColor and 0xFFFFFF)
        isHexInputError = false
    }

    // 팔레트에서 색상 선택시 RGB 슬라이더도 업데이트
    fun updateFromColor(color: Int) {
        selectedColor = color
        redValue = ((color shr 16) and 0xFF).toFloat()
        greenValue = ((color shr 8) and 0xFF).toFloat()
        blueValue = (color and 0xFF).toFloat()
        hexInput = String.format("%06X", color and 0xFFFFFF)
        isHexInputError = false
    }

    // Hex 입력 파싱
    fun parseHexInput(input: String) {
        val cleanInput = input.replace("#", "").uppercase()
        if (cleanInput.length == 6 && cleanInput.all { it in '0'..'9' || it in 'A'..'F' }) {
            try {
                val parsedColor = (0xFF shl 24) or cleanInput.toLong(16).toInt()
                updateFromColor(parsedColor)
            } catch (e: Exception) {
                isHexInputError = true
            }
        } else {
            isHexInputError = input.isNotEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("색상 선택") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 미리보기: 새 색상 / 현재 색상
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "새 색상",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                .background(Color(selectedColor))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                )
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "현재 색상",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                .background(Color(currentColor))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                )
                        )
                    }
                }

                HorizontalDivider()

                // 차트 기본 색상 (빠른 선택)
                Text(
                    "차트 기본 색상",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        count = chartDefaultColors.size,
                        key = { index -> chartDefaultColors[index].toArgb() }
                    ) { index ->
                        val color = chartDefaultColors[index]
                        val isSelected = color.toArgb() == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { updateFromColor(color.toArgb()) }
                        )
                    }
                }

                HorizontalDivider()

                // 확장 색상 팔레트 (그리드)
                Text(
                    "색상 팔레트",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 8열 그리드 색상 팔레트
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        colorPaletteRow1,
                        colorPaletteRow2,
                        colorPaletteRow3,
                        colorPaletteRow4,
                        colorPaletteRow5,
                        colorPaletteRow6
                    ).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { color ->
                                val isSelected = color.toArgb() == selectedColor
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { updateFromColor(color.toArgb()) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // RGB 슬라이더
                Text(
                    "RGB 조절",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 빨강 슬라이더
                ColorSliderRow(
                    label = "R",
                    value = redValue,
                    onValueChange = { redValue = it },
                    color = Color.Red
                )

                // 초록 슬라이더
                ColorSliderRow(
                    label = "G",
                    value = greenValue,
                    onValueChange = { greenValue = it },
                    color = Color.Green
                )

                // 파랑 슬라이더
                ColorSliderRow(
                    label = "B",
                    value = blueValue,
                    onValueChange = { blueValue = it },
                    color = Color.Blue
                )

                HorizontalDivider()

                // Hex 코드 입력
                Text(
                    "Hex 코드",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
                            .take(6)
                            .uppercase()
                        hexInput = filtered
                        if (filtered.length == 6) {
                            parseHexInput(filtered)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("#") },
                    singleLine = true,
                    isError = isHexInputError,
                    supportingText = if (isHexInputError) {
                        { Text("6자리 16진수를 입력하세요 (예: FF5500)") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * RGB 슬라이더 행
 */
@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(20.dp),
            color = color
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
        Text(
            text = value.roundToInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(32.dp)
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/DataCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R

/**
 * Settings Screen - Data Period Tab Card Components
 * Contains DefaultDaysCard, SearchHistoryLimitCard, DatabaseCard
 * and their related dialogs
 *
 * Note: ETF 데이터 관리 기능은 UpdateCards.kt의 EtfDataManagementCard에서 제공
 */

@Composable
fun DefaultDaysCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_etf_period), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_etf_period_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_days_format, currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    stringResource(R.string.settings_etf_period_recommend),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        DaysSelectionDialog(
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days, reinitialize ->
                onDaysChange(days, reinitialize)
                showDialog = false
            }
        )
    }
}

@Composable
fun SearchHistoryLimitCard(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_search_history), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_search_history_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_count_format, currentLimit),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    stringResource(R.string.settings_search_history_range),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        SearchHistoryLimitDialog(
            currentLimit = currentLimit,
            onDismiss = { showDialog = false },
            onConfirm = { limit ->
                onLimitChange(limit)
                showDialog = false
            }
        )
    }
}

@Composable
fun DatabaseCard(
    onReset: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.settings_database), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_database_desc),
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_database_reset))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_database_reset)) },
            text = { Text(stringResource(R.string.settings_database_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// ==================== Dialogs ====================

@Composable
fun DaysSelectionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val options = listOf(
        DaysOption(5, stringResource(R.string.option_days_5), stringResource(R.string.option_days_5_desc)),
        DaysOption(10, stringResource(R.string.option_days_10), stringResource(R.string.option_days_10_desc)),
        DaysOption(15, stringResource(R.string.option_days_15), stringResource(R.string.option_days_15_desc)),
        DaysOption(20, stringResource(R.string.option_days_20), stringResource(R.string.option_days_20_desc)),
        DaysOption(25, stringResource(R.string.option_days_25), stringResource(R.string.option_days_25_desc)),
        DaysOption(30, stringResource(R.string.option_days_30), stringResource(R.string.option_days_30_desc)),
        DaysOption(40, stringResource(R.string.option_days_40), stringResource(R.string.option_days_40_desc)),
        DaysOption(50, stringResource(R.string.option_days_50), stringResource(R.string.option_days_50_desc))
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var reinitialize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_period_change_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 즉시 적용 옵션
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reinitialize,
                        onCheckedChange = { reinitialize = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "지금 데이터 재수집",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "선택한 기간으로 ETF 데이터를 즉시 재수집합니다 (시간 소요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays, reinitialize) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun SearchHistoryLimitDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedLimit by remember { mutableIntStateOf(currentLimit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_search_history_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.settings_search_history_select))

                Column {
                    Text(
                        stringResource(R.string.settings_count_format, selectedLimit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = selectedLimit.toFloat(),
                        onValueChange = { selectedLimit = it.toInt() },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_count_format, 5), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.settings_count_format, 30), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLimit) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ==================== Data Classes ====================

private data class DaysOption(
    val days: Int,
    val label: String,
    val description: String
)

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/GeneralCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.etfmonitor.R
import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.feature.settings.presentation.ApiKeyTestState
import com.etfmonitor.core.ui.theme.FontScaleSettings

/**
 * Settings Screen - General Tab Card Components
 * Contains ThemeSettingCard, AIApiKeyCard, FontScaleCard and related components
 */

@Composable
fun QuickChartAnalysisCard(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_quick_chart_analysis_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_quick_chart_analysis_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnabledChange(!isEnabled) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_quick_chart_analysis_enable),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_quick_chart_analysis_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

@Composable
fun ThemeSettingCard(
    isDarkTheme: Boolean?,
    onThemeChange: (Boolean?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (isDarkTheme) {
                        true -> Icons.Default.DarkMode
                        false -> Icons.Default.LightMode
                        null -> Icons.Default.BrightnessAuto
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_theme_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 시스템 설정
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == null,
                        onClick = { onThemeChange(null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.BrightnessAuto, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_system_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 라이트 모드
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(false) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == false,
                        onClick = { onThemeChange(false) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.LightMode, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_light_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 다크 모드
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(true) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == true,
                        onClick = { onThemeChange(true) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.DarkMode, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_dark_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIApiKeyCard(
    selectedProvider: AIProvider,
    isClaudeConfigured: Boolean,
    isGeminiConfigured: Boolean,
    testState: ApiKeyTestState,
    claudeModels: List<AIModel>,
    geminiModels: List<AIModel>,
    selectedClaudeModel: String?,
    selectedGeminiModel: String?,
    isLoadingClaudeModels: Boolean,
    isLoadingGeminiModels: Boolean,
    onProviderSelected: (AIProvider) -> Unit,
    onSetClaudeApiKey: (String) -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onClearClaudeApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onTestConnection: () -> Unit,
    onClearTestState: () -> Unit,
    onLoadClaudeModels: () -> Unit,
    onLoadGeminiModels: () -> Unit,
    onSelectClaudeModel: (String) -> Unit,
    onSelectGeminiModel: (String) -> Unit
) {
    var showClaudeDialog by remember { mutableStateOf(false) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var expandedClaudeModels by remember { mutableStateOf(false) }
    var expandedGeminiModels by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_ai_api_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_ai_api_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            // AI 프로바이더 선택
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_ai_provider_select),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(8.dp))

                    // Claude 선택
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderSelected(AIProvider.CLAUDE) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == AIProvider.CLAUDE,
                            onClick = { onProviderSelected(AIProvider.CLAUDE) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Claude (Anthropic)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isClaudeConfigured) {
                                Text(
                                    stringResource(R.string.settings_api_key_set_check),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Gemini 선택
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderSelected(AIProvider.GEMINI) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == AIProvider.GEMINI,
                            onClick = { onProviderSelected(AIProvider.GEMINI) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Gemini (Google)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isGeminiConfigured) {
                                Text(
                                    stringResource(R.string.settings_api_key_set_check),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            val currentIsConfigured = when (selectedProvider) {
                AIProvider.CLAUDE -> isClaudeConfigured
                AIProvider.GEMINI -> isGeminiConfigured
            }

            // 선택된 프로바이더의 API 키 상태 표시
            Surface(
                color = if (currentIsConfigured)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (currentIsConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (currentIsConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "${selectedProvider.toDisplayName()} " +
                                    if (currentIsConfigured) stringResource(R.string.settings_api_key_set) else stringResource(R.string.settings_api_key_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentIsConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // API 테스트 상태 표시
            ApiTestStateIndicator(testState)

            // 모델 선택 (API 키가 설정된 경우에만 표시)
            if (currentIsConfigured) {
                HorizontalDivider()

                // 현재 선택된 제공자에 따라 모델 선택 UI 표시
                when (selectedProvider) {
                    AIProvider.CLAUDE -> {
                        ModelSelectionSection(
                            providerName = "Claude",
                            models = claudeModels,
                            selectedModel = selectedClaudeModel,
                            isLoading = isLoadingClaudeModels,
                            expanded = expandedClaudeModels,
                            onExpandChanged = { expandedClaudeModels = it },
                            onLoadModels = onLoadClaudeModels,
                            onSelectModel = onSelectClaudeModel
                        )
                    }
                    AIProvider.GEMINI -> {
                        ModelSelectionSection(
                            providerName = "Gemini",
                            models = geminiModels,
                            selectedModel = selectedGeminiModel,
                            isLoading = isLoadingGeminiModels,
                            expanded = expandedGeminiModels,
                            onExpandChanged = { expandedGeminiModels = it },
                            onLoadModels = onLoadGeminiModels,
                            onSelectModel = onSelectGeminiModel
                        )
                    }
                }
            }

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        when (selectedProvider) {
                            AIProvider.CLAUDE -> showClaudeDialog = true
                            AIProvider.GEMINI -> showGeminiDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (currentIsConfigured) Icons.Default.Edit else Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (currentIsConfigured) stringResource(R.string.settings_action_change) else stringResource(R.string.settings_action_set))
                }

                if (currentIsConfigured) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f),
                        enabled = testState !is ApiKeyTestState.Testing
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_action_test))
                    }

                    IconButton(
                        onClick = { showClearConfirmDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 안내 문구
            ApiKeyInfoSection(selectedProvider)
        }
    }

    // Claude API 키 입력 다이얼로그
    if (showClaudeDialog) {
        ApiKeyInputDialog(
            title = stringResource(R.string.settings_api_key_claude_title),
            placeholder = "sk-ant-...",
            onDismiss = { showClaudeDialog = false },
            onConfirm = { apiKey ->
                onSetClaudeApiKey(apiKey)
                showClaudeDialog = false
                onClearTestState()
            }
        )
    }

    // Gemini API 키 입력 다이얼로그
    if (showGeminiDialog) {
        ApiKeyInputDialog(
            title = stringResource(R.string.settings_api_key_gemini_title),
            placeholder = "AIza...",
            onDismiss = { showGeminiDialog = false },
            onConfirm = { apiKey ->
                onSetGeminiApiKey(apiKey)
                showGeminiDialog = false
                onClearTestState()
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showClearConfirmDialog) {
        val providerName = selectedProvider.toDisplayName()
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_api_key_delete)) },
            text = { Text(stringResource(R.string.settings_api_key_delete_confirm, providerName)) },
            confirmButton = {
                Button(
                    onClick = {
                        when (selectedProvider) {
                            AIProvider.CLAUDE -> onClearClaudeApiKey()
                            AIProvider.GEMINI -> onClearGeminiApiKey()
                        }
                        showClearConfirmDialog = false
                        onClearTestState()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ApiTestStateIndicator(testState: ApiKeyTestState) {
    when (testState) {
        is ApiKeyTestState.Testing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_api_testing), style = MaterialTheme.typography.bodySmall)
            }
        }
        is ApiKeyTestState.Success -> {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        stringResource(R.string.settings_api_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        is ApiKeyTestState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        stringResource(R.string.settings_api_fail, testState.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun ApiKeyInfoSection(selectedProvider: AIProvider) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (selectedProvider) {
                AIProvider.CLAUDE -> {
                    Text(
                        stringResource(R.string.settings_api_key_claude_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "https://console.anthropic.com/settings/keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AIProvider.GEMINI -> {
                    Text(
                        stringResource(R.string.settings_api_key_gemini_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "https://aistudio.google.com/app/apikey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * FRED API Key Card for Blood Indicator data collection.
 * FRED (Federal Reserve Economic Data) provides free economic data.
 * Get API key from: https://fred.stlouisfed.org/docs/api/api_key.html
 */
@Composable
fun FredApiKeyCard(
    isConfigured: Boolean,
    onSetApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit
) {
    var showInputDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.settings_fred_api_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_fred_api_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            // API 키 상태 표시
            Surface(
                color = if (isConfigured)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            if (isConfigured) stringResource(R.string.settings_fred_api_set)
                            else stringResource(R.string.settings_fred_api_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showInputDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isConfigured) Icons.Default.Edit else Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isConfigured) stringResource(R.string.settings_action_change) else stringResource(R.string.settings_action_set))
                }

                if (isConfigured) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 안내 문구
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        stringResource(R.string.settings_fred_api_url_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "https://fred.stlouisfed.org/docs/api/api_key.html",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // API 키 입력 다이얼로그
    if (showInputDialog) {
        ApiKeyInputDialog(
            title = stringResource(R.string.settings_fred_api_dialog_title),
            placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            onDismiss = { showInputDialog = false },
            onConfirm = { apiKey ->
                onSetApiKey(apiKey)
                showInputDialog = false
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_fred_api_delete)) },
            text = { Text(stringResource(R.string.settings_fred_api_delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearApiKey()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun ApiKeyInputDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_api_key_enter),
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text(placeholder) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        stringResource(R.string.settings_api_key_secure),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(apiKey) },
                enabled = apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun FontScaleCard(
    fontScaleSettings: FontScaleSettings,
    onDisplayScaleChange: (Float) -> Unit,
    onHeadlineScaleChange: (Float) -> Unit,
    onTitleScaleChange: (Float) -> Unit,
    onBodyScaleChange: (Float) -> Unit,
    onLabelScaleChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_font_scale), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_font_scale_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            // Display
            FontScaleSlider(
                label = "Display",
                description = stringResource(R.string.settings_font_display),
                currentScale = fontScaleSettings.displayScale,
                onScaleChange = onDisplayScaleChange
            )

            // Headline
            FontScaleSlider(
                label = "Headline",
                description = stringResource(R.string.settings_font_headline),
                currentScale = fontScaleSettings.headlineScale,
                onScaleChange = onHeadlineScaleChange
            )

            // Title
            FontScaleSlider(
                label = "Title",
                description = stringResource(R.string.settings_font_title),
                currentScale = fontScaleSettings.titleScale,
                onScaleChange = onTitleScaleChange
            )

            // Body
            FontScaleSlider(
                label = "Body",
                description = stringResource(R.string.settings_font_body),
                currentScale = fontScaleSettings.bodyScale,
                onScaleChange = onBodyScaleChange,
                minScale = 1.1f,
                maxScale = 1.8f,
                steps = 6
            )

            // Label
            FontScaleSlider(
                label = "Label",
                description = stringResource(R.string.settings_font_label),
                currentScale = fontScaleSettings.labelScale,
                onScaleChange = onLabelScaleChange
            )
        }
    }
}

@Composable
fun FontScaleSlider(
    label: String,
    description: String,
    currentScale: Float,
    onScaleChange: (Float) -> Unit,
    minScale: Float = 0.8f,
    maxScale: Float = 1.4f,
    steps: Int = 5
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${(currentScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = currentScale,
            onValueChange = {
                val rounded = (it * 10).toInt() / 10f
                onScaleChange(rounded)
            },
            valueRange = minScale..maxScale,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSection(
    providerName: String,
    models: List<AIModel>,
    selectedModel: String?,
    isLoading: Boolean,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    onLoadModels: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_model_select, providerName),
            style = MaterialTheme.typography.labelLarge
        )

        // 모델 목록이 비어있으면 로드 버튼 표시
        if (models.isEmpty() && !isLoading) {
            OutlinedButton(
                onClick = onLoadModels,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_model_load))
            }
        }

        // 로딩 중일 때
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_model_loading), style = MaterialTheme.typography.bodySmall)
            }
        }

        val modelSelectPlaceholder = stringResource(R.string.settings_model_select_placeholder)
        val selectedModelLabel = stringResource(R.string.settings_model_selected)
        // 모델 목록이 있을 때 드롭다운 표시
        if (models.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandChanged
            ) {
                OutlinedTextField(
                    value = selectedModel?.let { modelId ->
                        models.find { it.id == modelId }?.displayName() ?: modelId
                    } ?: modelSelectPlaceholder,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(selectedModelLabel) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandChanged(false) }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        model.displayName(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (model.description != null) {
                                        Text(
                                            model.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (model.contextWindow != null || model.maxOutputTokens != null) {
                                        val inputLabel = stringResource(R.string.settings_model_input, model.contextWindow ?: "-")
                                        val outputLabel = stringResource(R.string.settings_model_output, model.maxOutputTokens ?: "-")
                                        val tokenLabel = stringResource(R.string.settings_model_tokens)
                                        Text(
                                            buildString {
                                                if (model.contextWindow != null) {
                                                    append(inputLabel)
                                                }
                                                if (model.maxOutputTokens != null) {
                                                    if (model.contextWindow != null) append(" | ")
                                                    append(outputLabel)
                                                }
                                                append(" $tokenLabel")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectModel(model.id)
                                onExpandChanged(false)
                            },
                            leadingIcon = if (selectedModel == model.id) {
                                {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // 새로고침 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onLoadModels,
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.nav_refresh), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/KeywordCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.etfmonitor.R
import com.etfmonitor.core.ui.theme.*

/**
 * Settings Screen - Keyword Tab Card Components
 * Contains ThemeCard and ExclusionCard for managing ETF filter keywords
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeCard(
    themes: List<String>,
    onAddTheme: (String) -> Unit,
    onRemoveTheme: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newTheme by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.settings_include_theme), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.settings_add))
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_include_theme_desc),
                style = MaterialTheme.typography.bodySmall
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themes.forEach { theme ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveTheme(theme) },
                        label = { Text(theme, maxLines = 1) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_add_theme)) },
            text = {
                OutlinedTextField(
                    value = newTheme,
                    onValueChange = { newTheme = it },
                    label = {
                        Text(
                            stringResource(R.string.settings_keyword),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_keyword_example),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.extendedShapes.searchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddTheme(newTheme)
                        newTheme = ""
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExclusionCard(
    exclusions: List<String>,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var newExclusion by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(stringResource(R.string.settings_exclude_keyword), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.settings_add))
                }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_exclude_keyword_desc),
                style = MaterialTheme.typography.bodySmall
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exclusions.forEach { exclusion ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveExclusion(exclusion) },
                        label = { Text(exclusion, maxLines = 1) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }
        }
    }

    if (showDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_add_exclusion)) },
            text = {
                OutlinedTextField(
                    value = newExclusion,
                    onValueChange = { newExclusion = it },
                    label = {
                        Text(
                            stringResource(R.string.settings_keyword),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.settings_exclude_example),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.extendedShapes.searchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddExclusion(newExclusion)
                        newExclusion = ""
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/PeriodCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R

/**
 * 기간 옵션 데이터 클래스
 */
data class PeriodOption(
    val days: Int,
    val label: String,
    val description: String
)

/**
 * 기간 설정 카드 구성 데이터 클래스
 */
data class PeriodCardConfig(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val dialogTitle: String,
    val recommendationText: String
)

/**
 * 일 수를 표시 텍스트로 변환 (Composable)
 */
@Composable
private fun daysToDisplayText(days: Int): String = when (days) {
    180 -> stringResource(R.string.period_6_months).substringBefore(" ")
    365 -> stringResource(R.string.period_12_months).substringBefore(" ")
    540 -> stringResource(R.string.period_18_months)
    730 -> stringResource(R.string.period_24_months)
    else -> stringResource(R.string.settings_days_format, days)
}

/**
 * 공통 기간 설정 카드 컴포넌트
 */
@Composable
fun PeriodCard(
    config: PeriodCardConfig,
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(config.title, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 설명
            Text(
                config.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // 현재 설정 표시 및 변경 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        daysToDisplayText(currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            // 권장 사항 표시
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    config.recommendationText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        PeriodSelectionDialog(
            title = config.dialogTitle,
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days, reinitialize ->
                onDaysChange(days, reinitialize)
                showDialog = false
            }
        )
    }
}

/**
 * Fear & Greed Index 수집 기간 카드
 */
@Composable
fun FearGreedPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_feargreed_period),
        icon = Icons.Default.BarChart,
        description = stringResource(R.string.settings_feargreed_period_desc),
        dialogTitle = stringResource(R.string.settings_feargreed_period_title),
        recommendationText = stringResource(R.string.settings_feargreed_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 과매수/과매도 수집 기간 카드
 */
@Composable
fun MarketOscillatorPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_oscillator_period),
        icon = Icons.Default.Leaderboard,
        description = stringResource(R.string.settings_oscillator_period_desc),
        dialogTitle = stringResource(R.string.settings_oscillator_period_title),
        recommendationText = stringResource(R.string.settings_oscillator_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * 시장 지수 수집 기간 카드
 */
@Composable
fun MarketIndexPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    val config = PeriodCardConfig(
        title = stringResource(R.string.settings_market_index_period),
        icon = Icons.Default.Analytics,
        description = stringResource(R.string.settings_market_index_period_desc),
        dialogTitle = stringResource(R.string.settings_market_index_period_title),
        recommendationText = stringResource(R.string.settings_market_index_period_recommend)
    )

    PeriodCard(
        config = config,
        currentDays = currentDays,
        onDaysChange = onDaysChange
    )
}

/**
 * Blood Indicator 수집 기간 카드
 */
@Composable
fun BloodIndicatorPeriodCard(
    currentDays: Int,
    onDaysChange: (Int, Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Bloodtype,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Blood Indicator 수집 기간", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 설명
            Text(
                "US Treasury 기반 시장 건강도 지표 (IRX, HYG, TNX, SPY)의 수집 기간을 설정합니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            // 현재 설정 표시 및 변경 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_current_setting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        bloodIndicatorDaysToDisplayText(currentDays),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            // 권장 사항 표시
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "장기 추세 분석을 위해 5년 이상을 권장합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (showDialog) {
        BloodIndicatorPeriodSelectionDialog(
            currentDays = currentDays,
            onDismiss = { showDialog = false },
            onConfirm = { days, reinitialize ->
                onDaysChange(days, reinitialize)
                showDialog = false
            }
        )
    }
}

/**
 * Blood Indicator 일 수를 표시 텍스트로 변환
 */
@Composable
private fun bloodIndicatorDaysToDisplayText(days: Int): String = when (days) {
    365 -> "1년"
    1095 -> "3년"
    1825 -> "5년"
    2555 -> "7년"
    3650 -> "10년"
    else -> "${days}일"
}

/**
 * Blood Indicator 기간 선택 다이얼로그
 */
@Composable
fun BloodIndicatorPeriodSelectionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(365, "1년", "약 365일"),
        PeriodOption(1095, "3년", "약 1,095일"),
        PeriodOption(1825, "5년 (권장)", "약 1,825일"),
        PeriodOption(2555, "7년", "약 2,555일"),
        PeriodOption(3650, "10년", "약 3,650일")
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var reinitialize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blood Indicator 수집 기간") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_period_select),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 즉시 적용 옵션
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reinitialize,
                        onCheckedChange = { reinitialize = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "지금 데이터 재수집",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "선택한 기간으로 데이터를 즉시 재수집합니다 (시간 소요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays, reinitialize) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * 기간 선택 다이얼로그
 */
@Composable
fun PeriodSelectionDialog(
    title: String,
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    val periodOptions = listOf(
        PeriodOption(180, stringResource(R.string.option_months_6), stringResource(R.string.option_months_6_desc)),
        PeriodOption(365, stringResource(R.string.option_months_12), stringResource(R.string.option_months_12_desc)),
        PeriodOption(540, stringResource(R.string.option_months_18), stringResource(R.string.option_months_18_desc)),
        PeriodOption(730, stringResource(R.string.option_months_24), stringResource(R.string.option_months_24_desc))
    )

    var selectedDays by remember { mutableIntStateOf(currentDays) }
    var reinitialize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_period_select),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                periodOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedDays == option.days),
                            onClick = { selectedDays = option.days }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 즉시 적용 옵션
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reinitialize,
                        onCheckedChange = { reinitialize = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "지금 데이터 재수집",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "선택한 기간으로 데이터를 즉시 재수집합니다 (시간 소요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDays, reinitialize) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// FILE: app/src/main/java/com/etfmonitor/feature/settings/presentation/component/UpdateCards.kt
package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.feature.settings.presentation.BloodIndicatorUpdateSettings
import com.etfmonitor.feature.settings.presentation.EtfUpdateSettings
import com.etfmonitor.feature.settings.presentation.FearGreedUpdateSettings
import com.etfmonitor.feature.settings.presentation.MarketDepositUpdateSettings
import com.etfmonitor.feature.settings.presentation.MarketIndexUpdateSettings
import com.etfmonitor.feature.settings.presentation.MarketOscillatorUpdateSettings
import com.etfmonitor.feature.settings.presentation.StockUpdateSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 통계 정보 항목
 */
data class StatItem(
    val label: String,
    val value: String
)

/**
 * 데이터 업데이트 카드 설정
 */
data class DataUpdateCardConfig(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val updateHour: Int,
    val updateMinute: Int,
    val lastUpdateTime: Long?,
    val isUpdating: Boolean,
    val stats: List<StatItem>
)

/**
 * 공통 데이터 업데이트 카드 컴포넌트
 */
@Composable
fun DataUpdateCard(
    config: DataUpdateCardConfig,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    config.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(config.title, style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            // 설명
            Text(
                config.description,
                style = MaterialTheme.typography.bodyMedium
            )

            // 업데이트 시간 설정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_update_time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${String.format("%02d", config.updateHour)}:${String.format("%02d", config.updateMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.settings_action_change))
                }
            }

            // 통계 정보
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    config.stats.forEach { stat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stat.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stat.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    config.lastUpdateTime?.let { time ->
                        val dateStr = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.getDefault()
                        ).format(Date(time))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.settings_last_update),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 업데이트 버튼
            Button(
                onClick = onUpdateNow,
                enabled = !config.isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (config.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_updating))
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_update_now))
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentHour = config.updateHour,
            currentMinute = config.updateMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            }
        )
    }
}

/**
 * 종목 DB 자동 업데이트 카드
 */
@Composable
fun StockUpdateCard(
    settings: StockUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_stock_update),
        icon = Icons.Default.Schedule,
        description = stringResource(R.string.settings_stock_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_stock_count), stringResource(R.string.label_etf_count_unit, settings.stockCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 증시 자금 DB 자동 업데이트 카드
 */
@Composable
fun MarketDepositUpdateCard(
    settings: MarketDepositUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_deposit_update),
        icon = Icons.Default.TrendingUp,
        description = stringResource(R.string.settings_deposit_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_deposit_count), stringResource(R.string.label_etf_count_unit, settings.depositCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * Fear & Greed Index DB 자동 업데이트 카드
 */
@Composable
fun FearGreedUpdateCard(
    settings: FearGreedUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_feargreed_update),
        icon = Icons.Default.Psychology,
        description = stringResource(R.string.settings_feargreed_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_kospi_count), stringResource(R.string.label_etf_count_unit, settings.kospiCount)),
            StatItem(stringResource(R.string.settings_kosdaq_count), stringResource(R.string.label_etf_count_unit, settings.kosdaqCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 시장 오실레이터 DB 자동 업데이트 카드
 */
@Composable
fun MarketOscillatorUpdateCard(
    settings: MarketOscillatorUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_oscillator_update),
        icon = Icons.Default.ShowChart,
        description = stringResource(R.string.settings_oscillator_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_kospi_count), stringResource(R.string.label_etf_count_unit, settings.kospiCount)),
            StatItem(stringResource(R.string.settings_kosdaq_count), stringResource(R.string.label_etf_count_unit, settings.kosdaqCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 시장 지수 DB 자동 업데이트 카드
 */
@Composable
fun MarketIndexUpdateCard(
    settings: MarketIndexUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_market_index_update),
        icon = Icons.Default.Analytics,
        description = stringResource(R.string.settings_market_index_update_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_kospi_count), stringResource(R.string.label_etf_count_unit, settings.kospiCount)),
            StatItem(stringResource(R.string.settings_kosdaq_count), stringResource(R.string.label_etf_count_unit, settings.kosdaqCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * ETF 데이터 자동 업데이트 카드
 * 다른 데이터 업데이트 카드와 동일한 형식
 * 참고: ETF 데이터 초기화는 DatabaseCard의 데이터베이스 초기화에서 지원됨
 */
@Composable
fun EtfDataManagementCard(
    settings: EtfUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = stringResource(R.string.settings_etf_data_management),
        icon = Icons.Default.CloudDownload,
        description = stringResource(R.string.settings_etf_data_management_desc),
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem(stringResource(R.string.settings_etf_count), stringResource(R.string.label_etf_count_unit, settings.etfCount)),
            StatItem(stringResource(R.string.settings_holding_count), stringResource(R.string.label_etf_count_unit, settings.holdingCount))
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * Blood Indicator 자동 업데이트 카드
 * US Treasury 기반 시장 건강도 지표
 */
@Composable
fun BloodIndicatorUpdateCard(
    settings: BloodIndicatorUpdateSettings,
    onTimeChange: (Int, Int) -> Unit,
    onUpdateNow: () -> Unit
) {
    val config = DataUpdateCardConfig(
        title = "Blood Indicator",
        icon = Icons.Default.Bloodtype,
        description = "US Treasury 기반 시장 건강도 지표 (미국 시장 마감 후 업데이트 권장)",
        updateHour = settings.updateHour,
        updateMinute = settings.updateMinute,
        lastUpdateTime = settings.lastUpdateTime,
        isUpdating = settings.isUpdating,
        stats = listOf(
            StatItem("데이터 수", "${settings.dataCount}개")
        )
    )

    DataUpdateCard(
        config = config,
        onTimeChange = onTimeChange,
        onUpdateNow = onUpdateNow
    )
}

/**
 * 시간 선택 다이얼로그
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    currentHour: Int,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = currentHour,
        initialMinute = currentMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_update_time_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
