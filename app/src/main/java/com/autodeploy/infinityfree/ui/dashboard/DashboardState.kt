package com.autodeploy.infinityfree.ui.dashboard

import com.autodeploy.infinityfree.data.preferences.SyncControlState

data class DashboardState(
    val isAutoSyncOn: Boolean = false,
    val syncControlState: SyncControlState = SyncControlState.STOPPED,
    val projectName: String? = null,
    val projectFolderUri: String? = null,

    // GitHub Connection
    val isGitHubConfigured: Boolean = false,
    val gitHubOwner: String? = null,
    val gitHubRepo: String? = null,
    val gitHubBranch: String? = null,
    val gitHubPath: String? = null,
    val gitHubStatus: String = "Not Configured",

    // InfinityFree Connection
    val isHostingConfigured: Boolean = false,
    val hostingConnectionName: String? = null,
    val hostingServer: String? = null,
    val hostingStatus: String = "Not Configured",

    // Metrics
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val lastScanTime: Long = 0L,
    val lastSuccessfulSyncTime: Long = 0L,
    val lastGitHubSyncTime: Long = 0L,
    val lastInfinityFreeSyncTime: Long = 0L,
    val currentActivity: String = "Idle",
    val syncProgressText: String = "Idle",
    val pendingQueueCount: Int = 0,
    val failedQueueCount: Int = 0,
    val conflictCount: Int = 0,
    val activeBackupCount: Int = 0,
    val isSyncingNow: Boolean = false,
    val userMessage: String? = null
)
