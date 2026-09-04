package com.autodeploy.infinityfree.ui.github

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autodeploy.infinityfree.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubConnectionScreen(
    viewModel: GitHubViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var tokenVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Repository Setup", fontWeight = FontWeight.Bold) },
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
                    Text("GitHub REST API Integration", fontWeight = FontWeight.Bold)
                    Text(
                        "Changes in your local project folder will automatically be committed and pushed to your GitHub repository and branch using GitHub's Contents API.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            OutlinedTextField(
                value = state.owner,
                onValueChange = { viewModel.onOwnerChange(it) },
                label = { Text("GitHub Account / Organization (e.g. android356)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = state.repo,
                onValueChange = { viewModel.onRepoChange(it) },
                label = { Text("Repository Name (e.g. Android_apk)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = state.branch,
                onValueChange = { viewModel.onBranchChange(it) },
                label = { Text("Target Branch (e.g. main)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = state.destinationPath,
                onValueChange = { viewModel.onDestinationPathChange(it) },
                label = { Text("Repository Destination Folder (e.g. / or /src/)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = state.token,
                onValueChange = { viewModel.onTokenChange(it) },
                label = { Text("Personal Access Token (with 'repo' scope)") },
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val icon = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(imageVector = icon, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

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

            OutlinedButton(
                onClick = { viewModel.testConnection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                enabled = !state.isTesting && state.owner.isNotBlank() && state.repo.isNotBlank() && state.token.isNotBlank()
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing GitHub Connection...")
                } else {
                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection", fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = { viewModel.saveConnection(onNavigateBack) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !state.isSaving && state.owner.isNotBlank() && state.repo.isNotBlank() && state.token.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save GitHub Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
