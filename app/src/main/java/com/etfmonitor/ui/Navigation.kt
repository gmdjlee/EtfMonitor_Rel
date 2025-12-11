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
import com.etfmonitor.ui.screens.oscillator.OscillatorScreen
import com.etfmonitor.ui.screens.oscillator.MarketDepositScreen
import com.etfmonitor.ui.screens.feargreed.FearGreedScreen
import com.etfmonitor.ui.screens.marketoscillator.MarketOscillatorScreen
import com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisScreen
import com.etfmonitor.ui.screens.prediction.PredictionScreen
import com.etfmonitor.ui.screens.advanced.AdvancedDashboardScreen

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
    object Statistics : Screen("statistics?stockTicker={stockTicker}") {
        fun createRoute(stockTicker: String? = null) = if (stockTicker != null) {
            "statistics?stockTicker=$stockTicker"
        } else {
            "statistics"
        }
    }
    // ✅ 전체 ETF 통합 종목 추이
    object AggregatedStockTrend : Screen("aggregated_trend/{stockTicker}") {
        fun createRoute(stockTicker: String) = "aggregated_trend/$stockTicker"
    }
    // ✅ 수급 오실레이터 (차트 분석)
    object Oscillator : Screen("oscillator?ticker={ticker}") {
        fun createRoute(ticker: String? = null) = if (ticker != null) {
            "oscillator?ticker=$ticker"
        } else {
            "oscillator"
        }
    }
    // ✅ 증시 자금 동향
    object MarketDeposit : Screen("market_deposit")
    // ✅ Fear & Greed Index
    object FearGreed : Screen("fear_greed")
    // ✅ Market Oscillator (시장 과매수/과매도)
    object MarketOscillator : Screen("market_oscillator")
    // ✅ AI Analysis (AI 시장 분석)
    object AIAnalysis : Screen("ai_analysis")
    // ✅ ML 주가 예측
    object Prediction : Screen("prediction")
    // ✅ 고급 분석 대시보드
    object AdvancedDashboard : Screen("advanced_dashboard")
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
                onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) },
                onNavigateToOscillator = { navController.navigate(Screen.Oscillator.createRoute()) },
                onNavigateToMarketDeposit = { navController.navigate(Screen.MarketDeposit.route) },
                onNavigateToFearGreed = { navController.navigate(Screen.FearGreed.route) },
                onNavigateToMarketOscillator = { navController.navigate(Screen.MarketOscillator.route) },
                onNavigateToAIAnalysis = { navController.navigate(Screen.AIAnalysis.route) },
                onNavigateToPrediction = { navController.navigate(Screen.Prediction.route) },
                onNavigateToAdvancedDashboard = { navController.navigate(Screen.AdvancedDashboard.route) }
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOscillator = { ticker ->
                    navController.navigate(Screen.Oscillator.createRoute(ticker))
                }
            )
        }

        composable(
            route = Screen.Statistics.route,
            arguments = listOf(
                navArgument("stockTicker") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val initialStockTicker = backStackEntry.arguments?.getString("stockTicker")
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
                onStockClick = { stockTicker ->
                    navController.navigate(Screen.AggregatedStockTrend.createRoute(stockTicker))
                },
                onNavigateToOscillator = { ticker ->
                    navController.navigate(Screen.Oscillator.createRoute(ticker))
                },
                initialStockTicker = initialStockTicker
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOscillator = { ticker ->
                    navController.navigate(Screen.Oscillator.createRoute(ticker))
                }
            )
        }

        // ✅ 수급 오실레이터 화면 (차트 분석)
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
                    navController.navigate(Screen.Statistics.createRoute(stockTicker))
                }
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

        // ✅ AI Analysis 화면 (AI 시장 분석 - 새 버전)
        composable(Screen.AIAnalysis.route) {
            NewAIAnalysisScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOscillator = { ticker ->
                    navController.navigate(Screen.Oscillator.createRoute(ticker))
                }
            )
        }

        // ✅ ML 주가 예측 화면
        composable(Screen.Prediction.route) {
            PredictionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ✅ 고급 분석 대시보드 화면
        composable(Screen.AdvancedDashboard.route) {
            AdvancedDashboardScreen(
                navController = navController
            )
        }
    }
}