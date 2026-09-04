package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "BackupManager"
    }

    private val backupDao = database.temporaryBackupDao()
    private val historyDao = database.syncHistoryDao()

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

            val id = backupDao.insertBackup(backupEntity)
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
            val expired = backupDao.getExpiredBackups(now)
            for (backup in expired) {
                try {
                    val file = File(backup.backupPath)
                    if (file.exists()) file.delete()
                    backupDao.markExpired(backup.id)
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
}
