package com.autodeploy.infinityfree.di

import android.content.Context
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
    val backupManager: BackupManager by lazy { BackupManager(context, database) }

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
            ftpManager = ftpManager,
            githubManager = githubManager,
            backupManager = backupManager
        )
    }

    val reconciliationScanner: ReconciliationScanner by lazy {
        ReconciliationScanner(
            context = context,
            database = database,
            preferences = preferences,
            safScanner = safScanner,
            stabilityTracker = stabilityTracker
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
