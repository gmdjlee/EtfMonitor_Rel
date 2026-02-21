package com.etfmonitor.feature.ranking.domain.repository

import com.etfmonitor.feature.ranking.domain.model.CreditRatioTopParams
import com.etfmonitor.feature.ranking.domain.model.DailyVolumeTopParams
import com.etfmonitor.feature.ranking.domain.model.ForeignInstitutionTopParams
import com.etfmonitor.feature.ranking.domain.model.OrderBookSurgeParams
import com.etfmonitor.feature.ranking.domain.model.RankingResult
import com.etfmonitor.feature.ranking.domain.model.VolumeSurgeParams

interface RankingRepository {
    suspend fun getOrderBookSurge(params: OrderBookSurgeParams): Result<RankingResult>
    suspend fun getVolumeSurge(params: VolumeSurgeParams): Result<RankingResult>
    suspend fun getDailyVolumeTop(params: DailyVolumeTopParams): Result<RankingResult>
    suspend fun getCreditRatioTop(params: CreditRatioTopParams): Result<RankingResult>
    suspend fun getForeignInstitutionTop(params: ForeignInstitutionTopParams): Result<RankingResult>
}
