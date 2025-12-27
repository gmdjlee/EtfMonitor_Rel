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
    var error: Throwable? by mutableStateOf(null)
        private set

    fun setError(throwable: Throwable) {
        error = throwable
    }

    fun clearError() {
        error = null
    }

    fun hasError(): Boolean = error != null
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
