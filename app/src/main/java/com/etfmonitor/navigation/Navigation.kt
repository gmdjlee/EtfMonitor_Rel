package com.etfmonitor.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etfmonitor.core.ui.component.MainBottomNavigationBar
import com.etfmonitor.core.ui.component.MainNavItem
import com.etfmonitor.feature.etf.presentation.detail.EtfDetailScreen
import com.etfmonitor.feature.home.presentation.screen.HomeScreen
import com.etfmonitor.feature.etf.presentation.list.EtfListScreen
import com.etfmonitor.feature.settings.presentation.SettingsScreen
import com.etfmonitor.feature.stock.presentation.statistics.AggregatedStockTrendScreen
import com.etfmonitor.feature.stock.presentation.trend.StockTrendScreen
import com.etfmonitor.feature.stock.presentation.oscillator.OscillatorScreen
import com.etfmonitor.feature.market.presentation.deposit.MarketDepositScreen
import com.etfmonitor.feature.market.presentation.feargreed.FearGreedScreen
import com.etfmonitor.feature.market.presentation.oscillator.MarketOscillatorScreen
import com.etfmonitor.feature.analysis.presentation.aianalysis.NewAIAnalysisScreen
import com.etfmonitor.feature.analysis.presentation.advanced.AdvancedDashboardScreen
import com.etfmonitor.feature.market.presentation.hub.MarketIndicatorHubScreen
import com.etfmonitor.feature.etf.presentation.hub.EtfHubScreen
import com.etfmonitor.feature.stock.presentation.hub.StocksHubScreen
import com.etfmonitor.feature.analysis.presentation.hub.AnalysisHubScreen

sealed class Screen(val route: String) {
    // Main navigation tabs
    object Home : Screen("home")
    object MarketIndicator : Screen("market_indicator")
    object EtfHub : Screen("etf_hub?stockTicker={stockTicker}") {
        fun createRoute(stockTicker: String? = null) = if (stockTicker != null) {
            "etf_hub?stockTicker=$stockTicker"
        } else {
            "etf_hub"
        }
    }
    object Stocks : Screen("stocks?ticker={ticker}") {
        fun createRoute(ticker: String? = null) = if (ticker != null) {
            "stocks?ticker=$ticker"
        } else {
            "stocks"
        }
    }
    object Analysis : Screen("analysis")

    // Detail screens
    object List : Screen("list")
    object Detail : Screen("detail/{ticker}") {
        fun createRoute(ticker: String) = "detail/$ticker"
    }
    object Settings : Screen("settings")
    object StockTrend : Screen("trend/{etfTicker}/{stockTicker}") {
        fun createRoute(etfTicker: String, stockTicker: String) =
            "trend/$etfTicker/$stockTicker"
    }
    // 전체 ETF 통합 종목 추이
    object AggregatedStockTrend : Screen("aggregated_trend/{stockTicker}") {
        fun createRoute(stockTicker: String) = "aggregated_trend/$stockTicker"
    }
    // 수급 오실레이터 (차트 분석)
    object Oscillator : Screen("oscillator?ticker={ticker}") {
        fun createRoute(ticker: String? = null) = if (ticker != null) {
            "oscillator?ticker=$ticker"
        } else {
            "oscillator"
        }
    }
    // 증시 자금 동향
    object MarketDeposit : Screen("market_deposit")
    // Fear & Greed Index
    object FearGreed : Screen("fear_greed")
    // Market Oscillator (시장 과매수/과매도)
    object MarketOscillator : Screen("market_oscillator")
    // AI Analysis (AI 시장 분석)
    object AIAnalysis : Screen("ai_analysis")
    // 고급 분석 대시보드
    object AdvancedDashboard : Screen("advanced_dashboard")
}

// Routes that show bottom navigation (use base routes for pattern matching)
private val mainNavRoutes = setOf(
    Screen.Home.route,
    Screen.MarketIndicator.route,
    Screen.EtfHub.route,
    Screen.Stocks.route,
    Screen.Analysis.route
)

// Additional routes that show bottom navigation (ETF sub-screens and analysis)
private val etfSubRoutes = setOf(
    "detail",              // ETF Detail
    "aggregated_trend",    // Stock Trend from ETF Statistics
    "oscillator"           // Chart Analysis (차트 분석)
)

// Check if current route is a main nav route (handles routes with parameters)
private fun isMainNavRoute(currentRoute: String?): Boolean {
    if (currentRoute == null) return false

    // Check main navigation routes
    if (mainNavRoutes.any { route ->
        currentRoute == route || currentRoute.startsWith(route.substringBefore("?"))
    }) return true

    // Check ETF sub-routes (keep bottom nav when navigating within ETF section)
    return etfSubRoutes.any { prefix ->
        currentRoute.startsWith(prefix)
    }
}

@Composable
fun Navigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if we should show bottom navigation
    val showBottomNav = isMainNavRoute(currentRoute)

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                MainBottomNavigationBar(
                    currentRoute = currentRoute ?: Screen.Home.route,
                    onNavigate = { item ->
                        navController.navigate(item.route) {
                            // Pop up to the start destination to avoid building up stack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Only restore state for non-Home destinations
                            restoreState = item != MainNavItem.HOME
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            // =====================
            // Main Navigation Tabs
            // =====================

            composable(Screen.Home.route) {
                HomeScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToMarketIndicator = {
                        navController.navigate(Screen.MarketIndicator.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEtf = {
                        navController.navigate(Screen.EtfHub.createRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToStocks = {
                        navController.navigate(Screen.Stocks.createRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAnalysis = {
                        navController.navigate(Screen.Analysis.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.MarketIndicator.route) {
                MarketIndicatorHubScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(
                route = Screen.EtfHub.route,
                arguments = listOf(
                    navArgument("stockTicker") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val initialStockTicker = backStackEntry.arguments?.getString("stockTicker")
                EtfHubScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onEtfClick = { ticker ->
                        navController.navigate(Screen.Detail.createRoute(ticker))
                    },
                    onStockClick = { stockTicker ->
                        navController.navigate(Screen.AggregatedStockTrend.createRoute(stockTicker))
                    },
                    onNavigateToStocks = { ticker ->
                        // Navigate without restoring state to ensure fresh analysis with new ticker
                        navController.navigate(Screen.Stocks.createRoute(ticker)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Don't restore state - we want fresh analysis for the specified ticker
                            restoreState = false
                        }
                    },
                    initialStockTicker = initialStockTicker
                )
            }

            composable(
                route = Screen.Stocks.route,
                arguments = listOf(
                    navArgument("ticker") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val initialTicker = backStackEntry.arguments?.getString("ticker")
                StocksHubScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToStatistics = { ticker ->
                        // Navigate without restoring state to ensure fresh analysis with new ticker
                        navController.navigate(Screen.EtfHub.createRoute(ticker)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Don't restore state - we want fresh analysis for the specified ticker
                            restoreState = false
                        }
                    },
                    initialTicker = initialTicker
                )
            }

            composable(Screen.Analysis.route) {
                AnalysisHubScreen(
                    navController = navController,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToStocks = { ticker ->
                        // Navigate without restoring state to ensure fresh analysis with new ticker
                        navController.navigate(Screen.Stocks.createRoute(ticker)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Don't restore state - we want fresh analysis for the specified ticker
                            restoreState = false
                        }
                    }
                )
            }

            // =====================
            // Detail Screens
            // =====================

            composable(Screen.List.route) {
                EtfListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEtfClick = { ticker ->
                        navController.navigate(Screen.Detail.createRoute(ticker))
                    }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("ticker") { type = NavType.StringType })
            ) { backStackEntry ->
                val ticker = backStackEntry.arguments?.getString("ticker") ?: ""
                EtfDetailScreen(
                    etfTicker = ticker,
                    onNavigateBack = { navController.popBackStack() },
                    onStockClick = { stockTicker ->
                        navController.navigate(Screen.StockTrend.createRoute(ticker, stockTicker))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.StockTrend.route,
                arguments = listOf(
                    navArgument("etfTicker") { type = NavType.StringType },
                    navArgument("stockTicker") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val etfTicker = backStackEntry.arguments?.getString("etfTicker") ?: ""
                val stockTicker = backStackEntry.arguments?.getString("stockTicker") ?: ""
                StockTrendScreen(
                    etfTicker = etfTicker,
                    stockTicker = stockTicker,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOscillator = { ticker ->
                        navController.navigate(Screen.Oscillator.createRoute(ticker))
                    }
                )
            }

            // 통합 종목 추이 화면
            composable(
                route = Screen.AggregatedStockTrend.route,
                arguments = listOf(
                    navArgument("stockTicker") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val stockTicker = backStackEntry.arguments?.getString("stockTicker") ?: ""
                AggregatedStockTrendScreen(
                    stockTicker = stockTicker,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOscillator = { ticker ->
                        navController.navigate(Screen.Oscillator.createRoute(ticker))
                    }
                )
            }

            // 수급 오실레이터 화면 (차트 분석)
            composable(
                route = Screen.Oscillator.route,
                arguments = listOf(
                    navArgument("ticker") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val ticker = backStackEntry.arguments?.getString("ticker")
                OscillatorScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialTicker = ticker,
                    onNavigateToStatistics = { stockTicker ->
                        // Navigate without restoring state to ensure fresh analysis with new ticker
                        navController.navigate(Screen.EtfHub.createRoute(stockTicker)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Don't restore state - we want fresh analysis for the specified ticker
                            restoreState = false
                        }
                    }
                )
            }

            // 증시 자금 동향 화면
            composable(Screen.MarketDeposit.route) {
                MarketDepositScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Fear & Greed Index 화면
            composable(Screen.FearGreed.route) {
                FearGreedScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Market Oscillator 화면 (시장 과매수/과매도)
            composable(Screen.MarketOscillator.route) {
                MarketOscillatorScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // AI Analysis 화면 (AI 시장 분석 - 새 버전)
            composable(Screen.AIAnalysis.route) {
                NewAIAnalysisScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOscillator = { ticker ->
                        navController.navigate(Screen.Oscillator.createRoute(ticker))
                    }
                )
            }

            // 고급 분석 대시보드 화면
            composable(Screen.AdvancedDashboard.route) {
                AdvancedDashboardScreen(
                    navController = navController
                )
            }
        }
    }
}
