package com.etfmonitor.core.common.util

/**
 * Shared KRX network constants.
 *
 * KRX CDN (Akamai WAF) returns HTTP 403 after ~50-65 rapid requests.
 * All KRX data sources must throttle using these shared values.
 */
object KrxConstants {
    /**
     * Cooldown delay between KOSPI and KOSDAQ sequential runs to avoid
     * triggering the Akamai WAF rate limiter on KRX CDN.
     */
    const val KRX_RATE_LIMIT_COOLDOWN_MS = 15_000L
}
