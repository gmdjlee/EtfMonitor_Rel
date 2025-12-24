package com.etfmonitor.core.common.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Thread-safe date formatting utility
 * Uses DateTimeFormatter which is immutable and thread-safe (unlike SimpleDateFormat)
 */
object DateFormatter {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Format current date to "yyyy-MM-dd" format
     */
    fun formatToday(): String {
        return LocalDate.now().format(dateFormatter)
    }

    /**
     * Format a LocalDate to "yyyy-MM-dd" format
     */
    fun format(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    /**
     * Parse a string in "yyyy-MM-dd" format to LocalDate
     */
    fun parse(dateString: String): LocalDate {
        return LocalDate.parse(dateString, dateFormatter)
    }
}
