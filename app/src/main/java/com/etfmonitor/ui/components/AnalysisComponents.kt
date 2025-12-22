package com.etfmonitor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import com.etfmonitor.oscillator.model.FearGreedState
import com.etfmonitor.oscillator.model.TrendSignalAnalysis
import com.etfmonitor.oscillator.model.TrendTradeSignal

/**
 * Analysis Components - Unified analysis UI templates
 *
 * This file contains reusable analysis-related composables:
 * - TrendSignalAnalysisCard: Card displaying trend signal analysis
 * - LabeledValueRow: Row with label and value pair
 * - SignalBadge: Badge displaying trading signal with color
 * - FearGreedBadge: Badge displaying Fear & Greed state
 * - PeriodToggleButton: Button for toggling between periods/intervals
 */

/**
 * Returns the display text and color for a TrendTradeSignal.
 */
@Composable
fun getSignalTextAndColor(signal: TrendTradeSignal): Pair<String, Color> {
    return when (signal) {
        TrendTradeSignal.STRONG_BUY -> stringResource(R.string.signal_strong_buy) to SignalColors.StrongBuy
        TrendTradeSignal.BUY -> stringResource(R.string.signal_buy) to SignalColors.Buy
        TrendTradeSignal.NEUTRAL -> stringResource(R.string.signal_neutral) to SignalColors.Neutral
        TrendTradeSignal.SELL -> stringResource(R.string.signal_sell) to SignalColors.Sell
        TrendTradeSignal.STRONG_SELL -> stringResource(R.string.signal_strong_sell) to SignalColors.StrongSell
    }
}

/**
 * Returns the color for a FearGreedState.
 */
fun getFearGreedColor(state: FearGreedState): Color {
    return when (state) {
        FearGreedState.EXTREME_FEAR -> SignalColors.StrongSell
        FearGreedState.FEAR -> SignalColors.Sell
        FearGreedState.NEUTRAL -> SignalColors.Neutral
        FearGreedState.GREED -> SignalColors.Buy
        FearGreedState.EXTREME_GREED -> SignalColors.StrongBuy
    }
}

/**
 * Predefined colors for trading signals.
 */
object SignalColors {
    val StrongBuy = Color(0xFF4CAF50)
    val Buy = Color(0xFF8BC34A)
    val Neutral = Color(0xFF9E9E9E)
    val Sell = Color(0xFFFF9800)
    val StrongSell = Color(0xFFF44336)
}

/**
 * Badge displaying a trading signal with appropriate color.
 *
 * @param signal The trading signal to display
 * @param modifier Modifier for the badge
 */
@Composable
fun SignalBadge(
    signal: TrendTradeSignal,
    modifier: Modifier = Modifier
) {
    val (signalText, signalColor) = getSignalTextAndColor(signal)

    Surface(
        modifier = modifier,
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

/**
 * Badge displaying Fear & Greed state with appropriate color.
 *
 * @param state The Fear & Greed state to display
 * @param modifier Modifier for the badge
 */
@Composable
fun FearGreedBadge(
    state: FearGreedState,
    modifier: Modifier = Modifier
) {
    val color = getFearGreedColor(state)

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = state.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * Simple row displaying a label and value pair.
 * Commonly used for displaying analysis metrics.
 *
 * @param label The label text on the left
 * @param value The value text on the right
 * @param modifier Modifier for the row
 */
@Composable
fun LabeledValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
 * Row displaying Fear & Greed value with state badge.
 *
 * @param value The Fear & Greed numeric value
 * @param modifier Modifier for the row
 */
@Composable
fun FearGreedRow(
    value: Double,
    modifier: Modifier = Modifier
) {
    val fearGreedState = FearGreedState.fromValue(value)
    val fearGreedColor = getFearGreedColor(fearGreedState)

    Row(
        modifier = modifier.fillMaxWidth(),
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
                String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            FearGreedBadge(state = fearGreedState)
        }
    }
}

/**
 * Card displaying comprehensive trend signal analysis.
 * Shows signal, description, indicators, Fear & Greed, and recommendation.
 *
 * @param analysis The trend signal analysis data
 * @param modifier Modifier for the card
 * @param title Optional custom title (defaults to trend analysis string)
 */
@Composable
fun TrendSignalAnalysisCard(
    analysis: TrendSignalAnalysis,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.oscillator_trend_analysis)
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + Signal Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                SignalBadge(signal = analysis.signal)
            }

            HorizontalDivider()

            // Trend description
            Text(
                analysis.trendDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Indicator values
            LabeledValueRow(
                label = stringResource(R.string.label_current_price),
                value = String.format("%,.0f", analysis.currentPrice)
            )
            LabeledValueRow(
                label = stringResource(R.string.label_ma),
                value = String.format("%,.0f", analysis.maPrice)
            )
            LabeledValueRow(
                label = stringResource(R.string.label_cmf),
                value = String.format("%.3f", analysis.cmfValue)
            )

            // Fear & Greed state
            FearGreedRow(value = analysis.fearGreedValue)

            // Signal counts
            LabeledValueRow(
                label = stringResource(R.string.label_recent_buy_signals),
                value = "${analysis.recentBuyCount}회"
            )
            LabeledValueRow(
                label = stringResource(R.string.label_recent_sell_signals),
                value = "${analysis.recentSellCount}회"
            )

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

/**
 * Toggle button for selecting periods/intervals.
 * Shows as filled button when selected, outlined when not.
 *
 * @param text Button text
 * @param isSelected Whether this button is currently selected
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 */
@Composable
fun PeriodToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier
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
