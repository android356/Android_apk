package com.autodeploy.infinityfree.ui.backups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val backups by viewModel.backups.collectAsState()
    val rollbackStatus by viewModel.rollbackStatus.collectAsState()
    var selectedBackupForRollback by remember { mutableStateOf<TemporaryBackupEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(rollbackStatus) {
        rollbackStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRollbackStatus()
        }
    }

    if (selectedBackupForRollback != null) {
        val target = selectedBackupForRollback!!
        AlertDialog(
            onDismissRequest = { selectedBackupForRollback = null },
            title = { Text("Confirm Version Rollback", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to restore the previous version of ${target.relativePath}? This will restore the file and redeploy it to both GitHub and InfinityFree."
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Temporary Old-Version Backups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
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
