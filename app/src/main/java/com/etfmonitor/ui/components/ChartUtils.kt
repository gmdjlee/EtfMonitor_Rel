package com.etfmonitor.ui.components

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
import com.etfmonitor.ui.theme.*
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
