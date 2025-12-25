package com.etfmonitor.feature.stock.data.datasource

import com.etfmonitor.core.database.StockAnalysisDao
import com.etfmonitor.core.database.entities.StockAnalysisData
import com.etfmonitor.core.database.entities.StockAnalysisWithName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stock Analysis Local Data Source
 *
 * 종목 수급 분석 데이터에 대한 로컬 데이터 접근을 담당합니다.
 * StockAnalysisDao를 래핑하여 데이터 레이어에서 사용합니다.
 *
 * @property stockAnalysisDao Stock Analysis DAO
 */
@Singleton
class StockAnalysisLocalDataSource @Inject constructor(
    private val stockAnalysisDao: StockAnalysisDao
) {
    /**
     * 분석 데이터 조회 (stocks JOIN으로 name 포함)
     */
    suspend fun getAnalysisDataWithName(ticker: String): StockAnalysisWithName? {
        return stockAnalysisDao.getAnalysisDataWithName(ticker)
    }

    /**
     * 분석 데이터 삽입/업데이트
     */
    suspend fun insertAnalysisData(data: StockAnalysisData) {
        stockAnalysisDao.insertAnalysisData(data)
    }

    /**
     * 분석 데이터 삭제 (특정 종목)
     */
    suspend fun deleteAnalysisData(ticker: String) {
        stockAnalysisDao.deleteAnalysisData(ticker)
    }

    /**
     * 분석 데이터 전체 삭제
     */
    suspend fun deleteAll() {
        stockAnalysisDao.deleteAll()
    }
}
