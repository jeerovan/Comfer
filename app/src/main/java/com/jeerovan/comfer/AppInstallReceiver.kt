package com.jeerovan.comfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        val allApps = AppInfoManager.getAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME)?.toMutableSet() ?: mutableSetOf()

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    allApps.add(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                allApps.remove(packageName)
                // Also remove from other lists
                val quickApps = AppInfoManager.getAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME)?.toMutableSet()
                quickApps?.remove(packageName)
                quickApps?.let { AppInfoManager.saveAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME, it) }

                val primaryApps = AppInfoManager.getAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME)?.toMutableSet()
                primaryApps?.remove(packageName)
                primaryApps?.let { AppInfoManager.saveAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME, it) }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    allApps.add(packageName)
                } else {
                    allApps.remove(packageName)
                }
            }
        }
        AppInfoManager.saveAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME, allApps)
    }
}
