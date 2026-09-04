package com.autodeploy.infinityfree.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.service.AutoSyncForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as AutoDeployApplication
            CoroutineScope(Dispatchers.IO).launch {
                val autoSyncEnabled = app.container.preferences.isAutoSyncEnabled.first()
                if (autoSyncEnabled) {
                    AutoSyncForegroundService.start(context)
                }
            }
        }
    }
}
