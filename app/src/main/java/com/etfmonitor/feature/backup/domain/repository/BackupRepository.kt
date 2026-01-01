package com.etfmonitor.feature.backup.domain.repository

import android.net.Uri
import com.etfmonitor.feature.backup.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 백업 및 복구 Repository 인터페이스
 */
interface BackupRepository {

    // ==================== Backup Operations ====================

    /**
     * 백업 생성
     * @param options 백업 옵션
     * @return 백업 진행 상태 Flow
     */
    fun createBackup(options: BackupOptions): Flow<BackupProgress>

    /**
     * 외부 저장소로 백업 내보내기
     * @param backupId 백업 ID
     * @param destinationUri 저장할 위치 URI
     */
    suspend fun exportBackup(backupId: String, destinationUri: Uri): Result<Unit>

    // ==================== Restore Operations ====================

    /**
     * 백업 복구
     * @param backupFile 백업 파일 URI (로컬 또는 외부)
     * @param options 복구 옵션
     * @return 복구 진행 상태 Flow
     */
    fun restoreBackup(backupFile: Uri, options: RestoreOptions): Flow<RestoreProgress>

    /**
     * 로컬 백업 파일에서 복구
     * @param backupId 백업 ID
     * @param options 복구 옵션
     */
    fun restoreFromLocalBackup(backupId: String, options: RestoreOptions): Flow<RestoreProgress>

    /**
     * 백업 파일 유효성 검사
     * @param backupFile 백업 파일 URI
     * @return 백업 메타데이터 (유효한 경우)
     */
    suspend fun validateBackup(backupFile: Uri): Result<BackupMetadata>

    // ==================== Local Backup Management ====================

    /**
     * 로컬 백업 목록 조회
     */
    suspend fun listLocalBackups(): List<BackupInfo>

    /**
     * 로컬 백업 삭제
     * @param backupId 백업 ID
     */
    suspend fun deleteLocalBackup(backupId: String): Result<Unit>

    /**
     * 백업 정보 조회
     * @param backupId 백업 ID
     */
    suspend fun getBackupInfo(backupId: String): BackupInfo?

    // ==================== Google Drive Operations ====================

    /**
     * Google Drive에 백업 업로드
     * @param backupId 로컬 백업 ID
     */
    fun uploadToGoogleDrive(backupId: String): Flow<BackupProgress>

    /**
     * Google Drive 백업 목록 조회
     */
    suspend fun listGoogleDriveBackups(): Result<List<BackupInfo>>

    /**
     * Google Drive에서 백업 다운로드
     * @param driveFileId Google Drive 파일 ID
     */
    fun downloadFromGoogleDrive(driveFileId: String): Flow<BackupProgress>

    /**
     * Google Drive 백업 삭제
     * @param driveFileId Google Drive 파일 ID
     */
    suspend fun deleteFromGoogleDrive(driveFileId: String): Result<Unit>

    /**
     * Google Drive 로그인 상태 확인
     */
    fun isGoogleDriveSignedIn(): Boolean

    // ==================== Statistics ====================

    /**
     * 각 엔티티별 데이터 개수 조회
     */
    suspend fun getEntityCounts(): Map<EntityType, Int>

    /**
     * 데이터가 존재하는 날짜 범위 조회
     */
    suspend fun getDataDateRange(): DateRange?

    /**
     * 예상 백업 파일 크기 계산 (bytes)
     */
    suspend fun estimateBackupSize(options: BackupOptions): Long
}
