package com.etfmonitor.feature.backup.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etfmonitor.feature.backup.domain.model.*
import com.etfmonitor.feature.backup.domain.repository.BackupRepository
import com.etfmonitor.feature.backup.presentation.state.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 백업/복구 기능 ViewModel
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    // ==================== Main State ====================
    private val _state = MutableStateFlow<BackupState>(BackupState.Loading)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    // ==================== Dialog States ====================
    private val _createBackupState = MutableStateFlow<CreateBackupState>(CreateBackupState.Hidden)
    val createBackupState: StateFlow<CreateBackupState> = _createBackupState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Hidden)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    private val _backupDetailState = MutableStateFlow<BackupDetailState>(BackupDetailState.Hidden)
    val backupDetailState: StateFlow<BackupDetailState> = _backupDetailState.asStateFlow()

    private val _deleteConfirmState = MutableStateFlow<DeleteConfirmState>(DeleteConfirmState.Hidden)
    val deleteConfirmState: StateFlow<DeleteConfirmState> = _deleteConfirmState.asStateFlow()

    private val _googleDriveState = MutableStateFlow<GoogleDriveState>(GoogleDriveState.NotSignedIn)
    val googleDriveState: StateFlow<GoogleDriveState> = _googleDriveState.asStateFlow()

    // ==================== Snackbar ====================
    private val _snackbarMessage = MutableSharedFlow<SnackbarMessage>()
    val snackbarMessage: SharedFlow<SnackbarMessage> = _snackbarMessage.asSharedFlow()

    init {
        loadData()
        checkGoogleDriveStatus()
    }

    // ==================== Data Loading ====================

    fun loadData() {
        viewModelScope.launch {
            _state.value = BackupState.Loading
            try {
                val backups = backupRepository.listLocalBackups()
                val entityCounts = backupRepository.getEntityCounts()
                val dateRange = backupRepository.getDataDateRange()
                val estimatedSize = backupRepository.estimateBackupSize(
                    BackupOptions(
                        selectedEntities = EntityType.entries.toSet(),
                        compress = true
                    )
                )

                _state.value = BackupState.Idle(
                    localBackups = backups,
                    entityCounts = entityCounts,
                    dateRange = dateRange,
                    estimatedSize = estimatedSize
                )
            } catch (e: Exception) {
                _state.value = BackupState.Error(e.message ?: "데이터 로드 실패")
            }
        }
    }

    private fun checkGoogleDriveStatus() {
        _googleDriveState.value = if (backupRepository.isGoogleDriveSignedIn()) {
            GoogleDriveState.SignedIn
        } else {
            GoogleDriveState.NotSignedIn
        }
    }

    // ==================== Create Backup ====================

    fun showCreateBackupDialog() {
        val currentState = _state.value
        if (currentState is BackupState.Idle) {
            _createBackupState.value = CreateBackupState.Visible(
                dateRange = currentState.dateRange
            )
        }
    }

    fun hideCreateBackupDialog() {
        _createBackupState.value = CreateBackupState.Hidden
    }

    fun updateCreateBackupOptions(
        selectedEntities: Set<EntityType>? = null,
        useCompression: Boolean? = null,
        startDate: String? = null,
        endDate: String? = null
    ) {
        val current = _createBackupState.value
        if (current is CreateBackupState.Visible) {
            _createBackupState.value = current.copy(
                selectedEntities = selectedEntities ?: current.selectedEntities,
                useCompression = useCompression ?: current.useCompression,
                startDate = startDate ?: current.startDate,
                endDate = endDate ?: current.endDate
            )
        }
    }

    fun createBackup() {
        val current = _createBackupState.value
        if (current !is CreateBackupState.Visible) return

        val dateRange = if (current.startDate != null && current.endDate != null) {
            DateRange(current.startDate, current.endDate)
        } else null

        val options = BackupOptions(
            selectedEntities = current.selectedEntities,
            compress = current.useCompression,
            dateRange = dateRange
        )

        viewModelScope.launch {
            backupRepository.createBackup(options)
                .collect { progress ->
                    when (progress) {
                        is BackupProgress.Preparing -> {
                            _createBackupState.value = CreateBackupState.InProgress(
                                message = progress.message,
                                progress = 0,
                                processedEntities = 0,
                                totalEntities = 0
                            )
                        }
                        is BackupProgress.Exporting -> {
                            _createBackupState.value = CreateBackupState.InProgress(
                                message = "${progress.currentEntity} 처리 중...",
                                progress = (progress.overallProgress * 100) / progress.overallTotal.coerceAtLeast(1),
                                processedEntities = progress.overallProgress,
                                totalEntities = progress.overallTotal
                            )
                        }
                        is BackupProgress.Compressing -> {
                            val currentProgress = _createBackupState.value
                            if (currentProgress is CreateBackupState.InProgress) {
                                _createBackupState.value = currentProgress.copy(
                                    message = "압축 중... ${progress.progress}%"
                                )
                            }
                        }
                        is BackupProgress.Uploading -> {
                            val currentProgress = _createBackupState.value
                            if (currentProgress is CreateBackupState.InProgress) {
                                _createBackupState.value = currentProgress.copy(
                                    message = "업로드 중... ${progress.progress}%"
                                )
                            }
                        }
                        is BackupProgress.Success -> {
                            _createBackupState.value = CreateBackupState.Success(progress.backupInfo)
                            loadData() // Refresh backup list
                            _snackbarMessage.emit(SnackbarMessage("백업이 완료되었습니다"))
                        }
                        is BackupProgress.Error -> {
                            _createBackupState.value = CreateBackupState.Error(progress.message)
                        }
                        is BackupProgress.Idle -> { /* Do nothing */ }
                    }
                }
        }
    }

    // ==================== Restore ====================

    fun showRestoreFromFileDialog() {
        _restoreState.value = RestoreState.SelectFile()
    }

    fun showRestoreFromLocalBackup(backupInfo: BackupInfo) {
        viewModelScope.launch {
            _restoreState.value = RestoreState.SelectFile(isValidating = true)

            val result = backupRepository.getBackupInfo(backupInfo.id)
            if (result != null) {
                // Convert BackupInfo to BackupMetadata for the Configure state
                val metadata = BackupMetadata(
                    appVersion = "",  // Not stored in BackupInfo
                    schemaVersion = backupInfo.schemaVersion,
                    createdAt = backupInfo.createdAt,
                    backupType = backupInfo.backupType,
                    dateRange = backupInfo.dateRange,
                    selectedEntities = backupInfo.entityCounts.keys.toList(),
                    entityCounts = backupInfo.entityCounts
                )
                _restoreState.value = RestoreState.Configure(
                    metadata = metadata,
                    backupId = backupInfo.id
                )
            } else {
                _restoreState.value = RestoreState.SelectFile(
                    validationError = "백업 파일을 찾을 수 없습니다"
                )
            }
        }
    }

    fun hideRestoreDialog() {
        _restoreState.value = RestoreState.Hidden
    }

    fun validateBackupFile(uri: Uri) {
        viewModelScope.launch {
            _restoreState.value = RestoreState.SelectFile(
                selectedFileUri = uri,
                isValidating = true
            )

            backupRepository.validateBackup(uri)
                .onSuccess { metadata ->
                    _restoreState.value = RestoreState.Configure(
                        metadata = metadata,
                        useUri = uri
                    )
                }
                .onFailure { error ->
                    _restoreState.value = RestoreState.SelectFile(
                        selectedFileUri = uri,
                        validationError = error.message ?: "유효하지 않은 백업 파일"
                    )
                }
        }
    }

    fun updateRestoreOptions(selectedEntities: Set<EntityType>) {
        val current = _restoreState.value
        if (current is RestoreState.Configure) {
            _restoreState.value = current.copy(selectedEntities = selectedEntities)
        }
    }

    fun startRestore() {
        val current = _restoreState.value
        if (current !is RestoreState.Configure) return

        val options = RestoreOptions(
            selectedEntities = current.selectedEntities,
            mergeMode = true // Always use merge mode
        )

        val restoreFlow = if (current.useUri != null) {
            backupRepository.restoreBackup(current.useUri, options)
        } else if (current.backupId != null) {
            backupRepository.restoreFromLocalBackup(current.backupId, options)
        } else {
            return
        }

        viewModelScope.launch {
            restoreFlow.collect { progress ->
                when (progress) {
                    is RestoreProgress.Validating -> {
                        _restoreState.value = RestoreState.InProgress(
                            message = progress.message,
                            progress = 0,
                            processedEntities = 0,
                            totalEntities = 0
                        )
                    }
                    is RestoreProgress.Importing -> {
                        _restoreState.value = RestoreState.InProgress(
                            message = "${progress.currentEntity} 복구 중...",
                            progress = (progress.overallProgress * 100) / progress.overallTotal.coerceAtLeast(1),
                            processedEntities = progress.overallProgress,
                            totalEntities = progress.overallTotal
                        )
                    }
                    is RestoreProgress.Success -> {
                        // Create ImportResult for UI state
                        val result = ImportResult(
                            imported = progress.totalImported,
                            skipped = progress.totalSkipped,
                            errors = progress.details.values.sumOf { it.errors }
                        )
                        _restoreState.value = RestoreState.Success(result)
                        loadData() // Refresh
                        _snackbarMessage.emit(
                            SnackbarMessage("복구 완료: ${progress.totalImported}개 항목 추가됨")
                        )
                    }
                    is RestoreProgress.Error -> {
                        _restoreState.value = RestoreState.Error(progress.message)
                    }
                    is RestoreProgress.Idle -> { /* Do nothing */ }
                }
            }
        }
    }

    // ==================== Backup Detail ====================

    fun showBackupDetail(backupInfo: BackupInfo) {
        _backupDetailState.value = BackupDetailState.Visible(backupInfo)
    }

    fun hideBackupDetail() {
        _backupDetailState.value = BackupDetailState.Hidden
    }

    // ==================== Delete ====================

    fun showDeleteConfirmation(backupInfo: BackupInfo) {
        _deleteConfirmState.value = DeleteConfirmState.Visible(backupInfo)
    }

    fun hideDeleteConfirmation() {
        _deleteConfirmState.value = DeleteConfirmState.Hidden
    }

    fun confirmDelete() {
        val current = _deleteConfirmState.value
        if (current !is DeleteConfirmState.Visible) return

        viewModelScope.launch {
            _deleteConfirmState.value = DeleteConfirmState.Deleting

            backupRepository.deleteLocalBackup(current.backupInfo.id)
                .onSuccess {
                    _deleteConfirmState.value = DeleteConfirmState.Hidden
                    loadData()
                    _snackbarMessage.emit(SnackbarMessage("백업이 삭제되었습니다"))
                }
                .onFailure { error ->
                    _deleteConfirmState.value = DeleteConfirmState.Hidden
                    _snackbarMessage.emit(
                        SnackbarMessage(error.message ?: "삭제 실패", isError = true)
                    )
                }
        }
    }

    // ==================== Export ====================

    fun exportBackup(backupId: String, destinationUri: Uri) {
        viewModelScope.launch {
            backupRepository.exportBackup(backupId, destinationUri)
                .onSuccess {
                    _snackbarMessage.emit(SnackbarMessage("백업 파일이 내보내기 되었습니다"))
                }
                .onFailure { error ->
                    _snackbarMessage.emit(
                        SnackbarMessage(error.message ?: "내보내기 실패", isError = true)
                    )
                }
        }
    }

    // ==================== Google Drive ====================

    fun uploadToGoogleDrive(backupId: String) {
        viewModelScope.launch {
            backupRepository.uploadToGoogleDrive(backupId)
                .collect { progress ->
                    when (progress) {
                        is BackupProgress.Preparing -> {
                            _googleDriveState.value = GoogleDriveState.Uploading(0, "업로드 준비 중...")
                        }
                        is BackupProgress.Uploading -> {
                            _googleDriveState.value = GoogleDriveState.Uploading(
                                progress.progress,
                                "업로드 중..."
                            )
                        }
                        is BackupProgress.Success -> {
                            _googleDriveState.value = GoogleDriveState.SignedIn
                            _snackbarMessage.emit(SnackbarMessage("Google Drive에 업로드되었습니다"))
                        }
                        is BackupProgress.Error -> {
                            _googleDriveState.value = GoogleDriveState.Error(progress.message)
                            _snackbarMessage.emit(
                                SnackbarMessage(progress.message, isError = true)
                            )
                        }
                        else -> {}
                    }
                }
        }
    }

    fun loadGoogleDriveBackups() {
        viewModelScope.launch {
            _googleDriveState.value = GoogleDriveState.Loading

            backupRepository.listGoogleDriveBackups()
                .onSuccess { backups ->
                    _googleDriveState.value = GoogleDriveState.Backups(backups)
                }
                .onFailure { error ->
                    _googleDriveState.value = GoogleDriveState.Error(
                        error.message ?: "Google Drive 백업 목록 로드 실패"
                    )
                }
        }
    }

    fun downloadFromGoogleDrive(driveFileId: String) {
        viewModelScope.launch {
            backupRepository.downloadFromGoogleDrive(driveFileId)
                .collect { progress ->
                    when (progress) {
                        is BackupProgress.Preparing -> {
                            _googleDriveState.value = GoogleDriveState.Downloading(0, "다운로드 준비 중...")
                        }
                        is BackupProgress.Exporting -> {
                            _googleDriveState.value = GoogleDriveState.Downloading(
                                (progress.overallProgress * 100) / progress.overallTotal.coerceAtLeast(1),
                                "다운로드 중..."
                            )
                        }
                        is BackupProgress.Success -> {
                            loadGoogleDriveBackups()
                            loadData()
                            _snackbarMessage.emit(SnackbarMessage("다운로드가 완료되었습니다"))
                        }
                        is BackupProgress.Error -> {
                            _googleDriveState.value = GoogleDriveState.Error(progress.message)
                            _snackbarMessage.emit(
                                SnackbarMessage(progress.message, isError = true)
                            )
                        }
                        else -> {}
                    }
                }
        }
    }

    fun deleteFromGoogleDrive(driveFileId: String) {
        viewModelScope.launch {
            backupRepository.deleteFromGoogleDrive(driveFileId)
                .onSuccess {
                    loadGoogleDriveBackups()
                    _snackbarMessage.emit(SnackbarMessage("Google Drive에서 삭제되었습니다"))
                }
                .onFailure { error ->
                    _snackbarMessage.emit(
                        SnackbarMessage(error.message ?: "삭제 실패", isError = true)
                    )
                }
        }
    }
}
