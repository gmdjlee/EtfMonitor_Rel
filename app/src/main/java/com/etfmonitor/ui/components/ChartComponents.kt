@file:Suppress("unused")

package com.etfmonitor.ui.components

/**
 * Chart Components - Re-exports for backward compatibility
 *
 * This file re-exports all chart components from their new locations:
 * - ChartUtils.kt: ChartCard, ChartColorViewModel, InvertedTriangleShapeRenderer
 * - MarketCharts.kt: MarketCapOscillatorChart, MarketDepositChart
 * - TechnicalCharts.kt: MacdChart, TrendSignalChart, ElderImpulseChart, DemarkTDChart
 *
 * Usage remains unchanged - import from this package:
 * import com.etfmonitor.ui.components.MarketCapOscillatorChart
 * import com.etfmonitor.ui.components.ChartCard
 */

// All public components are now available through their respective files:
// - ChartUtils.kt: ChartCard, ChartColorViewModel, InvertedTriangleShapeRenderer, CHART_TAG
// - MarketCharts.kt: MarketCapOscillatorChart, MarketDepositChart
// - TechnicalCharts.kt: MacdChart, TrendSignalChart, ElderImpulseChart, DemarkTDChart

// MarkerView classes remain here for backward compatibility
import android.content.Context
import android.widget.TextView
import com.etfmonitor.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

/**
 * 시가총액 차트용 마커 뷰
 */
class MarketCapMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>
) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            val index = entry.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else ""
            val value = entry.y

            val billions = (value / 100_000_000).toLong()
            val formattedValue = when {
                billions >= 10000 -> "${billions / 10000}조 ${(billions % 10000) / 1000}천억"
                billions >= 1000 -> String.format("%.2f조", billions / 10000f)
                else -> "${billions}억"
            }

            tvContent.text = "$date\n$formattedValue"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

/**
 * MACD 차트용 마커 뷰
 */
class MacdMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>,
    private val macdValues: List<Double>,
    private val signalValues: List<Double>
) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            val index = entry.x.toInt()
            if (index >= 0 && index < dates.size) {
                val date = dates[index]
                val macd = if (index < macdValues.size) String.format("%.2f", macdValues[index]) else "N/A"
                val signal = if (index < signalValues.size) String.format("%.2f", signalValues[index]) else "N/A"
                val histogram = entry.y

                tvContent.text = "$date\nMACD: $macd\nSignal: $signal\nHist: ${String.format("%.2f", histogram)}"
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}

/**
 * 커스텀 마커 뷰 (범용)
 */
class CustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val dates: List<String>,
    private val valueFormatter: (Float) -> String
) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            val index = entry.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else ""
            val formattedValue = valueFormatter(entry.y)

            tvContent.text = "$date\n$formattedValue"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
