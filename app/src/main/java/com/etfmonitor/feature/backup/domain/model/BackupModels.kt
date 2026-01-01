package com.etfmonitor.feature.backup.domain.model

import kotlinx.serialization.Serializable

/**
 * 백업 메타데이터 - 백업 파일에 포함되는 정보
 */
@Serializable
data class BackupMetadata(
    val version: Int = CURRENT_VERSION,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val deviceName: String? = null,
    val backupType: BackupType,
    val dateRange: DateRange? = null,
    val selectedEntities: List<String>,
    val entityCounts: Map<String, Int>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
enum class BackupType {
    FULL,       // 전체 백업
    SELECTIVE   // 선택적 백업
}

@Serializable
data class DateRange(
    val startDate: String,  // "yyyy-MM-dd"
    val endDate: String     // "yyyy-MM-dd"
)

/**
 * 백업 생성 옵션
 */
data class BackupOptions(
    val backupType: BackupType = BackupType.FULL,
    val dateRange: DateRange? = null,
    val selectedEntities: Set<EntityType> = EntityType.entries.toSet(),
    val compress: Boolean = true,
    val fileName: String? = null  // null이면 자동 생성
)

/**
 * 복구 옵션
 */
data class RestoreOptions(
    val mergeMode: Boolean = true,  // true: 기존 데이터에 없는 것만 추가
    val selectedEntities: Set<EntityType>? = null,  // null이면 백업된 모든 항목 복구
    val skipSettings: Boolean = false,  // 설정은 복구하지 않음
    val confirmOverwrite: Boolean = false  // mergeMode=false일 때만 사용
)

/**
 * 로컬 백업 파일 정보
 */
data class BackupInfo(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val createdAt: Long,
    val backupType: BackupType,
    val entityCounts: Map<String, Int>,
    val schemaVersion: Int,
    val dateRange: DateRange? = null,
    val isCloudBackup: Boolean = false,
    val cloudFileId: String? = null
)

/**
 * 백업 가능한 엔티티 타입
 */
enum class EntityType(
    val displayName: String,
    val tableName: String,
    val category: EntityCategory,
    val hasDateField: Boolean
) {
    // Master/Reference Data
    ETF("ETF 목록", "etfs", EntityCategory.MASTER, false),
    STOCK("종목 마스터", "stocks", EntityCategory.MASTER, false),
    SETTING("설정", "settings", EntityCategory.MASTER, false),
    STOCK_ANALYSIS_DATA("종목 분석 데이터", "stock_analysis_data", EntityCategory.MASTER, false),

    // Time-Series Data (Date-filterable)
    HOLDING("ETF 보유 현황", "holdings", EntityCategory.TIME_SERIES, true),
    MARKET_DEPOSIT("증시 자금 동향", "market_deposits", EntityCategory.TIME_SERIES, true),
    FEAR_GREED_INDEX("Fear & Greed 지수", "fear_greed_index", EntityCategory.TIME_SERIES, true),
    MARKET_OSCILLATOR("시장 과매수/과매도", "market_oscillator", EntityCategory.TIME_SERIES, true),
    MARKET_INDEX("시장 지수", "market_index", EntityCategory.TIME_SERIES, true),
    DAILY_ETF_STATISTICS("일별 ETF 통계", "daily_etf_statistics", EntityCategory.TIME_SERIES, true),
    BLOOD_INDICATOR("Blood Indicator", "blood_indicator", EntityCategory.TIME_SERIES, true),
    PRICE_CACHE("가격 캐시", "price_cache", EntityCategory.TIME_SERIES, true),

    // Analysis Results
    AI_ANALYSIS_RESULT("AI 분석 결과", "ai_analysis_result", EntityCategory.ANALYSIS, false),
    AI_CHAT_SESSION("AI 채팅 세션", "ai_chat_session", EntityCategory.ANALYSIS, false),
    AI_CHAT_MESSAGE("AI 채팅 메시지", "ai_chat_message", EntityCategory.ANALYSIS, false),
    CORRELATION_RESULT("상관관계 분석", "correlation_analysis_result", EntityCategory.ANALYSIS, false),
    SECTOR_ANALYSIS("섹터 분석", "sector_analysis", EntityCategory.ANALYSIS, false),
    ETF_CORRELATION_CACHE("ETF 상관관계", "etf_correlation_cache", EntityCategory.ANALYSIS, false),
    LIQUIDITY_ANALYSIS("유동성 분석", "liquidity_analysis", EntityCategory.ANALYSIS, false),
    STOCK_INDICATOR_AI("종목-지표 AI 분석", "stock_indicator_ai_result", EntityCategory.ANALYSIS, false),
    ENHANCED_PREDICTION("ML 예측 결과", "enhanced_predictions", EntityCategory.ANALYSIS, false),

    // User Data
    SEARCH_HISTORY("검색 기록", "search_history", EntityCategory.USER_DATA, false);

    companion object {
        val timeSeriesEntities = entries.filter { it.hasDateField }
        val masterEntities = entries.filter { it.category == EntityCategory.MASTER }
        val analysisEntities = entries.filter { it.category == EntityCategory.ANALYSIS }
        val userDataEntities = entries.filter { it.category == EntityCategory.USER_DATA }

        fun fromTableName(tableName: String): EntityType? =
            entries.find { it.tableName == tableName }
    }
}

enum class EntityCategory(val displayName: String) {
    MASTER("기본 데이터"),
    TIME_SERIES("시계열 데이터"),
    ANALYSIS("분석 결과"),
    USER_DATA("사용자 데이터")
}

/**
 * 백업 진행 상태
 */
sealed class BackupProgress {
    data object Idle : BackupProgress()

    data class Preparing(val message: String) : BackupProgress()

    data class Exporting(
        val currentEntity: String,
        val entityProgress: Int,
        val entityTotal: Int,
        val overallProgress: Int,
        val overallTotal: Int
    ) : BackupProgress()

    data class Compressing(val progress: Int) : BackupProgress()

    data class Uploading(val progress: Int) : BackupProgress()

    data class Success(val backupInfo: BackupInfo) : BackupProgress()

    data class Error(val message: String, val exception: Throwable? = null) : BackupProgress()
}

/**
 * 복구 진행 상태
 */
sealed class RestoreProgress {
    data object Idle : RestoreProgress()

    data class Validating(val message: String) : RestoreProgress()

    data class Importing(
        val currentEntity: String,
        val imported: Int,
        val skipped: Int,
        val total: Int,
        val overallProgress: Int,
        val overallTotal: Int
    ) : RestoreProgress()

    data class Success(
        val totalImported: Int,
        val totalSkipped: Int,
        val details: Map<String, ImportResult>
    ) : RestoreProgress()

    data class Error(val message: String, val exception: Throwable? = null) : RestoreProgress()
}

/**
 * 엔티티별 복구 결과
 */
data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: Int
)

/**
 * 백업 오류 타입
 */
sealed class BackupError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileAccessError(message: String) : BackupError(message)
    class SerializationError(val entity: String, cause: Throwable) : BackupError("$entity 직렬화 오류", cause)
    class SchemaVersionMismatch(val fileVersion: Int, val appVersion: Int) :
        BackupError("스키마 버전 불일치: 파일($fileVersion) != 앱($appVersion)")
    class CorruptedBackup(message: String) : BackupError(message)
    class InsufficientStorage(val required: Long, val available: Long) :
        BackupError("저장 공간 부족: 필요($required) > 가용($available)")
    class GoogleDriveError(message: String, val code: Int? = null) : BackupError(message)
}
