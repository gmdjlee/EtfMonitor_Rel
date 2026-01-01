package com.etfmonitor.feature.backup.presentation.screen

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.feature.backup.domain.model.*
import com.etfmonitor.feature.backup.presentation.state.*
import com.etfmonitor.feature.backup.presentation.viewmodel.BackupViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val createBackupState by viewModel.createBackupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val backupDetailState by viewModel.backupDetailState.collectAsState()
    val deleteConfirmState by viewModel.deleteConfirmState.collectAsState()
    val googleDriveState by viewModel.googleDriveState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.handleGoogleSignInResult(account)
            } catch (e: ApiException) {
                viewModel.handleGoogleSignInResult(null)
            }
        } else {
            viewModel.handleGoogleSignInResult(null)
        }
    }

    // File picker for restore
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.validateBackupFile(it) }
    }

    // File saver for export
    var pendingExportBackupId by remember { mutableStateOf<String?>(null) }
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { destinationUri ->
            pendingExportBackupId?.let { backupId ->
                viewModel.exportBackup(backupId, destinationUri)
            }
        }
        pendingExportBackupId = null
    }

    // Collect snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("백업 및 복구") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is BackupState.Idle) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateBackupDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "백업 생성")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = state) {
                is BackupState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is BackupState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        onRetry = { viewModel.loadData() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is BackupState.Idle -> {
                    BackupContent(
                        localBackups = currentState.localBackups,
                        entityCounts = currentState.entityCounts,
                        dateRange = currentState.dateRange,
                        estimatedSize = currentState.estimatedSize,
                        googleDriveState = googleDriveState,
                        onBackupClick = { viewModel.showBackupDetail(it) },
                        onRestoreClick = { viewModel.showRestoreFromLocalBackup(it) },
                        onDeleteClick = { viewModel.showDeleteConfirmation(it) },
                        onExportClick = { backupInfo ->
                            pendingExportBackupId = backupInfo.id
                            fileSaverLauncher.launch("etfmonitor_backup_${backupInfo.id}.etfbackup")
                        },
                        onUploadClick = { viewModel.uploadToGoogleDrive(it.id) },
                        onRestoreFromFile = {
                            viewModel.showRestoreFromFileDialog()
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onGoogleSignIn = {
                            googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                        },
                        onGoogleSignOut = { viewModel.signOutFromGoogleDrive() },
                        onLoadDriveBackups = { viewModel.loadGoogleDriveBackups() },
                        onDownloadFromDrive = { viewModel.downloadFromGoogleDrive(it) },
                        onDeleteFromDrive = { viewModel.deleteFromGoogleDrive(it) }
                    )
                }
            }
        }
    }

    // Dialogs
    CreateBackupDialog(
        state = createBackupState,
        onDismiss = { viewModel.hideCreateBackupDialog() },
        onUpdateOptions = { entities, compress, startDate, endDate ->
            viewModel.updateCreateBackupOptions(entities, compress, startDate, endDate)
        },
        onConfirm = { viewModel.createBackup() }
    )

    RestoreDialog(
        state = restoreState,
        onDismiss = { viewModel.hideRestoreDialog() },
        onUpdateOptions = { entities -> viewModel.updateRestoreOptions(entities) },
        onConfirm = { viewModel.startRestore() }
    )

    BackupDetailDialog(
        state = backupDetailState,
        onDismiss = { viewModel.hideBackupDetail() }
    )

    DeleteConfirmDialog(
        state = deleteConfirmState,
        onDismiss = { viewModel.hideDeleteConfirmation() },
        onConfirm = { viewModel.confirmDelete() }
    )
}

@Composable
private fun BackupContent(
    localBackups: List<BackupInfo>,
    entityCounts: Map<EntityType, Int>,
    dateRange: DateRange?,
    estimatedSize: Long,
    googleDriveState: GoogleDriveState,
    onBackupClick: (BackupInfo) -> Unit,
    onRestoreClick: (BackupInfo) -> Unit,
    onDeleteClick: (BackupInfo) -> Unit,
    onExportClick: (BackupInfo) -> Unit,
    onUploadClick: (BackupInfo) -> Unit,
    onRestoreFromFile: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onLoadDriveBackups: () -> Unit,
    onDownloadFromDrive: (String) -> Unit,
    onDeleteFromDrive: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Database Status Card
        item {
            DatabaseStatusCard(
                entityCounts = entityCounts,
                dateRange = dateRange,
                estimatedSize = estimatedSize
            )
        }

        // Quick Actions
        item {
            QuickActionsCard(
                onRestoreFromFile = onRestoreFromFile
            )
        }

        // Google Drive Section
        item {
            GoogleDriveCard(
                state = googleDriveState,
                onSignIn = onGoogleSignIn,
                onSignOut = onGoogleSignOut,
                onLoadBackups = onLoadDriveBackups,
                onDownload = onDownloadFromDrive,
                onDelete = onDeleteFromDrive
            )
        }

        // Local Backups Section
        item {
            Text(
                text = "로컬 백업 (${localBackups.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (localBackups.isEmpty()) {
            item {
                EmptyBackupsCard()
            }
        } else {
            items(localBackups) { backup ->
                BackupCard(
                    backupInfo = backup,
                    onClick = { onBackupClick(backup) },
                    onRestore = { onRestoreClick(backup) },
                    onDelete = { onDeleteClick(backup) },
                    onExport = { onExportClick(backup) },
                    onUpload = { onUploadClick(backup) }
                )
            }
        }
    }
}

@Composable
private fun DatabaseStatusCard(
    entityCounts: Map<EntityType, Int>,
    dateRange: DateRange?,
    estimatedSize: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "현재 데이터베이스",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val totalRecords = entityCounts.values.sum()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(label = "총 레코드", value = formatNumber(totalRecords))
                StatItem(label = "예상 크기", value = formatFileSize(estimatedSize))
            }

            if (dateRange != null) {
                Text(
                    text = "데이터 기간: ${dateRange.startDate} ~ ${dateRange.endDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun QuickActionsCard(onRestoreFromFile: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                icon = Icons.Default.FileOpen,
                label = "파일에서 복구",
                onClick = onRestoreFromFile
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyBackupsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "백업이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "우측 하단 버튼을 눌러 첫 백업을 생성하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GoogleDriveCard(
    state: GoogleDriveState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onLoadBackups: () -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )

                when (state) {
                    is GoogleDriveState.NotSignedIn -> {
                        FilledTonalButton(onClick = onSignIn) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("로그인")
                        }
                    }
                    is GoogleDriveState.SignedIn, is GoogleDriveState.Backups -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onLoadBackups) {
                                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(Icons.Default.Logout, contentDescription = "로그아웃")
                            }
                        }
                    }
                    else -> {}
                }
            }

            when (state) {
                is GoogleDriveState.NotSignedIn -> {
                    Text(
                        text = "Google Drive에 로그인하여 백업을 클라우드에 저장하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                is GoogleDriveState.Loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "로딩 중...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is GoogleDriveState.Uploading -> {
                    Column {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is GoogleDriveState.Downloading -> {
                    Column {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is GoogleDriveState.SignedIn -> {
                    Text(
                        text = "연결됨. 백업 목록을 불러오려면 새로고침을 클릭하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                is GoogleDriveState.Backups -> {
                    if (state.backups.isEmpty()) {
                        Text(
                            text = "클라우드에 저장된 백업이 없습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "클라우드 백업 (${state.backups.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            state.backups.forEach { backup ->
                                DriveBackupItem(
                                    backupInfo = backup,
                                    onDownload = { onDownload(backup.id) },
                                    onDelete = { onDelete(backup.id) }
                                )
                            }
                        }
                    }
                }
                is GoogleDriveState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveBackupItem(
    backupInfo: BackupInfo,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backupInfo.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTimestamp(backupInfo.createdAt)} • ${formatFileSize(backupInfo.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("다운로드") },
                        leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    backupInfo: BackupInfo,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onUpload: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTimestamp(backupInfo.createdAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${backupInfo.schemaVersion} • ${formatFileSize(backupInfo.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("복구") },
                            leadingIcon = { Icon(Icons.Default.Restore, null) },
                            onClick = {
                                showMenu = false
                                onRestore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("내보내기") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = {
                                showMenu = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Google Drive 업로드") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, null) },
                            onClick = {
                                showMenu = false
                                onUpload()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entity summary
            val entitySummary = backupInfo.entityCounts.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(" • ") { (key, count) ->
                    val displayName = EntityType.fromTableName(key)?.displayName ?: key
                    "$displayName: ${formatNumber(count)}"
                }

            Text(
                text = entitySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}

// ==================== Dialogs ====================

@Composable
private fun CreateBackupDialog(
    state: CreateBackupState,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>?, Boolean?, String?, String?) -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is CreateBackupState.Hidden -> {}
        is CreateBackupState.Visible -> {
            CreateBackupConfigDialog(
                selectedEntities = state.selectedEntities,
                useCompression = state.useCompression,
                startDate = state.startDate,
                endDate = state.endDate,
                dateRange = state.dateRange,
                onDismiss = onDismiss,
                onUpdateOptions = onUpdateOptions,
                onConfirm = onConfirm
            )
        }
        is CreateBackupState.InProgress -> {
            ProgressDialog(
                title = "백업 생성 중",
                message = state.message,
                progress = state.progress,
                processedItems = state.processedEntities,
                totalItems = state.totalEntities
            )
        }
        is CreateBackupState.Success -> {
            SuccessDialog(
                title = "백업 완료",
                message = "백업이 성공적으로 생성되었습니다.\n크기: ${formatFileSize(state.backupInfo.fileSize)}",
                onDismiss = onDismiss
            )
        }
        is CreateBackupState.Error -> {
            ErrorDialog(
                title = "백업 실패",
                message = state.message,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun CreateBackupConfigDialog(
    selectedEntities: Set<EntityType>,
    useCompression: Boolean,
    startDate: String?,
    endDate: String?,
    dateRange: DateRange?,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>?, Boolean?, String?, String?) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 생성") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Entity selection
                Text(
                    text = "백업할 데이터 선택",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                EntityType.entries.groupBy { it.category }.forEach { (category, entities) ->
                    Text(
                        text = when (category) {
                            EntityCategory.MASTER -> "마스터 데이터"
                            EntityCategory.TIME_SERIES -> "시계열 데이터"
                            EntityCategory.ANALYSIS -> "분석 결과"
                            EntityCategory.USER_DATA -> "사용자 데이터"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    entities.forEach { entityType ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entityType in selectedEntities,
                                onCheckedChange = { checked ->
                                    val newSelection = if (checked) {
                                        selectedEntities + entityType
                                    } else {
                                        selectedEntities - entityType
                                    }
                                    onUpdateOptions(newSelection, null, null, null)
                                }
                            )
                            Text(
                                text = entityType.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Compression option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useCompression,
                        onCheckedChange = { onUpdateOptions(null, it, null, null) }
                    )
                    Text("파일 압축 (권장)")
                }

                // Date range (only show if time-series entities are selected)
                val hasTimeSeries = selectedEntities.any { it.category == EntityCategory.TIME_SERIES }
                if (hasTimeSeries && dateRange != null) {
                    HorizontalDivider()
                    Text(
                        text = "날짜 범위 (시계열 데이터)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "가능한 범위: ${dateRange.startDate} ~ ${dateRange.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Simple date inputs (in production, use DatePicker)
                    OutlinedTextField(
                        value = startDate ?: "",
                        onValueChange = { onUpdateOptions(null, null, it.takeIf { it.isNotBlank() }, null) },
                        label = { Text("시작일 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate ?: "",
                        onValueChange = { onUpdateOptions(null, null, null, it.takeIf { it.isNotBlank() }) },
                        label = { Text("종료일 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedEntities.isNotEmpty()
            ) {
                Text("백업 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun RestoreDialog(
    state: RestoreState,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>) -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is RestoreState.Hidden -> {}
        is RestoreState.SelectFile -> {
            if (state.isValidating) {
                ProgressDialog(
                    title = "백업 파일 확인 중",
                    message = "파일을 분석하고 있습니다...",
                    progress = -1,
                    processedItems = 0,
                    totalItems = 0
                )
            } else if (state.validationError != null) {
                ErrorDialog(
                    title = "유효하지 않은 파일",
                    message = state.validationError,
                    onDismiss = onDismiss
                )
            }
        }
        is RestoreState.Configure -> {
            RestoreConfigDialog(
                metadata = state.metadata,
                selectedEntities = state.selectedEntities,
                onDismiss = onDismiss,
                onUpdateOptions = onUpdateOptions,
                onConfirm = onConfirm
            )
        }
        is RestoreState.InProgress -> {
            ProgressDialog(
                title = "복구 중",
                message = state.message,
                progress = state.progress,
                processedItems = state.processedEntities,
                totalItems = state.totalEntities
            )
        }
        is RestoreState.Success -> {
            val result = state.result
            SuccessDialog(
                title = "복구 완료",
                message = buildString {
                    appendLine("추가된 항목: ${result.imported}")
                    appendLine("건너뛴 항목: ${result.skipped}")
                    if (result.errors > 0) {
                        appendLine("실패한 항목: ${result.errors}")
                    }
                },
                onDismiss = onDismiss
            )
        }
        is RestoreState.Error -> {
            ErrorDialog(
                title = "복구 실패",
                message = state.message,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun RestoreConfigDialog(
    metadata: BackupMetadata,
    selectedEntities: Set<EntityType>,
    onDismiss: () -> Unit,
    onUpdateOptions: (Set<EntityType>) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("복구 설정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Backup info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "백업 정보",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("생성일: ${formatTimestamp(metadata.createdAt)}")
                        Text("스키마 버전: ${metadata.schemaVersion}")
                        if (metadata.dateRange != null) {
                            Text("데이터 기간: ${metadata.dateRange.startDate} ~ ${metadata.dateRange.endDate}")
                        }
                    }
                }

                Text(
                    text = "복구할 데이터 선택",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "※ 현재 데이터에 없는 항목만 추가됩니다 (병합 모드)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Only show entities that exist in backup
                val availableEntities = metadata.entityCounts
                    .filter { it.value > 0 }
                    .mapNotNull { (tableName, count) ->
                        EntityType.fromTableName(tableName)?.let { it to count }
                    }

                availableEntities.forEach { (entityType, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = entityType in selectedEntities,
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    selectedEntities + entityType
                                } else {
                                    selectedEntities - entityType
                                }
                                onUpdateOptions(newSelection)
                            }
                        )
                        Text("${entityType.displayName} ($count)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedEntities.isNotEmpty()
            ) {
                Text("복구 시작")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun BackupDetailDialog(
    state: BackupDetailState,
    onDismiss: () -> Unit
) {
    if (state is BackupDetailState.Visible) {
        val backup = state.backupInfo
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("백업 상세 정보") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("백업 ID", backup.id)
                    DetailRow("생성일시", formatTimestamp(backup.createdAt))
                    DetailRow("스키마 버전", backup.schemaVersion.toString())
                    DetailRow("파일 크기", formatFileSize(backup.fileSize))
                    DetailRow("백업 타입", if (backup.backupType == BackupType.FULL) "전체" else "선택")

                    if (backup.dateRange != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "데이터 범위",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        DetailRow("시작일", backup.dateRange.startDate)
                        DetailRow("종료일", backup.dateRange.endDate)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "포함된 데이터",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    backup.entityCounts
                        .filter { it.value > 0 }
                        .forEach { (tableName, count) ->
                            val displayName = EntityType.fromTableName(tableName)?.displayName ?: tableName
                            DetailRow(displayName, formatNumber(count))
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    state: DeleteConfirmState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        is DeleteConfirmState.Hidden -> {}
        is DeleteConfirmState.Visible -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("백업 삭제") },
                text = {
                    Text("이 백업을 삭제하시겠습니까?\n\n${formatTimestamp(state.backupInfo.createdAt)}\n${formatFileSize(state.backupInfo.fileSize)}")
                },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                }
            )
        }
        is DeleteConfirmState.Deleting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("삭제 중...") },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("백업을 삭제하고 있습니다...")
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
private fun ProgressDialog(
    title: String,
    message: String,
    progress: Int,
    processedItems: Int,
    totalItems: Int
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(message)
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$processedItems / $totalItems",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun SuccessDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

// ==================== Utility Functions ====================

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun formatNumber(number: Int): String {
    return DecimalFormat("#,###").format(number)
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
