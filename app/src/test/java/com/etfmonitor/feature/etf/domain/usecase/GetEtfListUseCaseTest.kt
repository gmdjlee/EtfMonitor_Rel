package com.etfmonitor.feature.etf.domain.usecase

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GetEtfListUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: ETF 목록 Flow 반환
 * - 빈 ETF 목록 처리
 * - 단일 ETF 처리
 * - 대규모 ETF 목록 처리
 * - Flow 전파 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("GetEtfListUseCase 테스트")
class GetEtfListUseCaseTest {

    private lateinit var repository: EtfRepository
    private lateinit var useCase: GetEtfListUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetEtfListUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withEtfList_returnsFlowOfEtfList")
        fun `invoke_withEtfList_returnsFlowOfEtfList`() = runTest {
            // Given
            val etfs = listOf(
                Etf(ticker = "069500", name = "KODEX 200"),
                Etf(ticker = "102110", name = "TIGER 200")
            )
            every { repository.getVisibleEtfs() } returns flowOf(etfs)

            // When & Then
            useCase().test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertEquals("069500", result[0].ticker)
                assertEquals("KODEX 200", result[0].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withEmptyRepository_returnsEmptyFlow")
        fun `invoke_withEmptyRepository_returnsEmptyFlow`() = runTest {
            // Given
            every { repository.getVisibleEtfs() } returns flowOf(emptyList())

            // When & Then
            useCase().test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withSingleEtf_returnsSingleItemFlow")
        fun `invoke_withSingleEtf_returnsSingleItemFlow`() = runTest {
            // Given
            val etfs = listOf(Etf(ticker = "069500", name = "KODEX 200"))
            every { repository.getVisibleEtfs() } returns flowOf(etfs)

            // When & Then
            useCase().test {
                val result = awaitItem()
                assertEquals(1, result.size)
                assertEquals("069500", result[0].ticker)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withLargeEtfList_returnsAllEtfs")
        fun `invoke_withLargeEtfList_returnsAllEtfs`() = runTest {
            // Given: 100개 ETF
            val etfs = (1..100).map { i ->
                Etf(ticker = i.toString().padStart(6, '0'), name = "ETF종목$i")
            }
            every { repository.getVisibleEtfs() } returns flowOf(etfs)

            // When & Then
            useCase().test {
                val result = awaitItem()
                assertEquals(100, result.size)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ================================================================
    // Flow 전파 검증 테스트
    // ================================================================

    @Nested
    @DisplayName("Flow 전파 검증 테스트")
    inner class FlowPropagationTests {

        @Test
        @DisplayName("invoke_withMultipleEmissions_propagatesAllEmissions")
        fun `invoke_withMultipleEmissions_propagatesAllEmissions`() = runTest {
            // Given: 두 번 방출하는 Flow
            val firstList = listOf(Etf("069500", "KODEX 200"))
            val secondList = listOf(
                Etf("069500", "KODEX 200"),
                Etf("102110", "TIGER 200")
            )
            every { repository.getVisibleEtfs() } returns kotlinx.coroutines.flow.flow {
                emit(firstList)
                emit(secondList)
            }

            // When & Then
            useCase().test {
                val first = awaitItem()
                assertEquals(1, first.size)
                val second = awaitItem()
                assertEquals(2, second.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_etfFields_arePropagatedCorrectly")
        fun `invoke_etfFields_arePropagatedCorrectly`() = runTest {
            // Given
            val etfs = listOf(
                Etf(ticker = "069500", name = "KODEX 200"),
                Etf(ticker = "114800", name = "KODEX 인버스"),
                Etf(ticker = "122630", name = "KODEX 레버리지")
            )
            every { repository.getVisibleEtfs() } returns flowOf(etfs)

            // When & Then
            useCase().test {
                val result = awaitItem()
                assertEquals(3, result.size)
                // 이름 포함 인버스 ETF 확인
                assertTrue(result.any { it.name.contains("인버스") })
                // 레버리지 ETF 확인
                assertTrue(result.any { it.name.contains("레버리지") })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
