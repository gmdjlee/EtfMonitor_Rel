package com.etfmonitor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etfmonitor.ui.adaptive.AdaptiveNavigationScaffold
import com.etfmonitor.ui.adaptive.navigateToTopLevelDestination
import com.etfmonitor.ui.adaptive.topLevelDestinations
import com.etfmonitor.ui.screens.detail.DetailScreen
import com.etfmonitor.ui.screens.home.HomeScreen
import com.etfmonitor.ui.screens.list.EtfListScreen
import com.etfmonitor.ui.screens.list.EtfListDetailScreen
import com.etfmonitor.ui.screens.settings.SettingsScreen
import com.etfmonitor.ui.screens.statistics.AggregatedStockTrendScreen
import com.etfmonitor.ui.screens.statistics.StatisticsScreen
import com.etfmonitor.ui.screens.trend.StockTrendScreen
import com.etfmonitor.ui.screens.oscillator.OscillatorScreen
import com.etfmonitor.ui.screens.oscillator.MarketDepositScreen
import com.etfmonitor.ui.screens.feargreed.FearGreedScreen
import com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object List : Screen("list")
    object Detail : Screen("detail/{ticker}") {
        fun createRoute(ticker: String) = "detail/$ticker"
    }
    object Settings : Screen("settings")
    object StockTrend : Screen("trend/{etfTicker}/{stockTicker}") {
        fun createRoute(etfTicker: String, stockTicker: String) =
            "trend/$etfTicker/$stockTicker"
    }
    object Statistics : Screen("statistics")
    // ✅ 전체 ETF 통합 종목 추이
    object AggregatedStockTrend : Screen("aggregated_trend/{stockTicker}") {
        fun createRoute(stockTicker: String) = "aggregated_trend/$stockTicker"
    }
    // ✅ 수급 오실레이터 (차트 분석)
    object Oscillator : Screen("oscillator")
    // ✅ 증시 자금 동향
    object MarketDeposit : Screen("market_deposit")
    // ✅ Fear & Greed Index
    object FearGreed : Screen("fear_greed")
    // ✅ Market Oscillator (시장 과매수/과매도)
    object MarketOscillator : Screen("market_oscillator")
}

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    // Determine if we should show navigation for current destination
    val topLevelRoutes = topLevelDestinations.map { it.route }
    val showNavigation = currentDestination?.route in topLevelRoutes

    if (showNavigation) {
        AdaptiveNavigationScaffold(
            currentDestination = currentDestination,
            onNavigateToDestination = { destination ->
                navController.navigateToTopLevelDestination(destination)
            }
        ) {
            NavigationContent(navController = navController)
        }
    } else {
        NavigationContent(navController = navController)
    }
}

@Composable
private fun NavigationContent(navController: androidx.navigation.NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToList = { navController.navigate(Screen.List.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) },
                onNavigateToOscillator = { navController.navigate(Screen.Oscillator.route) },
                onNavigateToMarketDeposit = { navController.navigate(Screen.MarketDeposit.route) },
                onNavigateToFearGreed = { navController.navigate(Screen.FearGreed.route) },
                onNavigateToMarketOscillator = { navController.navigate(Screen.MarketOscillator.route) }
            )
        }

        // ✅ ETF List-Detail with Adaptive Supporting Pane
        composable(Screen.List.route) {
            EtfListDetailScreen(
                onNavigateToStockTrend = { etfTicker, stockTicker ->
                    navController.navigate(Screen.StockTrend.createRoute(etfTicker, stockTicker))
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
                onStockClick = { stockTicker ->
                    navController.navigate(Screen.AggregatedStockTrend.createRoute(stockTicker))
                }
            )
        }

        // ✅ 통합 종목 추이 화면
        composable(
            route = Screen.AggregatedStockTrend.route,
            arguments = listOf(
                navArgument("stockTicker") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val stockTicker = backStackEntry.arguments?.getString("stockTicker") ?: ""
            AggregatedStockTrendScreen(
                stockTicker = stockTicker,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ✅ 수급 오실레이터 화면 (차트 분석)
        composable(Screen.Oscillator.route) {
            OscillatorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ✅ 증시 자금 동향 화면
        composable(Screen.MarketDeposit.route) {
            MarketDepositScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ✅ Fear & Greed Index 화면
        composable(Screen.FearGreed.route) {
            FearGreedScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ✅ Market Oscillator 화면 (시장 과매수/과매도)
        composable(Screen.MarketOscillator.route) {
            MarketOscillatorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
