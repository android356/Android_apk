package com.autodeploy.infinityfree.ui.backups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.data.local.entity.BackupSnapshotEntity
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import com.autodeploy.infinityfree.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    viewModel: BackupsViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isCreatingSnapshot by viewModel.isCreatingSnapshot.collectAsState()

    var showCreateSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotLabelInput by remember { mutableStateOf("Manual Snapshot") }
    var selectedSnapshotForRollback by remember { mutableStateOf<BackupSnapshotEntity?>(null) }
    var selectedSnapshotForDelete by remember { mutableStateOf<BackupSnapshotEntity?>(null) }
    var selectedBackupForRollback by remember { mutableStateOf<TemporaryBackupEntity?>(null) }
    var showRollbackToStableDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // Dialog: Create Manual Snapshot
    if (showCreateSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSnapshotDialog = false },
            title = { Text("Create Manual Snapshot", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Capture a full point-in-time snapshot of the current project directory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = snapshotLabelInput,
                        onValueChange = { snapshotLabelInput = it },
                        label = { Text("Snapshot Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val label = snapshotLabelInput.trim().ifEmpty { "Manual Snapshot" }
                        viewModel.createManualSnapshot(label)
                        showCreateSnapshotDialog = false
                        snapshotLabelInput = "Manual Snapshot"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSnapshotDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Rollback Versioned Snapshot
    if (selectedSnapshotForRollback != null) {
        val snapshot = selectedSnapshotForRollback!!
        AlertDialog(
            onDismissRequest = { selectedSnapshotForRollback = null },
            title = { Text("Rollback to ${snapshot.versionTag}?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You are about to roll back to snapshot ${snapshot.versionTag} (\"${snapshot.label}\").")
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningBg),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Safety Guarantees:", fontWeight = FontWeight.Bold, color = WarningAmber, fontSize = 13.sp)
                            Text("1. An emergency snapshot will be saved prior to rollback.", fontSize = 12.sp, color = TextPrimary)
                            Text("2. All ${snapshot.fileCount} snapshot files will be restored locally.", fontSize = 12.sp, color = TextPrimary)
                            Text("3. Restored files will be queued for deployment to the active target.", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreSnapshot(snapshot)
                        selectedSnapshotForRollback = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Confirm Rollback")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSnapshotForRollback = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Delete Snapshot
    if (selectedSnapshotForDelete != null) {
        val snapshot = selectedSnapshotForDelete!!
        AlertDialog(
            onDismissRequest = { selectedSnapshotForDelete = null },
            title = { Text("Delete Snapshot ${snapshot.versionTag}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete snapshot ${snapshot.versionTag} (\"${snapshot.label}\")? This operation cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSnapshot(snapshot)
                        selectedSnapshotForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSnapshotForDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Temporary Backup Rollback
    if (selectedBackupForRollback != null) {
        val target = selectedBackupForRollback!!
        AlertDialog(
            onDismissRequest = { selectedBackupForRollback = null },
            title = { Text("Confirm File Rollback", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to restore the previous version of ${target.relativePath}? This will restore the file and queue it for deployment to the active deployment target."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackup(target)
                        selectedBackupForRollback = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Confirm Rollback")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBackupForRollback = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Rollback to Last Stable Snapshot
    if (showRollbackToStableDialog) {
        val latestStable = snapshots.firstOrNull { it.isStable }
        AlertDialog(
            onDismissRequest = { showRollbackToStableDialog = false },
            title = { Text("Rollback to Last Stable Version?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (latestStable != null) {
                        Text("Target Snapshot: ${latestStable.versionTag} (\"${latestStable.label}\")")
                    } else {
                        Text("Looking for the latest snapshot marked as STABLE...")
                    }
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningBg),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Safety Guarantees:", fontWeight = FontWeight.Bold, color = WarningAmber, fontSize = 13.sp)
                            Text("1. Manifest SHA-256 integrity is checked before restoration.", fontSize = 12.sp, color = TextPrimary)
                            Text("2. Emergency snapshot taken of current local state.", fontSize = 12.sp, color = TextPrimary)
                            Text("3. Post-snapshot orphaned files are cleaned up.", fontSize = 12.sp, color = TextPrimary)
                            Text("4. Restored files deployed exclusively to active target.", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rollbackToLastStable()
                        showRollbackToStableDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Rollback to Stable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRollbackToStableDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshots & Backups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showCreateSnapshotDialog = true }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Create Snapshot")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSnapshotDialog = true },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text("New Snapshot") },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Versioned Snapshots (${snapshots.size})", fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("1-Hr File Backups (${backups.size})", fontSize = 13.sp) }
                )
            }

            if (isCreatingSnapshot) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    // Versioned Snapshots tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Versioned Snapshot System", fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { showCreateSnapshotDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Snapshot", fontSize = 12.sp)
                                }
                            }
                            Text(
                                "Snapshots capture complete project state before deployment or on-demand. If deployment fails, automatic rollback can restore the prior snapshot. Star snapshots as STABLE to protect them from retention cleanup.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )

                            val hasStable = snapshots.any { it.isStable }
                            if (hasStable) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showRollbackToStableDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Rollback to Last Stable Version", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (snapshots.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No versioned snapshots yet", color = TextSecondary, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Tap 'Snapshot' to capture the current state",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 72.dp)
                        ) {
                            items(snapshots, key = { it.id }) { snapshot ->
                                SnapshotCard(
                                    snapshot = snapshot,
                                    onRollbackClick = { selectedSnapshotForRollback = snapshot },
                                    onToggleStable = { viewModel.toggleStable(snapshot) },
                                    onVerifyClick = { viewModel.verifySnapshot(snapshot) },
                                    onDeleteClick = { selectedSnapshotForDelete = snapshot }
                                )
                            }
                        }
                    }
                } else {
                    // Temporary 1-Hr Backups tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("1-Hour Rollback Safety Net", fontWeight = FontWeight.Bold)
                            Text(
                                "Whenever an AI tool updates an existing file, the previous known version is automatically preserved for 1 hour. You can roll back anytime before expiration.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (backups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No active temporary backups", color = TextSecondary, fontSize = 15.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(backups, key = { it.id }) { backup ->
                                BackupItemCard(
                                    backup = backup,
                                    onRollbackClick = { selectedBackupForRollback = backup }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: BackupSnapshotEntity,
    onRollbackClick: () -> Unit,
    onToggleStable: () -> Unit,
    onVerifyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val createdTime = sdf.format(Date(snapshot.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Version tag, Stable badge, Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(PrimaryBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = snapshot.versionTag,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }

                    if (snapshot.isStable) {
                        Box(
                            modifier = Modifier
                                .background(WarningAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "★ STABLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                        }
                    }

                    val statusColor = when (snapshot.status) {
                        "AVAILABLE" -> SuccessGreen
                        "RESTORED" -> AccentTeal
                        "EMERGENCY" -> WarningAmber
                        "FAILED" -> ErrorRed
                        else -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = snapshot.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }

                IconButton(
                    onClick = onToggleStable,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (snapshot.isStable) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (snapshot.isStable) "Unmark Stable" else "Mark Stable",
                        tint = if (snapshot.isStable) WarningAmber else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = snapshot.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$createdTime • ${snapshot.fileCount} files • ${formatBytes(snapshot.totalSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(10.dp))

            // Actions Row: Verify, Rollback, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onVerifyClick,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verify SHA-256", fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onRollbackClick,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rollback", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Snapshot",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupItemCard(
    backup: TemporaryBackupEntity,
    onRollbackClick: () -> Unit
) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val createdTime = sdf.format(Date(backup.createdAt))
    val remainingMillis = (backup.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    val remainingMinutes = remainingMillis / (60 * 1000)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.relativePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Backed up at $createdTime • Expires in ${remainingMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Version: ${backup.versionIdentifier}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentTeal
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRollbackClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rollback", fontSize = 12.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
