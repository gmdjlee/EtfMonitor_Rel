package com.etfmonitor.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etfmonitor.ui.theme.elevation
import com.etfmonitor.ui.theme.spacing

/**
 * Material Design 3 Supporting Pane Layouts
 * Canonical layouts with automatic adaptation based on window size
 */

/**
 * List-Detail Pane State
 */
enum class ListDetailPaneContent {
    List,
    Detail
}

/**
 * List-Detail Pane Scaffold with adaptive layout
 * Automatically shows list and detail side-by-side on large screens
 * and as separate screens on small screens
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
    val navigator = rememberListDetailPaneScaffoldNavigator<T>()

    // Sync external state with internal navigator state
    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedItem)
        } else {
            navigator.navigateTo(ListDetailPaneScaffoldRole.List)
        }
    }

    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
        onItemSelected(null)
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                listContent { item ->
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item)
                    onItemSelected(item)
                }
            }
        },
        detailPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                val currentItem = navigator.currentDestination?.content
                if (currentItem != null) {
                    detailContent(currentItem)
                } else {
                    emptyDetailContent()
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Supporting Pane Scaffold for main + supporting content
 * Used for screens with primary content and contextual supporting information
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveSupportingPaneLayout(
    showSupportingPane: Boolean,
    onSupportingPaneDismiss: () -> Unit,
    mainContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = rememberSupportingPaneScaffoldNavigator()

    // Sync external state with navigator
    LaunchedEffect(showSupportingPane) {
        if (showSupportingPane) {
            navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
        } else {
            navigator.navigateTo(SupportingPaneScaffoldRole.Main)
        }
    }

    BackHandler(navigator.canNavigateBack()) {
        navigator.navigateBack()
        onSupportingPaneDismiss()
    }

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                mainContent()
            }
        },
        supportingPane = {
            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = MaterialTheme.elevation.level1
                ) {
                    supportingContent()
                }
            }
        },
        modifier = modifier
    )
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
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.TouchApp,
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * BackHandler for adaptive navigation
 */
@Composable
private fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
