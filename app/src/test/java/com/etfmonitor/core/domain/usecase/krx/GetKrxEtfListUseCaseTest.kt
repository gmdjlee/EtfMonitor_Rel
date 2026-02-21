package com.etfmonitor.core.domain.usecase.krx

import com.etfmonitor.core.data.repository.krx.KrxEtfRepositoryImpl
import com.etfmonitor.core.database.entities.Etf
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
 * GetKrxEtfListUseCase 단위 테스트
 *
 * 테스트 범위:
 * - invoke() 성공 경로: 키워드 없는 전체 목록
 * - includeKeywords 필터링 (한국어 키워드 포함)
 * - excludeKeywords 필터링
 * - 혼합 필터(include + exclude)
 * - 빈 ticker 목록
 * - 이름 조회 실패 시 빈 이름("")으로 대체
 * - 실패 Result 전파
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetKrxEtfListUseCase 테스트")
class GetKrxEtfListUseCaseTest {

    private lateinit var krxEtfRepository: KrxEtfRepositoryImpl
    private lateinit var useCase: GetKrxEtfListUseCase

    @BeforeEach
    fun setUp() {
        krxEtfRepository = mockk()
        useCase = GetKrxEtfListUseCase(krxEtfRepository)
    }

    // ================================================================
    // 키워드 없는 전체 목록 테스트
    // ================================================================

    @Nested
    @DisplayName("키워드 없는 전체 목록 테스트")
    inner class NoFilterTests {

        @Test
        @DisplayName("invoke_withNoKeywords_returnsAllEtfs")
        fun `invoke_withNoKeywords_returnsAllEtfs`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500", "102110", "114800")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")
            coEvery { krxEtfRepository.getEtfName("102110", date) } returns Result.success("TIGER 200")
            coEvery { krxEtfRepository.getEtfName("114800", date) } returns Result.success("KODEX 인버스")

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(3, etfs.size)
            assertTrue(etfs.any { it.ticker == "069500" && it.name == "KODEX 200" })
        }

        @Test
        @DisplayName("invoke_withEmptyTickerList_returnsEmptyList")
        fun `invoke_withEmptyTickerList_returnsEmptyList`() = runTest {
            // Given
            val date = "20260219"
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(emptyList())

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_whenNameLookupFails_usesEmptyNameFallback")
        fun `invoke_whenNameLookupFails_usesEmptyNameFallback`() = runTest {
            // Given
            val date = "20260219"
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(listOf("069500"))
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.failure(RuntimeException("조회 실패"))

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(1, etfs.size)
            assertEquals("069500", etfs[0].ticker)
            assertEquals("", etfs[0].name)  // getOrElse { "" }에 의한 빈 이름
        }
    }

    // ================================================================
    // includeKeywords 필터링 테스트
    // ================================================================

    @Nested
    @DisplayName("includeKeywords 필터링 테스트")
    inner class IncludeKeywordFilterTests {

        @Test
        @DisplayName("invoke_withIncludeKeyword_filtersEtfsByName")
        fun `invoke_withIncludeKeyword_filtersEtfsByName`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500", "102110", "114800")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")
            coEvery { krxEtfRepository.getEtfName("102110", date) } returns Result.success("TIGER 200")
            coEvery { krxEtfRepository.getEtfName("114800", date) } returns Result.success("KODEX 인버스")

            // When: "KODEX"를 포함하는 ETF만 필터
            val result = useCase(date, includeKeywords = listOf("KODEX"))

            // Then
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(2, etfs.size)
            assertTrue(etfs.all { it.name.contains("KODEX") })
        }

        @Test
        @DisplayName("invoke_withKoreanIncludeKeyword_filtersCorrectly")
        fun `invoke_withKoreanIncludeKeyword_filtersCorrectly`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500", "091160", "069660")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")
            coEvery { krxEtfRepository.getEtfName("091160", date) } returns Result.success("KODEX 반도체")
            coEvery { krxEtfRepository.getEtfName("069660", date) } returns Result.success("KODEX 바이오")

            // When: 한국어 키워드 "반도체"
            val result = useCase(date, includeKeywords = listOf("반도체"))

            // Then
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(1, etfs.size)
            assertEquals("091160", etfs[0].ticker)
        }

        @Test
        @DisplayName("invoke_withNoMatchingIncludeKeyword_returnsEmptyList")
        fun `invoke_withNoMatchingIncludeKeyword_returnsEmptyList`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")

            // When
            val result = useCase(date, includeKeywords = listOf("존재하지않는키워드"))

            // Then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty() == true)
        }
    }

    // ================================================================
    // excludeKeywords 필터링 테스트
    // ================================================================

    @Nested
    @DisplayName("excludeKeywords 필터링 테스트")
    inner class ExcludeKeywordFilterTests {

        @Test
        @DisplayName("invoke_withExcludeKeyword_excludesMatchingEtfs")
        fun `invoke_withExcludeKeyword_excludesMatchingEtfs`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500", "102110", "114800")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")
            coEvery { krxEtfRepository.getEtfName("102110", date) } returns Result.success("TIGER 200")
            coEvery { krxEtfRepository.getEtfName("114800", date) } returns Result.success("KODEX 인버스")

            // When: "인버스"를 제외
            val result = useCase(date, excludeKeywords = listOf("인버스"))

            // Then
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(2, etfs.size)
            assertTrue(etfs.none { it.name.contains("인버스") })
        }
    }

    // ================================================================
    // 혼합 필터 테스트
    // ================================================================

    @Nested
    @DisplayName("혼합 필터(include + exclude) 테스트")
    inner class MixedFilterTests {

        @Test
        @DisplayName("invoke_withBothIncludeAndExclude_appliesIntersectionLogic")
        fun `invoke_withBothIncludeAndExclude_appliesIntersectionLogic`() = runTest {
            // Given
            val date = "20260219"
            val tickers = listOf("069500", "114800", "102110")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.success(tickers)
            coEvery { krxEtfRepository.getEtfName("069500", date) } returns Result.success("KODEX 200")
            coEvery { krxEtfRepository.getEtfName("114800", date) } returns Result.success("KODEX 인버스")
            coEvery { krxEtfRepository.getEtfName("102110", date) } returns Result.success("TIGER 200")

            // When: KODEX 포함 + 인버스 제외
            val result = useCase(
                date,
                includeKeywords = listOf("KODEX"),
                excludeKeywords = listOf("인버스")
            )

            // Then: "KODEX 200"만 결과에 포함되어야 함
            assertTrue(result.isSuccess)
            val etfs = result.getOrNull()!!
            assertEquals(1, etfs.size)
            assertEquals("069500", etfs[0].ticker)
        }
    }

    // ================================================================
    // 실패 경로 테스트
    // ================================================================

    @Nested
    @DisplayName("실패 경로 테스트")
    inner class FailurePathTests {

        @Test
        @DisplayName("invoke_whenGetEtfListFails_returnsFailureResult")
        fun `invoke_whenGetEtfListFails_returnsFailureResult`() = runTest {
            // Given
            val date = "20260219"
            val exception = RuntimeException("ETF 목록 조회 실패")
            coEvery { krxEtfRepository.getEtfList(date) } returns Result.failure(exception)

            // When
            val result = useCase(date)

            // Then
            assertTrue(result.isFailure)
            assertEquals(exception.message, result.exceptionOrNull()?.message)
        }
    }
}
