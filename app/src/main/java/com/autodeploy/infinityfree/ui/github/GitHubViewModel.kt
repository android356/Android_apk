package com.autodeploy.infinityfree.ui.github

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.github.GitHubResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitHubUiState(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val destinationPath: String = "/",
    val token: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isTestSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class GitHubViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repository = app.container.repository

    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState: StateFlow<GitHubUiState> = _uiState.asStateFlow()

    init {
        loadExisting()
    }

    private fun loadExisting() {
        viewModelScope.launch {
            val project = repository.getActiveProject() ?: return@launch
            val conn = repository.getGitHubConnection(project.id) ?: return@launch
            val token = repository.getStoredGitHubToken(conn.encryptedTokenReference) ?: ""

            _uiState.update {
                it.copy(
                    owner = conn.owner,
                    repo = conn.repo,
                    branch = conn.branch,
                    destinationPath = conn.destinationPath,
                    token = token
                )
            }
        }
    }

    fun onOwnerChange(v: String) = _uiState.update { it.copy(owner = v) }
    fun onRepoChange(v: String) = _uiState.update { it.copy(repo = v) }
    fun onBranchChange(v: String) = _uiState.update { it.copy(branch = v) }
    fun onDestinationPathChange(v: String) = _uiState.update { it.copy(destinationPath = v) }
    fun onTokenChange(v: String) = _uiState.update { it.copy(token = v) }

    fun testConnection() {
        val state = _uiState.value
        _uiState.update { it.copy(isTesting = true, testResult = null) }

        viewModelScope.launch {
            val result = repository.testGitHubConnection(
                owner = state.owner,
                repo = state.repo,
                branch = state.branch,
                token = state.token
            )

            when (result) {
                is GitHubResult.Success -> {
                    _uiState.update {
                        it.copy(isTesting = false, isTestSuccess = true, testResult = result.data)
                    }
                }
                is GitHubResult.Error -> {
                    _uiState.update {
                        it.copy(isTesting = false, isTestSuccess = false, testResult = result.message)
                    }
                }
            }
        }
    }

    fun saveConnection(onSaved: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val project = repository.getActiveProject()
            val projectId = project?.id ?: repository.saveProject("Default Project", "")

            repository.saveGitHubConnection(
                projectId = projectId,
                owner = state.owner,
                repo = state.repo,
                branch = state.branch,
                destinationPath = state.destinationPath,
                token = state.token
            )

            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            onSaved()
        }
    }
}
