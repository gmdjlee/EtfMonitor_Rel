package com.etfmonitor.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.window.core.layout.WindowWidthSizeClass
import com.etfmonitor.ui.Screen
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
 * Navigation Type based on window size
 */
enum class NavigationType {
    BOTTOM_NAVIGATION,  // Compact (phones)
    NAVIGATION_RAIL,    // Medium (tablets)
    PERMANENT_DRAWER    // Expanded (desktops)
}

/**
 * Adaptive Navigation Scaffold
 * Material3 canonical layout with automatic adaptation
 */
@Composable
fun AdaptiveNavigationScaffold(
    currentDestination: NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val navigationType = when (windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> NavigationType.BOTTOM_NAVIGATION
        WindowWidthSizeClass.MEDIUM -> NavigationType.NAVIGATION_RAIL
        else -> NavigationType.PERMANENT_DRAWER
    }

    // Determine current selected destination
    val selectedDestination = topLevelDestinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any {
            it.route == destination.route
        } == true
    }

    when (navigationType) {
        NavigationType.BOTTOM_NAVIGATION -> {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        topLevelDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = selectedDestination == destination,
                                onClick = { onNavigateToDestination(destination) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                },
                modifier = modifier
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    content()
                }
            }
        }

        NavigationType.NAVIGATION_RAIL -> {
            Row(modifier = modifier) {
                NavigationRail {
                    Spacer(Modifier.weight(1f))
                    topLevelDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = selectedDestination == destination,
                            onClick = { onNavigateToDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }

        NavigationType.PERMANENT_DRAWER -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet {
                        Spacer(Modifier.height(12.dp))
                        topLevelDestinations.forEach { destination ->
                            NavigationDrawerItem(
                                selected = selectedDestination == destination,
                                onClick = { onNavigateToDestination(destination) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                },
                modifier = modifier
            ) {
                content()
            }
        }
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
