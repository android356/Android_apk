package com.autodeploy.infinityfree.ui.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploymentMappingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onNavigateToHosting: () -> Unit,
    onNavigateToFolder: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoDeployApplication
    val repo = app.container.repository

    val activeProject by repo.observeActiveProject().collectAsState(initial = null)
    var githubConn by remember { mutableStateOf<com.autodeploy.infinityfree.data.local.entity.GitHubConnectionEntity?>(null) }
    var hostingConn by remember { mutableStateOf<com.autodeploy.infinityfree.data.local.entity.HostingConnectionEntity?>(null) }

    LaunchedEffect(activeProject?.id) {
        val pid = activeProject?.id ?: return@LaunchedEffect
        githubConn = repo.getGitHubConnection(pid)
        hostingConn = repo.getConnectionForProject(pid)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deployment Mapping", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dual Destination Architecture", fontWeight = FontWeight.Bold)
                    Text(
                        "Local code created or edited by your AI coding tool is automatically synchronized to both GitHub and InfinityFree according to the mapping below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Step 1: Local Source
            MappingNodeCard(
                title = "1. Local Source Directory",
                subtitle = activeProject?.projectName ?: "No folder selected",
                detail = activeProject?.folderUri ?: "Select project folder to begin monitoring",
                icon = Icons.Default.Folder,
                tint = PrimaryBlue,
                actionLabel = "Change",
                onAction = onNavigateToFolder
            )

            // Arrow down
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = PrimaryBlue)
            }

            // Step 2A: GitHub Target
            MappingNodeCard(
                title = "2A. GitHub Repository Destination",
                subtitle = if (githubConn != null) "${githubConn?.owner}/${githubConn?.repo} [${githubConn?.branch}]" else "Not Configured",
                detail = if (githubConn != null) "Mapped to: ${githubConn?.destinationPath}" else "Configure GitHub token and repo",
                icon = Icons.Default.Code,
                tint = AccentTeal,
                actionLabel = if (githubConn != null) "Edit" else "Setup",
                onAction = onNavigateToGitHub
            )

            // Step 2B: InfinityFree Target
            MappingNodeCard(
                title = "2B. InfinityFree Live Hosting",
                subtitle = hostingConn?.connectionName ?: "Hosting Not Configured",
                detail = if (hostingConn != null) "${hostingConn?.server} -> ${hostingConn?.remoteRootDirectory}" else "Configure FTP credentials",
                icon = Icons.Default.CloudUpload,
                tint = WarningAmber,
                actionLabel = if (hostingConn != null) "Edit" else "Setup",
                onAction = onNavigateToHosting
            )

            // Sample Path Mapping Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Relative Path Preservation Example:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Local: index.html", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("  → GitHub: ${githubConn?.destinationPath ?: "/"}index.html", style = MaterialTheme.typography.bodySmall, color = AccentTeal)
                    Text("  → InfinityFree: ${hostingConn?.remoteRootDirectory ?: "/htdocs/"}index.html", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Local: css/style.css", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("  → GitHub: ${githubConn?.destinationPath ?: "/"}css/style.css", style = MaterialTheme.typography.bodySmall, color = AccentTeal)
                    Text("  → InfinityFree: ${hostingConn?.remoteRootDirectory ?: "/htdocs/"}css/style.css", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }
            }
        }
    }
}

@Composable
private fun MappingNodeCard(
    title: String,
    subtitle: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}
