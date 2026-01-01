package com.etfmonitor.feature.backup.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import com.etfmonitor.BuildConfig
import com.etfmonitor.core.database.BackupDao
import com.etfmonitor.feature.backup.data.dto.*
import com.etfmonitor.feature.backup.data.remote.GoogleDriveHelper
import com.etfmonitor.feature.backup.domain.model.*
import com.etfmonitor.feature.backup.domain.repository.BackupRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val googleDriveHelper: GoogleDriveHelper
) : BackupRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val backupDirectory: File
        get() = File(context.filesDir, BACKUP_DIRECTORY).apply {
            if (!exists()) mkdirs()
        }

    companion object {
        private const val BACKUP_DIRECTORY = "backups"
        private const val BACKUP_EXTENSION = ".etfbackup"
        private const val COMPRESSED_EXTENSION = ".gz"
        private const val SCHEMA_VERSION = 19

        // 파일 크기 임계값 (1MB 이상이면 압축)
        private const val COMPRESSION_THRESHOLD = 1_000_000L
    }

    // ==================== Backup Operations ====================

    override fun createBackup(options: BackupOptions): Flow<BackupProgress> = flow {
        emit(BackupProgress.Preparing("백업 준비 중..."))

        try {
            // 엔티티별 데이터 수집 및 개수 계산
            val entityCounts = mutableMapOf<String, Int>()
            val selectedEntities = options.selectedEntities

            // 전체 엔티티 수 계산
            val totalEntities = selectedEntities.size
            var processedEntities = 0

            // 엔티티 데이터 수집
            val entityData = EntityData(
                etfs = if (EntityType.ETF in selectedEntities) {
                    emit(BackupProgress.Exporting("ETF", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllEtfs().also { entityCounts["etfs"] = it.size }.map { EtfDto.fromEntity(it) }
                } else null,

                stocks = if (EntityType.STOCK in selectedEntities) {
                    emit(BackupProgress.Exporting("종목", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllStocks().also { entityCounts["stocks"] = it.size }.map { StockDto.fromEntity(it) }
                } else null,

                settings = if (EntityType.SETTING in selectedEntities) {
                    emit(BackupProgress.Exporting("설정", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllSettings().also { entityCounts["settings"] = it.size }.map { SettingDto.fromEntity(it) }
                } else null,

                holdings = if (EntityType.HOLDING in selectedEntities) {
                    emit(BackupProgress.Exporting("보유 현황", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val holdings = if (options.dateRange != null) {
                        backupDao.getHoldingsByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllHoldings()
                    }
                    entityCounts["holdings"] = holdings.size
                    holdings.map { HoldingDto.fromEntity(it) }
                } else null,

                marketDeposits = if (EntityType.MARKET_DEPOSIT in selectedEntities) {
                    emit(BackupProgress.Exporting("시장 자금", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val deposits = if (options.dateRange != null) {
                        backupDao.getMarketDepositsByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllMarketDeposits()
                    }
                    entityCounts["marketDeposits"] = deposits.size
                    deposits.map { MarketDepositDto.fromEntity(it) }
                } else null,

                fearGreedIndices = if (EntityType.FEAR_GREED_INDEX in selectedEntities) {
                    emit(BackupProgress.Exporting("Fear & Greed", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val indices = if (options.dateRange != null) {
                        backupDao.getFearGreedByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllFearGreedIndices()
                    }
                    entityCounts["fearGreedIndices"] = indices.size
                    indices.map { FearGreedIndexDto.fromEntity(it) }
                } else null,

                marketOscillators = if (EntityType.MARKET_OSCILLATOR in selectedEntities) {
                    emit(BackupProgress.Exporting("과매수/과매도", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val oscillators = if (options.dateRange != null) {
                        backupDao.getMarketOscillatorsByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllMarketOscillators()
                    }
                    entityCounts["marketOscillators"] = oscillators.size
                    oscillators.map { MarketOscillatorDataDto.fromEntity(it) }
                } else null,

                marketIndices = if (EntityType.MARKET_INDEX in selectedEntities) {
                    emit(BackupProgress.Exporting("시장 지수", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val indices = if (options.dateRange != null) {
                        backupDao.getMarketIndicesByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllMarketIndices()
                    }
                    entityCounts["marketIndices"] = indices.size
                    indices.map { MarketIndexDto.fromEntity(it) }
                } else null,

                dailyEtfStatistics = if (EntityType.DAILY_ETF_STATISTICS in selectedEntities) {
                    emit(BackupProgress.Exporting("일별 통계", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val stats = if (options.dateRange != null) {
                        backupDao.getDailyEtfStatisticsByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllDailyEtfStatistics()
                    }
                    entityCounts["dailyEtfStatistics"] = stats.size
                    stats.map { DailyEtfStatisticsDto.fromEntity(it) }
                } else null,

                bloodIndicators = if (EntityType.BLOOD_INDICATOR in selectedEntities) {
                    emit(BackupProgress.Exporting("Blood Indicator", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val indicators = if (options.dateRange != null) {
                        backupDao.getBloodIndicatorsByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllBloodIndicators()
                    }
                    entityCounts["bloodIndicators"] = indicators.size
                    indicators.map { BloodIndicatorDto.fromEntity(it) }
                } else null,

                priceCaches = if (EntityType.PRICE_CACHE in selectedEntities) {
                    emit(BackupProgress.Exporting("가격 캐시", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    val caches = if (options.dateRange != null) {
                        backupDao.getPriceCachesByDateRange(options.dateRange.startDate, options.dateRange.endDate)
                    } else {
                        backupDao.getAllPriceCaches()
                    }
                    entityCounts["priceCaches"] = caches.size
                    caches.map { PriceCacheDto.fromEntity(it) }
                } else null,

                stockAnalysisData = if (EntityType.STOCK_ANALYSIS_DATA in selectedEntities) {
                    emit(BackupProgress.Exporting("종목 분석", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllStockAnalysisData().also { entityCounts["stockAnalysisData"] = it.size }.map { StockAnalysisDataDto.fromEntity(it) }
                } else null,

                aiAnalysisResults = if (EntityType.AI_ANALYSIS_RESULT in selectedEntities) {
                    emit(BackupProgress.Exporting("AI 분석", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllAIAnalysisResults().also { entityCounts["aiAnalysisResults"] = it.size }.map { AIAnalysisResultDto.fromEntity(it) }
                } else null,

                aiChatSessions = if (EntityType.AI_CHAT_SESSION in selectedEntities) {
                    emit(BackupProgress.Exporting("AI 세션", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllAIChatSessions().also { entityCounts["aiChatSessions"] = it.size }.map { AIChatSessionDto.fromEntity(it) }
                } else null,

                aiChatMessages = if (EntityType.AI_CHAT_MESSAGE in selectedEntities) {
                    emit(BackupProgress.Exporting("AI 메시지", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllAIChatMessages().also { entityCounts["aiChatMessages"] = it.size }.map { AIChatMessageDto.fromEntity(it) }
                } else null,

                correlationResults = if (EntityType.CORRELATION_RESULT in selectedEntities) {
                    emit(BackupProgress.Exporting("상관관계", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllCorrelationResults().also { entityCounts["correlationResults"] = it.size }.map { CorrelationAnalysisResultDto.fromEntity(it) }
                } else null,

                sectorAnalyses = if (EntityType.SECTOR_ANALYSIS in selectedEntities) {
                    emit(BackupProgress.Exporting("섹터 분석", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllSectorAnalyses().also { entityCounts["sectorAnalyses"] = it.size }.map { SectorAnalysisDto.fromEntity(it) }
                } else null,

                etfCorrelationCaches = if (EntityType.ETF_CORRELATION_CACHE in selectedEntities) {
                    emit(BackupProgress.Exporting("ETF 상관", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllEtfCorrelationCaches().also { entityCounts["etfCorrelationCaches"] = it.size }.map { EtfCorrelationCacheDto.fromEntity(it) }
                } else null,

                liquidityAnalyses = if (EntityType.LIQUIDITY_ANALYSIS in selectedEntities) {
                    emit(BackupProgress.Exporting("유동성", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllLiquidityAnalyses().also { entityCounts["liquidityAnalyses"] = it.size }.map { LiquidityAnalysisDto.fromEntity(it) }
                } else null,

                stockIndicatorAIResults = if (EntityType.STOCK_INDICATOR_AI in selectedEntities) {
                    emit(BackupProgress.Exporting("종목 AI", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllStockIndicatorAIResults().also { entityCounts["stockIndicatorAIResults"] = it.size }.map { StockIndicatorAIResultDto.fromEntity(it) }
                } else null,

                enhancedPredictions = if (EntityType.ENHANCED_PREDICTION in selectedEntities) {
                    emit(BackupProgress.Exporting("ML 예측", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllEnhancedPredictions().also { entityCounts["enhancedPredictions"] = it.size }.map { EnhancedPredictionDto.fromEntity(it) }
                } else null,

                searchHistories = if (EntityType.SEARCH_HISTORY in selectedEntities) {
                    emit(BackupProgress.Exporting("검색 기록", ++processedEntities, totalEntities, processedEntities, totalEntities))
                    backupDao.getAllSearchHistories().also { entityCounts["searchHistories"] = it.size }.map { SearchHistoryDto.fromEntity(it) }
                } else null
            )

            // 메타데이터 생성
            val metadata = BackupMetadata(
                appVersion = BuildConfig.VERSION_NAME,
                schemaVersion = SCHEMA_VERSION,
                createdAt = System.currentTimeMillis(),
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                backupType = options.backupType,
                dateRange = options.dateRange,
                selectedEntities = selectedEntities.map { it.name },
                entityCounts = entityCounts
            )

            val backupData = BackupData(metadata = metadata, data = entityData)

            // JSON 직렬화
            emit(BackupProgress.Compressing(0))
            val jsonString = json.encodeToString(backupData)

            // 파일 저장
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val baseFileName = options.fileName ?: "etfmonitor_backup_$timestamp"

            val shouldCompress = options.compress && jsonString.length > COMPRESSION_THRESHOLD
            val fileName = if (shouldCompress) {
                "$baseFileName$BACKUP_EXTENSION$COMPRESSED_EXTENSION"
            } else {
                "$baseFileName$BACKUP_EXTENSION"
            }

            val backupFile = File(backupDirectory, fileName)

            if (shouldCompress) {
                emit(BackupProgress.Compressing(50))
                GZIPOutputStream(FileOutputStream(backupFile)).use { gzip ->
                    gzip.write(jsonString.toByteArray(Charsets.UTF_8))
                }
            } else {
                backupFile.writeText(jsonString, Charsets.UTF_8)
            }

            emit(BackupProgress.Compressing(100))

            // 성공
            val backupInfo = BackupInfo(
                id = backupFile.nameWithoutExtension.replace("$COMPRESSED_EXTENSION", ""),
                fileName = fileName,
                filePath = backupFile.absolutePath,
                fileSize = backupFile.length(),
                createdAt = metadata.createdAt,
                backupType = metadata.backupType,
                entityCounts = entityCounts,
                schemaVersion = metadata.schemaVersion,
                dateRange = metadata.dateRange
            )

            emit(BackupProgress.Success(backupInfo))

        } catch (e: Exception) {
            emit(BackupProgress.Error("백업 실패: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun exportBackup(backupId: String, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val backupInfo = getBackupInfo(backupId)
                    ?: return@withContext Result.failure(BackupError.FileAccessError("백업을 찾을 수 없습니다"))

                val sourceFile = File(backupInfo.filePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(BackupError.FileAccessError("백업 파일이 없습니다"))
                }

                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(BackupError.FileAccessError("내보내기 실패: ${e.message}"))
            }
        }

    // ==================== Restore Operations ====================

    override fun restoreBackup(backupFile: Uri, options: RestoreOptions): Flow<RestoreProgress> = flow {
        emit(RestoreProgress.Validating("백업 파일 검증 중..."))

        try {
            // 백업 파일 읽기
            val inputStream = context.contentResolver.openInputStream(backupFile)
                ?: throw BackupError.FileAccessError("파일을 열 수 없습니다")

            val jsonString = inputStream.use { stream ->
                val isCompressed = backupFile.path?.endsWith(COMPRESSED_EXTENSION) == true ||
                    backupFile.lastPathSegment?.endsWith(COMPRESSED_EXTENSION) == true

                if (isCompressed) {
                    GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).readText()
                } else {
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }

            val backupData = json.decodeFromString<BackupData>(jsonString)
            val metadata = backupData.metadata

            // 스키마 버전 확인
            if (metadata.schemaVersion > SCHEMA_VERSION) {
                throw BackupError.SchemaVersionMismatch(metadata.schemaVersion, SCHEMA_VERSION)
            }

            emit(RestoreProgress.Validating("데이터 복구 시작..."))

            // 복구할 엔티티 결정
            val entitiesToRestore = options.selectedEntities
                ?: metadata.selectedEntities.mapNotNull { EntityType.entries.find { e -> e.name == it } }.toSet()

            val importResults = mutableMapOf<String, ImportResult>()
            val totalEntities = entitiesToRestore.size
            var processedEntities = 0
            var totalImported = 0
            var totalSkipped = 0

            // 각 엔티티 복구
            if (EntityType.ETF in entitiesToRestore) {
                backupData.data.etfs?.let { dtos ->
                    emit(RestoreProgress.Importing("ETF", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllEtfTickers().toSet() else emptySet(),
                        getKey = { it.ticker },
                        insert = { backupDao.insertEtfsIgnore(it) }
                    )
                    importResults["ETF"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.STOCK in entitiesToRestore) {
                backupData.data.stocks?.let { dtos ->
                    emit(RestoreProgress.Importing("종목", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllStockTickers().toSet() else emptySet(),
                        getKey = { it.ticker },
                        insert = { backupDao.insertStocksIgnore(it) }
                    )
                    importResults["종목"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.SETTING in entitiesToRestore && !options.skipSettings) {
                backupData.data.settings?.let { dtos ->
                    emit(RestoreProgress.Importing("설정", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllSettingKeys().toSet() else emptySet(),
                        getKey = { it.key },
                        insert = { backupDao.insertSettingsIgnore(it) }
                    )
                    importResults["설정"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.HOLDING in entitiesToRestore) {
                backupData.data.holdings?.let { dtos ->
                    emit(RestoreProgress.Importing("보유 현황", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val existingKeys = if (options.mergeMode) backupDao.getAllHoldingKeys().toSet() else emptySet()
                    val entities = dtos.map { it.toEntity() }
                    val result = restoreEntitiesWithMerge(
                        entities = entities,
                        existingKeys = existingKeys,
                        getKey = { "${it.etfTicker}-${it.stockTicker}-${it.date}" },
                        insert = { backupDao.insertHoldingsIgnore(it) }
                    )
                    importResults["보유 현황"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.MARKET_DEPOSIT in entitiesToRestore) {
                backupData.data.marketDeposits?.let { dtos ->
                    emit(RestoreProgress.Importing("시장 자금", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllMarketDepositDates().toSet() else emptySet(),
                        getKey = { it.date },
                        insert = { backupDao.insertMarketDepositsIgnore(it) }
                    )
                    importResults["시장 자금"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.FEAR_GREED_INDEX in entitiesToRestore) {
                backupData.data.fearGreedIndices?.let { dtos ->
                    emit(RestoreProgress.Importing("Fear & Greed", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllFearGreedIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertFearGreedIgnore(it) }
                    )
                    importResults["Fear & Greed"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.MARKET_OSCILLATOR in entitiesToRestore) {
                backupData.data.marketOscillators?.let { dtos ->
                    emit(RestoreProgress.Importing("과매수/과매도", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllMarketOscillatorIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertMarketOscillatorsIgnore(it) }
                    )
                    importResults["과매수/과매도"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.MARKET_INDEX in entitiesToRestore) {
                backupData.data.marketIndices?.let { dtos ->
                    emit(RestoreProgress.Importing("시장 지수", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllMarketIndexIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertMarketIndicesIgnore(it) }
                    )
                    importResults["시장 지수"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.DAILY_ETF_STATISTICS in entitiesToRestore) {
                backupData.data.dailyEtfStatistics?.let { dtos ->
                    emit(RestoreProgress.Importing("일별 통계", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllDailyEtfStatisticsDates().toSet() else emptySet(),
                        getKey = { it.date },
                        insert = { backupDao.insertDailyEtfStatisticsIgnore(it) }
                    )
                    importResults["일별 통계"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.BLOOD_INDICATOR in entitiesToRestore) {
                backupData.data.bloodIndicators?.let { dtos ->
                    emit(RestoreProgress.Importing("Blood Indicator", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllBloodIndicatorIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertBloodIndicatorsIgnore(it) }
                    )
                    importResults["Blood Indicator"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.PRICE_CACHE in entitiesToRestore) {
                backupData.data.priceCaches?.let { dtos ->
                    emit(RestoreProgress.Importing("가격 캐시", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllPriceCacheKeys().toSet() else emptySet(),
                        getKey = { "${it.ticker}-${it.date}" },
                        insert = { backupDao.insertPriceCachesIgnore(it) }
                    )
                    importResults["가격 캐시"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.STOCK_ANALYSIS_DATA in entitiesToRestore) {
                backupData.data.stockAnalysisData?.let { dtos ->
                    emit(RestoreProgress.Importing("종목 분석", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllStockAnalysisDataTickers().toSet() else emptySet(),
                        getKey = { it.ticker },
                        insert = { backupDao.insertStockAnalysisDataIgnore(it) }
                    )
                    importResults["종목 분석"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.AI_ANALYSIS_RESULT in entitiesToRestore) {
                backupData.data.aiAnalysisResults?.let { dtos ->
                    emit(RestoreProgress.Importing("AI 분석", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllAIAnalysisResultIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertAIAnalysisResultsIgnore(it) }
                    )
                    importResults["AI 분석"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.AI_CHAT_SESSION in entitiesToRestore) {
                backupData.data.aiChatSessions?.let { dtos ->
                    emit(RestoreProgress.Importing("AI 세션", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllAIChatSessionIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertAIChatSessionsIgnore(it) }
                    )
                    importResults["AI 세션"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.AI_CHAT_MESSAGE in entitiesToRestore) {
                backupData.data.aiChatMessages?.let { dtos ->
                    emit(RestoreProgress.Importing("AI 메시지", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllAIChatMessageIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertAIChatMessagesIgnore(it) }
                    )
                    importResults["AI 메시지"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.CORRELATION_RESULT in entitiesToRestore) {
                backupData.data.correlationResults?.let { dtos ->
                    emit(RestoreProgress.Importing("상관관계", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllCorrelationResultIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertCorrelationResultsIgnore(it) }
                    )
                    importResults["상관관계"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.SECTOR_ANALYSIS in entitiesToRestore) {
                backupData.data.sectorAnalyses?.let { dtos ->
                    emit(RestoreProgress.Importing("섹터 분석", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllSectorAnalysisIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertSectorAnalysesIgnore(it) }
                    )
                    importResults["섹터 분석"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.ETF_CORRELATION_CACHE in entitiesToRestore) {
                backupData.data.etfCorrelationCaches?.let { dtos ->
                    emit(RestoreProgress.Importing("ETF 상관", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllEtfCorrelationCacheIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertEtfCorrelationCachesIgnore(it) }
                    )
                    importResults["ETF 상관"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.LIQUIDITY_ANALYSIS in entitiesToRestore) {
                backupData.data.liquidityAnalyses?.let { dtos ->
                    emit(RestoreProgress.Importing("유동성", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllLiquidityAnalysisDates().toSet() else emptySet(),
                        getKey = { it.date },
                        insert = { backupDao.insertLiquidityAnalysesIgnore(it) }
                    )
                    importResults["유동성"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.STOCK_INDICATOR_AI in entitiesToRestore) {
                backupData.data.stockIndicatorAIResults?.let { dtos ->
                    emit(RestoreProgress.Importing("종목 AI", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllStockIndicatorAIResultIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertStockIndicatorAIResultsIgnore(it) }
                    )
                    importResults["종목 AI"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.ENHANCED_PREDICTION in entitiesToRestore) {
                backupData.data.enhancedPredictions?.let { dtos ->
                    emit(RestoreProgress.Importing("ML 예측", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllEnhancedPredictionIds().toSet() else emptySet(),
                        getKey = { it.id },
                        insert = { backupDao.insertEnhancedPredictionsIgnore(it) }
                    )
                    importResults["ML 예측"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            if (EntityType.SEARCH_HISTORY in entitiesToRestore) {
                backupData.data.searchHistories?.let { dtos ->
                    emit(RestoreProgress.Importing("검색 기록", 0, 0, dtos.size, ++processedEntities, totalEntities))
                    val result = restoreEntitiesWithMerge(
                        entities = dtos.map { it.toEntity() },
                        existingKeys = if (options.mergeMode) backupDao.getAllSearchHistoryIds().map { it.toString() }.toSet() else emptySet(),
                        getKey = { it.id.toString() },
                        insert = { backupDao.insertSearchHistoriesIgnore(it) }
                    )
                    importResults["검색 기록"] = result
                    totalImported += result.imported
                    totalSkipped += result.skipped
                }
            }

            emit(RestoreProgress.Success(
                totalImported = totalImported,
                totalSkipped = totalSkipped,
                details = importResults
            ))

        } catch (e: BackupError) {
            emit(RestoreProgress.Error(e.message ?: "복구 실패", e))
        } catch (e: Exception) {
            emit(RestoreProgress.Error("복구 실패: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    override fun restoreFromLocalBackup(backupId: String, options: RestoreOptions): Flow<RestoreProgress> = flow {
        val backupInfo = getBackupInfo(backupId)
            ?: throw BackupError.FileAccessError("백업을 찾을 수 없습니다")

        val file = File(backupInfo.filePath)
        if (!file.exists()) {
            throw BackupError.FileAccessError("백업 파일이 없습니다")
        }

        val uri = Uri.fromFile(file)
        restoreBackup(uri, options).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun <T> restoreEntitiesWithMerge(
        entities: List<T>,
        existingKeys: Set<String>,
        getKey: (T) -> String,
        insert: suspend (List<T>) -> List<Long>
    ): ImportResult {
        if (entities.isEmpty()) return ImportResult(0, 0, 0)

        val toInsert = if (existingKeys.isEmpty()) {
            entities
        } else {
            entities.filter { getKey(it) !in existingKeys }
        }

        val skipped = entities.size - toInsert.size

        return if (toInsert.isNotEmpty()) {
            try {
                // 배치 처리 (1000개씩)
                var imported = 0
                toInsert.chunked(1000).forEach { batch ->
                    val results = insert(batch)
                    imported += results.count { it != -1L }
                }
                ImportResult(imported, skipped, 0)
            } catch (e: Exception) {
                ImportResult(0, skipped, toInsert.size)
            }
        } else {
            ImportResult(0, skipped, 0)
        }
    }

    override suspend fun validateBackup(backupFile: Uri): Result<BackupMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(backupFile)
                    ?: return@withContext Result.failure(BackupError.FileAccessError("파일을 열 수 없습니다"))

                val jsonString = inputStream.use { stream ->
                    val isCompressed = backupFile.path?.endsWith(COMPRESSED_EXTENSION) == true ||
                        backupFile.lastPathSegment?.endsWith(COMPRESSED_EXTENSION) == true

                    if (isCompressed) {
                        GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).readText()
                    } else {
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                val backupData = json.decodeFromString<BackupData>(jsonString)
                Result.success(backupData.metadata)
            } catch (e: Exception) {
                Result.failure(BackupError.CorruptedBackup("백업 파일이 손상되었습니다: ${e.message}"))
            }
        }

    // ==================== Local Backup Management ====================

    override suspend fun listLocalBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        backupDirectory.listFiles()
            ?.filter { it.name.endsWith(BACKUP_EXTENSION) || it.name.endsWith(COMPRESSED_EXTENSION) }
            ?.mapNotNull { file ->
                try {
                    val jsonString = if (file.name.endsWith(COMPRESSED_EXTENSION)) {
                        GZIPInputStream(FileInputStream(file)).bufferedReader(Charsets.UTF_8).readText()
                    } else {
                        file.readText(Charsets.UTF_8)
                    }

                    val backupData = json.decodeFromString<BackupData>(jsonString)
                    val metadata = backupData.metadata

                    BackupInfo(
                        id = file.nameWithoutExtension.replace(COMPRESSED_EXTENSION, "").replace(BACKUP_EXTENSION, ""),
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        createdAt = metadata.createdAt,
                        backupType = metadata.backupType,
                        entityCounts = metadata.entityCounts,
                        schemaVersion = metadata.schemaVersion,
                        dateRange = metadata.dateRange
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    override suspend fun deleteLocalBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val files = backupDirectory.listFiles()?.filter {
                it.name.startsWith(backupId) &&
                    (it.name.endsWith(BACKUP_EXTENSION) || it.name.endsWith(COMPRESSED_EXTENSION))
            } ?: emptyList()

            if (files.isEmpty()) {
                return@withContext Result.failure(BackupError.FileAccessError("백업을 찾을 수 없습니다"))
            }

            files.forEach { it.delete() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(BackupError.FileAccessError("삭제 실패: ${e.message}"))
        }
    }

    override suspend fun getBackupInfo(backupId: String): BackupInfo? = withContext(Dispatchers.IO) {
        listLocalBackups().find { it.id == backupId || it.fileName.startsWith(backupId) }
    }

    // ==================== Google Drive Operations ====================

    override fun uploadToGoogleDrive(backupId: String): Flow<BackupProgress> = flow {
        emit(BackupProgress.Uploading(0))

        try {
            if (!googleDriveHelper.isSignedIn()) {
                emit(BackupProgress.Error("Google Drive에 로그인이 필요합니다"))
                return@flow
            }

            val backupInfo = getBackupInfo(backupId)
                ?: throw BackupError.FileAccessError("백업을 찾을 수 없습니다")

            val localFile = File(backupInfo.filePath)
            if (!localFile.exists()) {
                throw BackupError.FileAccessError("백업 파일이 없습니다")
            }

            emit(BackupProgress.Uploading(50))

            val result = googleDriveHelper.uploadBackup(localFile, localFile.name)
            result.fold(
                onSuccess = { driveInfo ->
                    emit(BackupProgress.Uploading(100))
                    // Return success with updated backup info
                    val updatedBackupInfo = backupInfo.copy(
                        isCloudBackup = true,
                        cloudFileId = driveInfo.id
                    )
                    emit(BackupProgress.Success(updatedBackupInfo))
                },
                onFailure = { e ->
                    emit(BackupProgress.Error("업로드 실패: ${e.message}", e))
                }
            )
        } catch (e: Exception) {
            emit(BackupProgress.Error("업로드 실패: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listGoogleDriveBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        if (!googleDriveHelper.isSignedIn()) {
            return@withContext Result.failure(BackupError.GoogleDriveError("Google Drive에 로그인이 필요합니다"))
        }

        googleDriveHelper.listBackups().fold(
            onSuccess = { driveBackups ->
                val backupInfoList = driveBackups.map { driveBackup ->
                    BackupInfo(
                        id = driveBackup.id,
                        fileName = driveBackup.name,
                        filePath = "",  // Cloud backup has no local path
                        fileSize = driveBackup.size,
                        createdAt = driveBackup.createdTime,
                        backupType = BackupType.FULL,  // Default, actual type would need to be read from file
                        entityCounts = emptyMap(),
                        schemaVersion = SCHEMA_VERSION,
                        dateRange = null,
                        isCloudBackup = true,
                        cloudFileId = driveBackup.id
                    )
                }
                Result.success(backupInfoList)
            },
            onFailure = { e ->
                Result.failure(BackupError.GoogleDriveError("목록 조회 실패: ${e.message}"))
            }
        )
    }

    override fun downloadFromGoogleDrive(driveFileId: String): Flow<BackupProgress> = flow {
        emit(BackupProgress.Preparing("다운로드 준비 중..."))

        try {
            if (!googleDriveHelper.isSignedIn()) {
                emit(BackupProgress.Error("Google Drive에 로그인이 필요합니다"))
                return@flow
            }

            // Create temporary file for download
            val tempFile = File(backupDirectory, "temp_download_${System.currentTimeMillis()}")

            val result = googleDriveHelper.downloadBackup(driveFileId, tempFile)
            result.fold(
                onSuccess = { downloadedFile ->
                    emit(BackupProgress.Uploading(100))

                    // Read metadata from downloaded file
                    try {
                        val jsonString = if (downloadedFile.name.endsWith(COMPRESSED_EXTENSION)) {
                            GZIPInputStream(FileInputStream(downloadedFile)).bufferedReader(Charsets.UTF_8).readText()
                        } else {
                            downloadedFile.readText(Charsets.UTF_8)
                        }

                        val backupData = json.decodeFromString<BackupData>(jsonString)
                        val metadata = backupData.metadata

                        // Rename to proper backup filename
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val timestamp = dateFormat.format(Date(metadata.createdAt))
                        val extension = if (downloadedFile.name.endsWith(COMPRESSED_EXTENSION))
                            "$BACKUP_EXTENSION$COMPRESSED_EXTENSION"
                        else
                            BACKUP_EXTENSION
                        val newFileName = "etfmonitor_backup_$timestamp$extension"
                        val newFile = File(backupDirectory, newFileName)
                        downloadedFile.renameTo(newFile)

                        val backupInfo = BackupInfo(
                            id = newFile.nameWithoutExtension.replace(COMPRESSED_EXTENSION, ""),
                            fileName = newFileName,
                            filePath = newFile.absolutePath,
                            fileSize = newFile.length(),
                            createdAt = metadata.createdAt,
                            backupType = metadata.backupType,
                            entityCounts = metadata.entityCounts,
                            schemaVersion = metadata.schemaVersion,
                            dateRange = metadata.dateRange,
                            isCloudBackup = false,
                            cloudFileId = driveFileId
                        )

                        emit(BackupProgress.Success(backupInfo))
                    } catch (e: Exception) {
                        tempFile.delete()
                        emit(BackupProgress.Error("백업 파일 처리 실패: ${e.message}", e))
                    }
                },
                onFailure = { e ->
                    tempFile.delete()
                    emit(BackupProgress.Error("다운로드 실패: ${e.message}", e))
                }
            )
        } catch (e: Exception) {
            emit(BackupProgress.Error("다운로드 실패: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteFromGoogleDrive(driveFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!googleDriveHelper.isSignedIn()) {
            return@withContext Result.failure(BackupError.GoogleDriveError("Google Drive에 로그인이 필요합니다"))
        }

        googleDriveHelper.deleteBackup(driveFileId).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { e -> Result.failure(BackupError.GoogleDriveError("삭제 실패: ${e.message}")) }
        )
    }

    override fun isGoogleDriveSignedIn(): Boolean = googleDriveHelper.isSignedIn()

    /**
     * Initialize Google Drive service after sign-in
     */
    suspend fun initializeGoogleDrive(account: GoogleSignInAccount): Result<Unit> {
        return googleDriveHelper.initializeDriveService(account)
    }

    /**
     * Get Google Drive helper for sign-in operations
     */
    fun getGoogleDriveHelper(): GoogleDriveHelper = googleDriveHelper

    // ==================== Statistics ====================

    override suspend fun getEntityCounts(): Map<EntityType, Int> = withContext(Dispatchers.IO) {
        mapOf(
            EntityType.ETF to backupDao.getEtfCount(),
            EntityType.STOCK to backupDao.getStockCount(),
            EntityType.SETTING to backupDao.getSettingCount(),
            EntityType.HOLDING to backupDao.getHoldingCount(),
            EntityType.MARKET_DEPOSIT to backupDao.getMarketDepositCount(),
            EntityType.FEAR_GREED_INDEX to backupDao.getFearGreedCount(),
            EntityType.MARKET_OSCILLATOR to backupDao.getMarketOscillatorCount(),
            EntityType.MARKET_INDEX to backupDao.getMarketIndexCount(),
            EntityType.DAILY_ETF_STATISTICS to backupDao.getDailyEtfStatisticsCount(),
            EntityType.BLOOD_INDICATOR to backupDao.getBloodIndicatorCount(),
            EntityType.PRICE_CACHE to backupDao.getPriceCacheCount(),
            EntityType.STOCK_ANALYSIS_DATA to backupDao.getStockAnalysisDataCount(),
            EntityType.AI_ANALYSIS_RESULT to backupDao.getAIAnalysisResultCount(),
            EntityType.AI_CHAT_SESSION to backupDao.getAIChatSessionCount(),
            EntityType.AI_CHAT_MESSAGE to backupDao.getAIChatMessageCount(),
            EntityType.CORRELATION_RESULT to backupDao.getCorrelationResultCount(),
            EntityType.SECTOR_ANALYSIS to backupDao.getSectorAnalysisCount(),
            EntityType.ETF_CORRELATION_CACHE to backupDao.getEtfCorrelationCacheCount(),
            EntityType.LIQUIDITY_ANALYSIS to backupDao.getLiquidityAnalysisCount(),
            EntityType.STOCK_INDICATOR_AI to backupDao.getStockIndicatorAIResultCount(),
            EntityType.ENHANCED_PREDICTION to backupDao.getEnhancedPredictionCount(),
            EntityType.SEARCH_HISTORY to backupDao.getSearchHistoryCount()
        )
    }

    override suspend fun getDataDateRange(): DateRange? = withContext(Dispatchers.IO) {
        val globalRange = backupDao.getGlobalDateRange()
        if (globalRange?.minDate != null && globalRange.maxDate != null) {
            DateRange(globalRange.minDate, globalRange.maxDate)
        } else {
            null
        }
    }

    override suspend fun estimateBackupSize(options: BackupOptions): Long = withContext(Dispatchers.IO) {
        val counts = getEntityCounts()
        val selectedEntities = options.selectedEntities

        // 대략적인 크기 추정 (각 엔티티 레코드당 평균 바이트 수)
        val sizeEstimates = mapOf(
            EntityType.ETF to 50L,
            EntityType.STOCK to 100L,
            EntityType.SETTING to 100L,
            EntityType.HOLDING to 120L,
            EntityType.MARKET_DEPOSIT to 100L,
            EntityType.FEAR_GREED_INDEX to 200L,
            EntityType.MARKET_OSCILLATOR to 100L,
            EntityType.MARKET_INDEX to 150L,
            EntityType.DAILY_ETF_STATISTICS to 300L,
            EntityType.BLOOD_INDICATOR to 150L,
            EntityType.PRICE_CACHE to 80L,
            EntityType.STOCK_ANALYSIS_DATA to 500L,
            EntityType.AI_ANALYSIS_RESULT to 2000L,
            EntityType.AI_CHAT_SESSION to 500L,
            EntityType.AI_CHAT_MESSAGE to 1000L,
            EntityType.CORRELATION_RESULT to 800L,
            EntityType.SECTOR_ANALYSIS to 400L,
            EntityType.ETF_CORRELATION_CACHE to 500L,
            EntityType.LIQUIDITY_ANALYSIS to 300L,
            EntityType.STOCK_INDICATOR_AI to 1500L,
            EntityType.ENHANCED_PREDICTION to 1000L,
            EntityType.SEARCH_HISTORY to 100L
        )

        var totalSize = 0L
        selectedEntities.forEach { entityType ->
            val count = counts[entityType] ?: 0
            val perRecordSize = sizeEstimates[entityType] ?: 100L
            totalSize += count * perRecordSize
        }

        // 압축 시 약 6배 감소 예상
        if (options.compress && totalSize > COMPRESSION_THRESHOLD) {
            totalSize / 6
        } else {
            totalSize
        }
    }
}
