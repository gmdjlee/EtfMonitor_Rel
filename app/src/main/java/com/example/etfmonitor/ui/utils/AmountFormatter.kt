package com.etfmonitor.ui.utils

import kotlin.math.abs

/**
 * 평가금액을 동적 단위로 포맷하는 유틸리티 함수
 */
object AmountFormatter {

    /**
     * 금액을 가장 적절한 단위로 표시
     *
     * @param amount 원화 금액
     * @param showUnit 단위 표시 여부 (기본: true)
     * @return 포맷된 문자열 (예: "15.0억", "50백만", "500만")
     */
    fun format(amount: Float, showUnit: Boolean = true): String {
        val absAmount = abs(amount)
        val sign = if (amount < 0) "-" else ""

        return when {
            absAmount >= 100_000_000 -> {
                val value = amount / 100_000_000
                if (showUnit) {
                    String.format("%s%.1f억", sign, abs(value))
                } else {
                    String.format("%s%.1f", sign, abs(value))
                }
            }
            absAmount >= 10_000_000 -> {
                val value = amount / 10_000_000
                if (showUnit) {
                    String.format("%s%.0f백만", sign, abs(value))
                } else {
                    String.format("%s%.0f", sign, abs(value))
                }
            }
            absAmount >= 10_000 -> {
                val value = amount / 10_000
                if (showUnit) {
                    String.format("%s%.0f만", sign, abs(value))
                } else {
                    String.format("%s%.0f", sign, abs(value))
                }
            }
            absAmount >= 1 -> {
                if (showUnit) {
                    String.format("%s%.0f원", sign, absAmount)
                } else {
                    String.format("%s%.0f", sign, absAmount)
                }
            }
            else -> "0원"
        }
    }

    /**
     * 변동액을 부호와 함께 표시
     *
     * @param change 변동 금액
     * @return 포맷된 문자열 (예: "+15.0억", "-50백만")
     */
    fun formatChange(change: Float): String {
        val sign = if (change >= 0) "+" else ""
        val absChange = abs(change)

        return when {
            absChange >= 100_000_000 -> {
                String.format("%s%.1f억", sign, change / 100_000_000)
            }
            absChange >= 10_000_000 -> {
                String.format("%s%.0f백만", sign, change / 10_000_000)
            }
            absChange >= 10_000 -> {
                String.format("%s%.0f만", sign, change / 10_000)
            }
            absChange >= 1 -> {
                String.format("%s%.0f원", sign, change)
            }
            else -> "0원"
        }
    }

    /**
     * 차트 표시용 (단위 없이 숫자만)
     * 억원 기준으로 변환하되, 1억 미만이면 백만원 단위 사용
     *
     * @param amount 원화 금액
     * @return 차트에 표시할 Double 값
     */
    fun toChartValue(amount: Float): Double {
        return when {
            amount >= 100_000_000 -> (amount / 100_000_000).toDouble()
            amount >= 1_000_000 -> (amount / 1_000_000).toDouble()
            else -> (amount / 10_000).toDouble()
        }
    }

    /**
     * 차트 Y축 레이블용 단위 결정
     *
     * @param maxAmount 최대 금액
     * @return 단위 문자열 (예: "억원", "백만원", "만원")
     */
    fun getChartUnit(maxAmount: Float): String {
        return when {
            maxAmount >= 100_000_000 -> "억원"
            maxAmount >= 1_000_000 -> "백만원"
            else -> "만원"
        }
    }

    /**
     * 데이터 범위에 따른 최적 포맷 (테이블용)
     *
     * @param amount 원화 금액
     * @param maxAmount 데이터 중 최대값 (범위 결정용)
     * @return 포맷된 문자열
     */
    fun formatForTable(amount: Float, maxAmount: Float): String {
        return when {
            maxAmount >= 100_000_000 -> {
                String.format("%.2f", amount / 100_000_000)
            }
            maxAmount >= 10_000_000 -> {
                String.format("%.1f", amount / 10_000_000)
            }
            maxAmount >= 10_000 -> {
                String.format("%.0f", amount / 10_000)
            }
            else -> {
                String.format("%.0f", amount)
            }
        }
    }

    /**
     * 테이블 헤더용 단위
     *
     * @param maxAmount 데이터 중 최대값
     * @return 헤더에 표시할 단위 (예: "금액(억)", "금액(백만)")
     */
    fun getTableHeader(maxAmount: Float): String {
        return when {
            maxAmount >= 100_000_000 -> "금액(억)"
            maxAmount >= 10_000_000 -> "금액(백만)"
            maxAmount >= 10_000 -> "금액(만)"
            else -> "금액(원)"
        }
    }
}