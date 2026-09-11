package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.BackupSnapshotEntity
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.saf.SafScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val safScanner: SafScanner,
    private val preferences: AppPreferences
) {
    companion object {
        private const val TAG = "BackupManager"
    }

    private val temporaryBackupDao = database.temporaryBackupDao()
    private val snapshotDao = database.backupSnapshotDao()
    private val historyDao = database.syncHistoryDao()

    // --- Temporary File-Level Backups (1-hour safety net) ---

    suspend fun createBackupBeforeReplace(
        projectId: Long,
        relativePath: String,
        sourceUri: Uri,
        retentionMinutes: Int = 60
    ): TemporaryBackupEntity? = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.filesDir, "backups/$projectId").apply { mkdirs() }
            val cleanName = relativePath.replace('/', '_').replace('\\', '_')
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "${timestamp}_${cleanName}")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val versionId = "v_${timestamp}_" + UUID.randomUUID().toString().take(6)
            val expiresAt = timestamp + (retentionMinutes * 60 * 1000L)

            val backupEntity = TemporaryBackupEntity(
                projectId = projectId,
                relativePath = relativePath,
                backupPath = backupFile.absolutePath,
                createdAt = timestamp,
                expiresAt = expiresAt,
                versionIdentifier = versionId,
                status = "AVAILABLE"
            )

            val id = temporaryBackupDao.insertBackup(backupEntity)
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "BACKUP",
                    relativePath = relativePath,
                    result = "SUCCESS",
                    errorMessage = "Version $versionId backed up (Expires in ${retentionMinutes}m)"
                )
            )

            backupEntity.copy(id = id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temporary backup for $relativePath", e)
            null
        }
    }

    suspend fun cleanupExpiredBackups() = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val expired = temporaryBackupDao.getExpiredBackups(now)
            for (backup in expired) {
                try {
                    val file = File(backup.backupPath)
                    if (file.exists()) file.delete()
                    temporaryBackupDao.markExpired(backup.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning backup file ${backup.backupPath}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup expired backups failed", e)
        }
    }

    fun getBackupFile(backup: TemporaryBackupEntity): File? {
        val file = File(backup.backupPath)
        return if (file.exists()) file else null
    }

    // --- Versioned Project Snapshots (Schema v3) ---

    suspend fun createPreDeploymentSnapshot(
        projectId: Long,
        rootFolderUri: Uri,
        label: String = "Pre-deployment Snapshot"
    ): BackupSnapshotEntity? = withContext(Dispatchers.IO) {
        createSnapshotInternal(projectId, rootFolderUri, label, isStable = false, status = "AVAILABLE")
    }

    suspend fun createManualSnapshot(
        projectId: Long,
        rootFolderUri: Uri,
        label: String = "Manual Snapshot"
    ): BackupSnapshotEntity? = withContext(Dispatchers.IO) {
        createSnapshotInternal(projectId, rootFolderUri, label, isStable = false, status = "AVAILABLE")
    }

    private suspend fun createSnapshotInternal(
        projectId: Long,
        rootFolderUri: Uri,
        label: String,
        isStable: Boolean = false,
        status: String = "AVAILABLE"
    ): BackupSnapshotEntity? {
        val scannedFiles = try {
            safScanner.scanDirectory(rootFolderUri).filter { !it.isDirectory }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan folder for snapshot", e)
            return null
        }

        if (scannedFiles.isEmpty()) {
            Log.w(TAG, "No files found to snapshot")
            return null
        }

        val versionCode = snapshotDao.getNextVersionCode(projectId)
        val versionTag = "v%03d".format(versionCode)
        val snapshotDir = File(context.filesDir, "snapshots/$projectId/$versionTag")
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs()
        }

        val manifestArray = JSONArray()
        var totalSize = 0L

        for (fileItem in scannedFiles) {
            try {
                val targetFile = File(snapshotDir, fileItem.relativePath)
                targetFile.parentFile?.mkdirs()

                context.contentResolver.openInputStream(fileItem.uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists()) {
                    val sha256 = calculateSha256(targetFile)
                    val fileSize = targetFile.length()
                    totalSize += fileSize

                    val fileObj = JSONObject().apply {
                        put("path", fileItem.relativePath)
                        put("size", fileSize)
                        put("sha256", sha256)
                    }
                    manifestArray.put(fileObj)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy ${fileItem.relativePath} into snapshot", e)
            }
        }

        val snapshotEntity = BackupSnapshotEntity(
            projectId = projectId,
            versionCode = versionCode,
            versionTag = versionTag,
            label = label,
            snapshotDirectory = snapshotDir.absolutePath,
            fileCount = manifestArray.length(),
            totalSizeBytes = totalSize,
            manifestJson = manifestArray.toString(),
            isStable = isStable,
            status = status,
            createdAt = System.currentTimeMillis()
        )

        val id = snapshotDao.insertSnapshot(snapshotEntity)
        enforceRetention(projectId)

        historyDao.insert(
            SyncHistoryEntity(
                projectId = projectId,
                operation = "SNAPSHOT",
                relativePath = versionTag,
                result = "SUCCESS",
                errorMessage = "$label ($versionTag: ${manifestArray.length()} files, ${totalSize / 1024} KB)"
            )
        )

        return snapshotEntity.copy(id = id)
    }

    suspend fun restoreSnapshot(
        projectId: Long,
        snapshotId: Long,
        rootFolderUri: Uri,
        createEmergencyBackup: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val snapshot = snapshotDao.getSnapshotById(snapshotId) ?: return@withContext false
        val snapDir = File(snapshot.snapshotDirectory)
        if (!snapDir.exists() || !snapDir.isDirectory) {
            Log.e(TAG, "Snapshot directory ${snapshot.snapshotDirectory} does not exist")
            return@withContext false
        }

        // 1. Verify snapshot integrity before restoring
        if (!verifySnapshotIntegrity(snapshot)) {
            Log.e(TAG, "Snapshot ${snapshot.versionTag} failed manifest integrity check before restore")
            return@withContext false
        }

        try {
            // 2. Emergency snapshot of current local state before performing rollback
            if (createEmergencyBackup) {
                createSnapshotInternal(
                    projectId = projectId,
                    rootFolderUri = rootFolderUri,
                    label = "Emergency Pre-Rollback (before restoring ${snapshot.versionTag})",
                    isStable = false,
                    status = "EMERGENCY"
                )
            }

            // 3. Restore files from snapshot to SAF folder
            val manifest = JSONArray(snapshot.manifestJson)
            val manifestPaths = mutableSetOf<String>()
            val rootDoc = DocumentFile.fromTreeUri(context, rootFolderUri)
                ?: return@withContext false

            for (i in 0 until manifest.length()) {
                val item = manifest.getJSONObject(i)
                val relativePath = item.getString("path")
                manifestPaths.add(relativePath)
                val sourceFile = File(snapDir, relativePath)
                if (!sourceFile.exists()) continue

                val targetDoc = findOrCreateDocumentFile(rootDoc, relativePath)
                if (targetDoc != null) {
                    context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // 4. Clean up post-snapshot orphaned files (files created after snapshot was taken)
            try {
                val currentFiles = safScanner.scanDirectory(rootFolderUri).filter { !it.isDirectory }
                for (currentFile in currentFiles) {
                    if (!manifestPaths.contains(currentFile.relativePath)) {
                        val fileDoc = DocumentFile.fromSingleUri(context, currentFile.uri)
                        fileDoc?.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Warning cleaning up post-snapshot orphaned files", e)
            }

            // 5. Mark snapshot status as RESTORED
            snapshotDao.updateStatus(snapshot.id, "RESTORED")

            val now = System.currentTimeMillis()
            historyDao.insert(
                SyncHistoryEntity(
                    projectId = projectId,
                    operation = "ROLLBACK",
                    relativePath = snapshot.versionTag,
                    startedAt = now,
                    completedAt = now,
                    result = "SUCCESS",
                    errorMessage = "Successfully rolled back to snapshot ${snapshot.versionTag} (${snapshot.label})",
                    targetProvider = "LOCAL",
                    errorCategory = "NONE",
                    rollbackInfo = snapshot.versionTag,
                    verified = true
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring snapshot ${snapshot.versionTag}", e)
            false
        }
    }

    fun verifySnapshotIntegrity(snapshot: BackupSnapshotEntity): Boolean {
        val baseDir = File(snapshot.snapshotDirectory)
        if (!baseDir.exists() || !baseDir.isDirectory) return false
        return try {
            val manifest = JSONArray(snapshot.manifestJson)
            for (i in 0 until manifest.length()) {
                val item = manifest.getJSONObject(i)
                val path = item.getString("path")
                val expectedSize = item.getLong("size")
                val expectedSha256 = item.getString("sha256")

                val file = File(baseDir, path)
                if (!file.exists() || file.length() != expectedSize) {
                    return false
                }
                val actualSha256 = calculateSha256(file)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Integrity verification error for snapshot ${snapshot.versionTag}", e)
            false
        }
    }

    suspend fun enforceRetention(projectId: Long) = withContext(Dispatchers.IO) {
        try {
            val retentionCount = preferences.backupRetentionCount.first()
            // Retention count <= 0 means unlimited retention
            if (retentionCount <= 0) return@withContext

            val allSnapshots = snapshotDao.getSnapshots(projectId)
            if (allSnapshots.size <= retentionCount) return@withContext

            // Only delete unstable snapshots (is_stable = false)
            val unstables = snapshotDao.getUnstableSnapshotsAscending(projectId)
            val excess = allSnapshots.size - retentionCount
            val toDelete = unstables.take(excess)

            for (snap in toDelete) {
                try {
                    val dir = File(snap.snapshotDirectory)
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                    snapshotDao.deleteSnapshot(snap.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete old snapshot ${snap.versionTag}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot retention enforcement failed", e)
        }
    }

    suspend fun markSnapshotStable(snapshotId: Long, isStable: Boolean) = withContext(Dispatchers.IO) {
        snapshotDao.setStable(snapshotId, isStable)
    }

    suspend fun getSnapshotById(id: Long): BackupSnapshotEntity? = withContext(Dispatchers.IO) {
        snapshotDao.getSnapshotById(id)
    }

    private fun findOrCreateDocumentFile(
        root: DocumentFile,
        relativePath: String
    ): DocumentFile? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        var current = root
        for (i in 0 until segments.size - 1) {
            val dirName = segments[i]
            val existing = current.findFile(dirName)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                current.createDirectory(dirName) ?: return null
            }
        }
        val fileName = segments.last()
        return current.findFile(fileName) ?: current.createFile("application/octet-stream", fileName)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

