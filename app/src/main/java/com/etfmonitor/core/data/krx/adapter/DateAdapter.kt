package com.etfmonitor.core.data.krx.adapter

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateAdapter {
    private val KRX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun toKrxFormat(date: LocalDate): String = date.format(KRX_FORMAT)
    fun fromKrxFormat(dateStr: String): LocalDate = LocalDate.parse(dateStr, KRX_FORMAT)
    fun today(): String = toKrxFormat(LocalDate.now())
}
