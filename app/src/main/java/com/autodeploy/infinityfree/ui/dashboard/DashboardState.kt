package com.autodeploy.infinityfree.ui.dashboard

data class DashboardState(
    val isAutoSyncOn: Boolean = false,
    val projectName: String? = null,
    val projectFolderUri: String? = null,
    val hostingConnectionName: String? = null,
    val hostingServer: String? = null,
    val isHostingConfigured: Boolean = false,
    val hostingStatus: String = "Not Configured", // Connected, Not Connected, Error, Testing...
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val lastScanTime: Long = 0L,
    val lastSuccessfulSyncTime: Long = 0L,
    val currentActivity: String = "Idle",
    val pendingQueueCount: Int = 0,
    val failedQueueCount: Int = 0,
    val activeBackupCount: Int = 0,
    val isSyncingNow: Boolean = false,
    val userMessage: String? = null
)
