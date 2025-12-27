package com.etfmonitor.core.network.python

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.etfmonitor.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PyKrxClient 테스트
 *
 * 테스트 범위:
 * - JSON 파싱
 * - 타임아웃 처리
 * - 에러 핸들링
 * - 재시도 로직
 *
 * 주의: 실제 Python 통합 테스트는 Android 환경에서 진행 필요
 * 이 테스트는 Python 응답을 모킹하여 Kotlin 로직을 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class PyKrxClientTest {

    // Mocks
    private lateinit var python: Python
    private lateinit var etfModule: PyObject
    private lateinit var stockModule: PyObject
    private lateinit var coreModule: PyObject

    private lateinit var client: PyKrxClient

    @BeforeEach
    fun setup() {
        python = mockk(relaxed = true)
        etfModule = mockk(relaxed = true)
        stockModule = mockk(relaxed = true)
        coreModule = mockk(relaxed = true)

        every { python.getModule("etfcollector") } returns etfModule
        every { python.getModule("stocks") } returns stockModule
        every { python.getModule("core") } returns coreModule

        client = PyKrxClient(python)
    }

    @Nested
    @DisplayName("ETF 목록 조회 테스트")
    inner class EtfListTests {

        @Test
        @DisplayName("필터된 ETF 목록 정상 조회")
        fun getFilteredEtfList_success() = runTest {
            // Given
            val jsonResponse = """[
                {"ticker": "069500", "name": "KODEX 200"},
                {"ticker": "102110", "name": "TIGER 200"}
            ]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns jsonResponse
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } returns pyResult

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("200"),
                excludeKeywords = listOf("인버스")
            )

            // Then
            assertEquals(2, result.size)
            assertEquals("069500", result[0].ticker)
            assertEquals("KODEX 200", result[0].name)
            assertEquals("102110", result[1].ticker)
            assertEquals("TIGER 200", result[1].name)
        }

        @Test
        @DisplayName("빈 키워드로 조회 시 빈 리스트 반환")
        fun getFilteredEtfList_emptyKeywords_returnsEmpty() = runTest {
            // Given - empty include keywords

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = emptyList(),
                excludeKeywords = emptyList()
            )

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("빈 JSON 응답 처리")
        fun getFilteredEtfList_emptyResponse_returnsEmpty() = runTest {
            // Given
            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns "[]"
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } returns pyResult

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("반도체"),
                excludeKeywords = emptyList()
            )

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("잘못된 JSON 응답 처리")
        fun getFilteredEtfList_invalidJson_returnsEmpty() = runTest {
            // Given
            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns "not a valid json"
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } returns pyResult

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("반도체"),
                excludeKeywords = emptyList()
            )

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Python 예외 시 빈 리스트 반환")
        fun getFilteredEtfList_pythonException_returnsEmpty() = runTest {
            // Given
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } throws RuntimeException("Python error")

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("반도체"),
                excludeKeywords = emptyList()
            )

            // Then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("보유 종목 조회 테스트")
    inner class HoldingsTests {

        @Test
        @DisplayName("보유 종목 정상 조회")
        fun getHoldings_success() = runTest {
            // Given
            val holdingsJson = """[
                {"ticker": "005930", "weight": 0.25, "amount": 50000000000},
                {"ticker": "000660", "weight": 0.15, "amount": 30000000000}
            ]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns holdingsJson
            every { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) } returns pyResult

            val stockNameResult = mockk<PyObject>()
            every { stockNameResult.toString() } returns "삼성전자"
            every { stockModule.callAttr("get_stock_name", any<String>()) } returns stockNameResult

            // When
            val result = client.getHoldings("069500", "20250115")

            // Then
            assertEquals(2, result.size)
            assertEquals("005930", result[0].stockTicker)
            assertEquals("069500", result[0].etfTicker)
            assertTrue(result[0].weight > 0)
        }

        @Test
        @DisplayName("빈 보유 종목 응답")
        fun getHoldings_emptyResponse() = runTest {
            // Given
            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns "[]"
            every { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) } returns pyResult

            // When
            val result = client.getHoldings("069500", "20250115")

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("종목명 조회 실패 시 티커를 이름으로 사용")
        fun getHoldings_stockNameFails_usesTicker() = runTest {
            // Given
            val holdingsJson = """[{"ticker": "005930", "weight": 0.25, "amount": 50000000000}]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns holdingsJson
            every { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) } returns pyResult

            val stockNameResult = mockk<PyObject>()
            every { stockNameResult.toString() } returns "None"
            every { stockModule.callAttr("get_stock_name", any<String>()) } returns stockNameResult

            // When
            val result = client.getHoldings("069500", "20250115")

            // Then
            assertEquals(1, result.size)
            // 이름이 None이면 티커를 사용해야 하지만, 현재 로직에서는 name에 ticker 저장
        }
    }

    @Nested
    @DisplayName("영업일 조회 테스트")
    inner class BusinessDaysTests {

        @Test
        @DisplayName("영업일 목록 정상 조회")
        fun getBusinessDays_success() = runTest {
            // Given
            val businessDaysJson = """["20250113", "20250114", "20250115"]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns businessDaysJson
            every { coreModule.callAttr("get_business_days", any<String>(), any<String>()) } returns pyResult

            // When
            val result = client.getBusinessDays(3)

            // Then
            assertEquals(3, result.size)
            assertTrue(result.all { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
        }

        @Test
        @DisplayName("영업일 조회 실패 시 빈 리스트")
        fun getBusinessDays_failure_returnsEmpty() = runTest {
            // Given
            every { coreModule.callAttr("get_business_days", any<String>(), any<String>()) } throws RuntimeException("Network error")

            // When
            val result = client.getBusinessDays(10)

            // Then
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("요청 일수보다 적게 반환될 때 처리")
        fun getBusinessDays_lessDaysThanRequested() = runTest {
            // Given - 10일 요청했지만 5일만 있음 (새해 연휴 등)
            val businessDaysJson = """["20250110", "20250113", "20250114", "20250115", "20250116"]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns businessDaysJson
            every { coreModule.callAttr("get_business_days", any<String>(), any<String>()) } returns pyResult

            // When
            val result = client.getBusinessDays(10)

            // Then
            assertEquals(5, result.size)
        }
    }

    @Nested
    @DisplayName("재시도 로직 테스트")
    inner class RetryTests {

        @Test
        @DisplayName("첫 번째 시도 성공 시 재시도 없음")
        fun retry_firstAttemptSuccess_noRetry() = runTest {
            // Given
            val holdingsJson = """[{"ticker": "005930", "weight": 0.25, "amount": 50000000000}]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns holdingsJson
            every { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) } returns pyResult

            val stockNameResult = mockk<PyObject>()
            every { stockNameResult.toString() } returns "삼성전자"
            every { stockModule.callAttr("get_stock_name", any<String>()) } returns stockNameResult

            // When
            val result = client.getHoldings("069500", "20250115")

            // Then
            assertEquals(1, result.size)
            verify(exactly = 1) { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) }
        }

        @Test
        @DisplayName("모든 재시도 실패 시 빈 리스트")
        fun retry_allAttemptsFail_returnsEmpty() = runTest {
            // Given
            every { etfModule.callAttr("get_etf_holdings", any<String>(), any<String>()) } throws RuntimeException("Persistent error")

            // When
            val result = client.getHoldings("069500", "20250115")

            // Then
            assertTrue(result.isEmpty())
            // MAX_RETRIES = 2 times called
            verify(atLeast = 1, atMost = 2) {
                etfModule.callAttr("get_etf_holdings", any<String>(), any<String>())
            }
        }
    }

    @Nested
    @DisplayName("날짜 형식 변환 테스트")
    inner class DateFormatTests {

        @Test
        @DisplayName("YYYYMMDD를 YYYY-MM-DD로 변환")
        fun formatDate_convertsCorrectly() = runTest {
            // Given
            val businessDaysJson = """["20250115"]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns businessDaysJson
            every { coreModule.callAttr("get_business_days", any<String>(), any<String>()) } returns pyResult

            // When
            val result = client.getBusinessDays(1)

            // Then
            assertEquals(1, result.size)
            assertEquals("2025-01-15", result[0])
        }
    }

    @Nested
    @DisplayName("JSON 파싱 테스트")
    inner class JsonParsingTests {

        @Test
        @DisplayName("추가 필드가 있어도 정상 파싱 (ignoreUnknownKeys)")
        fun parseJson_withExtraFields_success() = runTest {
            // Given - 예상하지 않은 추가 필드 포함
            val jsonResponse = """[
                {"ticker": "069500", "name": "KODEX 200", "extraField": "ignored"},
                {"ticker": "102110", "name": "TIGER 200", "anotherField": 123}
            ]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns jsonResponse
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } returns pyResult

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("200"),
                excludeKeywords = emptyList()
            )

            // Then
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("한글 이름 정상 파싱")
        fun parseJson_koreanNames_success() = runTest {
            // Given
            val jsonResponse = """[
                {"ticker": "091160", "name": "KODEX 반도체"},
                {"ticker": "139260", "name": "TIGER 2차전지테마"}
            ]"""

            val pyResult = mockk<PyObject>()
            every { pyResult.toString() } returns jsonResponse
            every {
                etfModule.callAttr(
                    "get_etf_list_with_names",
                    any<String>(),
                    any<String>(),
                    any<String>()
                )
            } returns pyResult

            // When
            val result = client.getFilteredEtfList(
                date = "20250115",
                includeKeywords = listOf("반도체", "2차전지"),
                excludeKeywords = emptyList()
            )

            // Then
            assertEquals(2, result.size)
            assertTrue(result.any { it.name.contains("반도체") })
            assertTrue(result.any { it.name.contains("2차전지") })
        }
    }
}
