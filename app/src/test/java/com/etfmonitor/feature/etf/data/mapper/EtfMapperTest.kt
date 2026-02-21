package com.etfmonitor.feature.etf.data.mapper

import com.etfmonitor.core.database.entities.HoldingStatus as HoldingStatusEntity
import com.etfmonitor.core.database.entities.HoldingWithComparison as HoldingWithComparisonEntity
import com.etfmonitor.core.database.entities.Etf as EtfEntity
import com.etfmonitor.feature.etf.data.mapper.EtfMapper.toDomain
import com.etfmonitor.feature.etf.data.mapper.EtfMapper.toDomainComparisons
import com.etfmonitor.feature.etf.data.mapper.EtfMapper.toEntity
import com.etfmonitor.feature.etf.domain.model.Etf
import com.etfmonitor.feature.etf.domain.model.HoldingStatus
import com.etfmonitor.feature.etf.domain.model.HoldingWithComparison
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * EtfMapper 단위 테스트
 *
 * 테스트 범위:
 * - EtfEntity → Domain 변환
 * - HoldingStatus Entity ↔ Domain 양방향 변환
 * - HoldingWithComparison Entity → Domain 변환
 * - 리스트 변환
 */
@DisplayName("EtfMapper 테스트")
class EtfMapperTest {

    // ========== Etf Entity → Domain ==========

    @Nested
    @DisplayName("Etf Entity → Domain 변환")
    inner class EtfEntityToDomainTests {

        @Test
        @DisplayName("toDomain()은 ticker와 name을 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsFieldsCorrectly`() {
            // Given
            val entity = EtfEntity(ticker = "069500", name = "KODEX 200")

            // When
            val domain: Etf
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("069500", domain.ticker)
            assertEquals("KODEX 200", domain.name)
        }

        @Test
        @DisplayName("toDomain()은 특수문자가 포함된 ETF 이름을 올바르게 변환한다")
        fun `toDomain_withSpecialCharsInName_preservesName`() {
            // Given
            val entity = EtfEntity(ticker = "091160", name = "KODEX 반도체 & 반도체장비")

            // When
            val domain: Etf
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("KODEX 반도체 & 반도체장비", domain.name)
        }

        @Test
        @DisplayName("List<EtfEntity>.toDomain()은 모든 요소를 변환한다")
        fun `listToDomain_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                EtfEntity(ticker = "069500", name = "KODEX 200"),
                EtfEntity(ticker = "229200", name = "KODEX 코스닥150"),
                EtfEntity(ticker = "102110", name = "TIGER 200")
            )

            // When
            val domains: List<Etf>
            with(EtfMapper) {
                domains = entities.toDomain()
            }

            // Then
            assertEquals(3, domains.size)
            assertEquals("069500", domains[0].ticker)
            assertEquals("229200", domains[1].ticker)
            assertEquals("102110", domains[2].ticker)
        }

        @Test
        @DisplayName("List<EtfEntity>.toDomain()은 빈 리스트에 대해 빈 리스트를 반환한다")
        fun `listToDomain_withEmptyList_returnsEmptyList`() {
            // Given
            val entities = emptyList<EtfEntity>()

            // When
            val domains: List<Etf>
            with(EtfMapper) {
                domains = entities.toDomain()
            }

            // Then
            assertEquals(0, domains.size)
        }
    }

    // ========== HoldingStatus Entity ↔ Domain ==========

    @Nested
    @DisplayName("HoldingStatus Entity → Domain 변환")
    inner class HoldingStatusEntityToDomainTests {

        @Test
        @DisplayName("HoldingStatusEntity.NEW → HoldingStatus.NEW")
        fun `toDomain_NEW_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatus.NEW, HoldingStatusEntity.NEW.toDomain())
            }
        }

        @Test
        @DisplayName("HoldingStatusEntity.INCREASE → HoldingStatus.INCREASE")
        fun `toDomain_INCREASE_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatus.INCREASE, HoldingStatusEntity.INCREASE.toDomain())
            }
        }

        @Test
        @DisplayName("HoldingStatusEntity.DECREASE → HoldingStatus.DECREASE")
        fun `toDomain_DECREASE_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatus.DECREASE, HoldingStatusEntity.DECREASE.toDomain())
            }
        }

        @Test
        @DisplayName("HoldingStatusEntity.MAINTAIN → HoldingStatus.MAINTAIN")
        fun `toDomain_MAINTAIN_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatus.MAINTAIN, HoldingStatusEntity.MAINTAIN.toDomain())
            }
        }

        @Test
        @DisplayName("HoldingStatusEntity.REMOVED → HoldingStatus.REMOVED")
        fun `toDomain_REMOVED_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatus.REMOVED, HoldingStatusEntity.REMOVED.toDomain())
            }
        }

        @Test
        @DisplayName("모든 HoldingStatus 값이 양방향으로 변환된다 (roundtrip)")
        fun `holdingStatus_allValues_roundtripCorrectly`() {
            with(EtfMapper) {
                HoldingStatus.entries.forEach { domainStatus ->
                    val entityStatus = domainStatus.toEntity()
                    val backToDomain = entityStatus.toDomain()
                    assertEquals(domainStatus, backToDomain, "Roundtrip failed for $domainStatus")
                }
            }
        }
    }

    @Nested
    @DisplayName("HoldingStatus Domain → Entity 변환")
    inner class HoldingStatusDomainToEntityTests {

        @Test
        @DisplayName("HoldingStatus.NEW → HoldingStatusEntity.NEW")
        fun `toEntity_NEW_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatusEntity.NEW, HoldingStatus.NEW.toEntity())
            }
        }

        @Test
        @DisplayName("HoldingStatus.INCREASE → HoldingStatusEntity.INCREASE")
        fun `toEntity_INCREASE_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatusEntity.INCREASE, HoldingStatus.INCREASE.toEntity())
            }
        }

        @Test
        @DisplayName("HoldingStatus.DECREASE → HoldingStatusEntity.DECREASE")
        fun `toEntity_DECREASE_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatusEntity.DECREASE, HoldingStatus.DECREASE.toEntity())
            }
        }

        @Test
        @DisplayName("HoldingStatus.MAINTAIN → HoldingStatusEntity.MAINTAIN")
        fun `toEntity_MAINTAIN_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatusEntity.MAINTAIN, HoldingStatus.MAINTAIN.toEntity())
            }
        }

        @Test
        @DisplayName("HoldingStatus.REMOVED → HoldingStatusEntity.REMOVED")
        fun `toEntity_REMOVED_mapsCorrectly`() {
            with(EtfMapper) {
                assertEquals(HoldingStatusEntity.REMOVED, HoldingStatus.REMOVED.toEntity())
            }
        }
    }

    // ========== HoldingWithComparison Entity → Domain ==========

    @Nested
    @DisplayName("HoldingWithComparison Entity → Domain 변환")
    inner class HoldingWithComparisonTests {

        @Test
        @DisplayName("toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val entity = HoldingWithComparisonEntity(
                stockTicker = "005930",
                stockName = "삼성전자",
                previousWeight = 3.5f,
                currentWeight = 4.0f,
                change = 0.5f,
                currentAmount = 1_000_000f,
                status = HoldingStatusEntity.INCREASE
            )

            // When
            val domain: HoldingWithComparison
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("005930", domain.stockTicker)
            assertEquals("삼성전자", domain.stockName)
            assertEquals(3.5f, domain.previousWeight)
            assertEquals(4.0f, domain.currentWeight)
            assertEquals(0.5f, domain.change)
            assertEquals(1_000_000f, domain.currentAmount)
            assertEquals(HoldingStatus.INCREASE, domain.status)
        }

        @Test
        @DisplayName("toDomain()은 NEW 상태를 올바르게 변환한다")
        fun `toDomain_withNewStatus_mapsStatusCorrectly`() {
            // Given
            val entity = HoldingWithComparisonEntity(
                stockTicker = "000660",
                stockName = "SK하이닉스",
                previousWeight = 0f,
                currentWeight = 2.1f,
                change = 2.1f,
                currentAmount = 500_000f,
                status = HoldingStatusEntity.NEW
            )

            // When
            val domain: HoldingWithComparison
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals(HoldingStatus.NEW, domain.status)
            assertEquals(0f, domain.previousWeight)
            assertEquals(2.1f, domain.currentWeight)
        }

        @Test
        @DisplayName("toDomain()은 REMOVED 상태에서 0 현재 비중을 올바르게 변환한다")
        fun `toDomain_withRemovedStatus_mapsZeroCurrentWeightCorrectly`() {
            // Given
            val entity = HoldingWithComparisonEntity(
                stockTicker = "035420",
                stockName = "NAVER",
                previousWeight = 1.5f,
                currentWeight = 0f,
                change = -1.5f,
                currentAmount = 0f,
                status = HoldingStatusEntity.REMOVED
            )

            // When
            val domain: HoldingWithComparison
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals(HoldingStatus.REMOVED, domain.status)
            assertEquals(0f, domain.currentWeight)
            assertEquals(-1.5f, domain.change)
        }

        @Test
        @DisplayName("List.toDomainComparisons()는 모든 요소를 변환한다")
        fun `toDomainComparisons_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                HoldingWithComparisonEntity(
                    stockTicker = "005930",
                    stockName = "삼성전자",
                    previousWeight = 3.5f,
                    currentWeight = 4.0f,
                    change = 0.5f,
                    currentAmount = 1_000_000f,
                    status = HoldingStatusEntity.INCREASE
                ),
                HoldingWithComparisonEntity(
                    stockTicker = "000660",
                    stockName = "SK하이닉스",
                    previousWeight = 0f,
                    currentWeight = 2.1f,
                    change = 2.1f,
                    currentAmount = 500_000f,
                    status = HoldingStatusEntity.NEW
                )
            )

            // When
            val domains: List<HoldingWithComparison>
            with(EtfMapper) {
                domains = entities.toDomainComparisons()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("005930", domains[0].stockTicker)
            assertEquals("000660", domains[1].stockTicker)
            assertEquals(HoldingStatus.INCREASE, domains[0].status)
            assertEquals(HoldingStatus.NEW, domains[1].status)
        }

        @Test
        @DisplayName("List.toDomainComparisons()는 빈 리스트에 빈 리스트를 반환한다")
        fun `toDomainComparisons_withEmptyList_returnsEmptyList`() {
            // Given
            val entities = emptyList<HoldingWithComparisonEntity>()

            // When
            val domains: List<HoldingWithComparison>
            with(EtfMapper) {
                domains = entities.toDomainComparisons()
            }

            // Then
            assertEquals(0, domains.size)
        }

        @Test
        @DisplayName("toDomain()은 MAINTAIN 상태에서 동일한 이전/현재 비중을 올바르게 변환한다")
        fun `toDomain_withMaintainStatus_mapsEqualWeightsCorrectly`() {
            // Given
            val entity = HoldingWithComparisonEntity(
                stockTicker = "035720",
                stockName = "카카오",
                previousWeight = 1.2f,
                currentWeight = 1.2f,
                change = 0f,
                currentAmount = 300_000f,
                status = HoldingStatusEntity.MAINTAIN
            )

            // When
            val domain: HoldingWithComparison
            with(EtfMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals(HoldingStatus.MAINTAIN, domain.status)
            assertEquals(domain.previousWeight, domain.currentWeight)
            assertEquals(0f, domain.change)
        }
    }
}
