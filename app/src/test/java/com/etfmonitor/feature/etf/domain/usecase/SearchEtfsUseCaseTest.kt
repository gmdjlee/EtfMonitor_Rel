package com.etfmonitor.feature.etf.domain.usecase

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.repository.EtfRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
 * SearchEtfsUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: query로 ETF 목록 Flow 반환
 * - 빈 검색어 처리
 * - 검색 결과 없음 처리
 * - ticker 검색 vs name 검색
 * - 한국어 검색어 처리
 * - 다중 방출 Flow 전파 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("SearchEtfsUseCase 테스트")
class SearchEtfsUseCaseTest {

    private lateinit var repository: EtfRepository
    private lateinit var useCase: SearchEtfsUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SearchEtfsUseCase(repository)
    }

    // ================================================================
    // 성공 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("성공 경로 테스트")
    inner class SuccessPathTests {

        @Test
        @DisplayName("invoke_withTickerQuery_returnsMatchingEtfs")
        fun `invoke_withTickerQuery_returnsMatchingEtfs`() = runTest {
            // Given: ticker로 검색
            val query = "069500"
            val etfs = listOf(Etf(ticker = "069500", name = "KODEX 200"))
            every { repository.searchEtfs(query) } returns flowOf(etfs)

            // When & Then
            useCase(query).test {
                val result = awaitItem()
                assertEquals(1, result.size)
                assertEquals("069500", result[0].ticker)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withNameQuery_returnsMatchingEtfs")
        fun `invoke_withNameQuery_returnsMatchingEtfs`() = runTest {
            // Given: name으로 검색
            val query = "KODEX"
            val etfs = listOf(
                Etf(ticker = "069500", name = "KODEX 200"),
                Etf(ticker = "114800", name = "KODEX 인버스"),
                Etf(ticker = "122630", name = "KODEX 레버리지")
            )
            every { repository.searchEtfs(query) } returns flowOf(etfs)

            // When & Then
            useCase(query).test {
                val result = awaitItem()
                assertEquals(3, result.size)
                assertTrue(result.all { it.name.startsWith("KODEX") })
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withKoreanQuery_returnsMatchingEtfs")
        fun `invoke_withKoreanQuery_returnsMatchingEtfs`() = runTest {
            // Given: 한국어 검색어
            val query = "인버스"
            val etfs = listOf(
                Etf(ticker = "114800", name = "KODEX 인버스"),
                Etf(ticker = "252670", name = "KODEX 200선물인버스2X")
            )
            every { repository.searchEtfs(query) } returns flowOf(etfs)

            // When & Then
            useCase(query).test {
                val result = awaitItem()
                assertEquals(2, result.size)
                assertTrue(result.all { it.name.contains("인버스") })
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withQuery_delegatesQueryToRepository")
        fun `invoke_withQuery_delegatesQueryToRepository`() = runTest {
            // Given
            val query = "TIGER"
            every { repository.searchEtfs(query) } returns flowOf(emptyList())

            // When & Then
            useCase(query).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            // Then: 정확한 query로 한 번만 호출
            verify(exactly = 1) { repository.searchEtfs(query) }
        }
    }

    // ================================================================
    // 빈 결과 테스트
    // ================================================================

    @Nested
    @DisplayName("빈 결과 테스트")
    inner class EmptyResultTests {

        @Test
        @DisplayName("invoke_withNoMatchingQuery_returnsEmptyList")
        fun `invoke_withNoMatchingQuery_returnsEmptyList`() = runTest {
            // Given
            val query = "NOMATCH_TICKER_XYZ"
            every { repository.searchEtfs(query) } returns flowOf(emptyList())

            // When & Then
            useCase(query).test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withEmptyQuery_returnsEmptyList")
        fun `invoke_withEmptyQuery_returnsEmptyList`() = runTest {
            // Given: 빈 검색어 (repository 동작에 위임)
            every { repository.searchEtfs("") } returns flowOf(emptyList())

            // When & Then
            useCase("").test {
                val result = awaitItem()
                assertTrue(result.isEmpty())
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
            // Given: 두 번 방출하는 Flow (예: DB 변경 감지)
            val firstResult = listOf(Etf("069500", "KODEX 200"))
            val secondResult = listOf(
                Etf("069500", "KODEX 200"),
                Etf("102110", "TIGER 200")
            )
            val query = "200"
            every { repository.searchEtfs(query) } returns flow {
                emit(firstResult)
                emit(secondResult)
            }

            // When & Then
            useCase(query).test {
                val first = awaitItem()
                assertEquals(1, first.size)
                val second = awaitItem()
                assertEquals(2, second.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("invoke_withPartialTickerQuery_returnsAllMatchingEtfs")
        fun `invoke_withPartialTickerQuery_returnsAllMatchingEtfs`() = runTest {
            // Given
            val query = "0695"
            val etfs = listOf(Etf(ticker = "069500", name = "KODEX 200"))
            every { repository.searchEtfs(query) } returns flowOf(etfs)

            // When & Then
            useCase(query).test {
                val result = awaitItem()
                assertEquals(1, result.size)
                assertTrue(result[0].ticker.startsWith("0695"))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
