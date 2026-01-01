package com.etfmonitor.feature.backup.presentation.state

import com.etfmonitor.feature.backup.domain.model.*

/**
 * 백업 화면 UI 상태
 */
sealed class BackupState {
    object Loading : BackupState()
    data class Idle(
        val localBackups: List<BackupInfo> = emptyList(),
        val entityCounts: Map<EntityType, Int> = emptyMap(),
        val dateRange: DateRange? = null,
        val estimatedSize: Long = 0L
    ) : BackupState()
    data class Error(val message: String) : BackupState()
}

/**
 * 백업 생성 다이얼로그 상태
 */
sealed class CreateBackupState {
    object Hidden : CreateBackupState()
    data class Visible(
        val selectedEntities: Set<EntityType> = EntityType.entries.toSet(),
        val useCompression: Boolean = true,
        val startDate: String? = null,
        val endDate: String? = null,
        val dateRange: DateRange? = null
    ) : CreateBackupState()
    data class InProgress(
        val message: String,
        val progress: Int,
        val processedEntities: Int,
        val totalEntities: Int
    ) : CreateBackupState()
    data class Success(val backupInfo: BackupInfo) : CreateBackupState()
    data class Error(val message: String) : CreateBackupState()
}

/**
 * 복구 다이얼로그 상태
 */
sealed class RestoreState {
    object Hidden : RestoreState()
    data class SelectFile(
        val selectedFileUri: android.net.Uri? = null,
        val metadata: BackupMetadata? = null,
        val isValidating: Boolean = false,
        val validationError: String? = null
    ) : RestoreState()
    data class Configure(
        val metadata: BackupMetadata,
        val selectedEntities: Set<EntityType> = EntityType.entries.toSet(),
        val useUri: android.net.Uri? = null,
        val backupId: String? = null
    ) : RestoreState()
    data class InProgress(
        val message: String,
        val progress: Int,
        val processedEntities: Int,
        val totalEntities: Int
    ) : RestoreState()
    data class Success(val result: ImportResult) : RestoreState()
    data class Error(val message: String) : RestoreState()
}

/**
 * 백업 상세 정보 다이얼로그 상태
 */
sealed class BackupDetailState {
    object Hidden : BackupDetailState()
    data class Visible(val backupInfo: BackupInfo) : BackupDetailState()
}

/**
 * 삭제 확인 다이얼로그 상태
 */
sealed class DeleteConfirmState {
    object Hidden : DeleteConfirmState()
    data class Visible(val backupInfo: BackupInfo) : DeleteConfirmState()
    object Deleting : DeleteConfirmState()
}

/**
 * Google Drive 상태
 */
sealed class GoogleDriveState {
    object NotSignedIn : GoogleDriveState()
    object SignedIn : GoogleDriveState()
    object Loading : GoogleDriveState()
    data class Backups(val backups: List<BackupInfo>) : GoogleDriveState()
    data class Uploading(val progress: Int, val message: String) : GoogleDriveState()
    data class Downloading(val progress: Int, val message: String) : GoogleDriveState()
    data class Error(val message: String) : GoogleDriveState()
}

/**
 * 스낵바 메시지
 */
data class SnackbarMessage(
    val message: String,
    val isError: Boolean = false
)
