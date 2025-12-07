package com.etfmonitor.ui.screens.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etfmonitor.R
import kotlin.math.abs

/**
 * Advanced Dashboard Screen - Common Components
 * Contains shared colors, utilities and common composables used across tabs
 */

// 색상 정의
internal val GreenPositive = Color(0xFF4CAF50)
internal val RedNegative = Color(0xFFF44336)
internal val BlueAccent = Color(0xFF2196F3)
internal val OrangeAccent = Color(0xFFFF9800)

// ==================== 공통 컴포넌트 ====================

@Composable
internal fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.advanced_data_loading))
        }
    }
}

@Composable
internal fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.advanced_retry)) }
        }
    }
}

@Composable
internal fun EmptyStateCard(message: String, icon: ImageVector) {
    EmptyStateCard(message, icon, null, null)
}

@Composable
internal fun EmptyStateCard(
    message: String,
    icon: ImageVector,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accentColor != null) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun MetricCard(modifier: Modifier, title: String, value: String, isPositive: Boolean?) {
    val backgroundColor = when (isPositive) {
        true -> GreenPositive.copy(alpha = 0.1f)
        false -> RedNegative.copy(alpha = 0.1f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (isPositive) {
        true -> GreenPositive
        false -> RedNegative
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==================== 유틸리티 ====================

internal fun formatAmount(amount: Long): String = when {
    abs(amount) >= 10000 -> String.format("%.1f조", amount / 10000.0)
    abs(amount) >= 1000 -> String.format("%.0f억", amount.toDouble())
    else -> "${amount}억"
}

internal fun formatMarketCap(cap: Long): String = when {
    cap >= 10000 -> String.format("%.0f조", cap / 10000.0)
    cap >= 1000 -> String.format("%.1f조", cap / 10000.0)
    else -> "${cap}억"
}

internal fun formatTrillion(amount: Double): String = String.format("%.1f조", amount / 10000)

internal fun getFearGreedColor(value: Double): Color = when {
    value > 0.8 -> Color(0xFF1B5E20)
    value > 0.6 -> GreenPositive
    value > 0.4 -> OrangeAccent
    value > 0.2 -> Color(0xFFE65100)
    else -> RedNegative
}
