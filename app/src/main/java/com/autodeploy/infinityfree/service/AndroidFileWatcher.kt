package com.autodeploy.infinityfree.service

import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AndroidFileWatcher : FileWatcher {

    companion object {
        private const val TAG = "AndroidFileWatcher"

        const val WATCH_EVENTS_MASK = FileObserver.CREATE or
                FileObserver.MODIFY or
                FileObserver.CLOSE_WRITE or
                FileObserver.DELETE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.ATTRIB or
                FileObserver.DELETE_SELF or
                FileObserver.MOVE_SELF

        private const val IN_ISDIR = 0x40000000
    }

    private val _healthState = MutableStateFlow(WatcherHealthState.WATCHER_STOPPED)
    override val healthState: StateFlow<WatcherHealthState> = _healthState.asStateFlow()

    private val _monitoredDirectoriesCount = MutableStateFlow(0)
    override val monitoredDirectoriesCount: StateFlow<Int> = _monitoredDirectoriesCount.asStateFlow()

    private val _monitoredFilesCount = MutableStateFlow(0)
    override val monitoredFilesCount: StateFlow<Int> = _monitoredFilesCount.asStateFlow()

    private val activeObservers = ConcurrentHashMap<String, SingleDirectoryObserver>()
    private var rootDirectory: File? = null
    private var eventListener: ((FileSystemEvent) -> Unit)? = null

    @Suppress("DEPRECATION")
    private inner class SingleDirectoryObserver(
        val directory: File,
        val relativeDir: String
    ) : FileObserver(directory.absolutePath, WATCH_EVENTS_MASK) {

        override fun onEvent(event: Int, path: String?) {
            try {
                handleDirectoryEvent(event, path, directory, relativeDir)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling FileObserver event in ${directory.absolutePath}", e)
                _healthState.value = WatcherHealthState.WATCHER_DEGRADED
            }
        }
    }

    @Synchronized
    override fun startWatching(projectRoot: File, onEvent: (FileSystemEvent) -> Unit): Boolean {
        stopWatching()

        if (!projectRoot.exists() || !projectRoot.isDirectory || !projectRoot.canRead()) {
            Log.e(TAG, "Cannot watch directory: ${projectRoot.absolutePath} (exists=${projectRoot.exists()}, isDir=${projectRoot.isDirectory}, canRead=${projectRoot.canRead()})")
            _healthState.value = WatcherHealthState.WATCHER_DEGRADED
            return false
        }

        this.rootDirectory = projectRoot
        this.eventListener = onEvent

        try {
            registerDirectoryRecursively(projectRoot, projectRoot)
            _monitoredDirectoriesCount.value = activeObservers.size
            _monitoredFilesCount.value = countFiles(projectRoot)
            _healthState.value = WatcherHealthState.WATCHER_RUNNING
            Log.i(TAG, "Started recursive watcher on ${projectRoot.absolutePath} (${activeObservers.size} dirs, ${_monitoredFilesCount.value} files)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize directory watchers", e)
            _healthState.value = WatcherHealthState.WATCHER_DEGRADED
            return false
        }
    }

    @Synchronized
    override fun stopWatching() {
        for ((_, observer) in activeObservers) {
            try {
                observer.stopWatching()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping observer for ${observer.directory.absolutePath}", e)
            }
        }
        activeObservers.clear()
        _monitoredDirectoriesCount.value = 0
        _monitoredFilesCount.value = 0
        _healthState.value = WatcherHealthState.WATCHER_STOPPED
        Log.i(TAG, "Stopped all directory watchers")
    }

    @Synchronized
    override fun restartWatching(projectRoot: File, onEvent: (FileSystemEvent) -> Unit): Boolean {
        _healthState.value = WatcherHealthState.WATCHER_RESTARTING
        stopWatching()
        return startWatching(projectRoot, onEvent)
    }

    private fun registerDirectoryRecursively(dir: File, root: File) {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return

        val relDir = if (dir.absolutePath == root.absolutePath) {
            ""
        } else {
            dir.toRelativeString(root).replace('\\', '/')
        }

        if (!activeObservers.containsKey(dir.absolutePath)) {
            val observer = SingleDirectoryObserver(dir, relDir)
            observer.startWatching()
            activeObservers[dir.absolutePath] = observer
        }

        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".git")) {
                registerDirectoryRecursively(child, root)
            }
        }
    }

    private fun handleDirectoryEvent(event: Int, childName: String?, dir: File, relDir: String) {
        val root = rootDirectory ?: return
        val listener = eventListener ?: return

        val pureEvent = event and 0x00000FFF
        val isDirectory = (event and IN_ISDIR) != 0

        // Handle deletion or movement of the directory itself
        if (childName.isNullOrEmpty()) {
            if ((pureEvent and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF)) != 0) {
                unregisterDirectorySubtree(dir.absolutePath)
                val relPath = if (relDir.isEmpty()) "" else relDir
                listener(
                    FileSystemEvent(
                        relativePath = relPath,
                        absolutePath = dir.absolutePath,
                        eventMask = pureEvent,
                        isDirectory = true
                    )
                )
                _monitoredDirectoriesCount.value = activeObservers.size
                _monitoredFilesCount.value = countFiles(root)
            }
            return
        }

        val childFile = File(dir, childName)
        val childRelPath = if (relDir.isEmpty()) childName else "$relDir/$childName"

        // Handle directory creation, rename, or deletion
        if (isDirectory || childFile.isDirectory) {
            if ((pureEvent and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0) {
                // New directory created or moved in -> dynamically register watchers recursively!
                registerDirectoryRecursively(childFile, root)
                _monitoredDirectoriesCount.value = activeObservers.size
                _monitoredFilesCount.value = countFiles(root)
                listener(
                    FileSystemEvent(
                        relativePath = childRelPath,
                        absolutePath = childFile.absolutePath,
                        eventMask = pureEvent,
                        isDirectory = true
                    )
                )
                return
            } else if ((pureEvent and (FileObserver.DELETE or FileObserver.MOVED_FROM)) != 0) {
                // Directory deleted or moved away -> unregister its watchers safely
                unregisterDirectorySubtree(childFile.absolutePath)
                _monitoredDirectoriesCount.value = activeObservers.size
                _monitoredFilesCount.value = countFiles(root)
                listener(
                    FileSystemEvent(
                        relativePath = childRelPath,
                        absolutePath = childFile.absolutePath,
                        eventMask = pureEvent,
                        isDirectory = true
                    )
                )
                return
            }
        }

        // File event
        listener(
            FileSystemEvent(
                relativePath = childRelPath,
                absolutePath = childFile.absolutePath,
                eventMask = pureEvent,
                isDirectory = false
            )
        )
        _monitoredFilesCount.value = countFiles(root)
    }

    private fun unregisterDirectorySubtree(dirPath: String) {
        val keysToRemove = activeObservers.keys.filter { it == dirPath || it.startsWith("$dirPath/") }
        for (key in keysToRemove) {
            try {
                activeObservers.remove(key)?.stopWatching()
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering observer for $key", e)
            }
        }
    }

    private fun countFiles(root: File): Int {
        if (!root.exists() || !root.isDirectory) return 0
        var count = 0
        val queue = ArrayDeque<File>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (!child.name.startsWith(".git")) {
                        queue.add(child)
                    }
                } else if (child.isFile) {
                    count++
                }
            }
        }
        return count
    }
}
