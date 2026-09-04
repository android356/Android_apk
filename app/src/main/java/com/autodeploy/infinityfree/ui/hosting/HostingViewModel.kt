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
    val connectionName: String = "InfinityFree Hosting",
    val server: String = "ftpupload.net",
    val port: String = "21",
    val username: String = "",
    val password: String = "",
    val remoteRoot: String = "/htdocs/",
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
        loadExistingConnection()
    }

    private fun loadExistingConnection() {
        viewModelScope.launch {
            val project = repo.getActiveProject() ?: return@launch
            val conn = repo.getConnectionForProject(project.id) ?: return@launch
            val pass = repo.getStoredPassword(conn.encryptedPasswordReference) ?: ""

            _uiState.update {
                it.copy(
                    connectionName = conn.connectionName,
                    server = conn.server,
                    port = conn.port.toString(),
                    username = conn.username,
                    password = pass,
                    remoteRoot = conn.remoteRootDirectory
                )
            }
        }
    }

    fun onConnectionNameChange(name: String) = _uiState.update { it.copy(connectionName = name) }
    fun onServerChange(server: String) = _uiState.update { it.copy(server = server) }
    fun onPortChange(port: String) = _uiState.update { it.copy(port = port) }
    fun onUsernameChange(username: String) = _uiState.update { it.copy(username = username) }
    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password) }
    fun onRemoteRootChange(remoteRoot: String) = _uiState.update { it.copy(remoteRoot = remoteRoot) }

    fun testConnection() {
        val state = _uiState.value
        val portInt = state.port.toIntOrNull() ?: 21

        _uiState.update { it.copy(isTesting = true, testResult = null) }

        viewModelScope.launch {
            val result = repo.testFtpConnection(
                server = state.server,
                port = portInt,
                username = state.username,
                password = state.password,
                remoteRootDirectory = state.remoteRoot
            )

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
        val portInt = state.port.toIntOrNull() ?: 21

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val project = repo.getActiveProject()
            val projectId = project?.id ?: repo.saveProject("Default Project", "")

            repo.saveHostingConnection(
                projectId = projectId,
                connectionName = state.connectionName,
                server = state.server,
                port = portInt,
                username = state.username,
                password = state.password,
                remoteRoot = state.remoteRoot
            )

            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            onSaved()
        }
    }
}
