package com.etfmonitor.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.etfmonitor.ui.Screen
import com.etfmonitor.ui.theme.elevation
import com.etfmonitor.ui.theme.extendedShapes
import com.etfmonitor.ui.theme.spacing

/**
 * Material Design 3 Adaptive Navigation
 * Automatically adapts between BottomBar, NavigationRail, and Drawer
 * based on screen size and window class
 */

/**
 * Top-level navigation destinations
 */
sealed class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    data object Home : TopLevelDestination(
        route = Screen.Home.route,
        icon = Icons.Default.Home,
        label = "홈"
    )

    data object List : TopLevelDestination(
        route = Screen.List.route,
        icon = Icons.AutoMirrored.Filled.List,
        label = "ETF 목록"
    )

    data object Statistics : TopLevelDestination(
        route = Screen.Statistics.route,
        icon = Icons.Default.Analytics,
        label = "통계"
    )

    data object Oscillator : TopLevelDestination(
        route = Screen.Oscillator.route,
        icon = Icons.Default.ShowChart,
        label = "종목 수급"
    )

    data object MarketDeposit : TopLevelDestination(
        route = Screen.MarketDeposit.route,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        label = "자금 동향"
    )

    data object FearGreed : TopLevelDestination(
        route = Screen.FearGreed.route,
        icon = Icons.Default.BarChart,
        label = "F&G Index"
    )

    data object MarketOscillator : TopLevelDestination(
        route = Screen.MarketOscillator.route,
        icon = Icons.Default.Speed,
        label = "시장 지표"
    )

    data object Settings : TopLevelDestination(
        route = Screen.Settings.route,
        icon = Icons.Default.Settings,
        label = "설정"
    )
}

val topLevelDestinations = listOf(
    TopLevelDestination.Home,
    TopLevelDestination.List,
    TopLevelDestination.Statistics,
    TopLevelDestination.Oscillator,
    TopLevelDestination.MarketDeposit,
    TopLevelDestination.FearGreed,
    TopLevelDestination.MarketOscillator,
    TopLevelDestination.Settings
)

/**
 * Adaptive Navigation Suite Scaffold
 * Material3 canonical layout with automatic adaptation
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun AdaptiveNavigationScaffold(
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Determine current selected destination
    val selectedDestination = topLevelDestinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any {
            it.route == destination.route
        } == true
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                val selected = selectedDestination == destination

                item(
                    selected = selected,
                    onClick = { onNavigateToDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = {
                        Text(destination.label)
                    },
                    colors = NavigationSuiteItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Navigate to top-level destination with proper back stack handling
 */
fun NavController.navigateToTopLevelDestination(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Pop up to the start destination to avoid building a large stack
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination
        launchSingleTop = true
        // Restore state when re-selecting a previously selected destination
        restoreState = true
    }
}
