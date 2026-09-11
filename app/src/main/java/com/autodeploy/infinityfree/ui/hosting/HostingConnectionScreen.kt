package com.autodeploy.infinityfree.ui.hosting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingConnectionScreen(
    viewModel: HostingViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hosting Connections", fontWeight = FontWeight.Bold) },
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
        ) {
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("InfinityFree (FTP)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("ShrotiHost cPanel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.selectedTab == 0) {
                    // --- InfinityFree Tab ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("InfinityFree Hosting Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Default remote root directory for InfinityFree is /htdocs/ on port 21.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.ifConnectionName,
                        onValueChange = { viewModel.onIfConnectionNameChange(it) },
                        label = { Text("Connection Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.ifServer,
                        onValueChange = { viewModel.onIfServerChange(it) },
                        label = { Text("FTP Host / Server (e.g. ftpupload.net)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.ifPort,
                        onValueChange = { viewModel.onIfPortChange(it) },
                        label = { Text("FTP Port (Default: 21)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.ifUsername,
                        onValueChange = { viewModel.onIfUsernameChange(it) },
                        label = { Text("FTP Username") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.ifPassword,
                        onValueChange = { viewModel.onIfPasswordChange(it) },
                        label = { Text("FTP Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = icon, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.ifRemoteRoot,
                        onValueChange = { viewModel.onIfRemoteRootChange(it) },
                        label = { Text("Remote Root Directory (e.g. /htdocs/)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    // --- ShrotiHost cPanel Tab ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ShrotiHost cPanel Hosting Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Default remote root directory for cPanel hosting is /public_html/. FTPS (Explicit TLS) is recommended.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.shConnectionName,
                        onValueChange = { viewModel.onShConnectionNameChange(it) },
                        label = { Text("Connection Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.shServer,
                        onValueChange = { viewModel.onShServerChange(it) },
                        label = { Text("Server Hostname / IP (e.g. server.shrotihost.com)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.shPort,
                        onValueChange = { viewModel.onShPortChange(it) },
                        label = { Text("FTP / FTPS Port (Default: 21)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.shUsername,
                        onValueChange = { viewModel.onShUsernameChange(it) },
                        label = { Text("cPanel FTP Username") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.shPassword,
                        onValueChange = { viewModel.onShPasswordChange(it) },
                        label = { Text("cPanel FTP Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = icon, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = state.shRemoteRoot,
                        onValueChange = { viewModel.onShRemoteRootChange(it) },
                        label = { Text("Remote Root Directory (e.g. /public_html/)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Use FTPS (Explicit TLS)", fontWeight = FontWeight.SemiBold)
                            Text("Encrypt connection using TLS/SSL", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = state.shUseFtps,
                            onCheckedChange = { viewModel.onShUseFtpsChange(it) }
                        )
                    }
                }

                // Connection Test Result Banner
                if (state.testResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isTestSuccess) SuccessBg else ErrorBg
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (state.isTestSuccess) SuccessGreen else ErrorRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.testResult ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isTestSuccess) SuccessGreen else ErrorRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val isCurrentValid = if (state.selectedTab == 0) {
                    state.ifServer.isNotBlank() && state.ifUsername.isNotBlank() && state.ifPassword.isNotBlank()
                } else {
                    state.shServer.isNotBlank() && state.shUsername.isNotBlank() && state.shPassword.isNotBlank()
                }

                // Test Connection Button
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !state.isTesting && isCurrentValid
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Connection...")
                    } else {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Save Connection Button
                Button(
                    onClick = { viewModel.saveConnection(onNavigateBack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = !state.isSaving && isCurrentValid
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

