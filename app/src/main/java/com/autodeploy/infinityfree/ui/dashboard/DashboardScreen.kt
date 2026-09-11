package com.autodeploy.infinityfree.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import com.autodeploy.infinityfree.data.preferences.SyncControlState
import com.autodeploy.infinityfree.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToFolderPicker: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onNavigateToHostingSetup: () -> Unit,
    onNavigateToMapping: () -> Unit,
    onNavigateToSyncSettings: () -> Unit,
    onNavigateToActivityLog: () -> Unit,
    onNavigateToQueueManager: () -> Unit,
    onNavigateToBackups: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val isEmergencyStopped = state.syncControlState == SyncControlState.EMERGENCY_STOPPED
    val isPaused = state.syncControlState == SyncControlState.PAUSED
    val isActive = state.syncControlState == SyncControlState.ACTIVE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Android Auto Deploy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            when (state.syncControlState) {
                                SyncControlState.ACTIVE -> "AUTO ON"
                                SyncControlState.PAUSED -> "PAUSED"
                                SyncControlState.EMERGENCY_STOPPED -> "STOPPED"
                                else -> "AUTO OFF"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (state.syncControlState) {
                                SyncControlState.ACTIVE -> SuccessGreen
                                SyncControlState.PAUSED -> WarningAmber
                                SyncControlState.EMERGENCY_STOPPED -> ErrorRed
                                else -> TextSecondary
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isActive,
                            onCheckedChange = { viewModel.toggleAutoSync(it) },
                            enabled = !isEmergencyStopped,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Emergency Alert Banner if Emergency Stopped
            if (isEmergencyStopped) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorBg)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EMERGENCY STOP ACTIVE", fontWeight = FontWeight.Bold, color = ErrorRed)
                            Text("All synchronization is forcefully halted. Resume below when safe.", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                        }
                        Button(
                            onClick = { viewModel.resumeSync() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Resume", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Current Activity Status Banner
            ActivityBanner(activity = state.currentActivity, controlState = state.syncControlState)

            // Local Project Card
            ProjectInfoCard(
                projectName = state.projectName,
                folderUri = state.projectFolderUri,
                onChangeFolder = onNavigateToFolderPicker
            )

            // Active Deployment Target & Connections Overview
            DeploymentTargetsCard(
                activeTarget = state.activeTarget,
                onSelectActiveTarget = { viewModel.setActiveTarget(it) },

                isGitHubConfigured = state.isGitHubConfigured,
                gitHubRepo = if (state.isGitHubConfigured) "${state.gitHubOwner}/${state.gitHubRepo}:${state.gitHubBranch}" else null,
                onConfigureGitHub = onNavigateToGitHub,

                isInfinityFreeConfigured = state.isHostingConfigured,
                infinityFreeServer = state.hostingServer,
                onConfigureInfinityFree = onNavigateToHostingSetup,

                isShrotiHostConfigured = state.isShrotiHostConfigured,
                shrotiHostServer = state.shrotiHostServer,
                onConfigureShrotiHost = onNavigateToHostingSetup,

                onViewMapping = onNavigateToMapping
            )

            // Sync Execution Controls (Start, Pause, Resume, Emergency Stop, Sync Now)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sync Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    // Primary Prominent SYNC NOW Button
                    Button(
                        onClick = { viewModel.triggerSyncNow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = !state.isSyncingNow && !isEmergencyStopped && state.projectName != null
                    ) {
                        if (state.isSyncingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SYNCHRONIZING...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SYNC NOW", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    // Secondary Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isActive) {
                            OutlinedButton(
                                onClick = { viewModel.pauseSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.stopSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 12.sp)
                            }
                        } else if (isPaused) {
                            Button(
                                onClick = { viewModel.resumeSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startSync() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Sync", fontSize = 12.sp)
                            }
                        }

                        // Prominent EMERGENCY STOP Button
                        Button(
                            onClick = { viewModel.emergencyStop() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Icon(imageVector = Icons.Default.FrontHand, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EMERGENCY STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Metrics Grid
            Text("Sync Metrics & Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Files / Folders",
                    value = "${state.totalFiles} / ${state.totalFolders}",
                    icon = Icons.Default.Folder,
                    tint = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Pending Queue",
                    value = "${state.pendingQueueCount}",
                    icon = Icons.Default.PendingActions,
                    tint = if (state.pendingQueueCount > 0) WarningAmber else TextSecondary,
                    modifier = Modifier.weight(1f).clickable { onNavigateToQueueManager() }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Failed Items",
                    value = "${state.failedQueueCount}",
                    icon = Icons.Default.ErrorOutline,
                    tint = if (state.failedQueueCount > 0) ErrorRed else SuccessGreen,
                    modifier = Modifier.weight(1f).clickable { onNavigateToQueueManager() }
                )
                MetricCard(
                    title = "Conflicts",
                    value = "${state.conflictCount}",
                    icon = Icons.Default.Rule,
                    tint = if (state.conflictCount > 0) WarningAmber else TextSecondary,
                    modifier = Modifier.weight(1f).clickable { onNavigateToQueueManager() }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Snapshots",
                    value = "${state.snapshotCount} (${state.stableSnapshotCount}★)",
                    icon = Icons.Default.Bookmark,
                    tint = PrimaryBlue,
                    modifier = Modifier.weight(1f).clickable { onNavigateToBackups() }
                )
                MetricCard(
                    title = "1-hr Backups",
                    value = "${state.activeBackupCount}",
                    icon = Icons.Default.History,
                    tint = AccentTeal,
                    modifier = Modifier.weight(1f).clickable { onNavigateToBackups() }
                )
            }

            // Timestamps Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimestampRow("Last Scan:", state.lastScanTime)
                    Divider(color = BorderColor, thickness = 0.5.dp)
                    TimestampRow("Last Successful Sync:", state.lastSuccessfulSyncTime)
                    Divider(color = BorderColor, thickness = 0.5.dp)
                    TimestampRow("Last InfinityFree Sync:", state.lastInfinityFreeSyncTime)
                    Divider(color = BorderColor, thickness = 0.5.dp)
                    TimestampRow("Last ShrotiHost Sync:", state.lastShrotiHostSyncTime)
                    Divider(color = BorderColor, thickness = 0.5.dp)
                    TimestampRow("Last GitHub Sync:", state.lastGitHubSyncTime)
                }
            }

            // Navigation Options
            Text("Configuration & Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            NavigationButton(
                title = "Deployment Mapping",
                subtitle = "Active Target: ${state.activeTarget.displayName} destination paths",
                icon = Icons.Default.AltRoute,
                onClick = onNavigateToMapping
            )

            NavigationButton(
                title = "Hosting Connections",
                subtitle = "Manage InfinityFree and ShrotiHost cPanel credentials & directories",
                icon = Icons.Default.CloudUpload,
                onClick = onNavigateToHostingSetup
            )

            NavigationButton(
                title = "GitHub Connection",
                subtitle = "Manage personal access token, branch, and repo mapping",
                icon = Icons.Default.Code,
                onClick = onNavigateToGitHub
            )

            NavigationButton(
                title = "Versioned Snapshots & Rollback",
                subtitle = "${state.snapshotCount} snapshots (${state.stableSnapshotCount} stable), 1-hr rollbacks, auto-restore",
                icon = Icons.Default.Restore,
                onClick = onNavigateToBackups
            )

            NavigationButton(
                title = "Activity Log",
                subtitle = "Deployment results, verifications, and snapshot history",
                icon = Icons.Default.ListAlt,
                onClick = onNavigateToActivityLog
            )

            NavigationButton(
                title = "Sync Queue & Conflicts",
                subtitle = "Review pending queue (${state.pendingQueueCount}), failed (${state.failedQueueCount}), conflicts (${state.conflictCount})",
                icon = Icons.Default.Queue,
                onClick = onNavigateToQueueManager
            )

            NavigationButton(
                title = "Sync Settings & Ignore Rules",
                subtitle = "Debounce delay, auto-rollback toggle, retention count, .gitignore",
                icon = Icons.Default.Tune,
                onClick = onNavigateToSyncSettings
            )
        }
    }
}

@Composable
private fun ActivityBanner(activity: String, controlState: SyncControlState) {
    val isEmergency = controlState == SyncControlState.EMERGENCY_STOPPED
    val isPaused = controlState == SyncControlState.PAUSED
    val isError = activity.startsWith("Error", ignoreCase = true)
    val isIdle = activity.equals("Idle", ignoreCase = true)

    val bgColor = when {
        isEmergency || isError -> ErrorBg
        isPaused -> WarningBg
        !isIdle -> PrimaryBlue.copy(alpha = 0.12f)
        controlState == SyncControlState.ACTIVE -> SuccessBg
        else -> SurfaceCard
    }

    val iconColor = when {
        isEmergency || isError -> ErrorRed
        isPaused -> WarningAmber
        !isIdle -> PrimaryBlue
        controlState == SyncControlState.ACTIVE -> SuccessGreen
        else -> TextSecondary
    }

    val icon = when {
        isEmergency -> Icons.Default.FrontHand
        isError -> Icons.Default.Error
        isPaused -> Icons.Default.PauseCircle
        !isIdle -> Icons.Default.Sync
        controlState == SyncControlState.ACTIVE -> Icons.Default.CheckCircle
        else -> Icons.Default.StopCircle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Current Status", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(activity, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun DeploymentTargetsCard(
    activeTarget: DeploymentTargetType,
    onSelectActiveTarget: (DeploymentTargetType) -> Unit,

    isGitHubConfigured: Boolean,
    gitHubRepo: String?,
    onConfigureGitHub: () -> Unit,

    isInfinityFreeConfigured: Boolean,
    infinityFreeServer: String?,
    onConfigureInfinityFree: () -> Unit,

    isShrotiHostConfigured: Boolean,
    shrotiHostServer: String?,
    onConfigureShrotiHost: () -> Unit,

    onViewMapping: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Deployment Target", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Only 1 target active • Direct push only",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = onViewMapping) {
                    Text("View Mapping", fontSize = 12.sp)
                }
            }

            // Quick Target Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DeploymentTargetType.entries.forEach { target ->
                    val isSelected = target == activeTarget
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectActiveTarget(target) },
                        label = {
                            Text(
                                target.displayName.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            // 1. InfinityFree Row
            TargetRow(
                name = "InfinityFree",
                details = infinityFreeServer ?: "Not Configured",
                isConfigured = isInfinityFreeConfigured,
                isActive = activeTarget == DeploymentTargetType.INFINITY_FREE,
                icon = Icons.Default.CloudUpload,
                tint = PrimaryBlue,
                onSelectActive = { onSelectActiveTarget(DeploymentTargetType.INFINITY_FREE) },
                onConfigure = onConfigureInfinityFree
            )

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            // 2. ShrotiHost cPanel Row
            TargetRow(
                name = "ShrotiHost cPanel",
                details = shrotiHostServer ?: "Not Configured",
                isConfigured = isShrotiHostConfigured,
                isActive = activeTarget == DeploymentTargetType.SHROTI_HOST,
                icon = Icons.Default.Dns,
                tint = WarningAmber,
                onSelectActive = { onSelectActiveTarget(DeploymentTargetType.SHROTI_HOST) },
                onConfigure = onConfigureShrotiHost
            )

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            // 3. GitHub Row
            TargetRow(
                name = "GitHub Repository",
                details = gitHubRepo ?: "Not Configured",
                isConfigured = isGitHubConfigured,
                isActive = activeTarget == DeploymentTargetType.GITHUB,
                icon = Icons.Default.Code,
                tint = AccentTeal,
                onSelectActive = { onSelectActiveTarget(DeploymentTargetType.GITHUB) },
                onConfigure = onConfigureGitHub
            )
        }
    }
}

@Composable
private fun TargetRow(
    name: String,
    details: String,
    isConfigured: Boolean,
    isActive: Boolean,
    icon: ImageVector,
    tint: Color,
    onSelectActive: () -> Unit,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) tint.copy(alpha = 0.08f) else Color.Transparent)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(SuccessGreen, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("ACTIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = if (isConfigured) TextSecondary else WarningAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        if (!isActive) {
            TextButton(
                onClick = onSelectActive,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Select", fontSize = 11.sp)
            }
        }
        OutlinedButton(
            onClick = onConfigure,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(if (isConfigured) "Edit" else "Setup", fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProjectInfoCard(projectName: String?, folderUri: String?, onChangeFolder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Selected Local Project", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    projectName ?: "No Project Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (projectName != null) TextPrimary else ErrorRed
                )
                if (folderUri != null) {
                    Text(folderUri, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onChangeFolder,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (projectName == null) "Select" else "Change", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TimestampRow(label: String, timestamp: Long) {
    val dateString = if (timestamp > 0) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestamp))
    } else {
        "Never"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(dateString, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun NavigationButton(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
