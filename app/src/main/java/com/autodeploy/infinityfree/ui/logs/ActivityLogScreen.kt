package com.autodeploy.infinityfree.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    viewModel: ActivityLogViewModel,
    onNavigateBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val currentFilter by viewModel.selectedFilter.collectAsState()
    val filterScrollState = rememberScrollState()

    val filters = listOf("ALL", "SUCCESS", "FAILED", "CONFLICT", "UPLOAD", "DELETE", "BACKUP", "ROLLBACK")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(filterScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = currentFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HistoryToggleOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No activity logs recorded yet", color = TextSecondary, fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        ActivityLogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogItem(log: SyncHistoryEntity) {
    val isSuccess = log.result.equals("SUCCESS", ignoreCase = true)
    val isConflict = log.result.equals("CONFLICT", ignoreCase = true)
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = sdf.format(Date(log.startedAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSuccess -> SuccessBg
                            isConflict -> WarningBg
                            else -> ErrorBg
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isSuccess -> Icons.Default.Check
                        isConflict -> Icons.Default.Rule
                        else -> Icons.Default.Close
                    },
                    contentDescription = null,
                    tint = when {
                        isSuccess -> SuccessGreen
                        isConflict -> WarningAmber
                        else -> ErrorRed
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.operation,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.relativePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                // Dual Target Results
                if (!log.githubResult.isNullOrEmpty() || !log.infinityFreeResult.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        log.githubResult?.let { gh ->
                            Text("GitHub: $gh", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (gh.startsWith("SUCCESS")) SuccessGreen else WarningAmber)
                        }
                        log.infinityFreeResult?.let { ifRes ->
                            Text("InfinityFree: $ifRes", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (ifRes.startsWith("SUCCESS")) SuccessGreen else WarningAmber)
                        }
                    }
                }

                if (!log.errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = if (isSuccess) TextSecondary else ErrorRed
                    )
                }
            }
        }
    }
}
