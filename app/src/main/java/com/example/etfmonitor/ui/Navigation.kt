package com.etfmonitor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.etfmonitor.ui.screens.detail.DetailScreen
import com.etfmonitor.ui.screens.home.HomeScreen
import com.etfmonitor.ui.screens.list.EtfListScreen
import com.etfmonitor.ui.screens.settings.SettingsScreen
import com.etfmonitor.ui.screens.statistics.AggregatedStockTrendScreen
import com.etfmonitor.ui.screens.statistics.StatisticsScreen
import com.etfmonitor.ui.screens.trend.StockTrendScreen

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
}

@Composable
fun Navigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToList = { navController.navigate(Screen.List.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }  // ✅ 추가
            )
        }

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
            DetailScreen(
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
                onStockClick = { stockTicker ->  // ✅ 추가
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
    }
}