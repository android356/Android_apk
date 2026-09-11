package com.autodeploy.infinityfree.di

import android.content.Context
import com.autodeploy.infinityfree.data.deployment.DeploymentManager
import com.autodeploy.infinityfree.data.deployment.GitHubDeploymentProvider
import com.autodeploy.infinityfree.data.deployment.InfinityFreeProvider
import com.autodeploy.infinityfree.data.deployment.ShrotiHostCPanelProvider
import com.autodeploy.infinityfree.data.ftp.FtpClientManager
import com.autodeploy.infinityfree.data.github.GitHubClientManager
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.repository.AppRepository
import com.autodeploy.infinityfree.data.saf.SafScanner
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import com.autodeploy.infinityfree.service.*

class AppContainer(private val context: Context) {
    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    val preferences: AppPreferences by lazy { AppPreferences(context) }
    val secureStorage: SecureStorageManager by lazy { SecureStorageManager(context) }
    val ftpManager: FtpClientManager by lazy { FtpClientManager() }
    val githubManager: GitHubClientManager by lazy { GitHubClientManager() }
    val safScanner: SafScanner by lazy { SafScanner(context) }
    val stabilityTracker: FileStabilityTracker by lazy { FileStabilityTracker() }

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = context,
            database = database,
            safScanner = safScanner,
            preferences = preferences
        )
    }

    val infinityFreeProvider: InfinityFreeProvider by lazy {
        InfinityFreeProvider(
            connectionDao = database.hostingConnectionDao(),
            secureStorage = secureStorage,
            ftpManager = ftpManager
        )
    }

    val shrotiHostProvider: ShrotiHostCPanelProvider by lazy {
        ShrotiHostCPanelProvider(
            connectionDao = database.shrotiHostConnectionDao(),
            secureStorage = secureStorage,
            ftpManager = ftpManager
        )
    }

    val githubProvider: GitHubDeploymentProvider by lazy {
        GitHubDeploymentProvider(
            connectionDao = database.githubConnectionDao(),
            secureStorage = secureStorage,
            githubManager = githubManager
        )
    }

    val deploymentManager: DeploymentManager by lazy {
        DeploymentManager(
            infinityFreeProvider = infinityFreeProvider,
            shrotiHostProvider = shrotiHostProvider,
            githubProvider = githubProvider,
            preferences = preferences,
            syncQueueDao = database.syncQueueDao()
        )
    }

    val repository: AppRepository by lazy {
        AppRepository(
            context = context,
            database = database,
            preferences = preferences,
            secureStorage = secureStorage,
            ftpManager = ftpManager,
            githubManager = githubManager
        )
    }

    val queueProcessor: SyncQueueProcessor by lazy {
        SyncQueueProcessor(
            context = context,
            database = database,
            preferences = preferences,
            secureStorage = secureStorage,
            deploymentManager = deploymentManager,
            backupManager = backupManager
        )
    }

    val reconciliationScanner: ReconciliationScanner by lazy {
        ReconciliationScanner(
            context = context,
            database = database,
            preferences = preferences,
            safScanner = safScanner,
            stabilityTracker = stabilityTracker,
            deploymentManager = deploymentManager
        )
    }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator.getInstance(
            context = context,
            repository = repository,
            scanner = reconciliationScanner,
            queueProcessor = queueProcessor,
            backupManager = backupManager,
            preferences = preferences
        )
    }
}
