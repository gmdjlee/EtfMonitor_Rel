package com.etfmonitor.feature.home.data.repository

import com.etfmonitor.core.database.EtfDao
import com.etfmonitor.core.database.entities.Setting
import com.etfmonitor.feature.home.domain.model.DataStatus
import com.etfmonitor.feature.home.domain.model.HomeSummary
import com.etfmonitor.feature.home.domain.repository.HomeRepository
import com.etfmonitor.feature.market.domain.repository.FearGreedRepository
import com.etfmonitor.feature.market.domain.repository.MarketDepositRepository
import com.etfmonitor.feature.market.domain.repository.MarketOscillatorRepository
import com.etfmonitor.repository.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home Repository 구현체
 *
 * 기존 Repository들을 조합하여 Home 화면에 필요한 데이터를 제공합니다.
 * 각 Repository의 캐싱 로직을 그대로 활용합니다.
 *
 * @property dataRepository ETF 데이터 Repository
 * @property fearGreedRepository Fear & Greed 데이터 Repository
 * @property marketOscillatorRepository 시장 과매수/과매도 Repository
 * @property marketDepositRepository 증시 자금 동향 Repository
 * @property etfDao ETF DAO (설정 저장용)
 */
@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val dataRepository: DataRepository,
    private val fearGreedRepository: FearGreedRepository,
    private val marketOscillatorRepository: MarketOscillatorRepository,
    private val marketDepositRepository: MarketDepositRepository,
    private val etfDao: EtfDao
) : HomeRepository {

    companion object {
        private const val OSCILLATOR_OVERBOUGHT_THRESHOLD = 70.0
        private const val OSCILLATOR_OVERSOLD_THRESHOLD = -70.0
    }

    override suspend fun hasEtfData(): Boolean {
        return dataRepository.hasData()
    }

    override suspend fun getLatestDate(): String? {
        return dataRepository.getLatestDate()
    }

    override suspend fun getHomeSummary(): HomeSummary? = withContext(Dispatchers.IO) {
        try {
            // 증시 자금 동향 - 최근 데이터
            val recentDeposits = marketDepositRepository.getRecentDeposits(2)
                .flowOn(Dispatchers.IO)
                .first()
            val latestDeposit = recentDeposits.firstOrNull()

            // Fear & Greed Index - KOSPI, KOSDAQ 최근 값 (oscillator 사용)
            val kospiFearGreed = fearGreedRepository.getRecentByMarket("KOSPI", 1)
                .flowOn(Dispatchers.IO)
                .first()
                .firstOrNull()
            val kosdaqFearGreed = fearGreedRepository.getRecentByMarket("KOSDAQ", 1)
                .flowOn(Dispatchers.IO)
                .first()
                .firstOrNull()

            // 시장 과매수/과매도 - KOSPI, KOSDAQ 최근 상태
            val kospiOscillator = marketOscillatorRepository.getLatestData("KOSPI")
            val kosdaqOscillator = marketOscillatorRepository.getLatestData("KOSDAQ")

            HomeSummary(
                depositChange = latestDeposit?.depositChange,
                creditChange = latestDeposit?.creditChange,
                kospiFearGreed = kospiFearGreed?.oscillator,
                kosdaqFearGreed = kosdaqFearGreed?.oscillator,
                kospiOscillator = kospiOscillator?.oscillator,
                kospiStatus = kospiOscillator?.let { calculateOscillatorStatus(it.oscillator) },
                kosdaqOscillator = kosdaqOscillator?.oscillator,
                kosdaqStatus = kosdaqOscillator?.let { calculateOscillatorStatus(it.oscillator) }
            )
        } catch (e: Exception) {
            android.util.Log.e("HomeRepositoryImpl", "Error loading summary data", e)
            null
        }
    }

    override suspend fun getDataStatus(): DataStatus = withContext(Dispatchers.IO) {
        val hasEtfData = dataRepository.hasData()
        val hasDepositData = marketDepositRepository.getDepositCount() > 0
        val hasFearGreedData = fearGreedRepository.getCountByMarket("KOSPI") > 0 ||
                fearGreedRepository.getCountByMarket("KOSDAQ") > 0
        val hasOscillatorData = marketOscillatorRepository.getDataCount("KOSPI") > 0 ||
                marketOscillatorRepository.getDataCount("KOSDAQ") > 0

        DataStatus(
            hasEtfData = hasEtfData,
            hasDepositData = hasDepositData,
            hasFearGreedData = hasFearGreedData,
            hasOscillatorData = hasOscillatorData
        )
    }

    override suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        etfDao.getSetting(key)
    }

    override suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        etfDao.saveSetting(Setting(key, value))
    }

    override suspend fun shouldShowUnifiedInitDialog(): Boolean = withContext(Dispatchers.IO) {
        val isFirstRun = getSetting("is_first_run")
        val hasEtfData = dataRepository.hasData()

        // 첫 실행이거나 ETF 데이터가 없으면 통합 다이얼로그 표시
        (isFirstRun == null || isFirstRun == "true") && !hasEtfData
    }

    override suspend fun getDefaultDays(): Int {
        return dataRepository.getDefaultDays()
    }

    private fun calculateOscillatorStatus(oscillatorValue: Double): String {
        return when {
            oscillatorValue >= OSCILLATOR_OVERBOUGHT_THRESHOLD -> "과매수"
            oscillatorValue <= OSCILLATOR_OVERSOLD_THRESHOLD -> "과매도"
            else -> "중립"
        }
    }
}
