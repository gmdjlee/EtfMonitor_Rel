package com.etfmonitor.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.etfmonitor.ui.theme.elevation
import com.etfmonitor.ui.theme.spacing

/**
 * Material Design 3 Supporting Pane Layouts
 * Canonical layouts with automatic adaptation based on window size
 */

/**
 * List-Detail Pane Scaffold with adaptive layout
 * Automatically shows list and detail side-by-side on large screens
 * and as separate screens on small screens
 */
@Composable
fun <T> AdaptiveListDetailLayout(
    selectedItem: T?,
    onItemSelected: (T?) -> Unit,
    listContent: @Composable (onItemClick: (T) -> Unit) -> Unit,
    detailContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    emptyDetailContent: @Composable () -> Unit = {
        EmptyDetailPane()
    }
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val showDetailInline = windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    if (showDetailInline) {
        // Side-by-side layout for medium/large screens
        Row(modifier = modifier.fillMaxSize()) {
            // List pane (1/3 width)
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                listContent { item ->
                    onItemSelected(item)
                }
            }

            // Divider
            VerticalDivider()

            // Detail pane (2/3 width)
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                if (selectedItem != null) {
                    detailContent(selectedItem)
                } else {
                    emptyDetailContent()
                }
            }
        }
    } else {
        // Single pane layout for compact screens
        Box(modifier = modifier.fillMaxSize()) {
            // Show list by default
            if (selectedItem == null) {
                listContent { item ->
                    onItemSelected(item)
                }
            } else {
                // Show detail when item is selected
                BackHandler {
                    onItemSelected(null)
                }
                detailContent(selectedItem)
            }
        }
    }
}

/**
 * Supporting Pane Scaffold for main + supporting content
 * Used for screens with primary content and contextual supporting information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSupportingPaneLayout(
    showSupportingPane: Boolean,
    onSupportingPaneDismiss: () -> Unit,
    mainContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val showSupportingInline = windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    if (showSupportingInline && showSupportingPane) {
        // Side-by-side layout for medium/large screens
        Row(modifier = modifier.fillMaxSize()) {
            // Main content (2/3 width)
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                mainContent()
            }

            // Divider
            VerticalDivider()

            // Supporting pane (1/3 width)
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = MaterialTheme.elevation.level1
                ) {
                    supportingContent()
                }
            }
        }
    } else {
        // Single pane layout for compact screens
        Box(modifier = modifier.fillMaxSize()) {
            mainContent()

            // Show supporting pane as modal on compact screens
            if (showSupportingPane) {
                BackHandler {
                    onSupportingPaneDismiss()
                }

                ModalBottomSheet(
                    onDismissRequest = onSupportingPaneDismiss
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                    ) {
                        supportingContent()
                    }
                }
            }
        }
    }
}

/**
 * Empty detail pane shown when no item is selected
 */
@Composable
private fun EmptyDetailPane() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "항목을 선택하세요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "목록에서 항목을 선택하면\n상세 정보가 여기에 표시됩니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
