package com.autodeploy.infinityfree.service

import java.util.concurrent.ConcurrentHashMap

data class TrackedFileInfo(
    val relativePath: String,
    var lastKnownSize: Long,
    var lastKnownModified: Long,
    var firstObservedTime: Long,
    var lastChangedTime: Long
)

class FileStabilityTracker {

    private val trackedFiles = ConcurrentHashMap<String, TrackedFileInfo>()

    fun recordObservation(relativePath: String, size: Long, lastModified: Long): Boolean {
        val now = System.currentTimeMillis()
        val existing = trackedFiles[relativePath]

        if (existing == null) {
            trackedFiles[relativePath] = TrackedFileInfo(
                relativePath = relativePath,
                lastKnownSize = size,
                lastKnownModified = lastModified,
                firstObservedTime = now,
                lastChangedTime = now
            )
            return false
        } else {
            if (existing.lastKnownSize != size || existing.lastKnownModified != lastModified) {
                // File is still being actively written or modified
                existing.lastKnownSize = size
                existing.lastKnownModified = lastModified
                existing.lastChangedTime = now
                return false
            } else {
                // File content metadata is identical between observations
                return true
            }
        }
    }

    fun isStable(relativePath: String, debounceDurationMillis: Long): Boolean {
        val info = trackedFiles[relativePath] ?: return false
        val now = System.currentTimeMillis()
        val quietPeriod = now - info.lastChangedTime
        return quietPeriod >= debounceDurationMillis
    }

    fun remove(relativePath: String) {
        trackedFiles.remove(relativePath)
    }

    fun clear() {
        trackedFiles.clear()
    }
}
