package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.ftp.FtpClientManager
import com.autodeploy.infinityfree.data.ftp.FtpConnectionConfig
import com.autodeploy.infinityfree.data.ftp.FtpResult
import com.autodeploy.infinityfree.data.github.GitHubClientManager
import com.autodeploy.infinityfree.data.github.GitHubResult
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.preferences.SyncControlState
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream

class SyncQueueProcessor(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val secureStorage: SecureStorageManager,
    private val ftpManager: FtpClientManager,
    private val githubManager: GitHubClientManager,
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
    private val githubDao = database.githubConnectionDao()
    private val projectDao = database.projectDao()

    suspend fun processPendingQueue(
        onActivityUpdate: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val project = projectDao.getActiveProject() ?: return@withContext 0

        val pendingItems = queueDao.getPendingItems(project.id)
        if (pendingItems.isEmpty()) return@withContext 0

        var processedCount = 0

        for (item in pendingItems) {
            // Check Emergency Stop or Pause State before each item
            val controlState = preferences.syncControlState.first()
            if (controlState == SyncControlState.EMERGENCY_STOPPED || controlState == SyncControlState.PAUSED) {
                Log.d(TAG, "Queue processor halted by user state: $controlState")
                onActivityUpdate("Halted: $controlState")
                break
            }

            val startTime = System.currentTimeMillis()
            queueDao.updateStatus(item.id, "UPLOADING")
            onActivityUpdate("Processing ${item.relativePath}...")

            when (item.operation) {
                "UPLOAD" -> {
                    handleUploadOperation(project.id, item, startTime, onActivityUpdate)
                    processedCount++
                }
                "DELETE_FILE" -> {
                    handleDeleteOperation(project.id, item, startTime, onActivityUpdate)
                    processedCount++
                }
                "ROLLBACK" -> {
                    handleRollbackOperation(project.id, item, startTime, onActivityUpdate)
                    processedCount++
                }
                else -> {
                    queueDao.updateStatus(item.id, "FAILED", "Unsupported operation ${item.operation}")
                }
            }
        }

        val finalState = preferences.syncControlState.first()
        if (finalState != SyncControlState.EMERGENCY_STOPPED && finalState != SyncControlState.PAUSED) {
            onActivityUpdate("Idle")
        }
        processedCount
    }

    private suspend fun handleUploadOperation(
        projectId: Long,
        item: SyncQueueEntity,
        startTime: Long,
        onActivityUpdate: (String) -> Unit
    ) {
        val metadata = fileMetadataDao.getByPath(projectId, item.relativePath)
        val fileUri = Uri.parse(metadata?.optionalHash ?: "")

        val fileBytes = try {
            if (fileUri != null && fileUri.toString().isNotEmpty()) {
                context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            } else null
        } catch (e: Exception) {
            null
        }

        if (fileBytes == null) {
            val errorMsg = "Local file stream unavailable"
            failOrRetry(item, errorMsg, projectId, "UPLOAD", startTime, "FAILED", "FAILED")
            return
        }

        // 1. Temporary Backup before overwriting modified file
        if (metadata != null && (metadata.lastSyncedAt != null || metadata.githubLastSyncedAt != null)) {
            val retention = preferences.backupRetentionMinutes.first()
            if (fileUri != null && fileUri.toString().isNotEmpty()) {
                backupManager.createBackupBeforeReplace(projectId, item.relativePath, fileUri, retention)
            }
        }

        var githubSuccess = true
        var githubResultMsg = "SKIPPED"
        var returnedSha: String? = metadata?.githubSha

        // 2. GitHub Synchronization
        val githubConn = githubDao.getConnectionForProject(projectId)
        if (githubConn != null) {
            val token = secureStorage.getGitHubToken(githubConn.encryptedTokenReference)
            if (!token.isNullOrEmpty()) {
                onActivityUpdate("Syncing to GitHub: ${item.relativePath}")
                val destPath = buildGitHubPath(githubConn.destinationPath, item.relativePath)

                // Conflict Detection: check if remote SHA has changed unexpectedly
                val remoteSha = githubManager.getFileSha(githubConn.owner, githubConn.repo, githubConn.branch, destPath, token)
                if (remoteSha != null && metadata?.githubSha != null && remoteSha != metadata.githubSha) {
                    // Remote was modified independently
                    val conflictMsg = "Conflict: Remote file on GitHub was modified independently (SHA: ${remoteSha.take(7)} != ${metadata.githubSha?.take(7)})"
                    queueDao.markConflict(item.id, conflictMsg)
                    historyDao.insert(
                        SyncHistoryEntity(
                            projectId = projectId,
                            operation = "UPLOAD",
                            relativePath = item.relativePath,
                            startedAt = startTime,
                            completedAt = System.currentTimeMillis(),
                            result = "CONFLICT",
                            githubResult = "CONFLICT",
                            infinityFreeResult = "SKIPPED",
                            errorMessage = conflictMsg
                        )
                    )
                    return
                }

                val commitMsg = "Auto-deploy: update ${item.relativePath}"
                val ghResult = githubManager.uploadOrUpdateFile(
                    owner = githubConn.owner,
                    repo = githubConn.repo,
                    branch = githubConn.branch,
                    filePath = destPath,
                    fileBytes = fileBytes,
                    commitMessage = commitMsg,
                    existingSha = remoteSha ?: metadata?.githubSha,
                    token = token
                )

                when (ghResult) {
                    is GitHubResult.Success -> {
                        githubSuccess = true
                        githubResultMsg = "SUCCESS"
                        returnedSha = ghResult.data
                        preferences.setLastGitHubSyncTimestamp(System.currentTimeMillis())
                    }
                    is GitHubResult.Error -> {
                        githubSuccess = false
                        githubResultMsg = "FAILED: ${ghResult.message}"
                    }
                }
            }
        }

        // 3. InfinityFree Hosting Synchronization
        var ifSuccess = true
        var ifResultMsg = "SKIPPED"

        val ifConn = connectionDao.getConnectionForProject(projectId)
        if (ifConn != null) {
            val pass = secureStorage.getFtpPassword(ifConn.encryptedPasswordReference)
            if (!pass.isNullOrEmpty()) {
                onActivityUpdate("Uploading to InfinityFree: ${item.relativePath}")
                val ftpConfig = FtpConnectionConfig(
                    server = ifConn.server,
                    port = ifConn.port,
                    username = ifConn.username,
                    password = pass,
                    remoteRootDirectory = ifConn.remoteRootDirectory
                )

                val uploadResult = ftpManager.uploadFile(ftpConfig, fileBytes.inputStream(), item.relativePath)
                when (uploadResult) {
                    is FtpResult.Success -> {
                        ifSuccess = true
                        ifResultMsg = "SUCCESS"
                        preferences.setLastInfinityFreeSyncTimestamp(System.currentTimeMillis())
                    }
                    is FtpResult.Error -> {
                        ifSuccess = false
                        ifResultMsg = "FAILED: ${uploadResult.message}"
                    }
                }
            }
        }

        val allOk = githubSuccess && ifSuccess
        val now = System.currentTimeMillis()

        if (allOk) {
            queueDao.updateStatus(item.id, "SUCCESS")
            fileMetadataDao.insertOrUpdate(
                (metadata ?: FileMetadataEntity(
                    projectId = projectId,
                    relativePath = item.relativePath,
                    itemType = "FILE",
                    fileSize = fileBytes.size.toLong(),
                    lastModified = now
                )).copy(
                    fileSize = fileBytes.size.toLong(),
                    lastSyncedAt = now,
                    githubLastSyncedAt = if (githubConn != null) now else metadata?.githubLastSyncedAt,
                    infinityFreeLastSyncedAt = if (ifConn != null) now else metadata?.infinityFreeLastSyncedAt,
                    githubSha = returnedSha,
                    syncStatus = "SYNCED",
                    isPresent = true
                )
            )
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "UPLOAD",
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = now,
                    result = "SUCCESS",
                    githubResult = githubResultMsg,
                    infinityFreeResult = ifResultMsg
                )
            )
            preferences.setLastSuccessfulSyncTimestamp(now)
        } else {
            val combinedError = buildString {
                if (!githubSuccess) append("GitHub: $githubResultMsg; ")
                if (!ifSuccess) append("InfinityFree: $ifResultMsg")
            }
            failOrRetry(item, combinedError, projectId, "UPLOAD", startTime, githubResultMsg, ifResultMsg)
        }
    }

    private suspend fun handleDeleteOperation(
        projectId: Long,
        item: SyncQueueEntity,
        startTime: Long,
        onActivityUpdate: (String) -> Unit
    ) {
        var githubSuccess = true
        var githubResultMsg = "SKIPPED"

        val githubConn = githubDao.getConnectionForProject(projectId)
        if (githubConn != null) {
            val token = secureStorage.getGitHubToken(githubConn.encryptedTokenReference)
            if (!token.isNullOrEmpty()) {
                val destPath = buildGitHubPath(githubConn.destinationPath, item.relativePath)
                val sha = githubManager.getFileSha(githubConn.owner, githubConn.repo, githubConn.branch, destPath, token)
                if (sha != null) {
                    val delRes = githubManager.deleteFile(
                        owner = githubConn.owner,
                        repo = githubConn.repo,
                        branch = githubConn.branch,
                        filePath = destPath,
                        commitMessage = "Auto-deploy: delete ${item.relativePath}",
                        existingSha = sha,
                        token = token
                    )
                    githubSuccess = delRes is GitHubResult.Success
                    githubResultMsg = if (githubSuccess) "SUCCESS" else "FAILED"
                }
            }
        }

        var ifSuccess = true
        var ifResultMsg = "SKIPPED"

        val ifConn = connectionDao.getConnectionForProject(projectId)
        if (ifConn != null) {
            val pass = secureStorage.getFtpPassword(ifConn.encryptedPasswordReference)
            if (!pass.isNullOrEmpty()) {
                val ftpConfig = FtpConnectionConfig(
                    server = ifConn.server,
                    port = ifConn.port,
                    username = ifConn.username,
                    password = pass,
                    remoteRootDirectory = ifConn.remoteRootDirectory
                )
                val res = ftpManager.deleteFile(ftpConfig, item.relativePath)
                ifSuccess = res is FtpResult.Success
                ifResultMsg = if (ifSuccess) "SUCCESS" else "FAILED"
            }
        }

        if (githubSuccess && ifSuccess) {
            queueDao.updateStatus(item.id, "SUCCESS")
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "DELETE",
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = System.currentTimeMillis(),
                    result = "SUCCESS",
                    githubResult = githubResultMsg,
                    infinityFreeResult = ifResultMsg
                )
            )
        } else {
            failOrRetry(item, "Deletion failed", projectId, "DELETE", startTime, githubResultMsg, ifResultMsg)
        }
    }

    private suspend fun handleRollbackOperation(
        projectId: Long,
        item: SyncQueueEntity,
        startTime: Long,
        onActivityUpdate: (String) -> Unit
    ) {
        val backups = database.temporaryBackupDao().getAvailableBackups(projectId)
        val backup = backups.firstOrNull { it.relativePath == item.relativePath }
        if (backup == null) {
            queueDao.updateStatus(item.id, "FAILED", "Backup file expired or missing")
            return
        }

        val backupFile = backupManager.getBackupFile(backup)
        if (backupFile == null || !backupFile.exists()) {
            queueDao.updateStatus(item.id, "FAILED", "Backup file not found on disk")
            return
        }

        val bytes = backupFile.readBytes()

        // Upload restored backup to GitHub and InfinityFree
        val githubConn = githubDao.getConnectionForProject(projectId)
        if (githubConn != null) {
            val token = secureStorage.getGitHubToken(githubConn.encryptedTokenReference)
            if (!token.isNullOrEmpty()) {
                val destPath = buildGitHubPath(githubConn.destinationPath, item.relativePath)
                val sha = githubManager.getFileSha(githubConn.owner, githubConn.repo, githubConn.branch, destPath, token)
                githubManager.uploadOrUpdateFile(
                    githubConn.owner,
                    githubConn.repo,
                    githubConn.branch,
                    destPath,
                    bytes,
                    "Rollback ${item.relativePath} to ${backup.versionIdentifier}",
                    sha,
                    token
                )
            }
        }

        val ifConn = connectionDao.getConnectionForProject(projectId)
        if (ifConn != null) {
            val pass = secureStorage.getFtpPassword(ifConn.encryptedPasswordReference)
            if (!pass.isNullOrEmpty()) {
                val ftpConfig = FtpConnectionConfig(
                    server = ifConn.server,
                    port = ifConn.port,
                    username = ifConn.username,
                    password = pass,
                    remoteRootDirectory = ifConn.remoteRootDirectory
                )
                ftpManager.uploadFile(ftpConfig, bytes.inputStream(), item.relativePath)
            }
        }

        queueDao.updateStatus(item.id, "SUCCESS")
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

    private suspend fun failOrRetry(
        item: SyncQueueEntity,
        errorMessage: String,
        projectId: Long,
        operation: String,
        startTime: Long,
        ghResult: String,
        ifResult: String
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
                    githubResult = ghResult,
                    infinityFreeResult = ifResult,
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
                    githubResult = ghResult,
                    infinityFreeResult = ifResult,
                    errorMessage = "Exceeded max retries ($MAX_RETRIES): $errorMessage"
                )
            )
        }
    }

    private fun buildGitHubPath(destRoot: String, relativePath: String): String {
        val cleanRoot = destRoot.trim().trimStart('/').trimEnd('/')
        val cleanRel = relativePath.trimStart('/')
        return if (cleanRoot.isEmpty()) cleanRel else "$cleanRoot/$cleanRel"
    }
}
