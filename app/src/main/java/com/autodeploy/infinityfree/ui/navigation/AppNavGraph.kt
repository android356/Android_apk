package com.autodeploy.infinityfree.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autodeploy.infinityfree.ui.backups.BackupsScreen
import com.autodeploy.infinityfree.ui.backups.BackupsViewModel
import com.autodeploy.infinityfree.ui.dashboard.DashboardScreen
import com.autodeploy.infinityfree.ui.dashboard.DashboardViewModel
import com.autodeploy.infinityfree.ui.folder.FolderSelectionScreen
import com.autodeploy.infinityfree.ui.hosting.HostingConnectionScreen
import com.autodeploy.infinityfree.ui.hosting.HostingViewModel
import com.autodeploy.infinityfree.ui.logs.ActivityLogScreen
import com.autodeploy.infinityfree.ui.logs.ActivityLogViewModel
import com.autodeploy.infinityfree.ui.queue.QueueManagerScreen
import com.autodeploy.infinityfree.ui.queue.QueueViewModel
import com.autodeploy.infinityfree.ui.settings.SyncSettingsScreen
import com.autodeploy.infinityfree.ui.settings.SyncSettingsViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            val viewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToFolderPicker = { navController.navigate(Screen.FolderPicker.route) },
                onNavigateToHostingSetup = { navController.navigate(Screen.HostingSetup.route) },
                onNavigateToSyncSettings = { navController.navigate(Screen.SyncSettings.route) },
                onNavigateToActivityLog = { navController.navigate(Screen.ActivityLog.route) },
                onNavigateToQueueManager = { navController.navigate(Screen.QueueManager.route) },
                onNavigateToBackups = { navController.navigate(Screen.Backups.route) }
            )
        }

        composable(Screen.FolderPicker.route) {
            FolderSelectionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.HostingSetup.route) {
            val viewModel: HostingViewModel = viewModel()
            HostingConnectionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SyncSettings.route) {
            val viewModel: SyncSettingsViewModel = viewModel()
            SyncSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ActivityLog.route) {
            val viewModel: ActivityLogViewModel = viewModel()
            ActivityLogScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.QueueManager.route) {
            val viewModel: QueueViewModel = viewModel()
            QueueManagerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Backups.route) {
            val viewModel: BackupsViewModel = viewModel()
            BackupsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
