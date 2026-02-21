package com.etfmonitor.feature.etf.domain.usecase

import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * GetEtfDetailUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: ETF 정보 반환
 * - invoke() 실패 경로: null 반환
 * - ticker 파라미터 전달 검증
 * - ETF 필드 값 검증 (ticker, name)
 * - 다양한 ticker 형식 처리
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetEtfDetailUseCase 테스트")
class GetEtfDetailUseCaseTest {

    private lateinit var repository: EtfRepository
    private lateinit var useCase: GetEtfDetailUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetEtfDetailUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withValidTicker_returnsEtf")
        fun `invoke_withValidTicker_returnsEtf`() = runTest {
            // Given
            val ticker = "069500"
            val etf = Etf(ticker = ticker, name = "KODEX 200")
            coEvery { repository.getEtf(ticker) } returns etf

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals("KODEX 200", result.name)
        }

        @Test
        @DisplayName("invoke_withTicker_delegatesToRepository")
        fun `invoke_withTicker_delegatesToRepository`() = runTest {
            // Given
            val ticker = "102110"
            coEvery { repository.getEtf(ticker) } returns Etf(ticker = ticker, name = "TIGER 200")

            // When
            useCase(ticker)

            // Then: 정확히 해당 ticker로 한 번만 호출
            coVerify(exactly = 1) { repository.getEtf(ticker) }
        }

        @Test
        @DisplayName("invoke_withKoreanNameEtf_returnsCorrectName")
        fun `invoke_withKoreanNameEtf_returnsCorrectName`() = runTest {
            // Given
            val ticker = "114800"
            val etf = Etf(ticker = ticker, name = "KODEX 인버스")
            coEvery { repository.getEtf(ticker) } returns etf

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals("KODEX 인버스", result.name)
        }

        @Test
        @DisplayName("invoke_withLeverageEtf_returnsCorrectEtf")
        fun `invoke_withLeverageEtf_returnsCorrectEtf`() = runTest {
            // Given
            val ticker = "122630"
            val etf = Etf(ticker = ticker, name = "KODEX 레버리지")
            coEvery { repository.getEtf(ticker) } returns etf

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals(ticker, result.ticker)
            assertEquals("KODEX 레버리지", result.name)
        }

        @Test
        @DisplayName("invoke_withBondEtf_returnsCorrectEtf")
        fun `invoke_withBondEtf_returnsCorrectEtf`() = runTest {
            // Given
            val ticker = "148070"
            val etf = Etf(ticker = ticker, name = "KOSEF 국고채10년")
            coEvery { repository.getEtf(ticker) } returns etf

            // When
            val result = useCase(ticker)

            // Then
            assertNotNull(result)
            assertEquals("KOSEF 국고채10년", result.name)
        }
    }

    // ================================================================
    // null 반환 처리 테스트
    // ================================================================

    @Nested
    @DisplayName("null 반환 처리 테스트")
    inner class NullReturnTests {

        @Test
        @DisplayName("invoke_withUnknownTicker_returnsNull")
        fun `invoke_withUnknownTicker_returnsNull`() = runTest {
            // Given
            val ticker = "999999"
            coEvery { repository.getEtf(ticker) } returns null

            // When
            val result = useCase(ticker)

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("invoke_withEmptyTicker_returnsNull")
        fun `invoke_withEmptyTicker_returnsNull`() = runTest {
            // Given
            coEvery { repository.getEtf("") } returns null

            // When
            val result = useCase("")

            // Then
            assertNull(result)
            coVerify(exactly = 1) { repository.getEtf("") }
        }

        @Test
        @DisplayName("invoke_whenRepositoryReturnsNull_returnsNull")
        fun `invoke_whenRepositoryReturnsNull_returnsNull`() = runTest {
            // Given
            coEvery { repository.getEtf(any()) } returns null

            // When
            val result = useCase("069500")

            // Then
            assertNull(result)
        }
    }
}
