package com.autodeploy.infinityfree.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repo = app.container.repository

    val selectedFilter = MutableStateFlow("ALL")

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<SyncHistoryEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { project ->
            selectedFilter.flatMapLatest { filter ->
                repo.observeHistory(project.id, filter)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: String) {
        selectedFilter.value = filter
    }

    fun clearLogs() {
        viewModelScope.launch {
            val project = repo.getActiveProject() ?: return@launch
            repo.clearHistory(project.id)
        }
    }
}
