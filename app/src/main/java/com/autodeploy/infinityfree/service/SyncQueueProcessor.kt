package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.ftp.FtpClientManager
import com.autodeploy.infinityfree.data.ftp.FtpConnectionConfig
import com.autodeploy.infinityfree.data.ftp.FtpResult
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream

class SyncQueueProcessor(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val secureStorage: SecureStorageManager,
    private val ftpManager: FtpClientManager,
    private val backupManager: BackupManager
) {
    companion object {
        private const val TAG = "SyncQueueProcessor"
        const val MAX_RETRIES = 3
    }

    private val queueDao = database.syncQueueDao()
    private val fileMetadataDao = database.fileMetadataDao()
    private val historyDao = database.syncHistoryDao()
    private val connectionDao = database.hostingConnectionDao()
    private val projectDao = database.projectDao()

    suspend fun processPendingQueue(
        onActivityUpdate: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val project = projectDao.getActiveProject() ?: return@withContext 0
        val connection = connectionDao.getConnectionForProject(project.id) ?: run {
            Log.w(TAG, "No hosting connection configured for active project")
            return@withContext 0
        }

        val password = secureStorage.getFtpPassword(connection.encryptedPasswordReference) ?: run {
            Log.w(TAG, "No FTP password found in secure storage")
            return@withContext 0
        }

        val ftpConfig = FtpConnectionConfig(
            server = connection.server,
            port = connection.port,
            username = connection.username,
            password = password,
            remoteRootDirectory = connection.remoteRootDirectory
        )

        val pendingItems = queueDao.getPendingItems(project.id)
        if (pendingItems.isEmpty()) return@withContext 0

        var processedCount = 0

        for (item in pendingItems) {
            val startTime = System.currentTimeMillis()
            queueDao.updateStatus(item.id, "UPLOADING")
            onActivityUpdate("Uploading ${item.relativePath}...")

            when (item.operation) {
                "UPLOAD" -> {
                    handleUploadOperation(project.id, item, ftpConfig, startTime)
                    processedCount++
                }
                "DELETE_FILE" -> {
                    handleDeleteOperation(project.id, item, ftpConfig, startTime)
                    processedCount++
                }
                "ROLLBACK" -> {
                    handleRollbackOperation(project.id, item, ftpConfig, startTime)
                    processedCount++
                }
                else -> {
                    queueDao.updateStatus(item.id, "FAILED", "Unsupported operation ${item.operation}")
                }
            }
        }

        onActivityUpdate("Idle")
        processedCount
    }

    private suspend fun handleUploadOperation(
        projectId: Long,
        item: SyncQueueEntity,
        ftpConfig: FtpConnectionConfig,
        startTime: Long
    ) {
        val metadata = fileMetadataDao.getByPath(projectId, item.relativePath)
        val fileUri = Uri.parse(metadata?.optionalHash ?: "") // We store content URI or resolve from tree

        var inputStream: InputStream? = null
        try {
            // Check if backup is needed (if replacing an existing synced file)
            if (metadata != null && metadata.lastSyncedAt != null && metadata.lastSyncedAt > 0) {
                val retentionMinutes = preferences.backupRetentionMinutes.first()
                if (fileUri != null && fileUri.toString().isNotEmpty()) {
                    backupManager.createBackupBeforeReplace(projectId, item.relativePath, fileUri, retentionMinutes)
                }
            }

            inputStream = if (fileUri != null && fileUri.toString().isNotEmpty()) {
                context.contentResolver.openInputStream(fileUri)
            } else {
                null
            }

            if (inputStream == null) {
                // Cannot open file, might have been deleted locally before upload
                val errorMsg = "Local file stream unavailable"
                failOrRetry(item, errorMsg, projectId, "UPLOAD", startTime)
                return
            }

            val result = ftpManager.uploadFile(ftpConfig, inputStream, item.relativePath)
            when (result) {
                is FtpResult.Success -> {
                    queueDao.updateStatus(item.id, "SUCCESS")
                    fileMetadataDao.updateSyncStatus(
                        projectId = projectId,
                        path = item.relativePath,
                        status = "SYNCED",
                        syncedAt = System.currentTimeMillis()
                    )
                    historyDao.insert(
                        SyncHistoryEntity(
                            projectId = projectId,
                            operation = "UPLOAD",
                            relativePath = item.relativePath,
                            startedAt = startTime,
                            completedAt = System.currentTimeMillis(),
                            result = "SUCCESS"
                        )
                    )
                    preferences.setLastSuccessfulSyncTimestamp(System.currentTimeMillis())
                }
                is FtpResult.Error -> {
                    failOrRetry(item, result.message, projectId, "UPLOAD", startTime)
                }
            }
        } catch (e: Exception) {
            failOrRetry(item, e.localizedMessage ?: "Unknown upload exception", projectId, "UPLOAD", startTime)
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
    }

    private suspend fun handleDeleteOperation(
        projectId: Long,
        item: SyncQueueEntity,
        ftpConfig: FtpConnectionConfig,
        startTime: Long
    ) {
        val result = ftpManager.deleteFile(ftpConfig, item.relativePath)
        when (result) {
            is FtpResult.Success -> {
                queueDao.updateStatus(item.id, "SUCCESS")
                historyDao.insert(
                    SyncHistoryEntity(
                        projectId = projectId,
                        operation = "DELETE",
                        relativePath = item.relativePath,
                        startedAt = startTime,
                        completedAt = System.currentTimeMillis(),
                        result = "SUCCESS"
                    )
                )
            }
            is FtpResult.Error -> {
                failOrRetry(item, result.message, projectId, "DELETE", startTime)
            }
        }
    }

    private suspend fun handleRollbackOperation(
        projectId: Long,
        item: SyncQueueEntity,
        ftpConfig: FtpConnectionConfig,
        startTime: Long
    ) {
        // Find latest available backup for this path
        val backups = database.temporaryBackupDao().getAvailableBackups(projectId)
        val backup = backups.firstOrNull { it.relativePath == item.relativePath }
        if (backup == null) {
            queueDao.updateStatus(item.id, "FAILED", "Backup file expired or missing")
            return
        }

        val backupFile = backupManager.getBackupFile(backup)
        if (backupFile == null || !backupFile.exists()) {
            queueDao.updateStatus(item.id, "FAILED", "Physical backup file not found")
            return
        }

        var stream: InputStream? = null
        try {
            stream = FileInputStream(backupFile)
            val result = ftpManager.uploadFile(ftpConfig, stream, item.relativePath)
            when (result) {
                is FtpResult.Success -> {
                    queueDao.updateStatus(item.id, "SUCCESS")
                    fileMetadataDao.updateSyncStatus(projectId, item.relativePath, "SYNCED")
                    historyDao.insert(
                        SyncHistoryEntity(
                            projectId = projectId,
                            operation = "ROLLBACK",
                            relativePath = item.relativePath,
                            startedAt = startTime,
                            completedAt = System.currentTimeMillis(),
                            result = "SUCCESS"
                        )
                    )
                }
                is FtpResult.Error -> {
                    failOrRetry(item, result.message, projectId, "ROLLBACK", startTime)
                }
            }
        } finally {
            try { stream?.close() } catch (ignored: Exception) {}
        }
    }

    private suspend fun failOrRetry(
        item: SyncQueueEntity,
        errorMessage: String,
        projectId: Long,
        operation: String,
        startTime: Long
    ) {
        val nextRetry = item.retryCount + 1
        if (nextRetry < MAX_RETRIES) {
            val updated = item.copy(
                retryCount = nextRetry,
                status = "RETRYING",
                errorMessage = errorMessage,
                lastAttemptAt = System.currentTimeMillis()
            )
            queueDao.updateItem(updated)
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = operation,
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = System.currentTimeMillis(),
                    result = "FAILED",
                    errorMessage = "Attempt $nextRetry/$MAX_RETRIES failed: $errorMessage"
                )
            )
        } else {
            val failed = item.copy(
                retryCount = nextRetry,
                status = "FAILED",
                errorMessage = errorMessage,
                lastAttemptAt = System.currentTimeMillis()
            )
            queueDao.updateItem(failed)
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = operation,
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = System.currentTimeMillis(),
                    result = "FAILED",
                    errorMessage = "Exceeded max retries ($MAX_RETRIES): $errorMessage"
                )
            )
        }
    }
}
