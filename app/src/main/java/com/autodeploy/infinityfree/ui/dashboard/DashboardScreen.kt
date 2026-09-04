package com.autodeploy.infinityfree.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import com.autodeploy.infinityfree.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToFolderPicker: () -> Unit,
    onNavigateToHostingSetup: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                    // Quick Auto Sync Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            if (state.isAutoSyncOn) "AUTO" else "MANUAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isAutoSyncOn) SuccessGreen else TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = state.isAutoSyncOn,
                            onCheckedChange = { viewModel.toggleAutoSync(it) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // Activity Status Banner
            ActivityBanner(activity = state.currentActivity, isAutoSyncOn = state.isAutoSyncOn)

            // Project Selection Card
            ProjectInfoCard(
                projectName = state.projectName,
                folderUri = state.projectFolderUri,
                onChangeFolder = onNavigateToFolderPicker
            )

            // Hosting Connection Card
            HostingInfoCard(
                server = state.hostingServer,
                connectionName = state.hostingConnectionName,
                status = state.hostingStatus,
                isConfigured = state.isHostingConfigured,
                onConfigure = onNavigateToHostingSetup
            )

            // Prominent "SYNC NOW" Button (PRD Section 12)
            Button(
                onClick = { viewModel.triggerSyncNow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                ),
                enabled = !state.isSyncingNow && state.projectName != null && state.isHostingConfigured
            ) {
                if (state.isSyncingNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SYNCHRONIZING...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SYNC NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Sync Metrics Grid
            Text(
                "Project & Sync Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

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
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToQueueManager() }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Failed Uploads",
                    value = "${state.failedQueueCount}",
                    icon = Icons.Default.ErrorOutline,
                    tint = if (state.failedQueueCount > 0) ErrorRed else SuccessGreen,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToQueueManager() }
                )
                MetricCard(
                    title = "Backups (1hr)",
                    value = "${state.activeBackupCount}",
                    icon = Icons.Default.History,
                    tint = AccentTeal,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToBackups() }
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
                    TimestampRow(label = "Last Scan:", timestamp = state.lastScanTime)
                    Divider(color = BorderColor, thickness = 0.5.dp)
                    TimestampRow(label = "Last Successful Sync:", timestamp = state.lastSuccessfulSyncTime)
                }
            }

            // Management Navigation Actions
            Text(
                "Management & Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            NavigationButton(
                title = "Activity Log",
                subtitle = "View real-time synchronization history and debug logs",
                icon = Icons.Default.ListAlt,
                onClick = onNavigateToActivityLog
            )

            NavigationButton(
                title = "Failed Uploads & Queue",
                subtitle = "Inspect, retry, or cancel queued items (${state.failedQueueCount} failed)",
                icon = Icons.Default.Queue,
                onClick = onNavigateToQueueManager
            )

            NavigationButton(
                title = "Temporary Backups & Rollback",
                subtitle = "Restore recent versions within the 1-hour window (${state.activeBackupCount} available)",
                icon = Icons.Default.Restore,
                onClick = onNavigateToBackups
            )

            NavigationButton(
                title = "Sync Settings",
                subtitle = "Debounce delay, scan interval (30s), deletion sync",
                icon = Icons.Default.Tune,
                onClick = onNavigateToSyncSettings
            )
        }
    }
}

@Composable
private fun ActivityBanner(activity: String, isAutoSyncOn: Boolean) {
    val isIdle = activity.equals("Idle", ignoreCase = true)
    val isError = activity.startsWith("Error", ignoreCase = true)

    val bgColor = when {
        isError -> ErrorBg
        !isIdle -> PrimaryBlue.copy(alpha = 0.12f)
        isAutoSyncOn -> SuccessBg
        else -> SurfaceCard
    }

    val iconColor = when {
        isError -> ErrorRed
        !isIdle -> PrimaryBlue
        isAutoSyncOn -> SuccessGreen
        else -> TextSecondary
    }

    val icon = when {
        isError -> Icons.Default.Error
        !isIdle -> Icons.Default.Sync
        isAutoSyncOn -> Icons.Default.CheckCircle
        else -> Icons.Default.PauseCircle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Current Activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = activity,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ProjectInfoCard(
    projectName: String?,
    folderUri: String?,
    onChangeFolder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Selected Local Project",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    projectName ?: "No Project Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (projectName != null) TextPrimary else ErrorRed
                )
                if (folderUri != null) {
                    Text(
                        folderUri,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
private fun HostingInfoCard(
    server: String?,
    connectionName: String?,
    status: String,
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "InfinityFree Hosting Connection",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    connectionName ?: "Hosting Not Configured",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigured) TextPrimary else WarningAmber
                )
                Text(
                    server ?: "Enter FTP credentials to connect",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onConfigure,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (isConfigured) "Edit" else "Configure", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(dateString, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun NavigationButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
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
