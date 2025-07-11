package com.jeerovan.comfer

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val REST_LIST_NAME = "Rest"

data class ManageLayersUiState(
    val quickApps: List<AppInfo> = emptyList(),
    val primaryApps: List<AppInfo> = emptyList(),
    val restApps: List<AppInfo> = emptyList(),
    val dragAndDropState: DragAndDropState = DragAndDropState()
)

data class DragAndDropState(
    val draggedApp: AppInfo? = null,
    val sourceList: String? = null,
    val dragPosition: Offset = Offset.Zero,
    val dropTarget: String? = null,
    val quickColumnBounds: Rect? = null,
    val primaryColumnBounds: Rect? = null,
    val restColumnBounds: Rect? = null
)

class ManageLayersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ManageLayersUiState())
    val uiState: StateFlow<ManageLayersUiState> = _uiState.asStateFlow()

    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    init {
        refreshAppLists()
    }

    fun moveAppInList(listName: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentList = when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> _uiState.value.quickApps
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> _uiState.value.primaryApps
                REST_LIST_NAME -> _uiState.value.restApps
                else -> return@launch
            }.toMutableList()

            val app = currentList.removeAt(fromIndex)
            currentList.add(toIndex, app)

            when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, currentList.map { it.packageName }.toSet())
                    _uiState.update { it.copy(quickApps = currentList) }
                }
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> {
                    AppInfoManager.saveAppPackageNames(getApplication(), listName, currentList.map { it.packageName }.toSet())
                    _uiState.update { it.copy(primaryApps = currentList) }
                }
                REST_LIST_NAME -> {
                    _uiState.update { it.copy(restApps = currentList) }
                }
            }
        }
    }

    private fun refreshAppLists() {
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val allInstalledApps = packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { mapPackageNameToAppInfo(packageManager, it.activityInfo.packageName) }

            val quickAppNames = AppInfoManager.getAppPackageNames(getApplication(), AppInfoManager.QUICK_APPS_LIST_NAME) ?: emptySet()
            val quickApps = quickAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val primaryAppNames = AppInfoManager.getAppPackageNames(getApplication(), AppInfoManager.PRIMARY_APPS_LIST_NAME) ?: emptySet()
            val primaryApps = primaryAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

            val quickAndPrimaryPackages = quickApps.map { it.packageName }.toSet() + primaryApps.map { it.packageName }.toSet()
            val restApps = allInstalledApps.filter { it.packageName !in quickAndPrimaryPackages }

            _uiState.update {
                it.copy(
                    quickApps = quickApps,
                    primaryApps = primaryApps,
                    restApps = restApps
                )
            }
        }
    }

    private fun mapPackageNameToAppInfo(packageManager: PackageManager, packageName: String): AppInfo? {
        return try {
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->
                    AppInfo(
                        resolveInfo = resolveInfo,
                        icon = resolveInfo.loadIcon(packageManager),
                        label = resolveInfo.loadLabel(packageManager),
                        packageName = packageName
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}