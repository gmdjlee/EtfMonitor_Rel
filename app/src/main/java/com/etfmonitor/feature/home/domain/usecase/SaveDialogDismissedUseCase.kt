package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * 다이얼로그 닫힘 상태 저장 UseCase
 *
 * 각 데이터 수집 다이얼로그가 닫혔을 때 설정을 저장합니다.
 */
class SaveDialogDismissedUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    companion object {
        const val KEY_IS_FIRST_RUN = "is_first_run"
        const val KEY_MARKET_DEPOSIT_DISMISSED = "market_deposit_dialog_dismissed"
        const val KEY_FEAR_GREED_DISMISSED = "fear_greed_dialog_dismissed"
        const val KEY_MARKET_OSCILLATOR_DISMISSED = "market_oscillator_dialog_dismissed"
        const val KEY_MARKET_INDEX_DISMISSED = "market_index_dialog_dismissed"
    }

    /**
     * 모든 다이얼로그 닫힘 상태 저장 (통합 초기화 완료 시)
     */
    suspend fun saveAllDialogsDismissed() {
        repository.saveSetting(KEY_IS_FIRST_RUN, "false")
        repository.saveSetting(KEY_MARKET_DEPOSIT_DISMISSED, "true")
        repository.saveSetting(KEY_FEAR_GREED_DISMISSED, "true")
        repository.saveSetting(KEY_MARKET_OSCILLATOR_DISMISSED, "true")
        repository.saveSetting(KEY_MARKET_INDEX_DISMISSED, "true")
    }

    /**
     * 특정 다이얼로그 닫힘 상태 저장
     */
    suspend fun saveDialogDismissed(dialogKey: String) {
        repository.saveSetting(dialogKey, "true")
    }

    /**
     * 첫 실행 상태만 저장
     */
    suspend fun saveFirstRunCompleted() {
        repository.saveSetting(KEY_IS_FIRST_RUN, "false")
    }
}
