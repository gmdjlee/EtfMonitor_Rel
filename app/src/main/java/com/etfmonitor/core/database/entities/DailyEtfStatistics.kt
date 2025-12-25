package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 일별 ETF 통계 데이터 엔티티
 * 신규/제외/비중증가/감소 종목 통계와 원화예금 추이를 저장
 * 시장 지수와의 상관관계 분석용
 */
@Entity(tableName = "daily_etf_statistics")
data class DailyEtfStatistics(
    @PrimaryKey
    val date: String, // "2025-01-01" 형식

    // 신규 편입 통계
    val newStockCount: Int, // 신규 편입 종목 수
    val newStockAmount: Long, // 신규 편입 총 금액 (원)

    // 제외 종목 통계
    val removedStockCount: Int, // 제외 종목 수
    val removedStockAmount: Long, // 제외 종목 총 금액 (원)

    // 비중 증가 통계
    val increasedStockCount: Int, // 비중 증가 종목 수
    val increasedStockAmount: Long, // 비중 증가 총 금액 (원)

    // 비중 감소 통계
    val decreasedStockCount: Int, // 비중 감소 종목 수
    val decreasedStockAmount: Long, // 비중 감소 총 금액 (원)

    // 원화예금 통계
    val cashDepositAmount: Long, // 총 원화예금 금액 (원)
    val cashDepositChange: Long, // 전일 대비 원화예금 변화 (원)
    val cashDepositChangeRate: Double, // 전일 대비 원화예금 변화율 (%)

    // 전체 ETF 통계
    val totalEtfCount: Int, // 분석된 ETF 수
    val totalHoldingAmount: Long, // 전체 보유 금액 (원)

    val lastUpdated: Long = System.currentTimeMillis()
)
