package com.autodeploy.infinityfree.ui.hosting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.ftp.FtpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HostingUiState(
    val selectedTab: Int = 0, // 0 = InfinityFree, 1 = ShrotiHost cPanel

    // InfinityFree fields
    val ifConnectionName: String = "InfinityFree Hosting",
    val ifServer: String = "ftpupload.net",
    val ifPort: String = "21",
    val ifUsername: String = "",
    val ifPassword: String = "",
    val ifRemoteRoot: String = "/htdocs/",

    // ShrotiHost cPanel fields
    val shConnectionName: String = "ShrotiHost cPanel",
    val shServer: String = "",
    val shPort: String = "21",
    val shUsername: String = "",
    val shPassword: String = "",
    val shRemoteRoot: String = "/public_html/",
    val shUseFtps: Boolean = true,

    // Status
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isTestSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class HostingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repo = app.container.repository

    private val _uiState = MutableStateFlow(HostingUiState())
    val uiState: StateFlow<HostingUiState> = _uiState.asStateFlow()

    init {
        loadExistingConnections()
    }

    private fun loadExistingConnections() {
        viewModelScope.launch {
            val project = repo.getActiveProject() ?: return@launch

            // Load InfinityFree connection
            val ifConn = repo.getConnectionForProject(project.id)
            if (ifConn != null) {
                val ifPass = repo.getStoredPassword(ifConn.encryptedPasswordReference) ?: ""
                _uiState.update {
                    it.copy(
                        ifConnectionName = ifConn.connectionName,
                        ifServer = ifConn.server,
                        ifPort = ifConn.port.toString(),
                        ifUsername = ifConn.username,
                        ifPassword = ifPass,
                        ifRemoteRoot = ifConn.remoteRootDirectory
                    )
                }
            }

            // Load ShrotiHost connection
            val shConn = repo.getShrotiHostConnection(project.id)
            if (shConn != null) {
                val shPass = repo.getStoredShrotiHostPassword(shConn.encryptedPasswordReference) ?: ""
                _uiState.update {
                    it.copy(
                        shConnectionName = shConn.connectionName,
                        shServer = shConn.server,
                        shPort = shConn.port.toString(),
                        shUsername = shConn.username,
                        shPassword = shPass,
                        shRemoteRoot = shConn.remoteRootDirectory,
                        shUseFtps = shConn.useFtps
                    )
                }
            }
        }
    }

    fun selectTab(tabIndex: Int) = _uiState.update { it.copy(selectedTab = tabIndex, testResult = null) }

    // InfinityFree handlers
    fun onIfConnectionNameChange(name: String) = _uiState.update { it.copy(ifConnectionName = name) }
    fun onIfServerChange(server: String) = _uiState.update { it.copy(ifServer = server) }
    fun onIfPortChange(port: String) = _uiState.update { it.copy(ifPort = port) }
    fun onIfUsernameChange(username: String) = _uiState.update { it.copy(ifUsername = username) }
    fun onIfPasswordChange(password: String) = _uiState.update { it.copy(ifPassword = password) }
    fun onIfRemoteRootChange(remoteRoot: String) = _uiState.update { it.copy(ifRemoteRoot = remoteRoot) }

    // ShrotiHost handlers
    fun onShConnectionNameChange(name: String) = _uiState.update { it.copy(shConnectionName = name) }
    fun onShServerChange(server: String) = _uiState.update { it.copy(shServer = server) }
    fun onShPortChange(port: String) = _uiState.update { it.copy(shPort = port) }
    fun onShUsernameChange(username: String) = _uiState.update { it.copy(shUsername = username) }
    fun onShPasswordChange(password: String) = _uiState.update { it.copy(shPassword = password) }
    fun onShRemoteRootChange(remoteRoot: String) = _uiState.update { it.copy(shRemoteRoot = remoteRoot) }
    fun onShUseFtpsChange(useFtps: Boolean) = _uiState.update { it.copy(shUseFtps = useFtps) }

    fun testConnection() {
        val state = _uiState.value
        _uiState.update { it.copy(isTesting = true, testResult = null) }

        viewModelScope.launch {
            val result = if (state.selectedTab == 0) {
                val portInt = state.ifPort.toIntOrNull() ?: 21
                repo.testFtpConnection(
                    server = state.ifServer,
                    port = portInt,
                    username = state.ifUsername,
                    password = state.ifPassword,
                    remoteRootDirectory = state.ifRemoteRoot
                )
            } else {
                val portInt = state.shPort.toIntOrNull() ?: 21
                repo.testShrotiHostConnection(
                    server = state.shServer,
                    port = portInt,
                    username = state.shUsername,
                    password = state.shPassword,
                    remoteRootDirectory = state.shRemoteRoot,
                    useFtps = state.shUseFtps
                )
            }

            when (result) {
                is FtpResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            isTestSuccess = true,
                            testResult = result.data
                        )
                    }
                }
                is FtpResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            isTestSuccess = false,
                            testResult = result.message
                        )
                    }
                }
            }
        }
    }

    fun saveConnection(onSaved: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val project = repo.getActiveProject()
            val projectId = project?.id ?: repo.saveProject("Default Project", "")

            if (state.selectedTab == 0) {
                val portInt = state.ifPort.toIntOrNull() ?: 21
                repo.saveHostingConnection(
                    projectId = projectId,
                    connectionName = state.ifConnectionName,
                    server = state.ifServer,
                    port = portInt,
                    username = state.ifUsername,
                    password = state.ifPassword,
                    remoteRoot = state.ifRemoteRoot
                )
            } else {
                val portInt = state.shPort.toIntOrNull() ?: 21
                repo.saveShrotiHostConnection(
                    projectId = projectId,
                    connectionName = state.shConnectionName,
                    server = state.shServer,
                    port = portInt,
                    username = state.shUsername,
                    password = state.shPassword,
                    remoteRoot = state.shRemoteRoot,
                    useFtps = state.shUseFtps
                )
            }

            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            onSaved()
        }
    }
}

