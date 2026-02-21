package com.etfmonitor.feature.backup.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive API helper for backup operations
 */
@Singleton
class GoogleDriveHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val APP_NAME = "ETF Monitor"
        private const val BACKUP_FOLDER_NAME = "ETF Monitor Backups"
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
        private const val MIME_TYPE_BACKUP = "application/octet-stream"
    }

    private var driveService: Drive? = null
    private var backupFolderId: String? = null

    /**
     * Check if user is signed in to Google account with Drive scope
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(
            account,
            Scope(DriveScopes.DRIVE_FILE)
        )
    }

    /**
     * Get current signed-in account
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Get sign-in client for Google Drive access
     */
    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Get sign-in intent for launching sign-in flow
     */
    fun getSignInIntent(): Intent {
        return getSignInClient().signInIntent
    }

    /**
     * Initialize Drive service with signed-in account
     */
    suspend fun initializeDriveService(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account

            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(APP_NAME)
                .build()

            // Ensure backup folder exists
            ensureBackupFolderExists()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensure the backup folder exists in Google Drive
     */
    private suspend fun ensureBackupFolderExists() = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext

        // Search for existing folder
        val query = "name = '$BACKUP_FOLDER_NAME' and mimeType = '$MIME_TYPE_FOLDER' and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        backupFolderId = if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            // Create new folder
            val folderMetadata = com.google.api.services.drive.model.File().apply {
                name = BACKUP_FOLDER_NAME
                mimeType = MIME_TYPE_FOLDER
            }
            val folder = drive.files().create(folderMetadata)
                .setFields("id")
                .execute()
            folder.id
        }
    }

    /**
     * Upload a backup file to Google Drive
     */
    suspend fun uploadBackup(localFile: File, fileName: String): Result<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                IllegalStateException("Drive service not initialized. Please sign in first.")
            )
            val folderId = backupFolderId ?: return@withContext Result.failure(
                IllegalStateException("Backup folder not found.")
            )

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = fileName
                parents = listOf(folderId)
            }

            val mediaContent = FileContent(MIME_TYPE_BACKUP, localFile)
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime, modifiedTime")
                .execute()

            Result.success(
                DriveBackupInfo(
                    id = uploadedFile.id,
                    name = uploadedFile.name,
                    size = uploadedFile.getSize()?.toLong() ?: localFile.length(),
                    createdTime = uploadedFile.createdTime?.value ?: System.currentTimeMillis(),
                    modifiedTime = uploadedFile.modifiedTime?.value ?: System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a backup file from Google Drive
     */
    suspend fun downloadBackup(fileId: String, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                IllegalStateException("Drive service not initialized. Please sign in first.")
            )

            FileOutputStream(destinationFile).use { outputStream ->
                drive.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
            }

            Result.success(destinationFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all backups in the backup folder
     */
    suspend fun listBackups(): Result<List<DriveBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                IllegalStateException("Drive service not initialized. Please sign in first.")
            )
            val folderId = backupFolderId ?: return@withContext Result.failure(
                IllegalStateException("Backup folder not found.")
            )

            val query = "'$folderId' in parents and trashed = false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, size, createdTime, modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .execute()

            val backups = result.files.map { file ->
                DriveBackupInfo(
                    id = file.id,
                    name = file.name,
                    size = file.getSize()?.toLong() ?: 0L,
                    createdTime = file.createdTime?.value ?: 0L,
                    modifiedTime = file.modifiedTime?.value ?: 0L
                )
            }

            Result.success(backups)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a backup from Google Drive
     */
    suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                IllegalStateException("Drive service not initialized. Please sign in first.")
            )

            drive.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out from Google account
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getSignInClient().signOut()
            driveService = null
            backupFolderId = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get storage quota information
     */
    suspend fun getStorageQuota(): Result<StorageQuota> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                IllegalStateException("Drive service not initialized. Please sign in first.")
            )

            val about = drive.about().get()
                .setFields("storageQuota")
                .execute()

            val quota = about.storageQuota
            Result.success(
                StorageQuota(
                    used = quota.usage ?: 0L,
                    total = quota.limit ?: 0L,
                    usedInDrive = quota.usageInDrive ?: 0L
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Information about a backup stored in Google Drive
 */
data class DriveBackupInfo(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long,
    val modifiedTime: Long
)

/**
 * Google Drive storage quota information
 */
data class StorageQuota(
    val used: Long,
    val total: Long,
    val usedInDrive: Long
)
