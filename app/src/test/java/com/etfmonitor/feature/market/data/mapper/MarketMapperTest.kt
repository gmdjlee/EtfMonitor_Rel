package com.etfmonitor.feature.market.data.mapper

import com.etfmonitor.core.analysis.model.MarketDepositData as LegacyMarketDepositData
import com.etfmonitor.core.database.entities.BloodIndicator as BloodIndicatorEntity
import com.etfmonitor.core.database.entities.FearGreedIndex as FearGreedEntity
import com.etfmonitor.core.database.entities.MarketDeposit as MarketDepositEntity
import com.etfmonitor.core.database.entities.MarketIndex as MarketIndexEntity
import com.etfmonitor.core.database.entities.MarketOscillatorData as MarketOscillatorEntity
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toBloodDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDomain
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toDepositDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toEntity
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toFearGreedDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toIndexDomainList
import com.etfmonitor.feature.market.data.mapper.MarketMapper.toOscillatorDomainList
import com.etfmonitor.feature.market.domain.model.BloodIndicator
import com.etfmonitor.feature.market.domain.model.BloodSignalType
import com.etfmonitor.feature.market.domain.model.FearGreedIndex
import com.etfmonitor.feature.market.domain.model.MarketDeposit
import com.etfmonitor.feature.market.domain.model.MarketDepositData
import com.etfmonitor.feature.market.domain.model.MarketIndex
import com.etfmonitor.feature.market.domain.model.MarketOscillator
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * MarketMapper 단위 테스트
 *
 * 테스트 범위:
 * - FearGreedIndex Entity ↔ Domain 양방향 변환
 * - MarketDeposit Entity ↔ Domain 양방향 변환
 * - LegacyMarketDepositData → Domain 변환
 * - MarketOscillator Entity ↔ Domain 양방향 변환
 * - MarketIndex Entity ↔ Domain 양방향 변환
 * - BloodIndicator Entity ↔ Domain 양방향 변환 (BloodSignalType.fromCode 포함)
 * - 리스트 변환
 */
@DisplayName("MarketMapper 테스트")
class MarketMapperTest {

    // ========== FearGreedIndex ==========

    @Nested
    @DisplayName("FearGreedIndex Entity ↔ Domain 변환")
    inner class FearGreedIndexTests {

        @Test
        @DisplayName("FearGreedEntity.toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = FearGreedEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                indexValue = 2800.0,
                fearGreedValue = 65.5,
                oscillator = 0.5,
                rsi = 55.0,
                momentum = 0.02,
                putCallRatio = 0.95,
                volatility = 15.0,
                spread = 0.3,
                lastUpdated = now
            )

            // When
            val domain: FearGreedIndex
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("KOSPI-2025-01-15", domain.id)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.date)
            assertEquals(2800.0, domain.indexValue)
            assertEquals(65.5, domain.fearGreedValue)
            assertEquals(0.5, domain.oscillator)
            assertEquals(55.0, domain.rsi)
            assertEquals(0.02, domain.momentum)
            assertEquals(0.95, domain.putCallRatio)
            assertEquals(15.0, domain.volatility)
            assertEquals(0.3, domain.spread)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("FearGreedIndex.toEntity()는 domain을 entity로 올바르게 역변환한다")
        fun `toEntity_withValidDomain_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = FearGreedIndex(
                id = "KOSDAQ-2025-01-15",
                market = "KOSDAQ",
                date = "2025-01-15",
                indexValue = 850.0,
                fearGreedValue = 40.0,
                oscillator = -0.2,
                rsi = 45.0,
                momentum = -0.01,
                putCallRatio = 1.1,
                volatility = 20.0,
                spread = 0.5,
                lastUpdated = now
            )

            // When
            val entity: FearGreedEntity
            with(MarketMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("KOSDAQ-2025-01-15", entity.id)
            assertEquals("KOSDAQ", entity.market)
            assertEquals("2025-01-15", entity.date)
            assertEquals(850.0, entity.indexValue)
            assertEquals(40.0, entity.fearGreedValue)
            assertEquals(now, entity.lastUpdated)
        }

        @Test
        @DisplayName("FearGreedEntity → Domain → Entity 라운드트립은 동일한 데이터를 유지한다")
        fun `toDomainToEntity_roundtrip_preservesAllFields`() {
            // Given
            val original = FearGreedEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                indexValue = 2800.0,
                fearGreedValue = 65.5,
                oscillator = 0.5,
                rsi = 55.0,
                momentum = 0.02,
                putCallRatio = 0.95,
                volatility = 15.0,
                spread = 0.3,
                lastUpdated = 1705300800000L
            )

            // When
            val roundtripped: FearGreedEntity
            with(MarketMapper) {
                roundtripped = original.toDomain().toEntity()
            }

            // Then
            assertEquals(original, roundtripped)
        }

        @Test
        @DisplayName("List<FearGreedEntity>.toFearGreedDomainList()는 모든 요소를 변환한다")
        fun `toFearGreedDomainList_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                FearGreedEntity("KOSPI-2025-01-15", "KOSPI", "2025-01-15", 2800.0, 65.5, 0.5, 55.0, 0.02, 0.95, 15.0, 0.3, 0L),
                FearGreedEntity("KOSPI-2025-01-14", "KOSPI", "2025-01-14", 2790.0, 60.0, 0.3, 52.0, 0.01, 0.98, 14.0, 0.25, 0L)
            )

            // When
            val domains: List<FearGreedIndex>
            with(MarketMapper) {
                domains = entities.toFearGreedDomainList()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("KOSPI-2025-01-15", domains[0].id)
            assertEquals("KOSPI-2025-01-14", domains[1].id)
        }
    }

    // ========== MarketDeposit ==========

    @Nested
    @DisplayName("MarketDeposit Entity ↔ Domain 변환")
    inner class MarketDepositTests {

        @Test
        @DisplayName("MarketDepositEntity.toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = MarketDepositEntity(
                date = "2025-01-15",
                depositAmount = 500_000.0,
                depositChange = 5_000.0,
                creditAmount = 20_000.0,
                creditChange = -500.0,
                lastUpdated = now
            )

            // When
            val domain: MarketDeposit
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("2025-01-15", domain.date)
            assertEquals(500_000.0, domain.depositAmount)
            assertEquals(5_000.0, domain.depositChange)
            assertEquals(20_000.0, domain.creditAmount)
            assertEquals(-500.0, domain.creditChange)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("MarketDeposit.toEntity()는 domain을 entity로 올바르게 역변환한다")
        fun `toEntity_withValidDomain_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = MarketDeposit(
                date = "2025-01-15",
                depositAmount = 500_000.0,
                depositChange = 5_000.0,
                creditAmount = 20_000.0,
                creditChange = -500.0,
                lastUpdated = now
            )

            // When
            val entity: MarketDepositEntity
            with(MarketMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("2025-01-15", entity.date)
            assertEquals(500_000.0, entity.depositAmount)
            assertEquals(5_000.0, entity.depositChange)
            assertEquals(now, entity.lastUpdated)
        }

        @Test
        @DisplayName("MarketDepositEntity → Domain → Entity 라운드트립은 동일한 데이터를 유지한다")
        fun `toDomainToEntity_roundtrip_preservesAllFields`() {
            // Given
            val original = MarketDepositEntity(
                date = "2025-01-15",
                depositAmount = 500_000.0,
                depositChange = 5_000.0,
                creditAmount = 20_000.0,
                creditChange = -500.0,
                lastUpdated = 1705300800000L
            )

            // When
            val roundtripped: MarketDepositEntity
            with(MarketMapper) {
                roundtripped = original.toDomain().toEntity()
            }

            // Then
            assertEquals(original, roundtripped)
        }

        @Test
        @DisplayName("List<MarketDepositEntity>.toDepositDomainList()는 모든 요소를 변환한다")
        fun `toDepositDomainList_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                MarketDepositEntity("2025-01-15", 500_000.0, 5_000.0, 20_000.0, -500.0, 0L),
                MarketDepositEntity("2025-01-14", 495_000.0, -2_000.0, 20_500.0, 200.0, 0L)
            )

            // When
            val domains: List<MarketDeposit>
            with(MarketMapper) {
                domains = entities.toDepositDomainList()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("2025-01-15", domains[0].date)
            assertEquals("2025-01-14", domains[1].date)
        }
    }

    // ========== LegacyMarketDepositData ==========

    @Nested
    @DisplayName("LegacyMarketDepositData → Domain 변환")
    inner class LegacyMarketDepositDataTests {

        @Test
        @DisplayName("LegacyMarketDepositData.toDomain()은 모든 리스트를 올바르게 변환한다")
        fun `toDomain_withValidLegacyData_mapsAllListsCorrectly`() {
            // Given
            val legacy = LegacyMarketDepositData(
                dates = listOf("2025-01-15", "2025-01-14"),
                depositAmounts = listOf(500_000.0, 495_000.0),
                depositChanges = listOf(5_000.0, -2_000.0),
                creditAmounts = listOf(20_000.0, 20_500.0),
                creditChanges = listOf(-500.0, 200.0)
            )

            // When
            val domain: MarketDepositData
            with(MarketMapper) {
                domain = legacy.toDomain()
            }

            // Then
            assertEquals(listOf("2025-01-15", "2025-01-14"), domain.dates)
            assertEquals(listOf(500_000.0, 495_000.0), domain.depositAmounts)
            assertEquals(listOf(5_000.0, -2_000.0), domain.depositChanges)
            assertEquals(listOf(20_000.0, 20_500.0), domain.creditAmounts)
            assertEquals(listOf(-500.0, 200.0), domain.creditChanges)
        }

        @Test
        @DisplayName("LegacyMarketDepositData.toDomain()은 빈 리스트를 올바르게 처리한다")
        fun `toDomain_withEmptyLists_mapsEmptyListsCorrectly`() {
            // Given
            val legacy = LegacyMarketDepositData(
                dates = emptyList(),
                depositAmounts = emptyList(),
                depositChanges = emptyList(),
                creditAmounts = emptyList(),
                creditChanges = emptyList()
            )

            // When
            val domain: MarketDepositData
            with(MarketMapper) {
                domain = legacy.toDomain()
            }

            // Then
            assertEquals(0, domain.dates.size)
            assertEquals(0, domain.depositAmounts.size)
        }
    }

    // ========== MarketOscillator ==========

    @Nested
    @DisplayName("MarketOscillator Entity ↔ Domain 변환")
    inner class MarketOscillatorTests {

        @Test
        @DisplayName("MarketOscillatorEntity.toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = MarketOscillatorEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                indexValue = 2800.0,
                oscillator = 75.0,
                lastUpdated = now
            )

            // When
            val domain: MarketOscillator
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("KOSPI-2025-01-15", domain.id)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.date)
            assertEquals(2800.0, domain.indexValue)
            assertEquals(75.0, domain.oscillator)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("MarketOscillator.toEntity()는 domain을 entity로 올바르게 역변환한다")
        fun `toEntity_withValidDomain_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = MarketOscillator(
                id = "KOSDAQ-2025-01-15",
                market = "KOSDAQ",
                date = "2025-01-15",
                indexValue = 850.0,
                oscillator = 30.0,
                lastUpdated = now
            )

            // When
            val entity: MarketOscillatorEntity
            with(MarketMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("KOSDAQ-2025-01-15", entity.id)
            assertEquals("KOSDAQ", entity.market)
            assertEquals(850.0, entity.indexValue)
            assertEquals(30.0, entity.oscillator)
        }

        @Test
        @DisplayName("MarketOscillatorEntity → Domain → Entity 라운드트립은 동일한 데이터를 유지한다")
        fun `toDomainToEntity_roundtrip_preservesAllFields`() {
            // Given
            val original = MarketOscillatorEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                indexValue = 2800.0,
                oscillator = 75.0,
                lastUpdated = 1705300800000L
            )

            // When
            val roundtripped: MarketOscillatorEntity
            with(MarketMapper) {
                roundtripped = original.toDomain().toEntity()
            }

            // Then
            assertEquals(original, roundtripped)
        }

        @Test
        @DisplayName("List<MarketOscillatorEntity>.toOscillatorDomainList()는 모든 요소를 변환한다")
        fun `toOscillatorDomainList_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                MarketOscillatorEntity("KOSPI-2025-01-15", "KOSPI", "2025-01-15", 2800.0, 75.0, 0L),
                MarketOscillatorEntity("KOSPI-2025-01-14", "KOSPI", "2025-01-14", 2790.0, 70.0, 0L)
            )

            // When
            val domains: List<MarketOscillator>
            with(MarketMapper) {
                domains = entities.toOscillatorDomainList()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("KOSPI-2025-01-15", domains[0].id)
        }
    }

    // ========== MarketIndex ==========

    @Nested
    @DisplayName("MarketIndex Entity ↔ Domain 변환")
    inner class MarketIndexTests {

        @Test
        @DisplayName("MarketIndexEntity.toDomain()은 모든 필드를 올바르게 변환한다")
        fun `toDomain_withValidEntity_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = MarketIndexEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                closePrice = 2800.0,
                openPrice = 2780.0,
                highPrice = 2820.0,
                lowPrice = 2775.0,
                volume = 500_000_000L,
                changeRate = 0.71,
                lastUpdated = now
            )

            // When
            val domain: MarketIndex
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("KOSPI-2025-01-15", domain.id)
            assertEquals("KOSPI", domain.market)
            assertEquals("2025-01-15", domain.date)
            assertEquals(2800.0, domain.closePrice)
            assertEquals(2780.0, domain.openPrice)
            assertEquals(2820.0, domain.highPrice)
            assertEquals(2775.0, domain.lowPrice)
            assertEquals(500_000_000L, domain.volume)
            assertEquals(0.71, domain.changeRate)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("MarketIndex.toEntity()는 domain을 entity로 올바르게 역변환한다")
        fun `toEntity_withValidDomain_mapsAllFieldsCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = MarketIndex(
                id = "KOSDAQ-2025-01-15",
                market = "KOSDAQ",
                date = "2025-01-15",
                closePrice = 850.0,
                openPrice = 840.0,
                highPrice = 860.0,
                lowPrice = 835.0,
                volume = 200_000_000L,
                changeRate = -0.5,
                lastUpdated = now
            )

            // When
            val entity: MarketIndexEntity
            with(MarketMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("KOSDAQ-2025-01-15", entity.id)
            assertEquals(850.0, entity.closePrice)
            assertEquals(-0.5, entity.changeRate)
        }

        @Test
        @DisplayName("MarketIndexEntity → Domain → Entity 라운드트립은 동일한 데이터를 유지한다")
        fun `toDomainToEntity_roundtrip_preservesAllFields`() {
            // Given
            val original = MarketIndexEntity(
                id = "KOSPI-2025-01-15",
                market = "KOSPI",
                date = "2025-01-15",
                closePrice = 2800.0,
                openPrice = 2780.0,
                highPrice = 2820.0,
                lowPrice = 2775.0,
                volume = 500_000_000L,
                changeRate = 0.71,
                lastUpdated = 1705300800000L
            )

            // When
            val roundtripped: MarketIndexEntity
            with(MarketMapper) {
                roundtripped = original.toDomain().toEntity()
            }

            // Then
            assertEquals(original, roundtripped)
        }

        @Test
        @DisplayName("List<MarketIndexEntity>.toIndexDomainList()는 모든 요소를 변환한다")
        fun `toIndexDomainList_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                MarketIndexEntity("KOSPI-2025-01-15", "KOSPI", "2025-01-15", 2800.0, 2780.0, 2820.0, 2775.0, 500_000_000L, 0.71, 0L),
                MarketIndexEntity("KOSPI-2025-01-14", "KOSPI", "2025-01-14", 2780.0, 2760.0, 2790.0, 2755.0, 450_000_000L, -0.3, 0L)
            )

            // When
            val domains: List<MarketIndex>
            with(MarketMapper) {
                domains = entities.toIndexDomainList()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals("KOSPI-2025-01-15", domains[0].id)
        }
    }

    // ========== BloodIndicator ==========

    @Nested
    @DisplayName("BloodIndicator Entity ↔ Domain 변환")
    inner class BloodIndicatorTests {

        @Test
        @DisplayName("BloodIndicatorEntity.toDomain()은 RISK_ON 신호를 올바르게 변환한다")
        fun `toDomain_withRiskOnSignal_mapsSignalTypeCorrectly`() {
            // Given
            val now = System.currentTimeMillis()
            val entity = BloodIndicatorEntity(
                id = "BLOOD-2025-01-15",
                date = "2025-01-15",
                bloodValue = 1.5,
                bloodSma = 1.2,
                us03my = 5.3,
                highYieldSpread = 3.5,
                spyClose = 475.0,
                signalType = "RISK_ON",
                signalColor = "green",
                lastUpdated = now
            )

            // When
            val domain: BloodIndicator
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals("BLOOD-2025-01-15", domain.id)
            assertEquals("2025-01-15", domain.date)
            assertEquals(1.5, domain.bloodValue)
            assertEquals(1.2, domain.bloodSma)
            assertEquals(5.3, domain.us03my)
            assertEquals(3.5, domain.highYieldSpread)
            assertEquals(475.0, domain.spyClose)
            assertEquals(BloodSignalType.RISK_ON, domain.signalType)
            assertEquals("green", domain.signalColor)
            assertEquals(now, domain.lastUpdated)
        }

        @Test
        @DisplayName("BloodIndicatorEntity.toDomain()은 RISK_OFF 신호를 올바르게 변환한다")
        fun `toDomain_withRiskOffSignal_mapsSignalTypeCorrectly`() {
            // Given
            val entity = BloodIndicatorEntity(
                id = "BLOOD-2025-01-10",
                date = "2025-01-10",
                bloodValue = 0.8,
                bloodSma = 1.1,
                us03my = 4.9,
                highYieldSpread = 6.1,
                spyClose = null,
                signalType = "RISK_OFF",
                signalColor = "red",
                lastUpdated = 0L
            )

            // When
            val domain: BloodIndicator
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals(BloodSignalType.RISK_OFF, domain.signalType)
            assertEquals("red", domain.signalColor)
            assertNull(domain.spyClose)
        }

        @Test
        @DisplayName("BloodIndicatorEntity.toDomain()은 NEUTRAL 신호를 올바르게 변환한다")
        fun `toDomain_withNeutralSignal_mapsSignalTypeCorrectly`() {
            // Given
            val entity = BloodIndicatorEntity(
                id = "BLOOD-2025-01-05",
                date = "2025-01-05",
                bloodValue = 1.0,
                bloodSma = 1.0,
                us03my = 5.0,
                highYieldSpread = 5.0,
                spyClose = 470.0,
                signalType = "NEUTRAL",
                signalColor = "gray",
                lastUpdated = 0L
            )

            // When
            val domain: BloodIndicator
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then
            assertEquals(BloodSignalType.NEUTRAL, domain.signalType)
        }

        @Test
        @DisplayName("BloodIndicatorEntity.toDomain()은 알 수 없는 signalType을 NEUTRAL로 처리한다")
        fun `toDomain_withUnknownSignalType_defaultsToNeutral`() {
            // Given
            val entity = BloodIndicatorEntity(
                id = "BLOOD-2025-01-03",
                date = "2025-01-03",
                bloodValue = 1.0,
                bloodSma = 1.0,
                us03my = 5.0,
                highYieldSpread = 5.0,
                spyClose = null,
                signalType = "UNKNOWN_SIGNAL",
                signalColor = "gray",
                lastUpdated = 0L
            )

            // When
            val domain: BloodIndicator
            with(MarketMapper) {
                domain = entity.toDomain()
            }

            // Then — BloodSignalType.fromCode falls back to NEUTRAL for unknown codes
            assertEquals(BloodSignalType.NEUTRAL, domain.signalType)
        }

        @Test
        @DisplayName("BloodIndicator.toEntity()는 signalType.code를 문자열로 저장한다")
        fun `toEntity_withValidDomain_mapsSignalTypeCodeAsString`() {
            // Given
            val now = System.currentTimeMillis()
            val domain = BloodIndicator(
                id = "BLOOD-2025-01-15",
                date = "2025-01-15",
                bloodValue = 1.5,
                bloodSma = 1.2,
                us03my = 5.3,
                highYieldSpread = 3.5,
                spyClose = 475.0,
                signalType = BloodSignalType.RISK_ON,
                signalColor = "green",
                lastUpdated = now
            )

            // When
            val entity: BloodIndicatorEntity
            with(MarketMapper) {
                entity = domain.toEntity()
            }

            // Then
            assertEquals("RISK_ON", entity.signalType)
            assertEquals("green", entity.signalColor)
            assertEquals(475.0, entity.spyClose)
        }

        @Test
        @DisplayName("BloodIndicatorEntity → Domain → Entity 라운드트립은 동일한 데이터를 유지한다")
        fun `toDomainToEntity_roundtrip_preservesAllFields`() {
            // Given
            val original = BloodIndicatorEntity(
                id = "BLOOD-2025-01-15",
                date = "2025-01-15",
                bloodValue = 1.5,
                bloodSma = 1.2,
                us03my = 5.3,
                highYieldSpread = 3.5,
                spyClose = 475.0,
                signalType = "RISK_ON",
                signalColor = "green",
                lastUpdated = 1705300800000L
            )

            // When
            val roundtripped: BloodIndicatorEntity
            with(MarketMapper) {
                roundtripped = original.toDomain().toEntity()
            }

            // Then
            assertEquals(original, roundtripped)
        }

        @Test
        @DisplayName("List<BloodIndicatorEntity>.toBloodDomainList()는 모든 요소를 변환한다")
        fun `toBloodDomainList_withMultipleEntities_convertsAll`() {
            // Given
            val entities = listOf(
                BloodIndicatorEntity("BLOOD-2025-01-15", "2025-01-15", 1.5, 1.2, 5.3, 3.5, 475.0, "RISK_ON", "green", 0L),
                BloodIndicatorEntity("BLOOD-2025-01-08", "2025-01-08", 0.8, 1.1, 4.9, 6.1, null, "RISK_OFF", "red", 0L)
            )

            // When
            val domains: List<BloodIndicator>
            with(MarketMapper) {
                domains = entities.toBloodDomainList()
            }

            // Then
            assertEquals(2, domains.size)
            assertEquals(BloodSignalType.RISK_ON, domains[0].signalType)
            assertEquals(BloodSignalType.RISK_OFF, domains[1].signalType)
            assertNotNull(domains[0].spyClose)
            assertNull(domains[1].spyClose)
        }

        @Test
        @DisplayName("List<BloodIndicatorEntity>.toBloodDomainList()는 빈 리스트에 빈 리스트를 반환한다")
        fun `toBloodDomainList_withEmptyList_returnsEmptyList`() {
            with(MarketMapper) {
                assertEquals(0, emptyList<BloodIndicatorEntity>().toBloodDomainList().size)
            }
        }
    }
}
