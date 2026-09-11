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
import kotlinx.coroutines.launch

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
    val prefs = app.container.preferences
    val scope = rememberCoroutineScope()

    val activeProject by repo.observeActiveProject().collectAsState(initial = null)
    val activeTarget by prefs.activeDeploymentTarget.collectAsState(initial = com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.INFINITY_FREE)

    var githubConn by remember { mutableStateOf<com.autodeploy.infinityfree.data.local.entity.GitHubConnectionEntity?>(null) }
    var hostingConn by remember { mutableStateOf<com.autodeploy.infinityfree.data.local.entity.HostingConnectionEntity?>(null) }
    var shrotiHostConn by remember { mutableStateOf<com.autodeploy.infinityfree.data.local.entity.ShrotiHostConnectionEntity?>(null) }

    LaunchedEffect(activeProject?.id) {
        val pid = activeProject?.id ?: return@LaunchedEffect
        githubConn = repo.getGitHubConnection(pid)
        hostingConn = repo.getConnectionForProject(pid)
        shrotiHostConn = repo.getShrotiHostConnection(pid)
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
                    Text("Single Active Target Architecture", fontWeight = FontWeight.Bold)
                    Text(
                        "Only ONE destination is active at any time. When file changes are detected, they are deployed directly and exclusively to the active target.",
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

            // Step 2: Active Target
            val activeTitle: String
            val activeSubtitle: String
            val activeDetail: String
            val activeTint: Color
            val activeIcon: ImageVector
            val activeAction: () -> Unit

            when (activeTarget) {
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.INFINITY_FREE -> {
                    activeTitle = "2. Active Target: InfinityFree (FTP)"
                    activeSubtitle = hostingConn?.connectionName ?: "Not Configured"
                    activeDetail = if (hostingConn != null) "${hostingConn?.server} -> ${hostingConn?.remoteRootDirectory}" else "Configure FTP credentials"
                    activeTint = PrimaryBlue
                    activeIcon = Icons.Default.CloudUpload
                    activeAction = onNavigateToHosting
                }
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.SHROTI_HOST -> {
                    activeTitle = "2. Active Target: ShrotiHost cPanel (FTPS)"
                    activeSubtitle = shrotiHostConn?.connectionName ?: "Not Configured"
                    activeDetail = if (shrotiHostConn != null) "${shrotiHostConn?.server} -> ${shrotiHostConn?.remoteRootDirectory}" else "Configure cPanel credentials"
                    activeTint = WarningAmber
                    activeIcon = Icons.Default.Dns
                    activeAction = onNavigateToHosting
                }
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.GITHUB -> {
                    activeTitle = "2. Active Target: GitHub Repository"
                    activeSubtitle = if (githubConn != null) "${githubConn?.owner}/${githubConn?.repo} [${githubConn?.branch}]" else "Not Configured"
                    activeDetail = if (githubConn != null) "Destination: ${githubConn?.destinationPath}" else "Configure GitHub token and repo"
                    activeTint = AccentTeal
                    activeIcon = Icons.Default.Code
                    activeAction = onNavigateToGitHub
                }
            }

            MappingNodeCard(
                title = activeTitle,
                subtitle = activeSubtitle,
                detail = activeDetail,
                icon = activeIcon,
                tint = activeTint,
                actionLabel = "Configure",
                onAction = activeAction
            )

            // Relative Path Mapping Breakdown
            val remotePathExample = when (activeTarget) {
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.INFINITY_FREE -> hostingConn?.remoteRootDirectory ?: "/htdocs/"
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.SHROTI_HOST -> shrotiHostConn?.remoteRootDirectory ?: "/public_html/"
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.GITHUB -> githubConn?.destinationPath ?: "/"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Relative Path Preservation Example (${activeTarget.displayName}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• Local: index.html", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("  → Remote: ${remotePathExample.trimEnd('/')}/index.html", style = MaterialTheme.typography.bodySmall, color = activeTint)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Local: css/style.css", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("  → Remote: ${remotePathExample.trimEnd('/')}/css/style.css", style = MaterialTheme.typography.bodySmall, color = activeTint)
                }
            }

            // Switch Active Target Section
            Text("Switch Active Target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.autodeploy.infinityfree.data.deployment.DeploymentTargetType.entries.forEach { target ->
                    val isCurrent = target == activeTarget
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                app.container.deploymentManager.setActiveTarget(target, activeProject?.id)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = if (isCurrent) ButtonDefaults.outlinedButtonColors(containerColor = PrimaryBlue.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(
                            text = if (isCurrent) "✓ ${target.displayName.substringBefore(" ")}" else target.displayName.substringBefore(" "),
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
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
