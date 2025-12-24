package com.etfmonitor.feature.market.data.datasource

import com.etfmonitor.database.MarketDepositDao
import com.etfmonitor.database.entities.MarketDeposit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 증시 자금 로컬 데이터 소스
 * Room DAO를 래핑하여 데이터 접근 제공
 */
@Singleton
class MarketDepositLocalDataSource @Inject constructor(
    private val dao: MarketDepositDao
) {
    fun getAllDeposits(): Flow<List<MarketDeposit>> =
        dao.getAllDeposits().flowOn(Dispatchers.IO)

    fun getRecentDeposits(limit: Int): Flow<List<MarketDeposit>> =
        dao.getRecentDeposits(limit).flowOn(Dispatchers.IO)

    suspend fun getRecentDepositsSuspend(limit: Int): List<MarketDeposit> =
        withContext(Dispatchers.IO) {
            dao.getRecentDeposits(limit).first()
        }

    suspend fun getDepositByDate(date: String): MarketDeposit? =
        withContext(Dispatchers.IO) {
            dao.getDepositByDate(date)
        }

    suspend fun getCount(): Int =
        withContext(Dispatchers.IO) {
            dao.getCount()
        }

    suspend fun getLastUpdateTime(): Long? =
        withContext(Dispatchers.IO) {
            dao.getLastUpdateTime()
        }

    suspend fun insertAll(deposits: List<MarketDeposit>) =
        withContext(Dispatchers.IO) {
            dao.insertAll(deposits)
        }

    suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
}
