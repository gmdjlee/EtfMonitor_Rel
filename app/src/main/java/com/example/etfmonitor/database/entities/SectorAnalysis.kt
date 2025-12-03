package com.etfmonitor.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 섹터별 Fear & Greed 분석 결과 저장
 */
@Entity(
    tableName = "sector_analysis",
    indices = [
        Index(value = ["date"]),
        Index(value = ["sector"]),
        Index(value = ["sector", "date"])
    ]
)
data class SectorAnalysis(
    @PrimaryKey
    val id: String,  // "{sector}-{date}"
    val sector: String,
    val sectorName: String,  // 한글 섹터명
    val date: String,
    val fearGreedValue: Double,      // 0.0 ~ 1.0
    val etfFlowScore: Double,        // -1.0 ~ 1.0
    val momentumScore: Double,       // -1.0 ~ 1.0
    val volatilityScore: Double,     // 0.0 ~ 1.0
    val stockCount: Int,             // 섹터 내 종목 수
    val newEntries: Int,             // 신규 ETF 편입 수
    val removals: Int,               // ETF 제외 수
    val avgWeightChange: Double,     // 평균 비중 변화 (%)
    val sentiment: String,           // EXTREME_GREED, GREED, NEUTRAL, FEAR, EXTREME_FEAR
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(sector: String, date: String) = "$sector-$date"
    }
}

/**
 * 섹터별 심리 상태
 */
enum class SectorSentiment(val displayName: String) {
    EXTREME_GREED("극도 탐욕"),
    GREED("탐욕"),
    NEUTRAL("중립"),
    FEAR("공포"),
    EXTREME_FEAR("극도 공포");

    companion object {
        fun fromValue(value: Double): SectorSentiment = when {
            value > 0.8 -> EXTREME_GREED
            value > 0.6 -> GREED
            value > 0.4 -> NEUTRAL
            value > 0.2 -> FEAR
            else -> EXTREME_FEAR
        }
    }
}

/**
 * 섹터 분류 매핑
 */
object SectorMapping {
    val SECTOR_NAMES = mapOf(
        "SEMICONDUCTOR" to "반도체",
        "BATTERY" to "2차전지",
        "BIO" to "바이오/헬스케어",
        "AUTO" to "자동차/모빌리티",
        "FINANCE" to "금융",
        "IT" to "IT/소프트웨어",
        "CHEMICAL" to "화학",
        "STEEL" to "철강/금속",
        "CONSTRUCTION" to "건설",
        "RETAIL" to "유통/소비재",
        "ENTERTAINMENT" to "엔터/미디어",
        "ENERGY" to "에너지/유틸리티",
        "TELECOM" to "통신",
        "OTHER" to "기타"
    )

    /**
     * ETF 이름에서 섹터 추론
     */
    fun inferSectorFromEtfName(etfName: String): String {
        return when {
            etfName.contains("반도체") || etfName.contains("시스템반도체") -> "SEMICONDUCTOR"
            etfName.contains("2차전지") || etfName.contains("배터리") || etfName.contains("전기차") -> "BATTERY"
            etfName.contains("바이오") || etfName.contains("헬스케어") || etfName.contains("제약") -> "BIO"
            etfName.contains("자동차") || etfName.contains("모빌리티") || etfName.contains("전기차") -> "AUTO"
            etfName.contains("금융") || etfName.contains("은행") || etfName.contains("보험") -> "FINANCE"
            etfName.contains("IT") || etfName.contains("소프트웨어") || etfName.contains("게임") || etfName.contains("인터넷") -> "IT"
            etfName.contains("화학") || etfName.contains("정유") -> "CHEMICAL"
            etfName.contains("철강") || etfName.contains("금속") || etfName.contains("비철") -> "STEEL"
            etfName.contains("건설") || etfName.contains("인프라") -> "CONSTRUCTION"
            etfName.contains("소비재") || etfName.contains("유통") || etfName.contains("리테일") -> "RETAIL"
            etfName.contains("엔터") || etfName.contains("미디어") || etfName.contains("콘텐츠") -> "ENTERTAINMENT"
            etfName.contains("에너지") || etfName.contains("신재생") || etfName.contains("유틸리티") -> "ENERGY"
            etfName.contains("통신") || etfName.contains("5G") -> "TELECOM"
            else -> "OTHER"
        }
    }

    /**
     * 섹터 코드를 한글 이름으로 변환
     */
    fun getSectorDisplayName(sector: String): String {
        return SECTOR_NAMES[sector] ?: "기타"
    }
}

/**
 * 섹터 로테이션 신호
 */
data class SectorRotationSignal(
    val fromSector: String,
    val toSector: String,
    val confidence: Double,
    val flowDifference: Double,  // 자금 흐름 차이
    val description: String
)
