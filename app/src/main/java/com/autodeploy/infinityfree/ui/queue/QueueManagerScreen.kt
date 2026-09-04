package com.autodeploy.infinityfree.ui.queue

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
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagerScreen(
    viewModel: QueueViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val queueItems by viewModel.queueItems.collectAsState()
    val failedItems by viewModel.failedItems.collectAsState()
    val conflictedItems by viewModel.conflictedItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronization Queue", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearCompleted() }) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Clear Completed")
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active (${queueItems.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Failed (${failedItems.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Conflicts (${conflictedItems.size})", fontSize = 12.sp) }
                )
            }

            if (selectedTab == 1 && failedItems.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = { viewModel.retryAllFailed() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry All Failed Items")
                    }
                }
            }

            val itemsToShow = when (selectedTab) {
                0 -> queueItems
                1 -> failedItems
                else -> conflictedItems
            }

            if (itemsToShow.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (selectedTab) {
                            0 -> "Queue is currently empty"
                            1 -> "No failed items"
                            else -> "No conflict items detected"
                        },
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itemsToShow, key = { it.id }) { item ->
                        QueueItemCard(
                            item = item,
                            onRetry = { viewModel.retryItem(item.id) },
                            onResolveConflict = { overwrite ->
                                viewModel.resolveConflict(item.id, overwrite)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: SyncQueueEntity,
    onRetry: () -> Unit,
    onResolveConflict: (Boolean) -> Unit
) {
    val isConflict = item.status == "CONFLICT"
    val statusColor = when (item.status) {
        "SUCCESS" -> SuccessGreen
        "FAILED" -> ErrorRed
        "CONFLICT" -> WarningAmber
        "UPLOADING", "PREPARING" -> PrimaryBlue
        "RETRYING" -> WarningAmber
        else -> TextSecondary
    }

    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = sdf.format(Date(item.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.operation} • ${item.status}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.relativePath,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Dual Target Status Pills
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(target = "GitHub", status = item.githubStatus)
                StatusPill(target = "InfinityFree", status = item.infinityFreeStatus)
            }

            if (item.retryCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Retry count: ${item.retryCount}/3",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }

            if (!item.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed
                )
            }

            if (!item.conflictDetails.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.conflictDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }

            if (isConflict) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onResolveConflict(true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Overwrite Remote", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onResolveConflict(false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Keep Remote", fontSize = 11.sp)
                    }
                }
            } else if (item.status == "FAILED" || item.status == "RETRYING") {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(target: String, status: String) {
    val color = when (status) {
        "SUCCESS" -> SuccessGreen
        "FAILED" -> ErrorRed
        "CONFLICT" -> WarningAmber
        "SKIPPED" -> TextSecondary
        else -> PrimaryBlue
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$target: $status",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
