package com.etfmonitor.core.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Service pause card for pykrx-dependent features.
 * Displayed when a feature is temporarily disabled due to pykrx service termination.
 *
 * @param title Feature name that is paused
 * @param reason Reason for the pause
 * @param alternativeMessage Optional message about alternative or next steps
 */
@Composable
fun ServicePauseCard(
    title: String,
    reason: String = "pykrx 서비스 종료로 인해 일시 중단되었습니다.",
    alternativeMessage: String? = "KIS API로 전환 작업 진행 중입니다.",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            alternativeMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Full screen service pause message.
 * Use this when the entire screen functionality is disabled.
 */
@Composable
fun ServicePauseScreen(
    title: String,
    reason: String = "pykrx 서비스 종료로 인해 이 기능이 일시 중단되었습니다.",
    alternativeMessage: String? = "KIS API 전환 후 다시 이용 가능합니다.",
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ServicePauseCard(
            title = title,
            reason = reason,
            alternativeMessage = alternativeMessage
        )

        if (onNavigateBack != null) {
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(onClick = onNavigateBack) {
                Text("돌아가기")
            }
        }
    }
}

/**
 * Predefined pause messages for different features.
 */
object ServicePauseMessages {
    const val ETF_DATA = "ETF 데이터 수집"
    const val ETF_REASON = "pykrx 서비스 종료로 ETF 보유 종목 데이터 수집이 일시 중단되었습니다."

    const val FEAR_GREED = "Fear & Greed Index"
    const val FEAR_GREED_REASON = "pykrx 서비스 종료로 Fear & Greed Index 계산이 일시 중단되었습니다."

    const val MARKET_OSCILLATOR = "시장 과매수/과매도"
    const val MARKET_OSCILLATOR_REASON = "pykrx 서비스 종료로 시장 과매수/과매도 지표 수집이 일시 중단되었습니다."

    const val TREND_SIGNAL = "추세 시그널 분석"
    const val TREND_SIGNAL_REASON = "pykrx 서비스 종료로 추세 시그널 분석이 일시 중단되었습니다."

    const val MARKET_INDEX = "시장 지수"
    const val MARKET_INDEX_REASON = "pykrx 서비스 종료로 시장 지수 데이터 수집이 일시 중단되었습니다."

    const val ALTERNATIVE_KIS = "KIS API로 전환 작업 진행 중입니다."
    const val ALTERNATIVE_WAITING = "대체 데이터 소스 업데이트 대기 중입니다."
}
