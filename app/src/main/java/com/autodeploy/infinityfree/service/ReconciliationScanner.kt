package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.ignore.IgnoreRuleMatcher
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.saf.SafFileItem
import com.autodeploy.infinityfree.data.saf.SafScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ReconciliationScanner(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val safScanner: SafScanner,
    private val stabilityTracker: FileStabilityTracker
) {
    companion object {
        private const val TAG = "ReconciliationScanner"
    }

    private val fileMetadataDao = database.fileMetadataDao()
    private val syncQueueDao = database.syncQueueDao()

    suspend fun performScan(
        projectId: Long,
        folderUri: Uri,
        forceAllAsPending: Boolean = false,
        onStatusUpdate: (String) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        onStatusUpdate("Scanning files...")
        val scannedItems = try {
            safScanner.scanDirectory(folderUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory $folderUri", e)
            onStatusUpdate("Scan error: ${e.localizedMessage}")
            return@withContext 0
        }

        val customPatterns = preferences.customIgnorePatterns.first()
        val ignoreMatcher = IgnoreRuleMatcher(customPatterns)

        val debounceSeconds = preferences.debounceDurationSeconds.first()
        val debounceMillis = debounceSeconds * 1000L
        val syncDeletions = preferences.syncDeletions.first()

        val existingRecords = fileMetadataDao.getAllForProject(projectId).associateBy { it.relativePath }
        val scannedPaths = scannedItems.map { it.relativePath }.toSet()

        var changesCount = 0

        for (item in scannedItems) {
            // Apply Ignore Rules (.gitignore and custom patterns)
            if (ignoreMatcher.isIgnored(item.relativePath)) {
                continue
            }

            if (item.isDirectory) {
                fileMetadataDao.insertOrUpdate(
                    FileMetadataEntity(
                        projectId = projectId,
                        relativePath = item.relativePath,
                        itemType = "DIRECTORY",
                        fileSize = 0L,
                        lastModified = item.lastModified,
                        syncStatus = "SYNCED",
                        isPresent = true
                    )
                )
                continue
            }

            val existing = existingRecords[item.relativePath]

            if (existing == null) {
                // New file
                stabilityTracker.recordObservation(item.relativePath, item.size, item.lastModified)
                val isStable = forceAllAsPending || stabilityTracker.isStable(item.relativePath, debounceMillis)

                if (isStable) {
                    enqueueUpload(projectId, item, "NOT_SYNCED")
                    changesCount++
                } else {
                    fileMetadataDao.insertOrUpdate(
                        FileMetadataEntity(
                            projectId = projectId,
                            relativePath = item.relativePath,
                            itemType = "FILE",
                            fileSize = item.size,
                            lastModified = item.lastModified,
                            syncStatus = "PENDING",
                            optionalHash = item.uri.toString(),
                            isPresent = true
                        )
                    )
                }
            } else {
                // Modified file comparison
                val sizeChanged = existing.fileSize != item.size
                val modifiedChanged = item.lastModified > existing.lastModified

                if (forceAllAsPending || sizeChanged || modifiedChanged) {
                    stabilityTracker.recordObservation(item.relativePath, item.size, item.lastModified)
                    val isStable = forceAllAsPending || stabilityTracker.isStable(item.relativePath, debounceMillis)

                    if (isStable) {
                        enqueueUpload(projectId, item, "MODIFIED")
                        changesCount++
                    } else {
                        fileMetadataDao.insertOrUpdate(
                            existing.copy(
                                fileSize = item.size,
                                lastModified = item.lastModified,
                                syncStatus = "PENDING",
                                optionalHash = item.uri.toString(),
                                isPresent = true
                            )
                        )
                    }
                } else {
                    if (!existing.isPresent) {
                        fileMetadataDao.insertOrUpdate(existing.copy(isPresent = true))
                    }
                }
            }
        }

        // Deletions
        for ((path, record) in existingRecords) {
            if (record.itemType == "FILE" && record.isPresent && !scannedPaths.contains(path)) {
                if (!ignoreMatcher.isIgnored(path)) {
                    fileMetadataDao.insertOrUpdate(record.copy(isPresent = false, syncStatus = "DELETED"))
                    if (syncDeletions) {
                        enqueueDelete(projectId, path)
                        changesCount++
                    }
                }
            }
        }

        preferences.setLastScanTimestamp(System.currentTimeMillis())
        onStatusUpdate("Scan complete. $changesCount pending.")
        changesCount
    }

    private suspend fun enqueueUpload(projectId: Long, item: SafFileItem, syncStatus: String) {
        val activeQueueItem = syncQueueDao.getActiveItemByPath(projectId, item.relativePath)
        if (activeQueueItem == null) {
            syncQueueDao.insertItem(
                SyncQueueEntity(
                    projectId = projectId,
                    relativePath = item.relativePath,
                    operation = "UPLOAD",
                    status = "PENDING",
                    retryCount = 0
                )
            )
        } else {
            syncQueueDao.updateItem(
                activeQueueItem.copy(
                    status = "PENDING",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        fileMetadataDao.insertOrUpdate(
            FileMetadataEntity(
                projectId = projectId,
                relativePath = item.relativePath,
                itemType = "FILE",
                fileSize = item.size,
                lastModified = item.lastModified,
                syncStatus = syncStatus,
                optionalHash = item.uri.toString(),
                isPresent = true
            )
        )
        stabilityTracker.remove(item.relativePath)
    }

    private suspend fun enqueueDelete(projectId: Long, relativePath: String) {
        val activeQueueItem = syncQueueDao.getActiveItemByPath(projectId, relativePath)
        if (activeQueueItem == null) {
            syncQueueDao.insertItem(
                SyncQueueEntity(
                    projectId = projectId,
                    relativePath = relativePath,
                    operation = "DELETE_FILE",
                    status = "PENDING",
                    retryCount = 0
                )
            )
        }
    }
}
