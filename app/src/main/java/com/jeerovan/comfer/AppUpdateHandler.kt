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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class AppUpdateHandler(private val context: Context) {
    // Lazy initialization ensures this is only created when needed
    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context)
    }

    private fun shouldPromptUser(): Boolean {
        return PreferenceManager.shouldAppUpdatePromptUser(context)
    }

    fun checkForUpdate(
        onUpdateAvailable: (AppUpdateInfo) -> Unit,
        onUpdateDownloaded: () -> Unit
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            val isAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateDownloaded()
                return@addOnSuccessListener
            }

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
        // Registering listener is a binder call, it must be executed in IO
        appUpdateManager.registerListener(listener)

        awaitClose {
            appUpdateManager.unregisterListener(listener)
        }
    }.flowOn(Dispatchers.IO) // <--- THIS FIXES THE ANR

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun saveLastPromptTime() {
        PreferenceManager.setAppUpdatePromptTime(context, System.currentTimeMillis())
    }
}