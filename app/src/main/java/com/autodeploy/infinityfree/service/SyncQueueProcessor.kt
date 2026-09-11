package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.deployment.DeploymentManager
import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import com.autodeploy.infinityfree.data.deployment.ProviderResult
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
import java.io.File

class SyncQueueProcessor(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val secureStorage: SecureStorageManager,
    private val deploymentManager: DeploymentManager,
    private val backupManager: BackupManager
) {
    companion object {
        private const val TAG = "SyncQueueProcessor"
        const val MAX_RETRIES = 3
    }

    private val queueDao = database.syncQueueDao()
    private val fileMetadataDao = database.fileMetadataDao()
    private val historyDao = database.syncHistoryDao()
    private val projectDao = database.projectDao()

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun shouldWaitForBackoff(item: SyncQueueEntity): Boolean {
        if (item.status != "RETRYING" || item.retryCount <= 0) return false
        val lastAttempt = item.lastAttemptAt ?: return false
        // Backoff: 2s * 2^(retryCount - 1), capped at 60s
        val backoffDelay = (2000L * (1L shl (item.retryCount - 1).coerceAtMost(5))).coerceAtMost(60_000L)
        return (System.currentTimeMillis() - lastAttempt) < backoffDelay
    }

    private fun sanitizeErrorMessage(raw: String?): String {
        if (raw == null) return ""
        var sanitized = raw
        // Mask GitHub tokens
        sanitized = sanitized.replace(Regex("""(ghp_[a-zA-Z0-9]{20,}|github_pat_[a-zA-Z0-9_]{20,})"""), "[REDACTED_TOKEN]")
        // Mask Bearer tokens
        sanitized = sanitized.replace(Regex("""Bearer\s+[a-zA-Z0-9_\-\.]+""", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        // Mask passwords in URLs or logs
        sanitized = sanitized.replace(Regex("""(:)[^:/@\s]+(@)"""), "$1[REDACTED]$2")
        sanitized = sanitized.replace(Regex("""password\s*=\s*['"]?[^\s,;&'"]+['"]?""", RegexOption.IGNORE_CASE), "password=[REDACTED]")
        return sanitized
    }

    private fun categorizeError(msg: String?): String {
        if (msg == null) return "UNKNOWN"
        val lower = msg.lowercase()
        return when {
            lower.contains("timeout") || lower.contains("connect") || lower.contains("network") ||
                lower.contains("socket") || lower.contains("unreachable") || lower.contains("resolve") -> "NETWORK"
            lower.contains("auth") || lower.contains("login") || lower.contains("password") ||
                lower.contains("credentials") || lower.contains("401") || lower.contains("403") ||
                lower.contains("token") || lower.contains("unauthorized") -> "AUTH"
            lower.contains("quota") || lower.contains("disk full") || lower.contains("space") ||
                lower.contains("552") || lower.contains("storage") -> "STORAGE_FULL"
            lower.contains("permission") || lower.contains("denied") -> "PERMISSION"
            lower.contains("not found") || lower.contains("404") || lower.contains("550") -> "NOT_FOUND"
            lower.contains("hash") || lower.contains("integrity") || lower.contains("checksum") ||
                lower.contains("mismatch") -> "INTEGRITY"
            else -> "GENERAL"
        }
    }

    suspend fun processPendingQueue(
        onActivityUpdate: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val project = projectDao.getActiveProject() ?: return@withContext 0

        val pendingItems = queueDao.getPendingItems(project.id)
        if (pendingItems.isEmpty()) return@withContext 0

        if (!isNetworkAvailable()) {
            Log.d(TAG, "Network unavailable, skipping queue processing")
            onActivityUpdate("Waiting for network connection...")
            return@withContext 0
        }

        val activeTarget = deploymentManager.getActiveTarget()
        var processedCount = 0

        for (rawItem in pendingItems) {
            // Check Emergency Stop or Pause State before each item
            val controlState = preferences.syncControlState.first()
            if (controlState == SyncControlState.EMERGENCY_STOPPED || controlState == SyncControlState.PAUSED) {
                Log.d(TAG, "Queue processor halted by user state: $controlState")
                onActivityUpdate("Halted: $controlState")
                break
            }

            // Exponential backoff cooldown check
            if (shouldWaitForBackoff(rawItem)) {
                Log.d(TAG, "Skipping ${rawItem.relativePath} (in backoff cooldown)")
                continue
            }

            // Ensure single active target binding
            val item = if (rawItem.targetProvider != activeTarget.name) {
                val rebound = rawItem.copy(targetProvider = activeTarget.name, target = activeTarget.name)
                queueDao.updateItem(rebound)
                rebound
            } else {
                rawItem
            }

            val startTime = System.currentTimeMillis()
            queueDao.updateStatus(item.id, "UPLOADING")
            onActivityUpdate("Processing ${item.relativePath}...")

            when (item.operation) {
                "UPLOAD" -> {
                    handleUploadOperation(project.id, item, startTime, activeTarget, onActivityUpdate)
                    processedCount++
                }
                "DELETE_FILE" -> {
                    handleDeleteOperation(project.id, item, startTime, activeTarget, onActivityUpdate)
                    processedCount++
                }
                "ROLLBACK" -> {
                    handleRollbackOperation(project.id, item, startTime, activeTarget, onActivityUpdate)
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
        targetType: DeploymentTargetType,
        onActivityUpdate: (String) -> Unit
    ) {
        val provider = deploymentManager.getProvider(targetType)

        if (!provider.isConfigured(projectId)) {
            val errorMsg = "${provider.displayName} is not configured"
            failOrRetry(item, errorMsg, projectId, "UPLOAD", startTime, targetType)
            return
        }

        val metadata = fileMetadataDao.getByPath(projectId, item.relativePath)
        val fileUri = Uri.parse(metadata?.optionalHash ?: "")
        val activeProj = projectDao.getActiveProject()
        val projectRootDir = StoragePathResolver.resolveToFile(context, activeProj?.folderUri)
        val localDiskFile = if (projectRootDir != null) File(projectRootDir, item.relativePath) else null

        val fileBytes = try {
            if (localDiskFile != null && localDiskFile.exists() && localDiskFile.canRead()) {
                localDiskFile.readBytes()
            } else if (fileUri != null && fileUri.scheme == "file") {
                val f = File(fileUri.path ?: "")
                if (f.exists() && f.canRead()) f.readBytes() else null
            } else if (fileUri != null && fileUri.toString().isNotEmpty()) {
                context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            } else null
        } catch (e: Exception) {
            null
        }

        if (fileBytes == null) {
            val errorMsg = "Local file stream unavailable"
            failOrRetry(item, errorMsg, projectId, "UPLOAD", startTime, targetType)
            return
        }

        // 1. Temporary Backup before overwriting modified file
        if (metadata != null && metadata.lastSyncedAt != null) {
            val retention = preferences.backupRetentionMinutes.first()
            if (fileUri != null && fileUri.toString().isNotEmpty()) {
                backupManager.createBackupBeforeReplace(projectId, item.relativePath, fileUri, retention)
            }
        }

        // 2. Deploy EXCLUSIVELY to the active target provider
        onActivityUpdate("Deploying to ${provider.displayName}: ${item.relativePath}")
        val uploadResult = provider.uploadFile(
            projectId = projectId,
            localStream = fileBytes.inputStream(),
            relativePath = item.relativePath,
            fileSize = fileBytes.size.toLong()
        )

        when (uploadResult) {
            is ProviderResult.Success -> {
                // Explicit transition: VERIFYING -> SUCCESS
                queueDao.updateStatus(item.id, "VERIFYING")
                onActivityUpdate("Verifying ${item.relativePath} on ${provider.displayName}...")

                val verification = uploadResult.data
                val isVerified = verification.verified
                val remoteSha = verification.remoteSha
                val now = System.currentTimeMillis()
                val duration = now - startTime

                queueDao.updateItem(
                    item.copy(
                        status = "SUCCESS",
                        verified = isVerified,
                        githubSha = remoteSha,
                        githubStatus = if (targetType == DeploymentTargetType.GITHUB) "SUCCESS" else "SKIPPED",
                        infinityFreeStatus = if (targetType == DeploymentTargetType.INFINITY_FREE) "SUCCESS" else "SKIPPED",
                        errorMessage = null,
                        lastAttemptAt = now
                    )
                )

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
                        githubLastSyncedAt = if (targetType == DeploymentTargetType.GITHUB) now else metadata?.githubLastSyncedAt,
                        infinityFreeLastSyncedAt = if (targetType == DeploymentTargetType.INFINITY_FREE) now else metadata?.infinityFreeLastSyncedAt,
                        githubSha = remoteSha ?: metadata?.githubSha,
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
                        githubResult = if (targetType == DeploymentTargetType.GITHUB) "SUCCESS" else "SKIPPED",
                        infinityFreeResult = if (targetType == DeploymentTargetType.INFINITY_FREE) "SUCCESS" else "SKIPPED",
                        errorMessage = "${provider.displayName}: ${verification.message}",
                        targetProvider = targetType.name,
                        retryCount = item.retryCount,
                        durationMs = duration,
                        errorCategory = "NONE",
                        verified = isVerified
                    )
                )

                when (targetType) {
                    DeploymentTargetType.INFINITY_FREE -> preferences.setLastInfinityFreeSyncTimestamp(now)
                    DeploymentTargetType.SHROTI_HOST -> preferences.setLastShrotiHostSyncTimestamp(now)
                    DeploymentTargetType.GITHUB -> preferences.setLastGitHubSyncTimestamp(now)
                }
                preferences.setLastSuccessfulSyncTimestamp(now)
            }
            is ProviderResult.Error -> {
                failOrRetry(item, uploadResult.message, projectId, "UPLOAD", startTime, targetType)

                // Optional automatic rollback after deployment failure if enabled in preferences
                if (preferences.isAutoRollbackEnabled.first()) {
                    triggerAutoRollback(projectId, item, targetType)
                }
            }
        }
    }

    private suspend fun handleDeleteOperation(
        projectId: Long,
        item: SyncQueueEntity,
        startTime: Long,
        targetType: DeploymentTargetType,
        onActivityUpdate: (String) -> Unit
    ) {
        val provider = deploymentManager.getProvider(targetType)

        if (!provider.isConfigured(projectId)) {
            val errorMsg = "${provider.displayName} is not configured"
            failOrRetry(item, errorMsg, projectId, "DELETE", startTime, targetType)
            return
        }

        onActivityUpdate("Deleting from ${provider.displayName}: ${item.relativePath}")
        val delResult = provider.deleteFile(projectId, item.relativePath)

        when (delResult) {
            is ProviderResult.Success -> {
                queueDao.updateStatus(item.id, "VERIFYING")
                onActivityUpdate("Verifying deletion on ${provider.displayName}: ${item.relativePath}")

                val verifyResult = provider.verifyDeletion(projectId, item.relativePath)
                val isVerified = when (verifyResult) {
                    is ProviderResult.Success -> verifyResult.data
                    is ProviderResult.Error -> false
                }

                val now = System.currentTimeMillis()
                val duration = now - startTime

                queueDao.updateItem(
                    item.copy(
                        status = "SUCCESS",
                        verified = isVerified,
                        githubStatus = if (targetType == DeploymentTargetType.GITHUB) "SUCCESS" else "SKIPPED",
                        infinityFreeStatus = if (targetType == DeploymentTargetType.INFINITY_FREE) "SUCCESS" else "SKIPPED",
                        lastAttemptAt = now
                    )
                )
                historyDao.insert(
                    SyncHistoryEntity(
                        projectId = projectId,
                        operation = "DELETE",
                        relativePath = item.relativePath,
                        startedAt = startTime,
                        completedAt = now,
                        result = "SUCCESS",
                        githubResult = if (targetType == DeploymentTargetType.GITHUB) "SUCCESS" else "SKIPPED",
                        infinityFreeResult = if (targetType == DeploymentTargetType.INFINITY_FREE) "SUCCESS" else "SKIPPED",
                        errorMessage = "${provider.displayName}: Deleted (Verified: $isVerified)",
                        targetProvider = targetType.name,
                        retryCount = item.retryCount,
                        durationMs = duration,
                        errorCategory = "NONE",
                        verified = isVerified
                    )
                )
            }
            is ProviderResult.Error -> {
                failOrRetry(item, delResult.message, projectId, "DELETE", startTime, targetType)
            }
        }
    }

    private suspend fun handleRollbackOperation(
        projectId: Long,
        item: SyncQueueEntity,
        startTime: Long,
        targetType: DeploymentTargetType,
        onActivityUpdate: (String) -> Unit
    ) {
        val provider = deploymentManager.getProvider(targetType)

        val backups = database.temporaryBackupDao().getAvailableBackups(projectId)
        val backup = if (item.backupId != null) {
            database.temporaryBackupDao().getBackupById(item.backupId)
        } else {
            backups.firstOrNull { it.relativePath == item.relativePath }
        }

        if (backup == null) {
            val err = "Backup record expired or missing"
            queueDao.updateStatus(item.id, "FAILED", err)
            val now = System.currentTimeMillis()
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "ROLLBACK",
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = now,
                    result = "FAILED",
                    errorMessage = err,
                    targetProvider = targetType.name,
                    retryCount = item.retryCount,
                    durationMs = now - startTime,
                    errorCategory = "NOT_FOUND",
                    verified = false
                )
            )
            return
        }

        val backupFile = backupManager.getBackupFile(backup)
        if (backupFile == null || !backupFile.exists()) {
            val err = "Backup file not found on disk"
            queueDao.updateStatus(item.id, "FAILED", err)
            val now = System.currentTimeMillis()
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "ROLLBACK",
                    relativePath = item.relativePath,
                    startedAt = startTime,
                    completedAt = now,
                    result = "FAILED",
                    errorMessage = err,
                    targetProvider = targetType.name,
                    retryCount = item.retryCount,
                    durationMs = now - startTime,
                    errorCategory = "NOT_FOUND",
                    verified = false
                )
            )
            return
        }

        val bytes = backupFile.readBytes()
        onActivityUpdate("Rolling back on ${provider.displayName}: ${item.relativePath}")
        val res = provider.uploadFile(projectId, bytes.inputStream(), item.relativePath, bytes.size.toLong())

        val now = System.currentTimeMillis()
        val duration = now - startTime

        when (res) {
            is ProviderResult.Success -> {
                val isVerified = res.data.verified
                queueDao.updateItem(
                    item.copy(
                        status = "ROLLED_BACK",
                        verified = isVerified,
                        lastAttemptAt = now
                    )
                )
                historyDao.insert(
                    SyncHistoryEntity(
                        projectId = projectId,
                        operation = "ROLLBACK",
                        relativePath = item.relativePath,
                        startedAt = startTime,
                        completedAt = now,
                        result = "SUCCESS",
                        errorMessage = "Rollback deployed to ${provider.displayName} (${backup.versionIdentifier})",
                        targetProvider = targetType.name,
                        retryCount = item.retryCount,
                        durationMs = duration,
                        errorCategory = "NONE",
                        rollbackInfo = backup.versionIdentifier,
                        verified = isVerified
                    )
                )
            }
            is ProviderResult.Error -> {
                val sanitized = sanitizeErrorMessage(res.message)
                queueDao.updateStatus(item.id, "FAILED", "Rollback failed on ${provider.displayName}: $sanitized")
                historyDao.insert(
                    SyncHistoryEntity(
                        projectId = projectId,
                        operation = "ROLLBACK",
                        relativePath = item.relativePath,
                        startedAt = startTime,
                        completedAt = now,
                        result = "FAILED",
                        errorMessage = "Rollback failed on ${provider.displayName}: $sanitized",
                        targetProvider = targetType.name,
                        retryCount = item.retryCount,
                        durationMs = duration,
                        errorCategory = categorizeError(res.message),
                        rollbackInfo = backup.versionIdentifier,
                        verified = false
                    )
                )
            }
        }
    }

    private suspend fun failOrRetry(
        item: SyncQueueEntity,
        rawErrorMessage: String,
        projectId: Long,
        operation: String,
        startTime: Long,
        targetType: DeploymentTargetType
    ) {
        val sanitizedError = sanitizeErrorMessage(rawErrorMessage)
        val errorCat = categorizeError(rawErrorMessage)
        val nextRetry = item.retryCount + 1
        val isRetrying = nextRetry < MAX_RETRIES
        val newStatus = if (isRetrying) "RETRYING" else "FAILED"
        val now = System.currentTimeMillis()
        val duration = now - startTime

        val updated = item.copy(
            retryCount = nextRetry,
            status = newStatus,
            errorMessage = sanitizedError,
            lastAttemptAt = now
        )
        queueDao.updateItem(updated)
        historyDao.insert(
            SyncHistoryEntity(
                projectId = projectId,
                operation = operation,
                relativePath = item.relativePath,
                startedAt = startTime,
                completedAt = now,
                result = "FAILED",
                githubResult = if (targetType == DeploymentTargetType.GITHUB) "FAILED" else "SKIPPED",
                infinityFreeResult = if (targetType == DeploymentTargetType.INFINITY_FREE) "FAILED" else "SKIPPED",
                errorMessage = if (isRetrying) {
                    "Attempt $nextRetry/$MAX_RETRIES failed on ${targetType.name}: $sanitizedError"
                } else {
                    "Exceeded max retries ($MAX_RETRIES) on ${targetType.name}: $sanitizedError"
                },
                targetProvider = targetType.name,
                retryCount = nextRetry,
                durationMs = duration,
                errorCategory = errorCat,
                verified = false
            )
        )
    }

    private suspend fun triggerAutoRollback(
        projectId: Long,
        failedItem: SyncQueueEntity,
        targetType: DeploymentTargetType
    ) {
        // Infinite Loop Prevention: Never auto-rollback an operation that was already a ROLLBACK
        if (failedItem.operation == "ROLLBACK") {
            Log.w(TAG, "Prevented infinite rollback loop: item ${failedItem.relativePath} is already a ROLLBACK operation.")
            return
        }

        // Avoid duplicate active rollback queue entries
        val activeItem = queueDao.getActiveItemByPath(projectId, failedItem.relativePath)
        if (activeItem != null && activeItem.operation == "ROLLBACK") {
            Log.d(TAG, "Rollback already active for ${failedItem.relativePath}, skipping duplicate trigger")
            return
        }

        val backups = database.temporaryBackupDao().getAvailableBackups(projectId)
        val backup = backups.firstOrNull { it.relativePath == failedItem.relativePath }
        if (backup != null) {
            Log.i(TAG, "Triggering automatic rollback for ${failedItem.relativePath} after failure on target ${targetType.name}")
            queueDao.insertItem(
                SyncQueueEntity(
                    projectId = projectId,
                    relativePath = failedItem.relativePath,
                    operation = "ROLLBACK",
                    status = "PENDING",
                    target = targetType.name,
                    targetProvider = targetType.name,
                    backupId = backup.id
                )
            )
        } else {
            Log.w(TAG, "No backup found to auto-rollback for ${failedItem.relativePath}")
        }
    }
}

