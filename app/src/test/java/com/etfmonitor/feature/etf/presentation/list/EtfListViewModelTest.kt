package com.etfmonitor.feature.etf.presentation.list

import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.service.CollectionState
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.usecase.GetEtfListUseCase
import com.etfmonitor.feature.etf.domain.usecase.SearchEtfsUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * EtfListViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Success/Empty)
 * - 전체 ETF 목록 로딩
 * - 검색 쿼리 변경 시 SearchEtfsUseCase 호출
 * - 검색어 300ms debounce
 * - 빈 쿼리는 즉시 전체 목록 로드 (debounce 0ms)
 * - 검색 결과 빈 경우 Empty 상태
 * - 오류 시 Error 상태
 * - onClearSearch() 로 searchQuery 초기화
 * - refresh() 로 수동 새로고침
 * - CollectionState 완료 시 자동 새로고침
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@ExtendWith(MainDispatcherExtension::class)
class EtfListViewModelTest {

    private lateinit var getEtfListUseCase: GetEtfListUseCase
    private lateinit var searchEtfsUseCase: SearchEtfsUseCase

    @BeforeEach
    fun setup() {
        getEtfListUseCase = mockk(relaxed = true)
        searchEtfsUseCase = mockk(relaxed = true)

        // Default: return empty list
        every { getEtfListUseCase() } returns flowOf(emptyList())
        every { searchEtfsUseCase(any()) } returns flowOf(emptyList())

        CollectionState.reset()
    }

    private fun createViewModel(): EtfListViewModel =
        EtfListViewModel(
            getEtfListUseCase = getEtfListUseCase,
            searchEtfsUseCase = searchEtfsUseCase
        )

    // --- test helpers ---

    private fun makeEtf(ticker: String, name: String = "ETF $ticker") = Etf(ticker, name)

    private fun makeSampleEtfs() = listOf(
        makeEtf("KODEX200", "KODEX 200"),
        makeEtf("TIGER200", "TIGER 200"),
        makeEtf("KBSTAR200", "KBSTAR 200")
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("ETF 있을 때 초기화 후 Success 상태")
        fun hasEtfs_initialState_isSuccess() = runTest {
            val etfs = makeSampleEtfs()
            every { getEtfListUseCase() } returns flowOf(etfs)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(3, state.etfs.size)
            }
        }

        @Test
        @DisplayName("ETF 없을 때 초기화 후 Empty 상태")
        fun noEtfs_initialState_isEmpty() = runTest {
            every { getEtfListUseCase() } returns flowOf(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfListState.Empty>(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 searchQuery 는 빈 문자열")
        fun initialSearchQuery_isEmpty() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }

        @Test
        @DisplayName("초기화 시 GetEtfListUseCase 호출")
        fun onInit_callsGetEtfListUseCase() = runTest {
            every { getEtfListUseCase() } returns flowOf(makeSampleEtfs())

            createViewModel()
            advanceUntilIdle()

            verify(atLeast = 1) { getEtfListUseCase() }
        }
    }

    // ---------------------------------------------------------------
    // ETF 목록 로딩 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("ETF 목록 로딩 테스트")
    inner class EtfListLoadingTests {

        @Test
        @DisplayName("단일 ETF 반환 시 Success 상태에 올바른 데이터")
        fun singleEtf_returnsSuccessWithCorrectData() = runTest {
            val etf = makeEtf("KODEX200", "KODEX 200")
            every { getEtfListUseCase() } returns flowOf(listOf(etf))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(1, state.etfs.size)
                assertEquals("KODEX200", state.etfs[0].ticker)
                assertEquals("KODEX 200", state.etfs[0].name)
            }
        }

        @Test
        @DisplayName("다수 ETF 반환 시 Success 상태에 모두 포함")
        fun multipleEtfs_returnsSuccessWithAll() = runTest {
            val etfs = makeSampleEtfs()
            every { getEtfListUseCase() } returns flowOf(etfs)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(3, state.etfs.size)
                assertEquals(
                    listOf("KODEX200", "TIGER200", "KBSTAR200"),
                    state.etfs.map { it.ticker }
                )
            }
        }

        @Test
        @DisplayName("오류 발생 시 Error 상태")
        fun error_producesErrorState() = runTest {
            val errorMessage = "DB 오류"
            every { getEtfListUseCase() } returns flow {
                throw RuntimeException(errorMessage)
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Error>(state)
                assertTrue(state.message.contains(errorMessage))
            }
        }
    }

    // ---------------------------------------------------------------
    // 검색 기능 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("검색 기능 테스트")
    inner class SearchTests {

        @Test
        @DisplayName("검색어 변경 시 searchQuery StateFlow 업데이트")
        fun onSearchQueryChanged_updatesSearchQuery() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("KODEX")
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("KODEX", awaitItem())
            }
        }

        @Test
        @DisplayName("검색어 입력 후 300ms 뒤 SearchEtfsUseCase 호출")
        fun searchQuery_after300ms_callsSearchUseCase() = runTest {
            val query = "TIGER"
            val searchResults = listOf(makeEtf("TIGER200", "TIGER 200"))
            every { searchEtfsUseCase(query) } returns flowOf(searchResults)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged(query)
            advanceTimeBy(350L) // > 300ms debounce
            advanceUntilIdle()

            verify(atLeast = 1) { searchEtfsUseCase(query) }
        }

        @Test
        @DisplayName("검색 결과 있을 때 Success 상태")
        fun searchWithResults_producesSuccessState() = runTest {
            val searchResults = listOf(makeEtf("TIGER200", "TIGER 200"))
            every { searchEtfsUseCase("TIGER") } returns flowOf(searchResults)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("TIGER")
            advanceTimeBy(350L)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(1, state.etfs.size)
                assertEquals("TIGER200", state.etfs[0].ticker)
            }
        }

        @Test
        @DisplayName("검색 결과 없을 때 Empty 상태")
        fun searchWithNoResults_producesEmptyState() = runTest {
            every { searchEtfsUseCase("없는ETF") } returns flowOf(emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("없는ETF")
            advanceTimeBy(350L)
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfListState.Empty>(awaitItem())
            }
        }

        @Test
        @DisplayName("빈 쿼리로 변경 시 즉시 GetEtfListUseCase 호출 (debounce 0ms)")
        fun emptyQuery_callsGetEtfListImmediately() = runTest {
            val etfs = makeSampleEtfs()
            every { getEtfListUseCase() } returns flowOf(etfs)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // First set a non-empty query
            viewModel.onSearchQueryChanged("TIGER")
            advanceTimeBy(350L)
            advanceUntilIdle()

            // Then clear it — should immediately trigger getEtfListUseCase
            viewModel.onSearchQueryChanged("")
            advanceTimeBy(10L) // No need for 300ms debounce for blank query
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(3, state.etfs.size)
            }
        }

        @Test
        @DisplayName("onClearSearch() 호출 시 searchQuery 초기화")
        fun onClearSearch_resetsSearchQuery() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("KODEX")
            advanceUntilIdle()

            viewModel.onClearSearch()
            advanceUntilIdle()

            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
            }
        }

        @Test
        @DisplayName("onClearSearch() 호출 시 전체 ETF 목록 재로드")
        fun onClearSearch_reloadsFullEtfList() = runTest {
            val etfs = makeSampleEtfs()
            every { getEtfListUseCase() } returns flowOf(etfs)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("TIGER")
            advanceTimeBy(350L)
            advanceUntilIdle()

            viewModel.onClearSearch()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                assertEquals(3, state.etfs.size)
            }
        }

        @Test
        @DisplayName("검색 오류 발생 시 Error 상태")
        fun searchError_producesErrorState() = runTest {
            every { searchEtfsUseCase(any()) } returns flow {
                throw RuntimeException("검색 DB 오류")
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("KODEX")
            advanceTimeBy(350L)
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Error>(state)
            }
        }
    }

    // ---------------------------------------------------------------
    // 수동 새로고침 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("refresh() 테스트")
    inner class RefreshTests {

        @Test
        @DisplayName("refresh() 호출 시 ETF 목록 재로드")
        fun refresh_reloadsEtfList() = runTest {
            var callCount = 0
            every { getEtfListUseCase() } answers {
                callCount++
                flowOf(makeSampleEtfs())
            }

            val viewModel = createViewModel()
            advanceUntilIdle()

            val callsBeforeRefresh = callCount

            viewModel.refresh()
            advanceUntilIdle()

            assertTrue(
                callCount > callsBeforeRefresh,
                "Expected getEtfListUseCase to be called again after refresh()"
            )
        }

        @Test
        @DisplayName("refresh() 호출 후 Success 상태 유지")
        fun refresh_maintainsSuccessState() = runTest {
            every { getEtfListUseCase() } returns flowOf(makeSampleEtfs())

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfListState.Success>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // CollectionState 감지 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("CollectionState 감지 테스트")
    inner class CollectionStateTests {

        @Test
        @DisplayName("수집 완료 시 ETF 목록 자동 새로고침")
        fun collectionComplete_triggersEtfListRefresh() = runTest {
            var callCount = 0
            every { getEtfListUseCase() } answers {
                callCount++
                flowOf(makeSampleEtfs())
            }
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            val callsBeforeCollection = callCount

            CollectionState.startCollection(isInitialize = true)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            assertTrue(
                callCount > callsBeforeCollection,
                "Expected getEtfListUseCase to be called again after collection completed"
            )
        }

        @Test
        @DisplayName("수집 완료 후 Success 상태 유지")
        fun collectionComplete_stateRemainsSuccess() = runTest {
            every { getEtfListUseCase() } returns flowOf(makeSampleEtfs())
            CollectionState.reset()

            val viewModel = createViewModel()
            advanceUntilIdle()

            CollectionState.startCollection(isInitialize = false)
            advanceUntilIdle()

            CollectionState.complete("done")
            advanceUntilIdle()

            viewModel.state.test {
                assertIs<EtfListState.Success>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // 연속 검색어 변경 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("연속 검색어 변경 (flatMapLatest) 테스트")
    inner class FlatMapLatestTests {

        @Test
        @DisplayName("검색어 연속 변경 시 마지막 결과만 반영")
        fun rapidSearchQueryChanges_onlyLatestResultReflected() = runTest {
            val kodexResults = listOf(makeEtf("KODEX200", "KODEX 200"))
            val tigerResults = listOf(makeEtf("TIGER200", "TIGER 200"))

            every { searchEtfsUseCase("KODEX") } returns flowOf(kodexResults)
            every { searchEtfsUseCase("TIGER") } returns flowOf(tigerResults)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("KODEX")
            advanceTimeBy(100L) // Less than 300ms debounce
            viewModel.onSearchQueryChanged("TIGER")
            advanceTimeBy(350L) // > 300ms from last change
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<EtfListState.Success>(state)
                // Should only have TIGER results (latest)
                assertEquals("TIGER200", state.etfs[0].ticker)
            }
        }
    }
}
