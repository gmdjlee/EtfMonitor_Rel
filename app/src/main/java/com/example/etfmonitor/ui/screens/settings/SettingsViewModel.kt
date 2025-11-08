package com.etfmonitor.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etfmonitor.EtfMonitorApp
import com.etfmonitor.database.entities.Setting
import com.etfmonitor.repository.DataRepository
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

class SettingsViewModel(
    private val application: Application,
    private val repository: DataRepository,
    private val stockRepository: StockRepository
) : AndroidViewModel(application) {

    private val _themes = MutableStateFlow<List<String>>(emptyList())
    val themes: StateFlow<List<String>> = _themes.asStateFlow()

    private val _exclusions = MutableStateFlow<List<String>>(emptyList())
    val exclusions: StateFlow<List<String>> = _exclusions.asStateFlow()

    private val _defaultDays = MutableStateFlow(25)
    val defaultDays: StateFlow<Int> = _defaultDays.asStateFlow()

    private val _stockUpdateSettings = MutableStateFlow(StockUpdateSettings())
    val stockUpdateSettings: StateFlow<StockUpdateSettings> = _stockUpdateSettings.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val dao = (application as EtfMonitorApp).database.dao()

    init {
        loadSettings()
        loadStockInfo()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _themes.value = repository.getThemes()
            _exclusions.value = repository.getExclusions()
            _defaultDays.value = repository.getDefaultDays()

            // Stock 업데이트 시간 로드
            val hourStr = dao.getSetting("stock_update_hour")
            val minuteStr = dao.getSetting("stock_update_minute")

            val hour = hourStr?.toIntOrNull() ?: 1 // 기본값: 새벽 1시
            val minute = minuteStr?.toIntOrNull() ?: 0

            _stockUpdateSettings.value = _stockUpdateSettings.value.copy(
                updateHour = hour,
                updateMinute = minute
            )

            // 스케줄 재설정
            WorkManagerHelper.scheduleStockUpdate(application, hour, minute)
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

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = EtfMonitorApp.instance
                return SettingsViewModel(
                    application = app,
                    repository = app.repository,
                    stockRepository = StockRepository(
                        stockDao = app.database.stockDao(),
                        python = app.python
                    )
                ) as T
            }
        }
    }
}