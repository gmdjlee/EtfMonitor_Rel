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

    /**
     * Convert "yyyyMMdd" format to "yyyy-MM-dd" format
     */
    fun formatFromYYYYMMDD(dateStr: String): String {
        return try {
            if (dateStr.contains(" ")) {
                dateStr.substring(0, 10)  // Timestamp format
            } else if (dateStr.contains("-")) {
                dateStr  // Already YYYY-MM-DD
            } else if (dateStr.length == 8) {
                "${dateStr.substring(0, 4)}-${dateStr.substring(4, 6)}-${dateStr.substring(6, 8)}"
            } else {
                dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * Convert "yyyy-MM-dd" format to "MM/dd" for chart display
     */
    fun formatForChart(date: String): String {
        return try {
            val parts = date.split("-")
            if (parts.size == 3) {
                "${parts[1]}/${parts[2]}"
            } else {
                date
            }
        } catch (e: Exception) {
            date
        }
    }
}
