package com.etfmonitor.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt 모듈: Core Repository 제공
 *
 * Phase 7.4 업데이트:
 * - 대부분의 Repository가 Feature Module로 이관됨
 * - Market repositories: MarketModule
 * - Analysis repositories: AnalysisModule
 * - ETF repositories: EtfModule
 * - Stock repositories: StockModule
 * - Settings repositories: SettingsModule
 *
 * Auto-injected via @Inject constructor:
 * - PyKrxClient
 * - StockRepository
 * - StockAnalysisRepository
 * - AdvancedAnalysisRepository (legacy, for Workers)
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // StatisticsAnalysisRepository: Provided by AnalysisModule as StatisticsAnalysisRepositoryImpl
    // All legacy repositories have been migrated to feature implementations
}
