package com.etfmonitor.ui.components

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.etfmonitor.R

private const val TAG = "CustomMarkerView"

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
                Log.e(TAG, "CustomMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CustomMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                Log.w(TAG, "CustomMarkerView: Entry is null")
                return
            }

            val index = e.x.toInt()
            val date = if (index >= 0 && index < dates.size) dates[index] else "N/A"

            val formattedValue = try {
                formatter(e.y)
            } catch (ex: Exception) {
                Log.e(TAG, "CustomMarkerView: Formatter error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$formattedValue"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            Log.e(TAG, "CustomMarkerView refreshContent error", e)
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
                Log.e(TAG, "MarketCapMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MarketCapMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                Log.w(TAG, "MarketCapMarkerView: Entry is null")
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
                Log.e(TAG, "MarketCapMarkerView: Format error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$formattedValue"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            Log.e(TAG, "MarketCapMarkerView refreshContent error", e)
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
            Log.e(TAG, "formatCurrency error", e)
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
            Log.e(TAG, "formatRatio error", e)
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
                Log.e(TAG, "MacdMarkerView: TextView not found in layout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MacdMarkerView init error", e)
        }
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        try {
            if (e == null) {
                Log.w(TAG, "MacdMarkerView: Entry is null")
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
                    Log.w(TAG, "MacdMarkerView: Index out of range - index=$index, macd.size=${macdValues.size}, signal.size=${signalValues.size}")
                    "N/A"
                }
            } catch (ex: Exception) {
                Log.e(TAG, "MacdMarkerView: Format error", ex)
                "Error"
            }

            tvContent?.text = "$date\n$macdText"
            super.refreshContent(e, highlight)
        } catch (e: Exception) {
            Log.e(TAG, "MacdMarkerView refreshContent error", e)
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat())
    }
}
