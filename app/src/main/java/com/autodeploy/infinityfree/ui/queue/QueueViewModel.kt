package com.autodeploy.infinityfree.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repo = app.container.repository
    private val coordinator = app.container.syncCoordinator

    @OptIn(ExperimentalCoroutinesApi::class)
    val queueItems: StateFlow<List<SyncQueueEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeAllQueue(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val failedItems: StateFlow<List<SyncQueueEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeFailedItems(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val conflictedItems: StateFlow<List<SyncQueueEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeConflictedItems(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryItem(id: Long) {
        viewModelScope.launch {
            repo.retryItem(id)
            app.container.queueProcessor.processPendingQueue()
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            coordinator.retryAllFailed()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            val project = repo.getActiveProject() ?: return@launch
            repo.clearCompletedQueue(project.id)
        }
    }

    fun resolveConflict(queueId: Long, overwriteRemote: Boolean) {
        viewModelScope.launch {
            coordinator.resolveConflict(queueId, overwriteRemote)
        }
    }
}
