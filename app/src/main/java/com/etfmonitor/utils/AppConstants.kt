package com.etfmonitor.utils

/**
 * Application-wide constants for EtfMonitor.
 * Centralizes magic numbers and configuration values for maintainability.
 */
object AppConstants {

    /**
     * Network timeout configurations (in milliseconds)
     */
    object Timeout {
        const val PYTHON_CALL_MS = 30_000L
        const val API_CALL_SECONDS = 60L
        const val STOCK_PREDICTION_MS = 120_000L
        const val DEFAULT_HTTP_SECONDS = 60L
    }

    /**
     * Data collection period configurations (in days)
     */
    object DataPeriod {
        const val DEFAULT_DAYS = 365
        const val SHORT_PERIOD = 180
        const val MEDIUM_PERIOD = 365
        const val LONG_PERIOD = 540
        const val MAX_PERIOD = 730 // ~2 years, KRX API limit

        // Data collection multiplier to account for analysis data loss
        const val COLLECTION_MULTIPLIER = 3
    }

    /**
     * Analysis thresholds for technical indicators
     */
    object AnalysisThreshold {
        // Fear & Greed thresholds
        const val EXTREME_FEAR = 0.2
        const val FEAR = 0.35
        const val GREED = 0.65
        const val EXTREME_GREED = 0.8

        // Oscillator thresholds
        const val OSCILLATOR_OVERSOLD_EXTREME = -70
        const val OSCILLATOR_OVERSOLD = -30
        const val OSCILLATOR_OVERBOUGHT = 30
        const val OSCILLATOR_OVERBOUGHT_EXTREME = 70

        // ETF net flow thresholds
        const val ETF_FLOW_STRONG_BUY = 10
        const val ETF_FLOW_BUY = 5
        const val ETF_FLOW_NEUTRAL = 0

        // Cash deposit change rate thresholds
        const val CASH_DEPOSIT_STRONG_SELL = -2.0
        const val CASH_DEPOSIT_SELL = -0.5

        // Market deposit threshold
        const val MARKET_DEPOSIT_THRESHOLD = 1000L
    }

    /**
     * Weight change thresholds for ETF composition analysis
     */
    object WeightChange {
        const val THRESHOLD = 0.01f // 1% change threshold
        const val THRESHOLD_BPS = 100 // Basis points (100bp = 1%)
    }

    /**
     * UI debounce configurations (in milliseconds)
     */
    object Debounce {
        const val SEARCH_DELAY_MS = 300L
        const val CLICK_DELAY_MS = 500L
    }

    /**
     * Pagination and limit configurations
     */
    object Pagination {
        const val DEFAULT_PAGE_SIZE = 20
        const val ETF_LIST_LIMIT = 100
        const val RECENT_DATA_LIMIT = 365
    }

    /**
     * Data expiry configurations
     */
    object DataExpiry {
        const val FEAR_GREED_EXPIRY_HOURS = 12
        const val MARKET_DATA_EXPIRY_HOURS = 24
    }

    /**
     * Signal score weights for correlation analysis
     */
    object SignalWeight {
        const val ETF_FLOW_WEIGHT = 0.25
        const val CASH_DEPOSIT_WEIGHT = 0.20
        const val MARKET_DEPOSIT_WEIGHT = 0.15
        const val FEAR_GREED_WEIGHT = 0.20
        const val OSCILLATOR_WEIGHT = 0.20
    }

    /**
     * Chart display configurations
     */
    object Chart {
        const val DEFAULT_LINE_WIDTH = 2f
        const val HIGHLIGHT_LINE_WIDTH = 3f
        const val MARKER_TEXT_SIZE = 12f
        const val AXIS_TEXT_SIZE = 10f
    }

    /**
     * Validation limits
     */
    object Validation {
        const val MAX_CHAT_MESSAGE_LENGTH = 5000
        const val MAX_API_KEY_LENGTH = 200
        const val MIN_API_KEY_LENGTH = 10
    }
}

/**
 * Extension functions for working with constants
 */

/**
 * Check if a Fear & Greed value indicates extreme fear
 */
fun Double.isExtremeFear(): Boolean = this < AppConstants.AnalysisThreshold.EXTREME_FEAR

/**
 * Check if a Fear & Greed value indicates extreme greed
 */
fun Double.isExtremeGreed(): Boolean = this > AppConstants.AnalysisThreshold.EXTREME_GREED

/**
 * Get signal classification for Fear & Greed value
 */
fun Double.toFearGreedSignal(): String = when {
    this < AppConstants.AnalysisThreshold.EXTREME_FEAR -> "EXTREME_FEAR"
    this < AppConstants.AnalysisThreshold.FEAR -> "FEAR"
    this < AppConstants.AnalysisThreshold.GREED -> "NEUTRAL"
    this < AppConstants.AnalysisThreshold.EXTREME_GREED -> "GREED"
    else -> "EXTREME_GREED"
}

/**
 * Get signal classification for oscillator value
 */
fun Double.toOscillatorSignal(): String = when {
    this < AppConstants.AnalysisThreshold.OSCILLATOR_OVERSOLD_EXTREME -> "STRONG_BUY"
    this < AppConstants.AnalysisThreshold.OSCILLATOR_OVERSOLD -> "BUY"
    this > AppConstants.AnalysisThreshold.OSCILLATOR_OVERBOUGHT_EXTREME -> "STRONG_SELL"
    this > AppConstants.AnalysisThreshold.OSCILLATOR_OVERBOUGHT -> "SELL"
    else -> "NEUTRAL"
}
