package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * ETF 간 상관관계 캐시
 * 계산 비용이 높은 상관관계 데이터를 캐싱하여 성능 최적화
 */
@Entity(
    tableName = "etf_correlation_cache",
    indices = [
        Index(value = ["date"]),
        Index(value = ["etf1Ticker", "etf2Ticker"]),
        Index(value = ["etf1Ticker"]),
        Index(value = ["etf2Ticker"])
    ]
)
data class EtfCorrelationCache(
    @PrimaryKey
    val id: String,  // "{etf1Ticker}-{etf2Ticker}-{date}" (정렬된 순서)
    val etf1Ticker: String,
    val etf1Name: String,
    val etf2Ticker: String,
    val etf2Name: String,
    val date: String,
    val overlapRatio: Double,           // 종목 중복률 (0.0 ~ 1.0)
    val weightCorrelation: Double,      // 비중 변화 상관계수 (-1.0 ~ 1.0)
    val commonStockCount: Int,          // 공통 종목 수
    val etf1StockCount: Int,            // ETF1 총 종목 수
    val etf2StockCount: Int,            // ETF2 총 종목 수
    val topCommonStocks: String,        // JSON: 상위 공통 종목 리스트
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * ID 생성 (알파벳 순 정렬하여 중복 방지)
         */
        fun createId(etf1: String, etf2: String, date: String): String {
            val (first, second) = if (etf1 < etf2) etf1 to etf2 else etf2 to etf1
            return "$first-$second-$date"
        }
    }
}

/**
 * ETF 상관관계 상세 정보 (캐시에서 로드 후 사용)
 */
data class EtfCorrelation(
    val etf1Ticker: String,
    val etf1Name: String,
    val etf2Ticker: String,
    val etf2Name: String,
    val overlapRatio: Double,
    val weightCorrelation: Double,
    val commonStockCount: Int,
    val topCommonStocks: List<CommonStock>
)

/**
 * 공통 종목 정보
 */
@Serializable
data class CommonStock(
    val ticker: String,
    val name: String,
    val etf1Weight: Double,  // ETF1 내 비중 (%)
    val etf2Weight: Double,  // ETF2 내 비중 (%)
    val avgWeight: Double    // 평균 비중
)

/**
 * ETF 클러스터 (유사 ETF 그룹)
 */
data class EtfCluster(
    val clusterId: Int,
    val etfs: List<String>,
    val etfNames: List<String>,
    val avgIntraCorrelation: Double,  // 그룹 내 평균 상관계수
    val dominantSector: String?       // 주요 섹터
)

/**
 * 포트폴리오 분산 분석 결과
 */
data class PortfolioDiversification(
    val selectedEtfs: List<String>,
    val overallDiversificationScore: Double,  // 0.0 ~ 1.0 (높을수록 분산)
    val pairwiseCorrelations: List<EtfCorrelation>,
    val avgCorrelation: Double,
    val suggestions: List<DiversificationSuggestion>
)

/**
 * 분산 투자 제안
 */
data class DiversificationSuggestion(
    val type: SuggestionType,
    val message: String,
    val affectedEtfs: List<String>,
    val impact: Double?  // 예상 분산도 변화
)

enum class SuggestionType {
    HIGH_OVERLAP_WARNING,      // 높은 중복률 경고
    ADD_FOR_DIVERSIFICATION,   // 분산을 위한 추가 추천
    REMOVE_REDUNDANT           // 중복 ETF 제거 추천
}
