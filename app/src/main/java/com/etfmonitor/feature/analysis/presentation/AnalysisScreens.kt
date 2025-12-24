package com.etfmonitor.feature.analysis.presentation

/**
 * Analysis Feature Module - Presentation Layer
 *
 * 현재 단계에서는 기존 화면과 ViewModel을 ui/screens/ 위치에 유지하면서
 * feature 모듈의 Domain/Data Layer를 통해 Clean Architecture 패턴을 적용합니다.
 *
 * ## 마이그레이션 전략
 *
 * Phase 6에서는 다음 구조를 구축했습니다:
 * - feature/analysis/domain/model/ - 도메인 모델
 * - feature/analysis/domain/repository/ - Repository 인터페이스
 * - feature/analysis/domain/usecase/ - UseCase 클래스
 * - feature/analysis/data/mapper/ - Entity <-> Domain 변환
 * - feature/analysis/data/repository/ - Repository 구현체
 * - feature/analysis/di/ - DI 모듈
 *
 * ## 기존 화면 위치 (유지)
 *
 * - ui/screens/aianalysis/
 *   - NewAIAnalysisScreen.kt
 *   - NewAIAnalysisViewModel.kt
 *
 * - ui/screens/advanced/
 *   - AdvancedDashboardScreen.kt
 *   - AdvancedDashboardViewModel.kt
 *   - (Tab 컴포넌트들)
 *
 * - ui/screens/hub/
 *   - AnalysisHubScreen.kt
 *
 * ## 향후 마이그레이션 계획
 *
 * Phase 7 또는 별도의 리팩토링에서:
 * 1. ViewModel을 UseCase 의존성으로 전환
 * 2. Screen 파일들을 feature/analysis/presentation/으로 이동
 * 3. 기존 Repository 의존성을 완전히 제거
 *
 * @see com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisScreen
 * @see com.etfmonitor.ui.screens.aianalysis.NewAIAnalysisViewModel
 * @see com.etfmonitor.ui.screens.advanced.AdvancedDashboardScreen
 * @see com.etfmonitor.ui.screens.advanced.AdvancedDashboardViewModel
 * @see com.etfmonitor.ui.screens.hub.AnalysisHubScreen
 */
object AnalysisScreens {
    // Re-exports for feature module access
    // 향후 UseCase 기반 ViewModel 마이그레이션 시 사용

    const val AI_ANALYSIS_ROUTE = "ai_analysis"
    const val ADVANCED_DASHBOARD_ROUTE = "advanced_dashboard"
    const val ANALYSIS_HUB_ROUTE = "analysis_hub"
}
