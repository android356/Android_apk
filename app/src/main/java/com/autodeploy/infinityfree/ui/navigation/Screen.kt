package com.autodeploy.infinityfree.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object FolderPicker : Screen("folder_picker", "Select Project Folder")
    data object GitHubSetup : Screen("github_setup", "GitHub Connection")
    data object HostingSetup : Screen("hosting_setup", "Hosting Connection")
    data object DeploymentMapping : Screen("deployment_mapping", "Deployment Mapping")
    data object SyncSettings : Screen("sync_settings", "Sync Settings")
    data object ActivityLog : Screen("activity_log", "Activity Log")
    data object QueueManager : Screen("queue_manager", "Sync Queue")
    data object Backups : Screen("backups", "Temporary Backups")
}
