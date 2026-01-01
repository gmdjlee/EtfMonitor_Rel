package com.etfmonitor.core.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 차트 X축 날짜 범위 옵션
 *
 * @property label UI에 표시될 레이블
 * @property days 범위에 해당하는 일수 (-1은 전체 데이터)
 */
enum class DateRangeOption(val label: String, val days: Int) {
    WEEK("1주", 7),
    MONTH("1개월", 30),
    THREE_MONTHS("3개월", 90),
    SIX_MONTHS("6개월", 180),
    YEAR("1년", 365),
    THREE_YEARS("3년", 1095),
    FIVE_YEARS("5년", 1825),
    SEVEN_YEARS("7년", 2555),
    ALL("전체", -1);

    companion object {
        /**
         * 기본 선택 옵션
         */
        val DEFAULT = YEAR
    }
}

/**
 * 날짜 범위 선택 UI 컴포넌트
 *
 * FilterChip을 사용하여 사용자가 차트의 X축 날짜 범위를 선택할 수 있게 합니다.
 *
 * @param selectedRange 현재 선택된 범위
 * @param onRangeSelected 범위가 선택되었을 때 호출되는 콜백
 * @param modifier Modifier
 * @param availableOptions 표시할 옵션 목록 (기본값: 전체 옵션)
 */
@Composable
fun DateRangeSelector(
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit,
    modifier: Modifier = Modifier,
    availableOptions: List<DateRangeOption> = DateRangeOption.entries
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        availableOptions.forEach { option ->
            FilterChip(
                selected = selectedRange == option,
                onClick = { onRangeSelected(option) },
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * 차트 라벨 및 날짜 범위 계산 유틸리티
 */
object ChartLabelCalculator {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 데이터 포인트 수에 따른 최적 라벨 개수 계산
     *
     * 라벨이 너무 많으면 겹치고, 너무 적으면 정보가 부족합니다.
     * 이 함수는 데이터 포인트 수에 따라 적절한 라벨 개수를 반환합니다.
     *
     * @param dataPoints 차트에 표시될 데이터 포인트 수
     * @return 최적의 라벨 개수
     */
    fun calculateOptimalLabelCount(dataPoints: Int): Int {
        return when {
            dataPoints <= 7 -> dataPoints.coerceAtLeast(2)   // 1주: 매일 표시
            dataPoints <= 14 -> 7                             // 2주: 2일마다
            dataPoints <= 30 -> 10                            // 1개월: 3일마다
            dataPoints <= 90 -> 10                            // 3개월: 9일마다
            dataPoints <= 180 -> 8                            // 6개월: 22일마다
            dataPoints <= 365 -> 8                            // 1년: 45일마다 (increased from 6)
            dataPoints <= 730 -> 10                           // 2년: 73일마다
            else -> 12                                        // 2년 이상: more labels for visibility
        }
    }

    /**
     * DateRangeOption에 따른 시작/종료 날짜 계산
     *
     * @param option 선택된 날짜 범위 옵션
     * @param endDate 종료일 (기본값: 오늘)
     * @return Pair(시작일, 종료일) - yyyy-MM-dd 형식
     */
    fun calculateDateRange(
        option: DateRangeOption,
        endDate: LocalDate = LocalDate.now()
    ): Pair<String, String> {
        val startDate = when (option) {
            DateRangeOption.ALL -> LocalDate.of(2020, 1, 1)  // 가장 이른 날짜
            else -> endDate.minusDays(option.days.toLong())
        }
        return Pair(
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        )
    }

    /**
     * 날짜 문자열 목록에서 시작/종료 날짜 추출
     *
     * @param dates 날짜 문자열 목록 (yyyy-MM-dd 형식, 정렬되어 있다고 가정)
     * @return Pair(시작일, 종료일) 또는 빈 목록이면 null
     */
    fun extractDateRange(dates: List<String>): Pair<String, String>? {
        if (dates.isEmpty()) return null
        return Pair(dates.first(), dates.last())
    }

    /**
     * 날짜 범위 옵션에 해당하는 예상 데이터 포인트 수 반환
     * (영업일 기준 대략적인 추정)
     *
     * @param option 날짜 범위 옵션
     * @return 예상 데이터 포인트 수
     */
    fun estimatedDataPoints(option: DateRangeOption): Int {
        return when (option) {
            DateRangeOption.WEEK -> 5          // 주 5일 영업
            DateRangeOption.MONTH -> 22        // 월 ~22일 영업
            DateRangeOption.THREE_MONTHS -> 66
            DateRangeOption.SIX_MONTHS -> 132
            DateRangeOption.YEAR -> 252        // 연 ~252일 영업
            DateRangeOption.THREE_YEARS -> 756 // 3년 ~756일 영업
            DateRangeOption.FIVE_YEARS -> 1260 // 5년 ~1260일 영업
            DateRangeOption.SEVEN_YEARS -> 1764 // 7년 ~1764일 영업
            DateRangeOption.ALL -> 2000        // 약 8년치
        }
    }
}
