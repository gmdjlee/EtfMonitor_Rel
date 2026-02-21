package com.etfmonitor.feature.ranking.presentation

import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyConfig
import com.etfmonitor.core.network.kiwoom.KiwoomApiKeyProvider
import com.etfmonitor.core.network.kiwoom.KiwoomApiError
import com.etfmonitor.core.network.kiwoom.KiwoomInvestmentMode
import com.etfmonitor.feature.ranking.domain.model.*
import com.etfmonitor.feature.ranking.domain.usecase.GetRankingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RankingViewModel 단위 테스트
 *
 * 테스트 범위:
 * - API 키 미설정 시 NoApiKey 상태
 * - API 키 설정 시 초기 로딩 및 Success 상태
 * - RankingType/MarketType/ExchangeType 변경 시 재조회
 * - ETF 필터링 (excludeEtf=true → ETF 브랜드명 제외)
 * - ItemCount 필터링 (로컬 슬라이스)
 * - getAvailableMarketTypes: FOREIGN_INSTITUTION_TOP 시 ALL 포함
 * - getAvailableExchangeTypes: MOCK → KRX_MOCK only, PRODUCTION → KRX + NXT
 * - 유스케이스 오류 → Error 상태
 * - isOrderBookSurgeType / isForeignInstitutionType 보조 함수
 * - refresh() → 재조회
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("RankingViewModel 테스트")
class RankingViewModelTest {

    private lateinit var getRankingUseCase: GetRankingUseCase
    private lateinit var kiwoomApiKeyProvider: KiwoomApiKeyProvider

    @BeforeEach
    fun setUp() {
        getRankingUseCase = mockk()
        kiwoomApiKeyProvider = mockk()
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun mockConfigured(
        mode: KiwoomInvestmentMode = KiwoomInvestmentMode.MOCK
    ) {
        val config = KiwoomApiKeyConfig(
            appKey = "testAppKey",
            secretKey = "testSecretKey",
            investmentMode = mode
        )
        every { kiwoomApiKeyProvider.getConfig() } returns config
        every { kiwoomApiKeyProvider.configFlow } returns MutableStateFlow(config)
    }

    private fun mockNotConfigured() {
        val config = KiwoomApiKeyConfig()
        every { kiwoomApiKeyProvider.getConfig() } returns config
        every { kiwoomApiKeyProvider.configFlow } returns MutableStateFlow(config)
    }

    private fun mockUseCaseSuccess(
        items: List<RankingItem> = emptyList(),
        rankingType: RankingType = RankingType.DAILY_VOLUME_TOP,
        marketType: MarketType = MarketType.KOSPI,
        exchangeType: ExchangeType = ExchangeType.KRX_MOCK
    ) {
        coEvery {
            getRankingUseCase.invoke(
                rankingType = any(),
                marketType = any(),
                exchangeType = any(),
                itemCount = any(),
                orderBookDirection = any(),
                investorType = any(),
                tradeDirection = any(),
                valueType = any()
            )
        } returns Result.success(
            RankingResult(
                rankingType = rankingType,
                marketType = marketType,
                exchangeType = exchangeType,
                items = items
            )
        )
    }

    private fun mockUseCaseFailure(error: Throwable) {
        coEvery {
            getRankingUseCase.invoke(
                rankingType = any(),
                marketType = any(),
                exchangeType = any(),
                itemCount = any(),
                orderBookDirection = any(),
                investorType = any(),
                tradeDirection = any(),
                valueType = any()
            )
        } returns Result.failure(error)
    }

    private fun makeRankingItem(
        rank: Int = 1,
        ticker: String = "005930",
        name: String = "삼성전자"
    ) = RankingItem(
        rank = rank,
        ticker = ticker,
        name = name,
        currentPrice = 70000L,
        priceChange = 1000L,
        priceChangeSign = "+",
        changeRate = 1.45
    )

    private fun createViewModel(): RankingViewModel =
        RankingViewModel(getRankingUseCase, kiwoomApiKeyProvider)

    // ================================================================
    // 초기 상태 테스트
    // ================================================================

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("initialState_withNoApiKey_isNoApiKey")
        fun `initialState_withNoApiKey_isNoApiKey`() = runTest {
            mockNotConfigured()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<RankingState.NoApiKey>(viewModel.state.value)
        }

        @Test
        @DisplayName("initialState_withValidApiKey_isSuccess")
        fun `initialState_withValidApiKey_isSuccess`() = runTest {
            mockConfigured()
            mockUseCaseSuccess(items = listOf(makeRankingItem()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<RankingState.Success>(viewModel.state.value)
        }

        @Test
        @DisplayName("initialState_withValidApiKey_callsUseCase")
        fun `initialState_withValidApiKey_callsUseCase`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            createViewModel()
            advanceUntilIdle()

            coVerify(atLeast = 1) {
                getRankingUseCase.invoke(
                    rankingType = any(),
                    marketType = any(),
                    exchangeType = any(),
                    itemCount = any(),
                    orderBookDirection = any(),
                    investorType = any(),
                    tradeDirection = any(),
                    valueType = any()
                )
            }
        }

        @Test
        @DisplayName("initialState_withMockMode_setsKrxMockExchangeType")
        fun `initialState_withMockMode_setsKrxMockExchangeType`() = runTest {
            mockConfigured(KiwoomInvestmentMode.MOCK)
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ExchangeType.KRX_MOCK, viewModel.exchangeType.value)
        }

        @Test
        @DisplayName("initialState_withProductionMode_setsKrxExchangeType")
        fun `initialState_withProductionMode_setsKrxExchangeType`() = runTest {
            mockConfigured(KiwoomInvestmentMode.PRODUCTION)
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ExchangeType.KRX, viewModel.exchangeType.value)
        }
    }

    // ================================================================
    // 랭킹 타입 변경 테스트
    // ================================================================

    @Nested
    @DisplayName("onRankingTypeChange 테스트")
    inner class RankingTypeChangeTests {

        @Test
        @DisplayName("onRankingTypeChange_triggersReload")
        fun `onRankingTypeChange_triggersReload`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRankingTypeChange(RankingType.ORDER_BOOK_SURGE)
            advanceUntilIdle()

            // Called at least twice: once on init, once after type change
            coVerify(atLeast = 2) {
                getRankingUseCase.invoke(
                    rankingType = any(),
                    marketType = any(),
                    exchangeType = any(),
                    itemCount = any(),
                    orderBookDirection = any(),
                    investorType = any(),
                    tradeDirection = any(),
                    valueType = any()
                )
            }
        }

        @Test
        @DisplayName("onRankingTypeChange_updatesRankingTypeStateFlow")
        fun `onRankingTypeChange_updatesRankingTypeStateFlow`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRankingTypeChange(RankingType.VOLUME_SURGE)
            advanceUntilIdle()

            assertEquals(RankingType.VOLUME_SURGE, viewModel.rankingType.value)
        }
    }

    // ================================================================
    // 마켓 타입 변경 테스트
    // ================================================================

    @Nested
    @DisplayName("onMarketTypeChange 테스트")
    inner class MarketTypeChangeTests {

        @Test
        @DisplayName("onMarketTypeChange_triggersReload")
        fun `onMarketTypeChange_triggersReload`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onMarketTypeChange(MarketType.KOSDAQ)
            advanceUntilIdle()

            coVerify(atLeast = 2) {
                getRankingUseCase.invoke(
                    rankingType = any(),
                    marketType = any(),
                    exchangeType = any(),
                    itemCount = any(),
                    orderBookDirection = any(),
                    investorType = any(),
                    tradeDirection = any(),
                    valueType = any()
                )
            }
        }

        @Test
        @DisplayName("onMarketTypeChange_updatesMarketTypeStateFlow")
        fun `onMarketTypeChange_updatesMarketTypeStateFlow`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onMarketTypeChange(MarketType.KOSDAQ)
            advanceUntilIdle()

            assertEquals(MarketType.KOSDAQ, viewModel.marketType.value)
        }
    }

    // ================================================================
    // ETF 필터링 테스트
    // ================================================================

    @Nested
    @DisplayName("onExcludeEtfChange 테스트")
    inner class ExcludeEtfTests {

        @Test
        @DisplayName("onExcludeEtfChange_withTrue_filtersEtfItems")
        fun `onExcludeEtfChange_withTrue_filtersEtfItems`() = runTest {
            mockConfigured()
            val items = listOf(
                makeRankingItem(1, "069500", "KODEX 200"),
                makeRankingItem(2, "005930", "삼성전자"),
                makeRankingItem(3, "102110", "TIGER 200"),
                makeRankingItem(4, "000660", "SK하이닉스")
            )
            mockUseCaseSuccess(items = items)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onExcludeEtfChange(true)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            val names = state.result.items.map { it.name }
            assertFalse(names.any { it.startsWith("KODEX") }, "KODEX should be filtered")
            assertFalse(names.any { it.startsWith("TIGER") }, "TIGER should be filtered")
            assertTrue(names.contains("삼성전자"), "삼성전자 should remain")
            assertTrue(names.contains("SK하이닉스"), "SK하이닉스 should remain")
        }

        @Test
        @DisplayName("onExcludeEtfChange_withFalse_includesAllItems")
        fun `onExcludeEtfChange_withFalse_includesAllItems`() = runTest {
            mockConfigured()
            val items = listOf(
                makeRankingItem(1, "069500", "KODEX 200"),
                makeRankingItem(2, "005930", "삼성전자")
            )
            mockUseCaseSuccess(items = items)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onExcludeEtfChange(false)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            assertEquals(2, state.result.items.size)
        }

        @Test
        @DisplayName("onExcludeEtfChange_withEtfKeywordInName_filtersEtfKeywordItems")
        fun `onExcludeEtfChange_withEtfKeywordInName_filtersEtfKeywordItems`() = runTest {
            mockConfigured()
            val items = listOf(
                makeRankingItem(1, "123456", "글로벌ETF"),
                makeRankingItem(2, "005930", "삼성전자")
            )
            mockUseCaseSuccess(items = items)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onExcludeEtfChange(true)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            assertFalse(state.result.items.any { it.name.contains("ETF") })
        }
    }

    // ================================================================
    // ItemCount 필터링 테스트
    // ================================================================

    @Nested
    @DisplayName("onItemCountChange 테스트")
    inner class ItemCountTests {

        @Test
        @DisplayName("onItemCountChange_toTen_limitsItemsToTen")
        fun `onItemCountChange_toTen_limitsItemsToTen`() = runTest {
            mockConfigured()
            val thirtyItems = (1..30).map { makeRankingItem(it, "00${it.toString().padStart(4, '0')}", "종목$it") }
            mockUseCaseSuccess(items = thirtyItems)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onItemCountChange(ItemCount.TEN)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            assertEquals(10, state.result.items.size)
        }

        @Test
        @DisplayName("onItemCountChange_toFive_limitsItemsToFive")
        fun `onItemCountChange_toFive_limitsItemsToFive`() = runTest {
            mockConfigured()
            val items = (1..30).map { makeRankingItem(it, "00${it.toString().padStart(4, '0')}", "종목$it") }
            mockUseCaseSuccess(items = items)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onItemCountChange(ItemCount.FIVE)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            assertEquals(5, state.result.items.size)
        }

        @Test
        @DisplayName("onItemCountChange_toTwenty_limitsItemsToTwenty")
        fun `onItemCountChange_toTwenty_limitsItemsToTwenty`() = runTest {
            mockConfigured()
            val items = (1..30).map { makeRankingItem(it, "00${it.toString().padStart(4, '0')}", "종목$it") }
            mockUseCaseSuccess(items = items)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onItemCountChange(ItemCount.TWENTY)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Success>(state)
            assertEquals(20, state.result.items.size)
        }
    }

    // ================================================================
    // 보조 함수 테스트
    // ================================================================

    @Nested
    @DisplayName("getAvailableMarketTypes 테스트")
    inner class AvailableMarketTypesTests {

        @Test
        @DisplayName("getAvailableMarketTypes_forForeignInstitutionTop_includesAll")
        fun `getAvailableMarketTypes_forForeignInstitutionTop_includesAll`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRankingTypeChange(RankingType.FOREIGN_INSTITUTION_TOP)
            advanceUntilIdle()

            val types = viewModel.getAvailableMarketTypes()
            assertTrue(types.contains(MarketType.ALL))
            assertTrue(types.contains(MarketType.KOSPI))
            assertTrue(types.contains(MarketType.KOSDAQ))
        }

        @Test
        @DisplayName("getAvailableMarketTypes_forDailyVolumeTop_excludesAll")
        fun `getAvailableMarketTypes_forDailyVolumeTop_excludesAll`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Default is DAILY_VOLUME_TOP
            val types = viewModel.getAvailableMarketTypes()
            assertFalse(types.contains(MarketType.ALL))
            assertTrue(types.contains(MarketType.KOSPI))
            assertTrue(types.contains(MarketType.KOSDAQ))
        }
    }

    @Nested
    @DisplayName("getAvailableExchangeTypes 테스트")
    inner class AvailableExchangeTypesTests {

        @Test
        @DisplayName("getAvailableExchangeTypes_inMockMode_returnsOnlyKrxMock")
        fun `getAvailableExchangeTypes_inMockMode_returnsOnlyKrxMock`() = runTest {
            mockConfigured(KiwoomInvestmentMode.MOCK)
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            val types = viewModel.getAvailableExchangeTypes()
            assertEquals(listOf(ExchangeType.KRX_MOCK), types)
        }

        @Test
        @DisplayName("getAvailableExchangeTypes_inProductionMode_returnsKrxAndNxt")
        fun `getAvailableExchangeTypes_inProductionMode_returnsKrxAndNxt`() = runTest {
            mockConfigured(KiwoomInvestmentMode.PRODUCTION)
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            val types = viewModel.getAvailableExchangeTypes()
            assertTrue(types.contains(ExchangeType.KRX))
            assertTrue(types.contains(ExchangeType.NXT))
            assertFalse(types.contains(ExchangeType.KRX_MOCK))
        }
    }

    @Nested
    @DisplayName("isOrderBookSurgeType / isForeignInstitutionType 테스트")
    inner class TypeCheckTests {

        @Test
        @DisplayName("isOrderBookSurgeType_whenOrderBookSurge_returnsTrue")
        fun `isOrderBookSurgeType_whenOrderBookSurge_returnsTrue`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRankingTypeChange(RankingType.ORDER_BOOK_SURGE)
            advanceUntilIdle()

            assertTrue(viewModel.isOrderBookSurgeType())
        }

        @Test
        @DisplayName("isOrderBookSurgeType_whenNotOrderBookSurge_returnsFalse")
        fun `isOrderBookSurgeType_whenNotOrderBookSurge_returnsFalse`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Default is DAILY_VOLUME_TOP
            assertFalse(viewModel.isOrderBookSurgeType())
        }

        @Test
        @DisplayName("isForeignInstitutionType_whenForeignInstitutionTop_returnsTrue")
        fun `isForeignInstitutionType_whenForeignInstitutionTop_returnsTrue`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onRankingTypeChange(RankingType.FOREIGN_INSTITUTION_TOP)
            advanceUntilIdle()

            assertTrue(viewModel.isForeignInstitutionType())
        }

        @Test
        @DisplayName("isForeignInstitutionType_whenNotForeignInstitutionTop_returnsFalse")
        fun `isForeignInstitutionType_whenNotForeignInstitutionTop_returnsFalse`() = runTest {
            mockConfigured()
            mockUseCaseSuccess()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.isForeignInstitutionType())
        }
    }

    // ================================================================
    // 오류 처리 테스트
    // ================================================================

    @Nested
    @DisplayName("오류 처리 테스트")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("useCaseFailure_withNetworkError_setsErrorState")
        fun `useCaseFailure_withNetworkError_setsErrorState`() = runTest {
            mockConfigured()
            mockUseCaseFailure(KiwoomApiError.NetworkError("네트워크 오류"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<RankingState.Error>(viewModel.state.value)
        }

        @Test
        @DisplayName("useCaseFailure_withApiCallError_setsErrorState")
        fun `useCaseFailure_withApiCallError_setsErrorState`() = runTest {
            mockConfigured()
            mockUseCaseFailure(KiwoomApiError.ApiCallError(500, "서버 오류"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Error>(state)
            assertTrue(state.message.contains("API 오류"))
        }

        @Test
        @DisplayName("useCaseFailure_withAuthError_setsErrorState")
        fun `useCaseFailure_withAuthError_setsErrorState`() = runTest {
            mockConfigured()
            mockUseCaseFailure(KiwoomApiError.AuthError("인증 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertIs<RankingState.Error>(state)
            assertTrue(state.message.contains("인증 오류"))
        }

        @Test
        @DisplayName("useCaseFailure_withNoApiKeyError_setsNoApiKeyState")
        fun `useCaseFailure_withNoApiKeyError_setsNoApiKeyState`() = runTest {
            mockConfigured()
            mockUseCaseFailure(KiwoomApiError.NoApiKeyError())

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertIs<RankingState.NoApiKey>(viewModel.state.value)
        }
    }

    // ================================================================
    // refresh() 테스트
    // ================================================================

    @Nested
    @DisplayName("refresh() 테스트")
    inner class RefreshTests {

        @Test
        @DisplayName("refresh_callsUseCaseAgain")
        fun `refresh_callsUseCaseAgain`() = runTest {
            mockConfigured()
            mockUseCaseSuccess(items = listOf(makeRankingItem()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            coVerify(atLeast = 2) {
                getRankingUseCase.invoke(
                    rankingType = any(),
                    marketType = any(),
                    exchangeType = any(),
                    itemCount = any(),
                    orderBookDirection = any(),
                    investorType = any(),
                    tradeDirection = any(),
                    valueType = any()
                )
            }
        }

        @Test
        @DisplayName("refresh_afterSuccess_maintainsSuccessState")
        fun `refresh_afterSuccess_maintainsSuccessState`() = runTest {
            mockConfigured()
            mockUseCaseSuccess(items = listOf(makeRankingItem()))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertIs<RankingState.Success>(viewModel.state.value)
        }
    }
}
