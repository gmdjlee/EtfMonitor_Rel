package com.etfmonitor.core.data.repository.krx

import com.etfmonitor.core.data.krx.adapter.DateAdapter
import com.etfmonitor.core.data.krx.adapter.KrxRepositoryBase
import com.etfmonitor.core.database.entities.MarketIndex
import com.krxkt.KrxIndex
import com.krxkt.model.IndexOhlcv
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KrxIndexRepositoryImpl @Inject constructor(
    private val krxIndex: KrxIndex
) : KrxRepositoryBase() {

    companion object {
        private const val KOSPI_TICKER = "1001"
        private const val KOSDAQ_TICKER = "2001"

        private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
    }

    /**
     * 지정된 기간 동안의 시장 지수 데이터 수집
     *
     * @param startDate 시작 날짜 (yyyyMMdd)
     * @param endDate 종료 날짜 (yyyyMMdd)
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return Result<List<MarketIndex>>
     */
    suspend fun getMarketIndices(
        startDate: String,
        endDate: String,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): Result<List<MarketIndex>> = krxCall {
        val allIndices = mutableListOf<MarketIndex>()

        for (market in markets) {
            val ohlcvList = when (market.uppercase()) {
                "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                else -> emptyList()
            }

            allIndices.addAll(ohlcvList.map { ohlcv ->
                toMarketIndex(ohlcv, market.uppercase())
            })
        }

        allIndices
    }

    /**
     * 최근 N일의 시장 지수 데이터 수집
     *
     * @param days 수집할 일수
     * @param markets 수집할 시장 목록 (기본값: ["KOSPI", "KOSDAQ"])
     * @return Result<List<MarketIndex>>
     */
    suspend fun getRecentMarketIndices(
        days: Int,
        markets: List<String> = listOf("KOSPI", "KOSDAQ")
    ): Result<List<MarketIndex>> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())

        val startDateStr = startDate.format(dateFormatter)
        val endDateStr = endDate.format(dateFormatter)

        return getMarketIndices(startDateStr, endDateStr, markets)
    }

    /**
     * kotlin_krx IndexOhlcv를 MarketIndex 엔티티로 변환
     *
     * @param ohlcv kotlin_krx IndexOhlcv 객체
     * @param marketName "KOSPI" 또는 "KOSDAQ"
     * @return MarketIndex 엔티티
     */
    private fun toMarketIndex(ohlcv: IndexOhlcv, marketName: String): MarketIndex {
        // yyyyMMdd → yyyy-MM-dd 변환
        val date = LocalDate.parse(ohlcv.date, dateFormatter)
        val isoDate = date.format(isoFormatter)

        // changeRate 계산: (change / prevClose) * 100
        // prevClose = close - change
        val change = ohlcv.change
        val changeRate = if (change != null && ohlcv.close > 0.0) {
            val prevClose = ohlcv.close - change
            if (prevClose > 0.0) {
                (change / prevClose) * 100.0
            } else {
                0.0
            }
        } else {
            0.0
        }

        return MarketIndex(
            id = "$marketName-$isoDate",
            market = marketName,
            date = isoDate,
            closePrice = ohlcv.close,
            openPrice = ohlcv.open,
            highPrice = ohlcv.high,
            lowPrice = ohlcv.low,
            volume = ohlcv.volume,
            changeRate = changeRate,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
