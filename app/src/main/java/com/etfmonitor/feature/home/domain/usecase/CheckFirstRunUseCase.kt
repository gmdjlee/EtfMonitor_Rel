package com.etfmonitor.feature.home.domain.usecase

import com.etfmonitor.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * 첫 실행 확인 및 다이얼로그 표시 결정 UseCase
 *
 * 앱 첫 실행 여부와 데이터 상태를 기반으로
 * 통합 초기화 다이얼로그를 표시할지 결정합니다.
 */
class CheckFirstRunUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    /**
     * 통합 초기화 다이얼로그 표시 필요 여부 확인
     *
     * @return true = 다이얼로그 표시 필요
     */
    suspend operator fun invoke(): Boolean {
        return repository.shouldShowUnifiedInitDialog()
    }
}

/**
 * 첫 실행 다이얼로그 닫힘 처리 UseCase
 */
class DismissFirstRunDialogUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    companion object {
        private const val KEY_IS_FIRST_RUN = "is_first_run"
    }

    /**
     * 첫 실행 상태를 false로 저장
     */
    suspend operator fun invoke() {
        repository.saveSetting(KEY_IS_FIRST_RUN, "false")
    }
}
