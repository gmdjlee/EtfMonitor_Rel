package com.etfmonitor.feature.ranking.data.repository

internal object RankingParseUtils {

    private val TICKER_SUFFIXES = listOf("_AL", "_KS", "_KQ")

    fun cleanTicker(value: String?): String =
        value?.let { ticker ->
            TICKER_SUFFIXES.fold(ticker) { acc, suffix -> acc.replace(suffix, "") }.trim()
        } ?: ""

    fun parseLong(value: String?): Long =
        value?.replace(",", "")?.replace("+", "")?.trim()?.toLongOrNull() ?: 0

    fun parseDouble(value: String?): Double =
        value?.replace(",", "")?.replace("+", "")?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0

    fun parseSign(value: String?): String = when (value?.trim()) {
        "1", "2", "+" -> "+"
        "4", "5", "-" -> "-"
        else -> ""
    }
}
