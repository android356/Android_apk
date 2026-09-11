package com.autodeploy.infinityfree.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class TrackedFileInfo(
    val relativePath: String,
    var lastKnownSize: Long,
    var lastKnownModified: Long,
    var firstObservedTime: Long,
    var lastChangedTime: Long,
    var closeWriteReceived: Boolean = false,
    var lastEventMask: Int = 0
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

    fun recordEvent(relativePath: String, eventMask: Int) {
        val now = System.currentTimeMillis()
        trackedFiles.compute(relativePath) { _, existing ->
            val info = existing ?: TrackedFileInfo(
                relativePath = relativePath,
                lastKnownSize = 0L,
                lastKnownModified = 0L,
                firstObservedTime = now,
                lastChangedTime = now
            )
            info.lastChangedTime = now
            info.lastEventMask = info.lastEventMask or eventMask
            if ((eventMask and 8) != 0) { // 8 is FileObserver.CLOSE_WRITE
                info.closeWriteReceived = true
            }
            info
        }
    }

    fun isCloseWriteReceived(relativePath: String): Boolean {
        return trackedFiles[relativePath]?.closeWriteReceived ?: false
    }

    fun isStable(relativePath: String, debounceDurationMillis: Long): Boolean {
        val info = trackedFiles[relativePath] ?: return false
        val now = System.currentTimeMillis()
        val quietPeriod = now - info.lastChangedTime
        return quietPeriod >= debounceDurationMillis
    }

    /**
     * Checks if a local file is accessible and stable (not currently in the middle of being written).
     * Samples file size and modification timestamp across a small delay.
     */
    fun checkFileStability(file: File, sampleDelayMs: Long = 50L): Boolean {
        if (!file.exists() || !file.canRead()) return false

        val initialSize = file.length()
        val initialModified = file.lastModified()

        if (sampleDelayMs > 0) {
            try {
                Thread.sleep(sampleDelayMs)
            } catch (ignored: InterruptedException) {}
        }

        if (!file.exists() || !file.canRead()) return false

        val sampleSize = file.length()
        val sampleModified = file.lastModified()

        return initialSize == sampleSize && initialModified == sampleModified
    }

    fun remove(relativePath: String) {
        trackedFiles.remove(relativePath)
    }

    fun clear() {
        trackedFiles.clear()
    }
}
