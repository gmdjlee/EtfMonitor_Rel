package com.etfmonitor.feature.backup.presentation.viewmodel

import android.net.Uri
import app.cash.turbine.test
import com.etfmonitor.MainDispatcherExtension
import com.etfmonitor.feature.backup.data.remote.GoogleDriveHelper
import com.etfmonitor.feature.backup.domain.model.BackupInfo
import com.etfmonitor.feature.backup.domain.model.BackupMetadata
import com.etfmonitor.feature.backup.domain.model.BackupOptions
import com.etfmonitor.feature.backup.domain.model.BackupProgress
import com.etfmonitor.feature.backup.domain.model.BackupType
import com.etfmonitor.feature.backup.domain.model.DateRange
import com.etfmonitor.feature.backup.domain.model.EntityType
import com.etfmonitor.feature.backup.domain.model.ImportResult
import com.etfmonitor.feature.backup.domain.model.RestoreOptions
import com.etfmonitor.feature.backup.domain.model.RestoreProgress
import com.etfmonitor.feature.backup.domain.repository.BackupRepository
import com.etfmonitor.feature.backup.presentation.state.BackupState
import com.etfmonitor.feature.backup.presentation.state.CreateBackupState
import com.etfmonitor.feature.backup.presentation.state.DeleteConfirmState
import com.etfmonitor.feature.backup.presentation.state.GoogleDriveState
import com.etfmonitor.feature.backup.presentation.state.RestoreState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * BackupViewModel 단위 테스트
 *
 * 테스트 범위:
 * - 초기 상태 (Loading → Idle)
 * - loadData() 성공/실패
 * - showCreateBackupDialog() / hideCreateBackupDialog()
 * - createBackup() 진행/성공/오류
 * - showRestoreFromLocalBackup() 성공/실패
 * - validateBackupFile() 성공/실패
 * - startRestore() 성공/오류
 * - showBackupDetail() / hideBackupDetail()
 * - showDeleteConfirmation() / confirmDelete() 성공/실패
 * - Google Drive 상태 확인
 * - loadGoogleDriveBackups() 성공/실패
 * - signOutFromGoogleDrive()
 */
@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class BackupViewModelTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var googleDriveHelper: GoogleDriveHelper

    @BeforeEach
    fun setup() {
        backupRepository = mockk(relaxed = true)
        googleDriveHelper = mockk(relaxed = true)

        // Default: signed out
        every { backupRepository.isGoogleDriveSignedIn() } returns false

        // Default: loadData succeeds with empty backup list
        coEvery { backupRepository.listLocalBackups() } returns emptyList()
        coEvery { backupRepository.getEntityCounts() } returns emptyMap()
        coEvery { backupRepository.getDataDateRange() } returns null
        coEvery { backupRepository.estimateBackupSize(any()) } returns 0L
    }

    private fun createViewModel(): BackupViewModel = BackupViewModel(
        backupRepository = backupRepository,
        googleDriveHelper = googleDriveHelper
    )

    // --- helpers ---

    private fun makeBackupInfo(
        id: String = "backup-001",
        fileName: String = "backup_20250115.zip"
    ) = BackupInfo(
        id = id,
        fileName = fileName,
        filePath = "/storage/backups/$fileName",
        fileSize = 1024 * 1024L,
        createdAt = System.currentTimeMillis(),
        backupType = BackupType.FULL,
        entityCounts = mapOf("holdings" to 1000, "fear_greed_index" to 365),
        schemaVersion = 21,
        dateRange = DateRange("2024-01-01", "2025-01-15")
    )

    private fun makeBackupMetadata() = BackupMetadata(
        appVersion = "1.6.0",
        schemaVersion = 21,
        createdAt = System.currentTimeMillis(),
        backupType = BackupType.FULL,
        dateRange = DateRange("2024-01-01", "2025-01-15"),
        selectedEntities = listOf("holdings", "fear_greed_index"),
        entityCounts = mapOf("holdings" to 1000, "fear_greed_index" to 365)
    )

    // ---------------------------------------------------------------
    // 초기 상태 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("초기 상태 테스트")
    inner class InitialStateTests {

        @Test
        @DisplayName("초기화 후 loadData 성공 시 Idle 상태")
        fun loadSuccess_stateIsIdle() = runTest {
            val backups = listOf(makeBackupInfo())
            coEvery { backupRepository.listLocalBackups() } returns backups

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BackupState.Idle>(state)
                assertEquals(1, state.localBackups.size)
            }
        }

        @Test
        @DisplayName("loadData 실패 시 Error 상태")
        fun loadFailure_stateIsError() = runTest {
            coEvery { backupRepository.listLocalBackups() } throws RuntimeException("파일 시스템 오류")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BackupState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }

        @Test
        @DisplayName("초기 createBackupState 는 Hidden")
        fun initialCreateBackupState_isHidden() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.Hidden>(awaitItem())
            }
        }

        @Test
        @DisplayName("초기 restoreState 는 Hidden")
        fun initialRestoreState_isHidden() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.restoreState.test {
                assertIs<RestoreState.Hidden>(awaitItem())
            }
        }

        @Test
        @DisplayName("Google Drive 미연결 시 NotSignedIn 상태")
        fun notSignedIn_googleDriveIsNotSignedIn() = runTest {
            every { backupRepository.isGoogleDriveSignedIn() } returns false

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                assertIs<GoogleDriveState.NotSignedIn>(awaitItem())
            }
        }

        @Test
        @DisplayName("Google Drive 연결 시 SignedIn 상태")
        fun signedIn_googleDriveIsSignedIn() = runTest {
            every { backupRepository.isGoogleDriveSignedIn() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                assertIs<GoogleDriveState.SignedIn>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // loadData() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("loadData() 테스트")
    inner class LoadDataTests {

        @Test
        @DisplayName("loadData() 호출 시 repository 메서드들 호출")
        fun loadData_callsRepositoryMethods() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadData()
            advanceUntilIdle()

            coVerify(atLeast = 2) { backupRepository.listLocalBackups() }
            coVerify(atLeast = 2) { backupRepository.getEntityCounts() }
        }

        @Test
        @DisplayName("백업 목록 있을 때 Idle 상태에 포함")
        fun backupsExist_idleStateContainsThem() = runTest {
            val backups = listOf(makeBackupInfo("b1"), makeBackupInfo("b2"))
            coEvery { backupRepository.listLocalBackups() } returns backups

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.state.test {
                val state = awaitItem()
                assertIs<BackupState.Idle>(state)
                assertEquals(2, state.localBackups.size)
            }
        }
    }

    // ---------------------------------------------------------------
    // 백업 생성 다이얼로그 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("백업 생성 다이얼로그 테스트")
    inner class CreateBackupDialogTests {

        @Test
        @DisplayName("showCreateBackupDialog() Idle 상태에서 Visible로 전환")
        fun showDialog_fromIdle_becomesVisible() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.Visible>(awaitItem())
            }
        }

        @Test
        @DisplayName("hideCreateBackupDialog() 호출 시 Hidden으로 전환")
        fun hideDialog_becomesHidden() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()
            viewModel.hideCreateBackupDialog()

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.Hidden>(awaitItem())
            }
        }

        @Test
        @DisplayName("createBackup() Visible 아닌 상태에선 무시")
        fun createBackup_notVisible_ignored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // createBackupState is Hidden — createBackup() should be no-op
            viewModel.createBackup()
            advanceUntilIdle()

            coVerify(exactly = 0) { backupRepository.createBackup(any()) }
        }

        @Test
        @DisplayName("createBackup() 성공 시 CreateBackupState.Success 전환")
        fun createBackup_success_transitionsToSuccess() = runTest {
            val backupInfo = makeBackupInfo()
            every { backupRepository.createBackup(any()) } returns flowOf(
                BackupProgress.Preparing("준비 중..."),
                BackupProgress.Success(backupInfo)
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()
            viewModel.createBackup()
            advanceUntilIdle()

            viewModel.createBackupState.test {
                // After success, state may be Success or reset
                val state = awaitItem()
                assertTrue(
                    state is CreateBackupState.Success || state is CreateBackupState.Hidden,
                    "Expected Success or Hidden, got $state"
                )
            }
        }

        @Test
        @DisplayName("createBackup() 오류 시 CreateBackupState.Error 전환")
        fun createBackup_error_transitionsToError() = runTest {
            every { backupRepository.createBackup(any()) } returns flowOf(
                BackupProgress.Error("파일 쓰기 오류")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()
            viewModel.createBackup()
            advanceUntilIdle()

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.Error>(awaitItem())
            }
        }

        @Test
        @DisplayName("Exporting 진행 중 InProgress 상태")
        fun createBackup_exporting_inProgressState() = runTest {
            every { backupRepository.createBackup(any()) } returns flowOf(
                BackupProgress.Exporting(
                    currentEntity = "holdings",
                    entityProgress = 500,
                    entityTotal = 1000,
                    overallProgress = 1,
                    overallTotal = 5
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()
            viewModel.createBackup()
            advanceUntilIdle()

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.InProgress>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // 복구 다이얼로그 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("복구 다이얼로그 테스트")
    inner class RestoreDialogTests {

        @Test
        @DisplayName("showRestoreFromFileDialog() 호출 시 SelectFile 상태")
        fun showRestoreFromFileDialog_selectFileState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreFromFileDialog()

            viewModel.restoreState.test {
                assertIs<RestoreState.SelectFile>(awaitItem())
            }
        }

        @Test
        @DisplayName("hideRestoreDialog() 호출 시 Hidden 상태")
        fun hideRestoreDialog_hiddenState() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreFromFileDialog()
            viewModel.hideRestoreDialog()

            viewModel.restoreState.test {
                assertIs<RestoreState.Hidden>(awaitItem())
            }
        }

        @Test
        @DisplayName("showRestoreFromLocalBackup() 성공 시 Configure 상태")
        fun showRestoreFromLocalBackup_success_configurState() = runTest {
            val backupInfo = makeBackupInfo()
            coEvery { backupRepository.getBackupInfo(backupInfo.id) } returns backupInfo

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreFromLocalBackup(backupInfo)
            advanceUntilIdle()

            viewModel.restoreState.test {
                assertIs<RestoreState.Configure>(awaitItem())
            }
        }

        @Test
        @DisplayName("showRestoreFromLocalBackup() 백업 없을 때 SelectFile(error) 상태")
        fun showRestoreFromLocalBackup_notFound_selectFileWithError() = runTest {
            val backupInfo = makeBackupInfo("non-existent")
            coEvery { backupRepository.getBackupInfo("non-existent") } returns null

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showRestoreFromLocalBackup(backupInfo)
            advanceUntilIdle()

            viewModel.restoreState.test {
                val state = awaitItem()
                assertIs<RestoreState.SelectFile>(state)
                assertTrue(state.validationError?.isNotEmpty() == true)
            }
        }

        @Test
        @DisplayName("validateBackupFile() 성공 시 Configure 상태")
        fun validateBackupFile_success_configureState() = runTest {
            val uri = mockk<Uri>()
            val metadata = makeBackupMetadata()
            coEvery { backupRepository.validateBackup(uri) } returns Result.success(metadata)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.validateBackupFile(uri)
            advanceUntilIdle()

            viewModel.restoreState.test {
                assertIs<RestoreState.Configure>(awaitItem())
            }
        }

        @Test
        @DisplayName("validateBackupFile() 실패 시 SelectFile(error) 상태")
        fun validateBackupFile_failure_selectFileWithError() = runTest {
            val uri = mockk<Uri>()
            coEvery { backupRepository.validateBackup(uri) } returns
                Result.failure(Exception("유효하지 않은 파일"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.validateBackupFile(uri)
            advanceUntilIdle()

            viewModel.restoreState.test {
                val state = awaitItem()
                assertIs<RestoreState.SelectFile>(state)
                assertTrue(state.validationError?.isNotEmpty() == true)
            }
        }

        @Test
        @DisplayName("startRestore() Configure 아닌 상태에선 무시")
        fun startRestore_notConfigure_ignored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // restoreState is Hidden — startRestore() should be no-op
            viewModel.startRestore()
            advanceUntilIdle()

            coVerify(exactly = 0) { backupRepository.restoreBackup(any(), any()) }
        }

        @Test
        @DisplayName("startRestore() 복구 성공 시 RestoreState.Success")
        fun startRestore_success_producesSuccessState() = runTest {
            val uri = mockk<Uri>()
            val metadata = makeBackupMetadata()
            coEvery { backupRepository.validateBackup(uri) } returns Result.success(metadata)
            every { backupRepository.restoreBackup(uri, any()) } returns flowOf(
                RestoreProgress.Success(
                    totalImported = 1000,
                    totalSkipped = 50,
                    details = mapOf(
                        "holdings" to ImportResult(imported = 950, skipped = 50, errors = 0)
                    )
                )
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.validateBackupFile(uri)
            advanceUntilIdle()

            viewModel.startRestore()
            advanceUntilIdle()

            viewModel.restoreState.test {
                assertIs<RestoreState.Success>(awaitItem())
            }
        }

        @Test
        @DisplayName("startRestore() 오류 시 RestoreState.Error")
        fun startRestore_error_producesErrorState() = runTest {
            val uri = mockk<Uri>()
            val metadata = makeBackupMetadata()
            coEvery { backupRepository.validateBackup(uri) } returns Result.success(metadata)
            every { backupRepository.restoreBackup(uri, any()) } returns flowOf(
                RestoreProgress.Error("복구 중 오류 발생")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.validateBackupFile(uri)
            advanceUntilIdle()

            viewModel.startRestore()
            advanceUntilIdle()

            viewModel.restoreState.test {
                assertIs<RestoreState.Error>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // 백업 상세 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("백업 상세 다이얼로그 테스트")
    inner class BackupDetailTests {

        @Test
        @DisplayName("showBackupDetail() 호출 시 Visible 상태")
        fun showBackupDetail_becomesVisible() = runTest {
            val backupInfo = makeBackupInfo()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showBackupDetail(backupInfo)

            viewModel.backupDetailState.test {
                val state = awaitItem()
                assertIs<com.etfmonitor.feature.backup.presentation.state.BackupDetailState.Visible>(state)
                assertEquals(backupInfo.id, state.backupInfo.id)
            }
        }

        @Test
        @DisplayName("hideBackupDetail() 호출 시 Hidden 상태")
        fun hideBackupDetail_becomesHidden() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showBackupDetail(makeBackupInfo())
            viewModel.hideBackupDetail()

            viewModel.backupDetailState.test {
                assertIs<com.etfmonitor.feature.backup.presentation.state.BackupDetailState.Hidden>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // 삭제 확인 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("삭제 확인 다이얼로그 테스트")
    inner class DeleteConfirmTests {

        @Test
        @DisplayName("showDeleteConfirmation() 호출 시 Visible 상태")
        fun showDeleteConfirmation_becomesVisible() = runTest {
            val backupInfo = makeBackupInfo()

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showDeleteConfirmation(backupInfo)

            viewModel.deleteConfirmState.test {
                val state = awaitItem()
                assertIs<DeleteConfirmState.Visible>(state)
                assertEquals(backupInfo.id, state.backupInfo.id)
            }
        }

        @Test
        @DisplayName("hideDeleteConfirmation() 호출 시 Hidden 상태")
        fun hideDeleteConfirmation_becomesHidden() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showDeleteConfirmation(makeBackupInfo())
            viewModel.hideDeleteConfirmation()

            viewModel.deleteConfirmState.test {
                assertIs<DeleteConfirmState.Hidden>(awaitItem())
            }
        }

        @Test
        @DisplayName("confirmDelete() Visible 아닌 상태에선 무시")
        fun confirmDelete_notVisible_ignored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.confirmDelete()
            advanceUntilIdle()

            coVerify(exactly = 0) { backupRepository.deleteLocalBackup(any()) }
        }

        @Test
        @DisplayName("confirmDelete() 성공 시 Hidden으로 전환 및 snackbar 발행")
        fun confirmDelete_success_hidesDialogAndEmitsSnackbar() = runTest {
            val backupInfo = makeBackupInfo()
            coEvery { backupRepository.deleteLocalBackup(backupInfo.id) } returns Result.success(Unit)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showDeleteConfirmation(backupInfo)
            viewModel.confirmDelete()
            advanceUntilIdle()

            viewModel.deleteConfirmState.test {
                assertIs<DeleteConfirmState.Hidden>(awaitItem())
            }
            coVerify { backupRepository.deleteLocalBackup(backupInfo.id) }
        }

        @Test
        @DisplayName("confirmDelete() 실패 시 Hidden으로 전환 및 오류 snackbar 발행")
        fun confirmDelete_failure_hidesDialogAndEmitsErrorSnackbar() = runTest {
            val backupInfo = makeBackupInfo()
            coEvery { backupRepository.deleteLocalBackup(backupInfo.id) } returns
                Result.failure(Exception("삭제 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showDeleteConfirmation(backupInfo)
            viewModel.confirmDelete()
            advanceUntilIdle()

            // Even on failure, the dialog is hidden
            viewModel.deleteConfirmState.test {
                assertIs<DeleteConfirmState.Hidden>(awaitItem())
            }
        }
    }

    // ---------------------------------------------------------------
    // Google Drive 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Google Drive 테스트")
    inner class GoogleDriveTests {

        @Test
        @DisplayName("loadGoogleDriveBackups() 성공 시 Backups 상태")
        fun loadGoogleDriveBackups_success_backupsState() = runTest {
            val driveBackups = listOf(makeBackupInfo("drive-001"))
            coEvery { backupRepository.listGoogleDriveBackups() } returns Result.success(driveBackups)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadGoogleDriveBackups()
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                val state = awaitItem()
                assertIs<GoogleDriveState.Backups>(state)
                assertEquals(1, state.backups.size)
            }
        }

        @Test
        @DisplayName("loadGoogleDriveBackups() 실패 시 Error 상태")
        fun loadGoogleDriveBackups_failure_errorState() = runTest {
            coEvery { backupRepository.listGoogleDriveBackups() } returns
                Result.failure(Exception("Google Drive 연결 실패"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadGoogleDriveBackups()
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                val state = awaitItem()
                assertIs<GoogleDriveState.Error>(state)
                assertTrue(state.message.isNotEmpty())
            }
        }

        @Test
        @DisplayName("signOutFromGoogleDrive() 호출 시 NotSignedIn 상태")
        fun signOut_transitionsToNotSignedIn() = runTest {
            every { backupRepository.isGoogleDriveSignedIn() } returns true

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.signOutFromGoogleDrive()
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                assertIs<GoogleDriveState.NotSignedIn>(awaitItem())
            }
        }

        @Test
        @DisplayName("uploadToGoogleDrive() 성공 시 SignedIn 상태로 복귀")
        fun uploadToGoogleDrive_success_signsIn() = runTest {
            val backupInfo = makeBackupInfo()
            every { backupRepository.uploadToGoogleDrive("backup-001") } returns flowOf(
                BackupProgress.Success(backupInfo)
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uploadToGoogleDrive("backup-001")
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                assertIs<GoogleDriveState.SignedIn>(awaitItem())
            }
        }

        @Test
        @DisplayName("uploadToGoogleDrive() 오류 시 Error 상태")
        fun uploadToGoogleDrive_error_errorState() = runTest {
            every { backupRepository.uploadToGoogleDrive(any()) } returns flowOf(
                BackupProgress.Error("업로드 실패")
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uploadToGoogleDrive("backup-001")
            advanceUntilIdle()

            viewModel.googleDriveState.test {
                val state = awaitItem()
                assertIs<GoogleDriveState.Error>(state)
                assertTrue(state.message.contains("업로드 실패"))
            }
        }
    }

    // ---------------------------------------------------------------
    // updateCreateBackupOptions() 테스트
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("updateCreateBackupOptions() 테스트")
    inner class UpdateCreateBackupOptionsTests {

        @Test
        @DisplayName("Visible 상태에서 옵션 업데이트")
        fun visible_updatesOptions() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.showCreateBackupDialog()

            viewModel.updateCreateBackupOptions(useCompression = false)

            viewModel.createBackupState.test {
                val state = awaitItem()
                assertIs<CreateBackupState.Visible>(state)
                assertEquals(false, state.useCompression)
            }
        }

        @Test
        @DisplayName("Hidden 상태에서 옵션 업데이트 시 무시")
        fun hidden_updatesIgnored() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // createBackupState is Hidden
            viewModel.updateCreateBackupOptions(useCompression = false)

            viewModel.createBackupState.test {
                assertIs<CreateBackupState.Hidden>(awaitItem())
            }
        }
    }
}
