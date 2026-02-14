package com.etfmonitor.core.data.krx.adapter

import com.krxkt.error.KrxError

/**
 * Maps kotlin_krx errors to standard Exceptions for Result.failure().
 * Simplified approach: no custom AppError sealed class needed.
 */
object KrxErrorMapper {
    fun toException(error: KrxError): Exception = when (error) {
        is KrxError.NetworkError -> Exception("Network error: ${error.message}", error)
        is KrxError.ParseError -> Exception("Data parsing error: ${error.message}", error)
        is KrxError.InvalidDateError -> IllegalArgumentException("Invalid date: ${error.date}", error)
    }
}
