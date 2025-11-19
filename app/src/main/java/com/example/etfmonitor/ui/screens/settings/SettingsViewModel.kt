package com.etfmonitor.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.repository.DataRepository
import com.etfmonitor.repository.FearGreedRepository
import com.etfmonitor.repository.MarketDepositRepository
import com.etfmonitor.repository.StockRepository
import com.etfmonitor.worker.WorkManagerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockUpdateSettings(
    val updateHour: Int = 1,
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val stockCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketDepositUpdateSettings(
    val updateHour: Int = 2, // 기본값: 새벽 2시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val depositCount: Int = 0,
    val isUpdating: Boolean = false
)

data class FearGreedUpdateSettings(
    val updateHour: Int = 3, // 기본값: 새벽 3시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

data class MarketOscillatorUpdateSettings(
    val updateHour: Int = 4, // 기본값: 새벽 4시
    val updateMinute: Int = 0,
    val lastUpdateTime: Long? = null,
    val kospiCount: Int = 0,
    val kosdaqCount: Int = 0,
    val isUpdating: Boolean = false
)

class SettingsViewModel(
    private val application: Application,
    private val repository: DataRepository,
    private val stockRepository: StockRepository,
    private val marketDepositRepository: MarketDepositRepository,
    private val fearGreedRepository: FearGreedRepository,
    private val marketOscillatorRepository: com.etfmonitor.repository.MarketOscillatorRepository
) : AndroidViewModel(application) {

    private val _themes = MutableStateFlow<List<String>>(emptyList())
    val themes: StateFlow<List<String>> = _themes.asStateFlow()

    private val _exclusions = MutableStateFlow<List<String>>(emptyList())
    val exclusions: StateFlow<List<String>> = _exclusions.asStateFlow()

    private val _defaultDays = MutableStateFlow(25)
    val defaultDays: StateFlow<Int> = _defaultDays.asStateFlow()

    private val _stockUpdateSettings = MutableStateFlow(StockUpdateSettings())
    val stockUpdateSettings: StateFlow<StockUpdateSettings> = _stockUpdateSettings.asStateFlow()

    private val _marketDepositUpdateSettings = MutableStateFlow(MarketDepositUpdateSettings())
    val marketDepositUpdateSettings: StateFlow<MarketDepositUpdateSettings> = _marketDepositUpdateSettings.asStateFlow()

    private val _fearGreedUpdateSettings = MutableStateFlow(FearGreedUpdateSettings())
    val fearGreedUpdateSettings: StateFlow<FearGreedUpdateSettings> = _fearGreedUpdateSettings.asStateFlow()

    private val _marketOscillatorUpdateSettings = MutableStateFlow(MarketOscillatorUpdateSettings())
    val marketOscillatorUpdateSettings: StateFlow<MarketOscillatorUpdateSettings> = _marketOscillatorUpdateSettings.asStateFlow()

    private val _searchHistoryLimit = MutableStateFlow(15)
    val searchHistoryLimit: StateFlow<Int> = _searchHistoryLimit.asStateFlow()

    private val _fearGreedPeriodDays = MutableStateFlow(365) // 기본값: 12개월
    val fearGreedPeriodDays: StateFlow<Int> = _fearGreedPeriodDays.asStateFlow()

    private val _marketOscillatorPeriodDays = MutableStateFlow(365) // 기본값: 12개월
    val marketOscillatorPeriodDays: StateFlow<Int> = _marketOscillatorPeriodDays.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val dao = (application as EtfMonitorApp).database.dao()

    init {
        loadSettings()
        loadStockInfo()
        loadMarketDepositInfo()
        loadFearGreedInfo()
        loadMarketOscillatorInfo()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _themes.value = repository.getThemes()
            _exclusions.value = repository.getExclusions()
            _defaultDays.value = repository.getDefaultDays()

            // 검색 히스토리 개수 로드
            val historyLimitStr = dao.getSetting("search_history_limit")
            _searchHistoryLimit.value = historyLimitStr?.toIntOrNull() ?: 15

            // Fear & Greed 데이터 수집 기간 로드
            val fearGreedPeriodStr = dao.getSetting("fear_greed_period_days")
            _fearGreedPeriodDays.value = fearGreedPeriodStr?.toIntOrNull() ?: 365 // 기본값: 12개월

            // 과매수/과매도 데이터 수집 기간 로드
            val marketOscillatorPeriodStr = dao.getSetting("market_oscillator_period_days")
            _marketOscillatorPeriodDays.value = marketOscillatorPeriodStr?.toIntOrNull() ?: 365 // 기본값: 12개월

            // Stock 업데이트 시간 로드
            val stockHourStr = dao.getSetting("stock_update_hour")
            val stockMinuteStr = dao.getSetting("stock_update_minute")

            val stockHour = stockHourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
            val stockMinute = stockMinuteStr?.toIntOrNull() ?: 0

            _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                updateHour = stockHour,
                updateMinute = stockMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleStockUpdate(application, stockHour, stockMinute)

            // Market Deposit 업데이트 시간 로드
            val depositHourStr = dao.getSetting("market_deposit_update_hour")
            val depositMinuteStr = dao.getSetting("market_deposit_update_minute")

            val depositHour = depositHourStr?.toIntOrNull() ?: 2 // 기본값: 새벽 2시
            val depositMinute = depositMinuteStr?.toIntOrNull() ?: 0

            _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                updateHour = depositHour,
                updateMinute = depositMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleMarketDepositUpdate(application, depositHour, depositMinute)

            // Fear & Greed 업데이트 시간 로드
            val fearGreedHourStr = dao.getSetting("fear_greed_update_hour")
            val fearGreedMinuteStr = dao.getSetting("fear_greed_update_minute")

            val fearGreedHour = fearGreedHourStr?.toIntOrNull() ?: 3 // 기본값: 새벽 3시
            val fearGreedMinute = fearGreedMinuteStr?.toIntOrNull() ?: 0

            _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                updateHour = fearGreedHour,
                updateMinute = fearGreedMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleFearGreedUpdate(application, fearGreedHour, fearGreedMinute)

            // 과매수/과매도 업데이트 시간 로드
            val marketOscillatorHourStr = dao.getSetting("market_oscillator_update_hour")
            val marketOscillatorMinuteStr = dao.getSetting("market_oscillator_update_minute")

            val marketOscillatorHour = marketOscillatorHourStr?.toIntOrNull() ?: 4 // 기본값: 새벽 4시
            val marketOscillatorMinute = marketOscillatorMinuteStr?.toIntOrNull() ?: 0

            _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                updateHour = marketOscillatorHour,
                updateMinute = marketOscillatorMinute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleMarketOscillatorUpdate(application, marketOscillatorHour, marketOscillatorMinute)
        }
    }

    private fun loadStockInfo() {
        viewModelScope.launch {
            try {
                val count = stockRepository.getStockCount()
                val lastUpdate = stockRepository.getLastUpdateTime()

                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                    stockCount = count,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading stock info", e)
            }
        }
    }

    private fun loadMarketDepositInfo() {
        viewModelScope.launch {
            try {
                val count = marketDepositRepository.getDepositCount()
                val lastUpdate = marketDepositRepository.getLastUpdateTime()

                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                    depositCount = count,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading market deposit info", e)
            }
        }
    }

    private fun loadFearGreedInfo() {
        viewModelScope.launch {
            try {
                val kospiCount = fearGreedRepository.getCountByMarket("KOSPI")
                val kosdaqCount = fearGreedRepository.getCountByMarket("KOSDAQ")
                val kospiLastUpdate = fearGreedRepository.getLastUpdateTime("KOSPI")
                val kosdaqLastUpdate = fearGreedRepository.getLastUpdateTime("KOSDAQ")
                val lastUpdate = maxOf(kospiLastUpdate ?: 0L, kosdaqLastUpdate ?: 0L).takeIf { it > 0L }

                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                    kospiCount = kospiCount,
                    kosdaqCount = kosdaqCount,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading fear greed info", e)
            }
        }
    }

    private fun loadMarketOscillatorInfo() {
        viewModelScope.launch {
            try {
                val kospiCount = marketOscillatorRepository.getDataCount("KOSPI")
                val kosdaqCount = marketOscillatorRepository.getDataCount("KOSDAQ")
                val kospiLatest = marketOscillatorRepository.getLatestData("KOSPI")
                val kosdaqLatest = marketOscillatorRepository.getLatestData("KOSDAQ")
                val kospiLastUpdate = kospiLatest?.lastUpdated
                val kosdaqLastUpdate = kosdaqLatest?.lastUpdated
                val lastUpdate = maxOf(kospiLastUpdate ?: 0L, kosdaqLastUpdate ?: 0L).takeIf { it > 0L }

                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                    kospiCount = kospiCount,
                    kosdaqCount = kosdaqCount,
                    lastUpdateTime = lastUpdate
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error loading market oscillator info", e)
            }
        }
    }

    // ✅ 기본 수집 기간 설정 메서드 추가
    fun setDefaultDays(days: Int) {
        viewModelScope.launch {
            repository.setDefaultDays(days)
            _defaultDays.value = days
            _message.value = "기본 수집 기간이 ${days}일로 설정되었습니다"
        }
    }

    fun addTheme(theme: String) {
        if (theme.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 추가됨: $theme"
        }
    }

    fun removeTheme(theme: String) {
        viewModelScope.launch {
            repository.removeTheme(theme)
            _themes.value = repository.getThemes()
            _message.value = "테마 제거됨: $theme"
        }
    }

    fun addExclusion(keyword: String) {
        if (keyword.isBlank()) {
            _message.value = "키워드를 입력하세요"
            return
        }
        viewModelScope.launch {
            repository.addExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 추가됨: $keyword"
        }
    }

    fun removeExclusion(keyword: String) {
        viewModelScope.launch {
            repository.removeExclusion(keyword)
            _exclusions.value = repository.getExclusions()
            _message.value = "제외 키워드 제거됨: $keyword"
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.resetDatabase()
            _message.value = "데이터베이스가 초기화되었습니다"
        }
    }

    fun setUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("stock_update_hour", hour.toString()))
                dao.saveSetting(Setting("stock_update_minute", minute.toString()))

                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleStockUpdate(application, hour, minute)
                _message.value = "업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateStocksNow() {
        viewModelScope.launch {
            try {
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = true)
                _message.value = "종목 데이터 업데이트 중..."

                val result = stockRepository.updateStocks()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadStockInfo()
                    _message.value = "업데이트 완료: ${count}개 종목"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _stockUpdateSettings.value = _stockUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setMarketDepositUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("market_deposit_update_hour", hour.toString()))
                dao.saveSetting(Setting("market_deposit_update_minute", minute.toString()))

                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleMarketDepositUpdate(application, hour, minute)
                _message.value = "증시 자금 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateMarketDepositsNow() {
        viewModelScope.launch {
            try {
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = true)
                _message.value = "증시 자금 데이터 업데이트 중..."

                val result = marketDepositRepository.updateDeposits(numPages = 10)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadMarketDepositInfo()
                    _message.value = "업데이트 완료: ${count}개 데이터"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketDepositUpdateSettings.value = _marketDepositUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setFearGreedUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("fear_greed_update_hour", hour.toString()))
                dao.saveSetting(Setting("fear_greed_update_minute", minute.toString()))

                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleFearGreedUpdate(application, hour, minute)
                _message.value = "Fear & Greed Index 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateFearGreedNow() {
        viewModelScope.launch {
            try {
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = true)
                _message.value = "Fear & Greed Index 업데이트 중..."

                val result = fearGreedRepository.updateFearGreed()

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    loadFearGreedInfo()
                    _message.value = "업데이트 완료: ${count}개 데이터"
                } else {
                    _message.value = "업데이트 실패: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _fearGreedUpdateSettings.value = _fearGreedUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setSearchHistoryLimit(limit: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("search_history_limit", limit.toString()))
                _searchHistoryLimit.value = limit
                _message.value = "검색 히스토리가 최대 ${limit}개로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setFearGreedPeriodDays(days: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("fear_greed_period_days", days.toString()))
                _fearGreedPeriodDays.value = days
                val monthText = when (days) {
                    180 -> "6개월"
                    365 -> "12개월"
                    540 -> "18개월"
                    730 -> "24개월"
                    else -> "${days}일"
                }
                _message.value = "Fear & Greed Index 데이터 수집 기간이 ${monthText}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun setMarketOscillatorUpdateTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("market_oscillator_update_hour", hour.toString()))
                dao.saveSetting(Setting("market_oscillator_update_minute", minute.toString()))

                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(
                    updateHour = hour,
                    updateMinute = minute
                )

                WorkManagerHelper.scheduleMarketOscillatorUpdate(application, hour, minute)
                _message.value = "과매수/과매도 업데이트 시간이 ${hour}:${String.format("%02d", minute)}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "시간 설정 실패: ${e.message}"
            }
        }
    }

    fun updateMarketOscillatorsNow() {
        viewModelScope.launch {
            try {
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = true)
                _message.value = "과매수/과매도 데이터 업데이트 중..."

                val kospiResult = marketOscillatorRepository.updateMarketData("KOSPI")
                val kosdaqResult = marketOscillatorRepository.updateMarketData("KOSDAQ")

                if (kospiResult.isSuccess && kosdaqResult.isSuccess) {
                    val kospiCount = kospiResult.getOrNull() ?: 0
                    val kosdaqCount = kosdaqResult.getOrNull() ?: 0
                    loadMarketOscillatorInfo()
                    _message.value = "업데이트 완료: KOSPI ${kospiCount}개, KOSDAQ ${kosdaqCount}개"
                } else {
                    val errors = mutableListOf<String>()
                    if (kospiResult.isFailure) errors.add("KOSPI: ${kospiResult.exceptionOrNull()?.message}")
                    if (kosdaqResult.isFailure) errors.add("KOSDAQ: ${kosdaqResult.exceptionOrNull()?.message}")
                    _message.value = "업데이트 실패: ${errors.joinToString(", ")}"
                }
            } catch (e: Exception) {
                _message.value = "오류 발생: ${e.message}"
            } finally {
                _marketOscillatorUpdateSettings.value = _marketOscillatorUpdateSettings.value.copy(isUpdating = false)
            }
        }
    }

    fun setMarketOscillatorPeriodDays(days: Int) {
        viewModelScope.launch {
            try {
                dao.saveSetting(Setting("market_oscillator_period_days", days.toString()))
                _marketOscillatorPeriodDays.value = days
                val monthText = when (days) {
                    180 -> "6개월"
                    365 -> "12개월"
                    540 -> "18개월"
                    730 -> "24개월"
                    else -> "${days}일"
                }
                _message.value = "과매수/과매도 데이터 수집 기간이 ${monthText}로 설정되었습니다"
            } catch (e: Exception) {
                _message.value = "설정 실패: ${e.message}"
            }
        }
    }

    fun initializeData(days: Int) {
        viewModelScope.launch {
            try {
                com.etfmonitor.service.DataCollectionService.startInitialize(application, days)
                _message.value = "데이터 초기화를 시작합니다"
            } catch (e: Exception) {
                _message.value = "초기화 실패: ${e.message}"
            }
        }
    }

    fun updateData() {
        viewModelScope.launch {
            try {
                com.etfmonitor.service.DataCollectionService.startUpdate(application)
                _message.value = "데이터 업데이트를 시작합니다"
            } catch (e: Exception) {
                _message.value = "업데이트 실패: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = EtfMonitorApp.instance
                // Use singleton repositories from EtfMonitorApp for optimized memory usage
                return SettingsViewModel(
                    application = app,
                    repository = app.repository,
                    stockRepository = app.stockRepository,
                    marketDepositRepository = app.marketDepositRepository,
                    fearGreedRepository = app.fearGreedRepository,
                    marketOscillatorRepository = app.marketOscillatorRepository
                ) as T
            }
        }
    }
}