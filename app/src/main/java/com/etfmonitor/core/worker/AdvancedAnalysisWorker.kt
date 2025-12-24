package com.etfmonitor.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.etfmonitor.database.EtfDao
import com.etfmonitor.repository.AdvancedAnalysisRepository
import com.etfmonitor.core.common.util.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 고급 분석 백그라운드 Worker
 *
 * 수행 작업:
 * 1. 시총 가중 ETF 흐름 분석
 * 2. 외국인/기관 수급 Divergence 분석
 * 3. 유동성 분석 (예탁금/시총 비율)
 * 4. 섹터별 Fear & Greed 분석
 * 5. ETF 간 상관관계 분석
 *
 * 실행 시점:
 * - 매일 장 마감 후 (18:30)
 * - ETF 데이터 수집 완료 후 자동 실행
 * - 수동 실행 가능
 */
@HiltWorker
class AdvancedAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val advancedAnalysisRepository: AdvancedAnalysisRepository,
    private val etfDao: EtfDao
) : CoroutineWorker(context, params) {

    companion object {
        private val logger = AppLogger.getLogger("AdvancedAnalysisWorker")
        const val WORK_NAME = "advanced_analysis_work"

        // Output keys
        const val KEY_MARKET_CAP_FLOW_SUCCESS = "market_cap_flow_success"
        const val KEY_DIVERGENCE_SUCCESS = "divergence_success"
        const val KEY_LIQUIDITY_SUCCESS = "liquidity_success"
        const val KEY_SECTOR_SUCCESS = "sector_success"
        const val KEY_CORRELATION_SUCCESS = "correlation_success"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.d("Starting advanced analysis...")

        val results = mutableMapOf<String, Boolean>()
        val errors = mutableListOf<String>()

        try {
            // 날짜 조회
            val dates = etfDao.getAllDistinctDates(10)
            if (dates.size < 2) {
                logger.w("Insufficient date data for analysis: ${dates.size} dates")
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "분석에 필요한 날짜 데이터가 부족합니다.")
                )
            }

            val currentDate = dates.first()
            val previousDate = dates[1]
            logger.d("Analyzing dates: current=$currentDate, previous=$previousDate")

            // 1. 시총 가중 ETF 흐름 분석
            results[KEY_MARKET_CAP_FLOW_SUCCESS] = runAnalysis("Market Cap Flow") {
                val flow = advancedAnalysisRepository.calculateMarketCapWeightedFlow(
                    currentDate, previousDate
                )
                logger.d("Market cap flow: netFlow=${flow.netFlow}억원")
            }

            // 2. 외국인/기관 수급 Divergence 분석
            results[KEY_DIVERGENCE_SUCCESS] = runAnalysis("Supply Demand Divergence") {
                val divergence = advancedAnalysisRepository.analyzeSupplyDemandDivergence(currentDate)
                val total = divergence.foreignBullishCount + divergence.institutionBullishCount +
                        divergence.alignedBullishCount + divergence.alignedBearishCount +
                        divergence.neutralCount
                logger.d("Divergence: $total stocks analyzed, sentiment=${divergence.marketSentiment}")
            }

            // 3. 유동성 분석
            results[KEY_LIQUIDITY_SUCCESS] = runAnalysis("Liquidity Analysis") {
                val liquidity = advancedAnalysisRepository.calculateAndSaveLiquidityAnalysis(currentDate)
                if (liquidity != null) {
                    logger.d("Liquidity: signal=${liquidity.signal}, ratio=${liquidity.depositToMarketCapRatio}%")
                } else {
                    logger.w("Liquidity analysis returned null (missing deposit data?)")
                }
            }

            // 4. 섹터별 Fear & Greed 분석
            results[KEY_SECTOR_SUCCESS] = runAnalysis("Sector Fear & Greed") {
                val sectors = advancedAnalysisRepository.calculateAndSaveSectorAnalysis(
                    currentDate, previousDate
                )
                logger.d("Sector analysis: ${sectors.size} sectors analyzed")

                // 섹터 로테이션 감지
                val rotations = advancedAnalysisRepository.detectSectorRotation(currentDate, previousDate)
                if (rotations.isNotEmpty()) {
                    logger.d("Sector rotation signals detected: ${rotations.size}")
                }
            }

            // 5. ETF 상관관계 분석 (비용이 높으므로 조건부 실행)
            results[KEY_CORRELATION_SUCCESS] = runAnalysis("ETF Correlation") {
                // 기존 캐시 확인
                val existingCorrelations = advancedAnalysisRepository.getHighOverlapEtfPairs(currentDate, 0.0)

                if (existingCorrelations.isEmpty()) {
                    // 캐시가 없으면 새로 계산
                    logger.d("No cached correlations found, calculating...")
                    val correlations = advancedAnalysisRepository.calculateAllEtfCorrelations(currentDate)
                    logger.d("ETF correlations: ${correlations.size} pairs calculated")
                } else {
                    logger.d("Using cached correlations: ${existingCorrelations.size} pairs")
                }
            }

            // 결과 집계
            val successCount = results.values.count { it }
            val totalCount = results.size

            logger.d("Advanced analysis completed: $successCount/$totalCount succeeded")

            if (successCount == totalCount) {
                Result.success(workDataOf(
                    KEY_MARKET_CAP_FLOW_SUCCESS to results[KEY_MARKET_CAP_FLOW_SUCCESS],
                    KEY_DIVERGENCE_SUCCESS to results[KEY_DIVERGENCE_SUCCESS],
                    KEY_LIQUIDITY_SUCCESS to results[KEY_LIQUIDITY_SUCCESS],
                    KEY_SECTOR_SUCCESS to results[KEY_SECTOR_SUCCESS],
                    KEY_CORRELATION_SUCCESS to results[KEY_CORRELATION_SUCCESS]
                ))
            } else if (successCount > 0) {
                // 일부 성공
                Result.success(workDataOf(
                    KEY_MARKET_CAP_FLOW_SUCCESS to results[KEY_MARKET_CAP_FLOW_SUCCESS],
                    KEY_DIVERGENCE_SUCCESS to results[KEY_DIVERGENCE_SUCCESS],
                    KEY_LIQUIDITY_SUCCESS to results[KEY_LIQUIDITY_SUCCESS],
                    KEY_SECTOR_SUCCESS to results[KEY_SECTOR_SUCCESS],
                    KEY_CORRELATION_SUCCESS to results[KEY_CORRELATION_SUCCESS],
                    KEY_ERROR_MESSAGE to "일부 분석 실패: ${totalCount - successCount}개"
                ))
            } else {
                // 모두 실패
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "모든 분석이 실패했습니다."))
            }
        } catch (e: Exception) {
            logger.e("Error in AdvancedAnalysisWorker", e)
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")))
        }
    }

    /**
     * 개별 분석 실행 및 예외 처리
     */
    private inline fun runAnalysis(name: String, block: () -> Unit): Boolean {
        return try {
            logger.d("Running $name analysis...")
            block()
            logger.d("$name analysis completed successfully")
            true
        } catch (e: Exception) {
            logger.e("Error in $name analysis", e)
            false
        }
    }
}
