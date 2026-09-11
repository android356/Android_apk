package com.autodeploy.infinityfree.service

enum class WatcherHealthState(val displayName: String) {
    WATCHER_RUNNING("Monitoring"),
    WATCHER_DEGRADED("Needs recovery"),
    WATCHER_RESTARTING("Restarting..."),
    WATCHER_STOPPED("Stopped")
}
