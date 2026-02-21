package com.etfmonitor.feature.ranking.domain.usecase

import com.etfmonitor.feature.ranking.domain.model.CreditRatioTopParams
import com.etfmonitor.feature.ranking.domain.model.DailyVolumeTopParams
import com.etfmonitor.feature.ranking.domain.model.ExchangeType
import com.etfmonitor.feature.ranking.domain.model.ForeignInstitutionTopParams
import com.etfmonitor.feature.ranking.domain.model.InvestorType
import com.etfmonitor.feature.ranking.domain.model.ItemCount
import com.etfmonitor.feature.ranking.domain.model.MarketType
import com.etfmonitor.feature.ranking.domain.model.OrderBookDirection
import com.etfmonitor.feature.ranking.domain.model.OrderBookSurgeParams
import com.etfmonitor.feature.ranking.domain.model.RankingItem
import com.etfmonitor.feature.ranking.domain.model.RankingResult
import com.etfmonitor.feature.ranking.domain.model.RankingType
import com.etfmonitor.feature.ranking.domain.model.TradeDirection
import com.etfmonitor.feature.ranking.domain.model.ValueType
import com.etfmonitor.feature.ranking.domain.model.VolumeSurgeParams
import com.etfmonitor.feature.ranking.domain.repository.RankingRepository
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
import kotlin.test.assertTrue

/**
 * GetRankingUseCase 단위 테스트
 *
 * 테스트 범위:
 * - 5가지 RankingType 분기별 올바른 repository 메서드 호출
 * - itemCount 슬라이싱 (items.take())
 * - 레포지토리 에러 전파
 * - 빈 결과 처리
 * - 파라미터 매핑 (orderBookDirection, investorType, tradeDirection, valueType)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GetRankingUseCase 테스트")
class GetRankingUseCaseTest {

    private lateinit var repository: RankingRepository
    private lateinit var useCase: GetRankingUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = GetRankingUseCase(repository)
    }

    // ================================================================
    // RankingType 분기 테스트
    // ================================================================

    @Nested
    @DisplayName("RankingType 분기 라우팅 테스트")
    inner class RankingTypeBranchTests {

        @Test
        @DisplayName("invoke_withOrderBookSurge_callsGetOrderBookSurge")
        fun `invoke_withOrderBookSurge_callsGetOrderBookSurge`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.ORDER_BOOK_SURGE)
            coEvery { repository.getOrderBookSurge(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.ORDER_BOOK_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            coVerify(exactly = 1) { repository.getOrderBookSurge(any()) }
            coVerify(exactly = 0) { repository.getVolumeSurge(any()) }
            coVerify(exactly = 0) { repository.getDailyVolumeTop(any()) }
            coVerify(exactly = 0) { repository.getCreditRatioTop(any()) }
            coVerify(exactly = 0) { repository.getForeignInstitutionTop(any()) }
        }

        @Test
        @DisplayName("invoke_withVolumeSurge_callsGetVolumeSurge")
        fun `invoke_withVolumeSurge_callsGetVolumeSurge`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.VOLUME_SURGE)
            coEvery { repository.getVolumeSurge(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.VOLUME_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            coVerify(exactly = 1) { repository.getVolumeSurge(any()) }
            coVerify(exactly = 0) { repository.getOrderBookSurge(any()) }
        }

        @Test
        @DisplayName("invoke_withDailyVolumeTop_callsGetDailyVolumeTop")
        fun `invoke_withDailyVolumeTop_callsGetDailyVolumeTop`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSDAQ,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            coVerify(exactly = 1) { repository.getDailyVolumeTop(any()) }
            coVerify(exactly = 0) { repository.getOrderBookSurge(any()) }
            coVerify(exactly = 0) { repository.getVolumeSurge(any()) }
        }

        @Test
        @DisplayName("invoke_withCreditRatioTop_callsGetCreditRatioTop")
        fun `invoke_withCreditRatioTop_callsGetCreditRatioTop`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.CREDIT_RATIO_TOP)
            coEvery { repository.getCreditRatioTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.CREDIT_RATIO_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            coVerify(exactly = 1) { repository.getCreditRatioTop(any()) }
            coVerify(exactly = 0) { repository.getForeignInstitutionTop(any()) }
        }

        @Test
        @DisplayName("invoke_withForeignInstitutionTop_callsGetForeignInstitutionTop")
        fun `invoke_withForeignInstitutionTop_callsGetForeignInstitutionTop`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.FOREIGN_INSTITUTION_TOP)
            coEvery { repository.getForeignInstitutionTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.FOREIGN_INSTITUTION_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            coVerify(exactly = 1) { repository.getForeignInstitutionTop(any()) }
            coVerify(exactly = 0) { repository.getOrderBookSurge(any()) }
            coVerify(exactly = 0) { repository.getDailyVolumeTop(any()) }
        }
    }

    // ================================================================
    // itemCount 슬라이싱 테스트
    // ================================================================

    @Nested
    @DisplayName("itemCount 슬라이싱 테스트")
    inner class ItemCountSlicingTests {

        @Test
        @DisplayName("invoke_withItemCountFive_returnsFiveItems")
        fun `invoke_withItemCountFive_returnsFiveItems`() = runTest {
            // Given: 레포지토리는 30개 아이템 반환
            val thirtyItems = makeItems(30)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, thirtyItems)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.FIVE
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(5, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_withItemCountTen_returnsTenItems")
        fun `invoke_withItemCountTen_returnsTenItems`() = runTest {
            // Given
            val thirtyItems = makeItems(30)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, thirtyItems)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.TEN
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(10, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_withItemCountTwenty_returnsTwentyItems")
        fun `invoke_withItemCountTwenty_returnsTwentyItems`() = runTest {
            // Given
            val thirtyItems = makeItems(30)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, thirtyItems)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.TWENTY
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(20, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_withItemCountThirty_returnsThirtyItems")
        fun `invoke_withItemCountThirty_returnsThirtyItems`() = runTest {
            // Given
            val thirtyItems = makeItems(30)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, thirtyItems)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.THIRTY
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(30, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_withItemCountLargerThanResult_returnsAllItems")
        fun `invoke_withItemCountLargerThanResult_returnsAllItems`() = runTest {
            // Given: 레포지토리가 3개 아이템만 반환할 때, ItemCount.TEN 요청해도 3개만 반환
            val threeItems = makeItems(3)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, threeItems)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.TEN
            )

            // Then: take(10)이지만 3개만 있으므로 3개 반환
            assertTrue(actual.isSuccess)
            assertEquals(3, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_preservesItemOrder_afterSlicing")
        fun `invoke_preservesItemOrder_afterSlicing`() = runTest {
            // Given
            val items = makeItems(20)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, items)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.FIVE
            )

            // Then: 순서 유지 — rank 1부터 5까지
            assertTrue(actual.isSuccess)
            val resultItems = actual.getOrNull()?.items!!
            assertEquals(5, resultItems.size)
            for (i in 0 until 5) {
                assertEquals(i + 1, resultItems[i].rank)
            }
        }
    }

    // ================================================================
    // 빈 결과 처리 테스트
    // ================================================================

    @Nested
    @DisplayName("빈 결과 처리 테스트")
    inner class EmptyResultTests {

        @Test
        @DisplayName("invoke_withEmptyItems_returnsSuccessWithEmptyList")
        fun `invoke_withEmptyItems_returnsSuccessWithEmptyList`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.VOLUME_SURGE, emptyList())
            coEvery { repository.getVolumeSurge(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.VOLUME_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.TEN
            )

            // Then
            assertTrue(actual.isSuccess)
            assertTrue(actual.getOrNull()?.items?.isEmpty() == true)
        }

        @Test
        @DisplayName("invoke_withEmptyItems_preservesRankingType")
        fun `invoke_withEmptyItems_preservesRankingType`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.CREDIT_RATIO_TOP, emptyList())
            coEvery { repository.getCreditRatioTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.CREDIT_RATIO_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(RankingType.CREDIT_RATIO_TOP, actual.getOrNull()?.rankingType)
        }
    }

    // ================================================================
    // 에러 전파 테스트
    // ================================================================

    @Nested
    @DisplayName("에러 전파 테스트")
    inner class ErrorPropagationTests {

        @Test
        @DisplayName("invoke_whenRepositoryFails_returnsFailureResult")
        fun `invoke_whenRepositoryFails_returnsFailureResult`() = runTest {
            // Given
            val exception = RuntimeException("네트워크 오류")
            coEvery { repository.getOrderBookSurge(any()) } returns Result.failure(exception)

            // When
            val actual = useCase(
                rankingType = RankingType.ORDER_BOOK_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isFailure)
            assertEquals("네트워크 오류", actual.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenDailyVolumeTopFails_returnsFailureResult")
        fun `invoke_whenDailyVolumeTopFails_returnsFailureResult`() = runTest {
            // Given
            val exception = RuntimeException("서버 오류")
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.failure(exception)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSDAQ,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isFailure)
            assertNotNull(actual.exceptionOrNull())
        }

        @Test
        @DisplayName("invoke_whenForeignInstitutionTopFails_propagatesError")
        fun `invoke_whenForeignInstitutionTopFails_propagatesError`() = runTest {
            // Given
            val exception = IllegalStateException("인증 오류")
            coEvery { repository.getForeignInstitutionTop(any()) } returns Result.failure(exception)

            // When
            val actual = useCase(
                rankingType = RankingType.FOREIGN_INSTITUTION_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isFailure)
            assertEquals("인증 오류", actual.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenCreditRatioTopFails_propagatesError")
        fun `invoke_whenCreditRatioTopFails_propagatesError`() = runTest {
            // Given
            val exception = RuntimeException("API 오류")
            coEvery { repository.getCreditRatioTop(any()) } returns Result.failure(exception)

            // When
            val actual = useCase(
                rankingType = RankingType.CREDIT_RATIO_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isFailure)
            assertEquals("API 오류", actual.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("invoke_whenVolumeSurgeFails_propagatesError")
        fun `invoke_whenVolumeSurgeFails_propagatesError`() = runTest {
            // Given
            val exception = RuntimeException("타임아웃")
            coEvery { repository.getVolumeSurge(any()) } returns Result.failure(exception)

            // When
            val actual = useCase(
                rankingType = RankingType.VOLUME_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isFailure)
            assertEquals("타임아웃", actual.exceptionOrNull()?.message)
        }
    }

    // ================================================================
    // 파라미터 매핑 테스트
    // ================================================================

    @Nested
    @DisplayName("파라미터 매핑 테스트")
    inner class ParamMappingTests {

        @Test
        @DisplayName("invoke_withOrderBookSurge_mapsOrderBookDirectionToParams")
        fun `invoke_withOrderBookSurge_mapsOrderBookDirectionToParams`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.ORDER_BOOK_SURGE)
            coEvery { repository.getOrderBookSurge(any()) } returns Result.success(result)

            // When: SELL 방향 지정
            useCase(
                rankingType = RankingType.ORDER_BOOK_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                orderBookDirection = OrderBookDirection.SELL
            )

            // Then: OrderBookSurgeParams에 tradeType="2"(SELL) 이 포함되어야 함
            coVerify(exactly = 1) {
                repository.getOrderBookSurge(
                    match { params ->
                        params is OrderBookSurgeParams &&
                            params.tradeType == OrderBookDirection.SELL.code
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withOrderBookSurgeBuy_mapsCorrectDirectionCode")
        fun `invoke_withOrderBookSurgeBuy_mapsCorrectDirectionCode`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.ORDER_BOOK_SURGE)
            coEvery { repository.getOrderBookSurge(any()) } returns Result.success(result)

            // When: BUY 방향 (기본값)
            useCase(
                rankingType = RankingType.ORDER_BOOK_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                orderBookDirection = OrderBookDirection.BUY
            )

            // Then: tradeType="1"(BUY)
            coVerify(exactly = 1) {
                repository.getOrderBookSurge(
                    match { params ->
                        params is OrderBookSurgeParams &&
                            params.tradeType == OrderBookDirection.BUY.code
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withForeignInstitutionTop_mapsValueTypeToParams")
        fun `invoke_withForeignInstitutionTop_mapsValueTypeToParams`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.FOREIGN_INSTITUTION_TOP)
            coEvery { repository.getForeignInstitutionTop(any()) } returns Result.success(result)

            // When: QUANTITY 타입
            useCase(
                rankingType = RankingType.FOREIGN_INSTITUTION_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                valueType = ValueType.QUANTITY
            )

            // Then: amountQtyType="2"(QUANTITY)
            coVerify(exactly = 1) {
                repository.getForeignInstitutionTop(
                    match { params ->
                        params is ForeignInstitutionTopParams &&
                            params.amountQtyType == ValueType.QUANTITY.code
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withForeignInstitutionTop_mapsInvestorTypeAndTradeDirection")
        fun `invoke_withForeignInstitutionTop_mapsInvestorTypeAndTradeDirection`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.FOREIGN_INSTITUTION_TOP)
            coEvery { repository.getForeignInstitutionTop(any()) } returns Result.success(result)

            // When: INSTITUTION 투자자, NET_SELL 방향
            useCase(
                rankingType = RankingType.FOREIGN_INSTITUTION_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                investorType = InvestorType.INSTITUTION,
                tradeDirection = TradeDirection.NET_SELL
            )

            // Then
            coVerify(exactly = 1) {
                repository.getForeignInstitutionTop(
                    match { params ->
                        params is ForeignInstitutionTopParams &&
                            params.investorType == InvestorType.INSTITUTION &&
                            params.tradeDirection == TradeDirection.NET_SELL
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withDailyVolumeTop_passesMarketTypeAndExchangeType")
        fun `invoke_withDailyVolumeTop_passesMarketTypeAndExchangeType`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSDAQ,
                exchangeType = ExchangeType.NXT
            )

            // Then: DailyVolumeTopParams에 KOSDAQ + NXT 전달
            coVerify(exactly = 1) {
                repository.getDailyVolumeTop(
                    match { params ->
                        params is DailyVolumeTopParams &&
                            params.marketType == MarketType.KOSDAQ &&
                            params.exchangeType == ExchangeType.NXT
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withVolumeSurge_passesMarketTypeAndExchangeType")
        fun `invoke_withVolumeSurge_passesMarketTypeAndExchangeType`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.VOLUME_SURGE)
            coEvery { repository.getVolumeSurge(any()) } returns Result.success(result)

            // When
            useCase(
                rankingType = RankingType.VOLUME_SURGE,
                marketType = MarketType.ALL,
                exchangeType = ExchangeType.KRX_MOCK
            )

            // Then: VolumeSurgeParams에 ALL + KRX_MOCK 전달
            coVerify(exactly = 1) {
                repository.getVolumeSurge(
                    match { params ->
                        params is VolumeSurgeParams &&
                            params.marketType == MarketType.ALL &&
                            params.exchangeType == ExchangeType.KRX_MOCK
                    }
                )
            }
        }

        @Test
        @DisplayName("invoke_withCreditRatioTop_passesMarketTypeAndExchangeType")
        fun `invoke_withCreditRatioTop_passesMarketTypeAndExchangeType`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.CREDIT_RATIO_TOP)
            coEvery { repository.getCreditRatioTop(any()) } returns Result.success(result)

            // When
            useCase(
                rankingType = RankingType.CREDIT_RATIO_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            coVerify(exactly = 1) {
                repository.getCreditRatioTop(
                    match { params ->
                        params is CreditRatioTopParams &&
                            params.marketType == MarketType.KOSPI &&
                            params.exchangeType == ExchangeType.KRX
                    }
                )
            }
        }
    }

    // ================================================================
    // 결과 메타데이터 보존 테스트
    // ================================================================

    @Nested
    @DisplayName("결과 메타데이터 보존 테스트")
    inner class ResultMetadataTests {

        @Test
        @DisplayName("invoke_preservesRankingTypeInResult")
        fun `invoke_preservesRankingTypeInResult`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.VOLUME_SURGE)
            coEvery { repository.getVolumeSurge(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.VOLUME_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(RankingType.VOLUME_SURGE, actual.getOrNull()?.rankingType)
        }

        @Test
        @DisplayName("invoke_preservesMarketTypeInResult")
        fun `invoke_preservesMarketTypeInResult`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, marketType = MarketType.KOSDAQ)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSDAQ,
                exchangeType = ExchangeType.KRX
            )

            // Then
            assertTrue(actual.isSuccess)
            assertEquals(MarketType.KOSDAQ, actual.getOrNull()?.marketType)
        }

        @Test
        @DisplayName("invoke_itemsAreTakenFromBeginningOfList")
        fun `invoke_itemsAreTakenFromBeginningOfList`() = runTest {
            // Given: rank 1..10인 아이템 10개, ItemCount.FIVE 요청
            val items = makeItems(10)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, items)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX,
                itemCount = ItemCount.FIVE
            )

            // Then: 상위 5개 (rank 1~5)
            assertTrue(actual.isSuccess)
            val resultItems = actual.getOrNull()?.items!!
            assertEquals(5, resultItems.size)
            assertEquals(1, resultItems.first().rank)
            assertEquals(5, resultItems.last().rank)
        }
    }

    // ================================================================
    // 기본값 테스트
    // ================================================================

    @Nested
    @DisplayName("기본 파라미터 값 테스트")
    inner class DefaultParameterTests {

        @Test
        @DisplayName("invoke_defaultItemCount_isTen")
        fun `invoke_defaultItemCount_isTen`() = runTest {
            // Given: 20개 아이템 반환
            val items = makeItems(20)
            val result = makeSuccessResult(RankingType.DAILY_VOLUME_TOP, items)
            coEvery { repository.getDailyVolumeTop(any()) } returns Result.success(result)

            // When: itemCount 기본값 사용 (ItemCount.TEN)
            val actual = useCase(
                rankingType = RankingType.DAILY_VOLUME_TOP,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then: 기본값 TEN(10)개 반환
            assertTrue(actual.isSuccess)
            assertEquals(10, actual.getOrNull()?.items?.size)
        }

        @Test
        @DisplayName("invoke_defaultOrderBookDirection_isBuy")
        fun `invoke_defaultOrderBookDirection_isBuy`() = runTest {
            // Given
            val result = makeSuccessResult(RankingType.ORDER_BOOK_SURGE)
            coEvery { repository.getOrderBookSurge(any()) } returns Result.success(result)

            // When: orderBookDirection 기본값 사용 (BUY)
            useCase(
                rankingType = RankingType.ORDER_BOOK_SURGE,
                marketType = MarketType.KOSPI,
                exchangeType = ExchangeType.KRX
            )

            // Then: BUY 코드("1") 사용
            coVerify(exactly = 1) {
                repository.getOrderBookSurge(
                    match { params ->
                        params is OrderBookSurgeParams &&
                            params.tradeType == OrderBookDirection.BUY.code
                    }
                )
            }
        }
    }

    // ================================================================
    // 헬퍼 함수
    // ================================================================

    private fun makeRankingItem(rank: Int, ticker: String = "00${rank.toString().padStart(4, '0')}") =
        RankingItem(
            rank = rank,
            ticker = ticker,
            name = "종목$rank",
            currentPrice = 50_000L,
            priceChange = 500L,
            priceChangeSign = "+",
            changeRate = 1.0
        )

    private fun makeItems(count: Int): List<RankingItem> =
        (1..count).map { makeRankingItem(it) }

    private fun makeSuccessResult(
        rankingType: RankingType,
        items: List<RankingItem> = emptyList(),
        marketType: MarketType = MarketType.KOSPI,
        exchangeType: ExchangeType = ExchangeType.KRX
    ) = RankingResult(
        rankingType = rankingType,
        marketType = marketType,
        exchangeType = exchangeType,
        items = items
    )
}
