package com.jeerovan.comfer

import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AppUpdateHandler(private val context: Context) {
    val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    // Check if we should prompt based on 48-hour cooldown
    private fun shouldPromptUser(): Boolean {
        return PreferenceManager.shouldAppUpdatePromptUser(context)
    }

    fun checkForUpdate(
        onUpdateAvailable: (AppUpdateInfo) -> Unit,
        onUpdateDownloaded: () -> Unit
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            // 1. Check if update is available AND allowed
            val isAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            // 2. Check if already downloaded (in case app was killed during download)
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateDownloaded()
                return@addOnSuccessListener
            }

            // 3. Show prompt only if available, allowed, and cooldown passed
            if (isAvailable && isAllowed && shouldPromptUser()) {
                onUpdateAvailable(info)
            }
        }
    }

    fun startUpdate(
        activityResultLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
        info: AppUpdateInfo
    ) {
        appUpdateManager.startUpdateFlowForResult(
            info,
            activityResultLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }

    fun registerDownloadListener(): Flow<Int> = callbackFlow {
        val listener = InstallStateUpdatedListener { state ->
            trySend(state.installStatus())
        }
        appUpdateManager.registerListener(listener)
        awaitClose { appUpdateManager.unregisterListener(listener) }
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun saveLastPromptTime() {
        PreferenceManager.setAppUpdatePromptTime(context,System.currentTimeMillis())
    }
}
