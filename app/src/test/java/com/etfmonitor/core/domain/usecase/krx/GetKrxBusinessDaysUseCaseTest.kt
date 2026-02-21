package com.etfmonitor.core.domain.usecase.krx

import com.krxkt.KrxIndex
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GetKrxBusinessDaysUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: yyyyMMdd → yyyy-MM-dd 변환 검증
 * - 영업일 수 검증
 * - 빈 결과 처리
 * - 예외 처리 (Result.failure 반환)
 * - 날짜 형식 변환 (KRX 8자리 → ISO 10자리)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxBusinessDaysUseCase 테스트")
class GetKrxBusinessDaysUseCaseTest {

    private lateinit var krxIndex: KrxIndex
    private lateinit var useCase: GetKrxBusinessDaysUseCase

    @BeforeEach
    fun setUp() {
        krxIndex = mockk()
        useCase = GetKrxBusinessDaysUseCase(krxIndex)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidDays_returnsIsoFormattedDates")
        fun `invoke_withValidDays_returnsIsoFormattedDates`() = runTest {
            // Given: KRX는 "yyyyMMdd" 형식 반환
            val krxDates = listOf("20260101", "20260102", "20260105")
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns krxDates

            // When
            val result = useCase(days = 5)

            // Then: ISO 형식(yyyy-MM-dd)으로 변환되어야 함
            assertTrue(result.isSuccess)
            val dates = result.getOrNull()!!
            assertEquals(3, dates.size)
            assertTrue(dates.all { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
        }

        @Test
        @DisplayName("invoke_withSpecificDate_convertsKrxFormatToIso")
        fun `invoke_withSpecificDate_convertsKrxFormatToIso`() = runTest {
            // Given
            val krxDates = listOf("20260219")
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns krxDates

            // When
            val result = useCase(days = 1)

            // Then
            assertTrue(result.isSuccess)
            assertEquals("2026-02-19", result.getOrNull()?.first())
        }

        @Test
        @DisplayName("invoke_withMultipleDates_convertsAllToIsoFormat")
        fun `invoke_withMultipleDates_convertsAllToIsoFormat`() = runTest {
            // Given
            val krxDates = listOf(
                "20260101", "20260102", "20260105",
                "20260106", "20260107", "20260108", "20260109"
            )
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns krxDates

            // When
            val result = useCase(days = 10)

            // Then
            assertTrue(result.isSuccess)
            val dates = result.getOrNull()!!
            assertEquals(7, dates.size)
            assertEquals("2026-01-01", dates[0])
            assertEquals("2026-01-02", dates[1])
            assertEquals("2026-01-09", dates[6])
        }

        @Test
        @DisplayName("invoke_withEmptyBusinessDays_returnsEmptyList")
        fun `invoke_withEmptyBusinessDays_returnsEmptyList`() = runTest {
            // Given: 영업일 없음 (공휴일 기간)
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns emptyList()

            // When
            val result = useCase(days = 7)

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_withZeroDays_returnsEmptyOrCurrentDay")
        fun `invoke_withZeroDays_returnsEmptyOrCurrentDay`() = runTest {
            // Given
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns emptyList()

            // When
            val result = useCase(days = 0)

            // Then
            assertTrue(result.isSuccess)
        }
    }

    // ================================================================
    // 날짜 형식 변환 검증
    // ================================================================

    @Nested
    @DisplayName("날짜 형식 변환 검증")
    inner class DateFormatConversionTests {

        @Test
        @DisplayName("invoke_withYearBoundaryDate_convertsCorrectly")
        fun `invoke_withYearBoundaryDate_convertsCorrectly`() = runTest {
            // Given: 연말/연초 날짜
            val krxDates = listOf("20251231", "20260101")
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns krxDates

            // When
            val result = useCase(days = 365)

            // Then
            assertTrue(result.isSuccess)
            val dates = result.getOrNull()!!
            assertEquals("2025-12-31", dates[0])
            assertEquals("2026-01-01", dates[1])
        }

        @Test
        @DisplayName("invoke_withSingleDigitMonthAndDay_paddsCorrectly")
        fun `invoke_withSingleDigitMonthAndDay_paddsCorrectly`() = runTest {
            // Given: 1월 5일 (한 자리 월, 한 자리 일)
            val krxDates = listOf("20260105")
            coEvery { krxIndex.getBusinessDays(any(), any()) } returns krxDates

            // When
            val result = useCase(days = 5)

            // Then
            assertTrue(result.isSuccess)
            assertEquals("2026-01-05", result.getOrNull()?.first())
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenKrxIndexThrows_returnsFailureResult")
        fun `invoke_whenKrxIndexThrows_returnsFailureResult`() = runTest {
            // Given
            coEvery { krxIndex.getBusinessDays(any(), any()) } throws RuntimeException("KRX 서버 오류")

            // When
            val result = useCase(days = 30)

            // Then
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("invoke_whenNetworkTimeout_returnsFailureWithException")
        fun `invoke_whenNetworkTimeout_returnsFailureWithException`() = runTest {
            // Given
            val timeoutException = RuntimeException("네트워크 타임아웃")
            coEvery { krxIndex.getBusinessDays(any(), any()) } throws timeoutException

            // When
            val result = useCase(days = 730)

            // Then
            assertTrue(result.isFailure)
            assertEquals(timeoutException.message, result.exceptionOrNull()?.message)
        }
    }
}
