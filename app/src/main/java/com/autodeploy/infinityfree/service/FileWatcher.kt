package com.autodeploy.infinityfree.service

import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface FileWatcher {
    val healthState: StateFlow<WatcherHealthState>
    val monitoredDirectoriesCount: StateFlow<Int>
    val monitoredFilesCount: StateFlow<Int>

    fun startWatching(projectRoot: File, onEvent: (FileSystemEvent) -> Unit): Boolean
    fun stopWatching()
    fun restartWatching(projectRoot: File, onEvent: (FileSystemEvent) -> Unit): Boolean
}
