package com.autodeploy.infinityfree.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: SyncSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronization Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Debounce / Stability Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "File Stability & Debounce Delay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Waits for rapid AI file writes to settle before uploading. Default: 3-5 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        "Current Value: ${state.debounceSeconds} seconds",
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                    Slider(
                        value = state.debounceSeconds.toFloat(),
                        onValueChange = { viewModel.setDebounceSeconds(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                }
            }

            // Reconciliation Interval Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Periodic Reconciliation Scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Interval for scanning and detecting any missed changes. Default: 30 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        "Current Interval: ${state.reconciliationIntervalSeconds} seconds",
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                    Slider(
                        value = state.reconciliationIntervalSeconds.toFloat(),
                        onValueChange = { viewModel.setReconciliationIntervalSeconds(it.toInt()) },
                        valueRange = 10f..120f,
                        steps = 10
                    )
                }
            }

            // Sync Deletions Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sync Deletions to Server & GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "When enabled, removing a local file will delete the remote file from both GitHub and InfinityFree.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.syncDeletions,
                        onCheckedChange = { viewModel.setSyncDeletions(it) }
                    )
                }
            }

            // Backup Retention Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Temporary Old-Version Backup Retention",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Preserves old version locally before replacing modified files. Expired backups are cleaned up automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        "Retention Window: ${state.backupRetentionMinutes} minutes (1 hour default)",
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                    Slider(
                        value = state.backupRetentionMinutes.toFloat(),
                        onValueChange = { viewModel.setBackupRetentionMinutes(it.toInt()) },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }

            // Custom Ignore Rules (.gitignore style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ignore Rules (.gitignore Style)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Enter glob patterns to ignore from synchronization, one per line (e.g. *.log, temp/**, test/*). Default ignores like .git, node_modules, and .idea are always applied.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = state.ignorePatternsText,
                        onValueChange = { viewModel.onIgnorePatternsTextChange(it) },
                        label = { Text("Custom Ignore Patterns") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = { viewModel.saveIgnorePatterns() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Ignore Rules")
                    }
                }
            }
        }
    }
}
