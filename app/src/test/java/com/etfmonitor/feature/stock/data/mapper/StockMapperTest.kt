package com.etfmonitor.feature.stock.data.mapper

import com.etfmonitor.core.database.StockSearchResult as StockSearchResultDb
import com.etfmonitor.core.database.entities.CashDepositTrend as CashDepositTrendEntity
import com.etfmonitor.core.database.entities.HoldingTimeSeries as HoldingTimeSeriesEntity
import com.etfmonitor.core.database.entities.Stock as StockEntity
import com.etfmonitor.core.database.entities.StockAmountRanking as StockAmountRankingEntity
import com.etfmonitor.core.database.entities.StockChangeInfo as StockChangeInfoEntity
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toCashDepositDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toChangeInfoDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toEntity
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toRankingDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toSearchResultDomain
import com.etfmonitor.feature.stock.data.mapper.StockMapper.toTimeSeriesDomain
import com.etfmonitor.feature.stock.domain.model.CashDepositTrend
import com.etfmonitor.feature.stock.domain.model.HoldingTimeSeries
import com.etfmonitor.feature.stock.domain.model.Stock
import com.etfmonitor.feature.stock.domain.model.StockAmountRanking
import com.etfmonitor.feature.stock.domain.model.StockChangeInfo
import com.etfmonitor.feature.stock.domain.repository.StockSearchResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * StockMapper 단위 테스트
 *
 * 테스트 범위:
 * - StockEntity ↔ Stock Domain 양방향 변환
 * - HoldingTimeSeriesEntity → HoldingTimeSeries 변환
 * - StockAmountRankingEntity → StockAmountRanking 변환
 * - StockChangeInfoEntity → StockChangeInfo 변환
 * - CashDepositTrendEntity → CashDepositTrend 변환
 * - StockSearchResultDb → StockSearchResult 변환
 * - 리스트 변환
 */
@DisplayName("StockMapper 테스트")
class StockMapperTest {

    // ========== Stock Entity ↔ Domain ==========

    @Nested
    @DisplayName("StockEntity → Domain 변환")
    inner class StockEntityToDomainTests {

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = StockEntity(
                ticker = "005930",
                name = "삼성전자",
                market = "KOSPI",
                sector = "반도체",
                isEtfHolding = true,
                lastUpdated = now
            )

            // When
            val domain: Stock
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("005930", domain.ticker)
            assertEquals("삼성전자", domain.name)
            assertEquals("KOSPI", domain.market)
            assertTrue(domain.isEtfHolding)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("toDomain()은 KOSDAQ 종목을 올바르게 변환한다")
        fun `toDomain_withKosdaqStock_mapsMarketCorrectly`() {
            // Given
            val entity = StockEntity(
                ticker = "247540",
                name = "에코프로비엠",
                market = "KOSDAQ",
                sector = "2차전지",
                isEtfHolding = false,
                lastUpdated = 0L
            )

            // When
            val domain: Stock
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("KOSDAQ", domain.market)
            assertFalse(domain.isEtfHolding)
        }

        @Test
        @DisplayName("List<StockEntity>.toDomain()은 모든 요소를 변환한다")
        fun `listToDomain_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                StockEntity("005930", "삼성전자", "KOSPI", sector = "", isEtfHolding = true, lastUpdated = 0L),
                StockEntity("000660", "SK하이닉스", "KOSPI", sector = "", isEtfHolding = true, lastUpdated = 0L),
                StockEntity("247540", "에코프로비엠", "KOSDAQ", sector = "", isEtfHolding = false, lastUpdated = 0L)
            )

            // When
            val domains: List<Stock>
            with(StockMapper) {
                domains = entities.toDomain()
            }

            // Then
            assertEquals(3, domains.size)
            assertEquals("005930", domains[0].ticker)
            assertEquals("000660", domains[1].ticker)
            assertEquals("247540", domains[2].ticker)
        }

        @Test
        @DisplayName("List<StockEntity>.toDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `listToDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<StockEntity>().toDomain().size)
            }
        }
    }

    @Nested
    @DisplayName("Stock Domain → Entity 변환")
    inner class StockDomainToEntityTests {

        @Test
        @DisplayName("toEntity()는 모든 필드를 올바르게 변환하고 sector는 빈 문자열로 설정된다")
        fun `toEntity_withValidDomain_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = Stock(
                ticker = "005930",
                name = "삼성전자",
                market = "KOSPI",
                isEtfHolding = true,
                lastUpdated = now
            )

            // When
            val entity: StockEntity
            with(StockMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("005930", entity.ticker)
            assertEquals("삼성전자", entity.name)
            assertEquals("KOSPI", entity.market)
            assertEquals("", entity.sector, "sector must be empty string when converting from domain")
            assertTrue(entity.isEtfHolding)
            assertEquals(now, entity.lastUpdated)
        }

        @Test
        @DisplayName("toEntity()는 isEtfHolding=false를 올바르게 변환한다")
        fun `toEntity_withIsEtfHoldingFalse_mapsCorrectly`() {
            // Given
            val domain = Stock(
                ticker = "247540",
                name = "에코프로비엠",
                market = "KOSDAQ",
                isEtfHolding = false,
                lastUpdated = 0L
            )

            // When
            val entity: StockEntity
            with(StockMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertFalse(entity.isEtfHolding)
        }
    }

    // ========== HoldingTimeSeries ==========

    @Nested
    @DisplayName("HoldingTimeSeriesEntity → Domain 변환")
    inner class HoldingTimeSeriesTests {

        @Test
        @DisplayName("toDomain()은 date, weight, amount를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsFieldsCorrectly`() {
            // Given
            val entity = HoldingTimeSeriesEntity(
                date = "2025-01-15",
                weight = 3.5f,
                amount = 1_000_000f
            )

            // When
            val domain: HoldingTimeSeries
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("2025-01-15", domain.date)
            assertEquals(3.5f, domain.weight)
            assertEquals(1_000_000f, domain.amount)
        }

        @Test
        @DisplayName("List.toTimeSeriesDomain()은 모든 요소를 변환하고 순서를 유지한다")
        fun `toTimeSeriesDomain_withMultipleEntities_preservesOrder`() {
            // Given
            val entities = listOf(
                HoldingTimeSeriesEntity("2025-01-15", 4.0f, 1_500_000f),
                HoldingTimeSeriesEntity("2025-01-14", 3.8f, 1_400_000f),
                HoldingTimeSeriesEntity("2025-01-13", 3.5f, 1_300_000f)
            )

            // When
            val domains: List<HoldingTimeSeries>
            with(StockMapper) {
                domains = entities.toTimeSeriesDomain()
            }

            // Then
            assertEquals(3, domains.size)
            assertEquals("2025-01-15", domains[0].date)
            assertEquals("2025-01-14", domains[1].date)
            assertEquals("2025-01-13", domains[2].date)
        }

        @Test
        @DisplayName("List.toTimeSeriesDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `toTimeSeriesDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<HoldingTimeSeriesEntity>().toTimeSeriesDomain().size)
            }
        }
    }

    // ========== StockAmountRanking ==========

    @Nested
    @DisplayName("StockAmountRankingEntity → Domain 변환")
    inner class StockAmountRankingTests {

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val entity = StockAmountRankingEntity(
                stockTicker = "005930",
                stockName = "삼성전자",
                totalAmount = 5_000_000f,
                etfCount = 10,
                maxWeight = 5.0f,
                etfList = "069500,229200",
                newEtfCount = 1,
                increasedEtfCount = 3,
                decreasedEtfCount = 2,
                removedEtfCount = 0
            )

            // When
            val domain: StockAmountRanking
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("005930", domain.stockTicker)
            assertEquals("삼성전자", domain.stockName)
            assertEquals(5_000_000f, domain.totalAmount)
            assertEquals(10, domain.etfCount)
            assertEquals(1, domain.newEtfCount)
            assertEquals(3, domain.increasedEtfCount)
            assertEquals(2, domain.decreasedEtfCount)
            assertEquals(0, domain.removedEtfCount)
        }

        @Test
        @DisplayName("List.toRankingDomain()은 모든 요소를 변환한다")
        fun `toRankingDomain_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                StockAmountRankingEntity("005930", "삼성전자", 5_000_000f, 10, 5.0f, "", 0, 0, 0, 0),
                StockAmountRankingEntity("000660", "SK하이닉스", 3_000_000f, 8, 4.0f, "", 0, 0, 0, 0)
            )

            // When
            val domains: List<StockAmountRanking>
            with(StockMapper) {
                domains = entities.toRankingDomain()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("005930", domains[0].stockTicker)
            assertEquals("000660", domains[1].stockTicker)
        }

        @Test
        @DisplayName("List.toRankingDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `toRankingDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<StockAmountRankingEntity>().toRankingDomain().size)
            }
        }
    }

    // ========== StockChangeInfo ==========

    @Nested
    @DisplayName("StockChangeInfoEntity → Domain 변환")
    inner class StockChangeInfoTests {

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val entity = StockChangeInfoEntity(
                stockTicker = "005930",
                stockName = "삼성전자",
                etfTicker = "069500",
                etfName = "KODEX 200",
                currentWeight = 4.0f,
                currentAmount = 1_200_000f,
                previousWeight = 3.5f,
                change = 0.5f
            )

            // When
            val domain: StockChangeInfo
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("005930", domain.stockTicker)
            assertEquals("삼성전자", domain.stockName)
            assertEquals("069500", domain.etfTicker)
            assertEquals("KODEX 200", domain.etfName)
            assertEquals(4.0f, domain.currentWeight)
            assertEquals(1_200_000f, domain.currentAmount)
            assertEquals(3.5f, domain.previousWeight)
            assertEquals(0.5f, domain.change)
        }

        @Test
        @DisplayName("List.toChangeInfoDomain()은 모든 요소를 변환한다")
        fun `toChangeInfoDomain_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                StockChangeInfoEntity("005930", "삼성전자", "069500", "KODEX 200", 4.0f, 1_200_000f, 3.5f, 0.5f),
                StockChangeInfoEntity("000660", "SK하이닉스", "069500", "KODEX 200", 2.1f, 600_000f, 0f, 2.1f)
            )

            // When
            val domains: List<StockChangeInfo>
            with(StockMapper) {
                domains = entities.toChangeInfoDomain()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("005930", domains[0].stockTicker)
            assertEquals("000660", domains[1].stockTicker)
        }

        @Test
        @DisplayName("List.toChangeInfoDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `toChangeInfoDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<StockChangeInfoEntity>().toChangeInfoDomain().size)
            }
        }
    }

    // ========== CashDepositTrend ==========

    @Nested
    @DisplayName("CashDepositTrendEntity → Domain 변환")
    inner class CashDepositTrendTests {

        @Test
        @DisplayName("toDomain()은 date, totalAmount, etfCount를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsFieldsCorrectly`() {
            // Given
            val entity = CashDepositTrendEntity(
                date = "2025-01-15",
                totalAmount = 2_500_000f,
                etfCount = 5
            )

            // When
            val domain: CashDepositTrend
            with(StockMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("2025-01-15", domain.date)
            assertEquals(2_500_000f, domain.totalAmount)
            assertEquals(5, domain.etfCount)
        }

        @Test
        @DisplayName("List.toCashDepositDomain()은 모든 요소를 변환한다")
        fun `toCashDepositDomain_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                CashDepositTrendEntity("2025-01-15", 2_500_000f, 5),
                CashDepositTrendEntity("2025-01-14", 2_400_000f, 4),
                CashDepositTrendEntity("2025-01-13", 2_300_000f, 3)
            )

            // When
            val domains: List<CashDepositTrend>
            with(StockMapper) {
                domains = entities.toCashDepositDomain()
            }

            // Then
            assertEquals(3, domains.size)
            assertEquals("2025-01-15", domains[0].date)
            assertEquals(2_400_000f, domains[1].totalAmount)
            assertEquals(3, domains[2].etfCount)
        }

        @Test
        @DisplayName("List.toCashDepositDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `toCashDepositDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<CashDepositTrendEntity>().toCashDepositDomain().size)
            }
        }
    }

    // ========== StockSearchResult ==========

    @Nested
    @DisplayName("StockSearchResultDb → Domain 변환")
    inner class StockSearchResultTests {

        @Test
        @DisplayName("toDomain()은 stockTicker와 stockName을 올바르게 변환한다")
        fun `toDomain_withValidDb_mapsFieldsCorrectly`() {
            // Given
            val dbResult = StockSearchResultDb(
                stockTicker = "005930",
                stockName = "삼성전자"
            )

            // When
            val domain: StockSearchResult
            with(StockMapper) {
                domain = dbResult.toDomain()
            }

            // Then
            assertEquals("005930", domain.stockTicker)
            assertEquals("삼성전자", domain.stockName)
        }

        @Test
        @DisplayName("List.toSearchResultDomain()은 모든 요소를 변환한다")
        fun `toSearchResultDomain_withMultipleResults_convertsAll`() {
            // Given
            val dbResults = listOf(
                StockSearchResultDb("005930", "삼성전자"),
                StockSearchResultDb("000660", "SK하이닉스"),
                StockSearchResultDb("035420", "NAVER")
            )

            // When
            val domains: List<StockSearchResult>
            with(StockMapper) {
                domains = dbResults.toSearchResultDomain()
            }

            // Then
            assertEquals(3, domains.size)
            assertEquals("005930", domains[0].stockTicker)
            assertEquals("000660", domains[1].stockTicker)
            assertEquals("035420", domains[2].stockTicker)
        }

        @Test
        @DisplayName("List.toSearchResultDomain()은 빈 리스트에 빈 리스트를 반환한다")
        fun `toSearchResultDomain_withEmptyList_returnsEmptyList`() {
            with(StockMapper) {
                assertEquals(0, emptyList<StockSearchResultDb>().toSearchResultDomain().size)
            }
        }
    }
}
