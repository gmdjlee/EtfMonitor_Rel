package com.etfmonitor.feature.backup.data.repository

import android.content.Context
import com.etfmonitor.core.database.BackupDao
import com.etfmonitor.feature.backup.data.remote.GoogleDriveHelper
import com.etfmonitor.core.database.GlobalDateRange
import com.etfmonitor.feature.backup.domain.model.BackupOptions
import com.etfmonitor.feature.backup.domain.model.EntityType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.etfmonitor.MainDispatcherExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BackupRepositoryImpl 단위 테스트
 *
 * 테스트 범위:
 * - getEntityCounts: DAO 위임, 모든 엔티티 타입 포함 확인
 * - getDataDateRange: min/max 날짜 있음 → DateRange 반환, 없음 → null
 * - estimateBackupSize: 압축 여부에 따른 크기 추정
 * - isGoogleDriveSignedIn: GoogleDriveHelper 위임
 * - listGoogleDriveBackups: 미로그인 → failure
 * - deleteLocalBackup: 파일 없음 → failure
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
@DisplayName("BackupRepositoryImpl 테스트")
class BackupRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var backupDao: BackupDao
    private lateinit var googleDriveHelper: GoogleDriveHelper
    private lateinit var repository: BackupRepositoryImpl

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        backupDao = mockk(relaxed = true)
        googleDriveHelper = mockk(relaxed = true)

        // Provide a real temp dir so the repository's backupDirectory doesn't fail
        val tempDir = createTempDir("etf_backup_test")
        every { context.filesDir } returns tempDir

        repository = BackupRepositoryImpl(
            context = context,
            backupDao = backupDao,
            googleDriveHelper = googleDriveHelper
        )
    }

    // ============================================================
    // getEntityCounts
    // ============================================================

    @Nested
    @DisplayName("getEntityCounts 테스트")
    inner class GetEntityCountsTests {

        @Test
        @DisplayName("getEntityCounts_returnsCountForAllEntityTypes")
        fun `getEntityCounts_returnsCountForAllEntityTypes`() = runTest {
            // Given: mock all DAO count methods
            coEvery { backupDao.getEtfCount() } returns 50
            coEvery { backupDao.getStockCount() } returns 200
            coEvery { backupDao.getSettingCount() } returns 25
            coEvery { backupDao.getHoldingCount() } returns 50_000
            coEvery { backupDao.getMarketDepositCount() } returns 500
            coEvery { backupDao.getFearGreedCount() } returns 365
            coEvery { backupDao.getMarketOscillatorCount() } returns 300
            coEvery { backupDao.getMarketIndexCount() } returns 700
            coEvery { backupDao.getDailyEtfStatisticsCount() } returns 365
            coEvery { backupDao.getBloodIndicatorCount() } returns 200
            coEvery { backupDao.getPriceCacheCount() } returns 100_000
            coEvery { backupDao.getStockAnalysisDataCount() } returns 150
            coEvery { backupDao.getAIAnalysisResultCount() } returns 30
            coEvery { backupDao.getAIChatSessionCount() } returns 10
            coEvery { backupDao.getAIChatMessageCount() } returns 100
            coEvery { backupDao.getCorrelationResultCount() } returns 20
            coEvery { backupDao.getSectorAnalysisCount() } returns 15
            coEvery { backupDao.getEtfCorrelationCacheCount() } returns 40
            coEvery { backupDao.getLiquidityAnalysisCount() } returns 30
            coEvery { backupDao.getStockIndicatorAIResultCount() } returns 25
            coEvery { backupDao.getEnhancedPredictionCount() } returns 10
            coEvery { backupDao.getSearchHistoryCount() } returns 50

            // When
            val counts = repository.getEntityCounts()

            // Then
            assertEquals(50, counts[EntityType.ETF])
            assertEquals(200, counts[EntityType.STOCK])
            assertEquals(25, counts[EntityType.SETTING])
            assertEquals(50_000, counts[EntityType.HOLDING])
            assertEquals(50_000, counts[EntityType.HOLDING])
            assertEquals(365, counts[EntityType.FEAR_GREED_INDEX])
            assertEquals(100_000, counts[EntityType.PRICE_CACHE])
            // All 22 entity types should be present
            assertEquals(EntityType.entries.size, counts.size)
        }

        @Test
        @DisplayName("getEntityCounts_whenAllEmpty_returnsZeroCounts")
        fun `getEntityCounts_whenAllEmpty_returnsZeroCounts`() = runTest {
            // Given: all DAOs return 0
            coEvery { backupDao.getEtfCount() } returns 0
            coEvery { backupDao.getStockCount() } returns 0
            coEvery { backupDao.getSettingCount() } returns 0
            coEvery { backupDao.getHoldingCount() } returns 0
            coEvery { backupDao.getMarketDepositCount() } returns 0
            coEvery { backupDao.getFearGreedCount() } returns 0
            coEvery { backupDao.getMarketOscillatorCount() } returns 0
            coEvery { backupDao.getMarketIndexCount() } returns 0
            coEvery { backupDao.getDailyEtfStatisticsCount() } returns 0
            coEvery { backupDao.getBloodIndicatorCount() } returns 0
            coEvery { backupDao.getPriceCacheCount() } returns 0
            coEvery { backupDao.getStockAnalysisDataCount() } returns 0
            coEvery { backupDao.getAIAnalysisResultCount() } returns 0
            coEvery { backupDao.getAIChatSessionCount() } returns 0
            coEvery { backupDao.getAIChatMessageCount() } returns 0
            coEvery { backupDao.getCorrelationResultCount() } returns 0
            coEvery { backupDao.getSectorAnalysisCount() } returns 0
            coEvery { backupDao.getEtfCorrelationCacheCount() } returns 0
            coEvery { backupDao.getLiquidityAnalysisCount() } returns 0
            coEvery { backupDao.getStockIndicatorAIResultCount() } returns 0
            coEvery { backupDao.getEnhancedPredictionCount() } returns 0
            coEvery { backupDao.getSearchHistoryCount() } returns 0

            // When
            val counts = repository.getEntityCounts()

            // Then: all counts are 0
            counts.values.forEach { count ->
                assertEquals(0, count)
            }
        }
    }

    // ============================================================
    // getDataDateRange
    // ============================================================

    @Nested
    @DisplayName("getDataDateRange 테스트")
    inner class GetDataDateRangeTests {

        @Test
        @DisplayName("getDataDateRange_withValidGlobalRange_returnsDateRange")
        fun `getDataDateRange_withValidGlobalRange_returnsDateRange`() = runTest {
            // Given
            val globalRange = GlobalDateRange(minDate = "2024-01-01", maxDate = "2026-01-15")
            coEvery { backupDao.getGlobalDateRange() } returns globalRange

            // When
            val result = repository.getDataDateRange()

            // Then
            assertNotNull(result)
            assertEquals("2024-01-01", result.startDate)
            assertEquals("2026-01-15", result.endDate)
        }

        @Test
        @DisplayName("getDataDateRange_withNullGlobalRange_returnsNull")
        fun `getDataDateRange_withNullGlobalRange_returnsNull`() = runTest {
            // Given
            coEvery { backupDao.getGlobalDateRange() } returns null

            // When
            val result = repository.getDataDateRange()

            // Then
            assertNull(result)
        }

        @Test
        @DisplayName("getDataDateRange_withNullMinDate_returnsNull")
        fun `getDataDateRange_withNullMinDate_returnsNull`() = runTest {
            // Given
            val globalRange = GlobalDateRange(minDate = null, maxDate = "2026-01-15")
            coEvery { backupDao.getGlobalDateRange() } returns globalRange

            // When
            val result = repository.getDataDateRange()

            // Then
            assertNull(result)
        }
    }

    // ============================================================
    // estimateBackupSize
    // ============================================================

    @Nested
    @DisplayName("estimateBackupSize 테스트")
    inner class EstimateBackupSizeTests {

        @Test
        @DisplayName("estimateBackupSize_withLargeDataAndCompression_reducesEstimate")
        fun `estimateBackupSize_withLargeDataAndCompression_reducesEstimate`() = runTest {
            // Given: large holdings count (>1MB uncompressed estimate)
            coEvery { backupDao.getEtfCount() } returns 0
            coEvery { backupDao.getStockCount() } returns 0
            coEvery { backupDao.getSettingCount() } returns 0
            coEvery { backupDao.getHoldingCount() } returns 100_000  // 100K * 120 bytes = 12MB
            coEvery { backupDao.getMarketDepositCount() } returns 0
            coEvery { backupDao.getFearGreedCount() } returns 0
            coEvery { backupDao.getMarketOscillatorCount() } returns 0
            coEvery { backupDao.getMarketIndexCount() } returns 0
            coEvery { backupDao.getDailyEtfStatisticsCount() } returns 0
            coEvery { backupDao.getBloodIndicatorCount() } returns 0
            coEvery { backupDao.getPriceCacheCount() } returns 0
            coEvery { backupDao.getStockAnalysisDataCount() } returns 0
            coEvery { backupDao.getAIAnalysisResultCount() } returns 0
            coEvery { backupDao.getAIChatSessionCount() } returns 0
            coEvery { backupDao.getAIChatMessageCount() } returns 0
            coEvery { backupDao.getCorrelationResultCount() } returns 0
            coEvery { backupDao.getSectorAnalysisCount() } returns 0
            coEvery { backupDao.getEtfCorrelationCacheCount() } returns 0
            coEvery { backupDao.getLiquidityAnalysisCount() } returns 0
            coEvery { backupDao.getStockIndicatorAIResultCount() } returns 0
            coEvery { backupDao.getEnhancedPredictionCount() } returns 0
            coEvery { backupDao.getSearchHistoryCount() } returns 0

            val optionsWithCompression = BackupOptions(
                selectedEntities = setOf(EntityType.HOLDING),
                compress = true
            )
            val optionsNoCompression = BackupOptions(
                selectedEntities = setOf(EntityType.HOLDING),
                compress = false
            )

            // When
            val compressedSize = repository.estimateBackupSize(optionsWithCompression)
            val uncompressedSize = repository.estimateBackupSize(optionsNoCompression)

            // Then: compressed estimate should be ~6x smaller
            assertTrue(compressedSize < uncompressedSize,
                "Compressed size ($compressedSize) should be less than uncompressed ($uncompressedSize)")
            // 100K * 120 / 6 ≈ 2_000_000
            assertEquals(uncompressedSize / 6, compressedSize)
        }

        @Test
        @DisplayName("estimateBackupSize_withSmallDataAndCompression_noCompressionApplied")
        fun `estimateBackupSize_withSmallDataAndCompression_noCompressionApplied`() = runTest {
            // Given: small ETF count — below 1MB threshold
            coEvery { backupDao.getEtfCount() } returns 10  // 10 * 50 = 500 bytes
            coEvery { backupDao.getStockCount() } returns 0
            coEvery { backupDao.getSettingCount() } returns 0
            coEvery { backupDao.getHoldingCount() } returns 0
            coEvery { backupDao.getMarketDepositCount() } returns 0
            coEvery { backupDao.getFearGreedCount() } returns 0
            coEvery { backupDao.getMarketOscillatorCount() } returns 0
            coEvery { backupDao.getMarketIndexCount() } returns 0
            coEvery { backupDao.getDailyEtfStatisticsCount() } returns 0
            coEvery { backupDao.getBloodIndicatorCount() } returns 0
            coEvery { backupDao.getPriceCacheCount() } returns 0
            coEvery { backupDao.getStockAnalysisDataCount() } returns 0
            coEvery { backupDao.getAIAnalysisResultCount() } returns 0
            coEvery { backupDao.getAIChatSessionCount() } returns 0
            coEvery { backupDao.getAIChatMessageCount() } returns 0
            coEvery { backupDao.getCorrelationResultCount() } returns 0
            coEvery { backupDao.getSectorAnalysisCount() } returns 0
            coEvery { backupDao.getEtfCorrelationCacheCount() } returns 0
            coEvery { backupDao.getLiquidityAnalysisCount() } returns 0
            coEvery { backupDao.getStockIndicatorAIResultCount() } returns 0
            coEvery { backupDao.getEnhancedPredictionCount() } returns 0
            coEvery { backupDao.getSearchHistoryCount() } returns 0

            val options = BackupOptions(
                selectedEntities = setOf(EntityType.ETF),
                compress = true
            )

            // When
            val estimatedSize = repository.estimateBackupSize(options)

            // Then: 10 * 50 = 500 bytes, below 1MB threshold — no compression factor applied
            assertEquals(500L, estimatedSize)
        }
    }

    // ============================================================
    // isGoogleDriveSignedIn
    // ============================================================

    @Test
    @DisplayName("isGoogleDriveSignedIn_delegatesToGoogleDriveHelper")
    fun `isGoogleDriveSignedIn_delegatesToGoogleDriveHelper`() {
        // Given
        every { googleDriveHelper.isSignedIn() } returns true

        // When
        val result = repository.isGoogleDriveSignedIn()

        // Then
        assertTrue(result)
    }

    @Test
    @DisplayName("isGoogleDriveSignedIn_whenNotSignedIn_returnsFalse")
    fun `isGoogleDriveSignedIn_whenNotSignedIn_returnsFalse`() {
        // Given
        every { googleDriveHelper.isSignedIn() } returns false

        // When
        val result = repository.isGoogleDriveSignedIn()

        // Then
        assertFalse(result)
    }

    // ============================================================
    // listGoogleDriveBackups
    // ============================================================

    @Test
    @DisplayName("listGoogleDriveBackups_whenNotSignedIn_returnsFailure")
    fun `listGoogleDriveBackups_whenNotSignedIn_returnsFailure`() = runTest {
        // Given
        every { googleDriveHelper.isSignedIn() } returns false

        // When
        val result = repository.listGoogleDriveBackups()

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("로그인") == true ||
                   error.message?.contains("Google Drive") == true)
    }

    // ============================================================
    // deleteLocalBackup
    // ============================================================

    @Test
    @DisplayName("deleteLocalBackup_withNonExistentId_returnsFailure")
    fun `deleteLocalBackup_withNonExistentId_returnsFailure`() = runTest {
        // Given: no files in backup directory matching the id
        // (backupDirectory is real temp dir from setup, which is empty)

        // When
        val result = repository.deleteLocalBackup("non-existent-backup-id")

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error.message?.contains("백업") == true || error.message?.contains("없습니다") == true)
    }

    // ============================================================
    // listLocalBackups
    // ============================================================

    @Test
    @DisplayName("listLocalBackups_withEmptyDirectory_returnsEmptyList")
    fun `listLocalBackups_withEmptyDirectory_returnsEmptyList`() = runTest {
        // Given: backup directory exists but is empty (from setup)

        // When
        val result = repository.listLocalBackups()

        // Then
        assertTrue(result.isEmpty())
    }
}
