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

    fun refreshAppLists() {
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val allInstalledApps = packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { mapPackageNameToAppInfo(packageManager, it.activityInfo.packageName) }

            val quickAppNames = AppInfoManager.getAppPackageNames(getApplication(), AppInfoManager.QUICK_APPS_LIST_NAME) ?: emptySet()
            val quickApps = quickAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }.sortedBy { it.label.toString() }

            val primaryAppNames = AppInfoManager.getAppPackageNames(getApplication(), AppInfoManager.PRIMARY_APPS_LIST_NAME) ?: emptySet()
            val primaryApps = primaryAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }.sortedBy { it.label.toString() }

            val quickAndPrimaryPackages = quickApps.map { it.packageName }.toSet() + primaryApps.map { it.packageName }.toSet()
            val restApps = allInstalledApps.filter { it.packageName !in quickAndPrimaryPackages }.sortedBy { it.label.toString() }

            _uiState.update {
                it.copy(
                    quickApps = quickApps,
                    primaryApps = primaryApps,
                    restApps = restApps
                )
            }
        }
    }

    fun onDragStart(app: AppInfo, sourceList: String, position: Offset) {
        _uiState.update {
            it.copy(
                dragAndDropState = it.dragAndDropState.copy(
                    draggedApp = app,
                    sourceList = sourceList,
                    dragPosition = position
                )
            )
        }
    }

    fun onDrag(dragAmount: Offset) {
        val currentDragPosition = _uiState.value.dragAndDropState.dragPosition + dragAmount
        val newDropTarget = when {
            _uiState.value.dragAndDropState.quickColumnBounds?.contains(currentDragPosition) == true -> AppInfoManager.QUICK_APPS_LIST_NAME
            _uiState.value.dragAndDropState.primaryColumnBounds?.contains(currentDragPosition) == true -> AppInfoManager.PRIMARY_APPS_LIST_NAME
            _uiState.value.dragAndDropState.restColumnBounds?.contains(currentDragPosition) == true -> REST_LIST_NAME
            else -> null
        }
        _uiState.update {
            it.copy(
                dragAndDropState = it.dragAndDropState.copy(
                    dragPosition = currentDragPosition,
                    dropTarget = newDropTarget
                )
            )
        }
    }

    fun onDragEnd() {
        val dndState = _uiState.value.dragAndDropState
        if (dndState.draggedApp != null && dndState.sourceList != null && dndState.dropTarget != null && dndState.sourceList != dndState.dropTarget) {
            val appToMove = dndState.draggedApp
            val from = dndState.sourceList
            val to = dndState.dropTarget

            if (from == AppInfoManager.QUICK_APPS_LIST_NAME || from == AppInfoManager.PRIMARY_APPS_LIST_NAME) {
                AppInfoManager.removeAppFromLayer(getApplication(), from, appToMove.packageName)
            }
            if (to == AppInfoManager.QUICK_APPS_LIST_NAME || to == AppInfoManager.PRIMARY_APPS_LIST_NAME) {
                AppInfoManager.addAppToLayer(getApplication(), to, appToMove.packageName)
            }
            refreshAppLists()
        }
        _uiState.update { it.copy(dragAndDropState = DragAndDropState()) } // Reset
    }

    fun updateColumnBounds(listName: String, bounds: Rect) {
        _uiState.update {
            val dndState = it.dragAndDropState
            when (listName) {
                AppInfoManager.QUICK_APPS_LIST_NAME -> it.copy(dragAndDropState = dndState.copy(quickColumnBounds = bounds))
                AppInfoManager.PRIMARY_APPS_LIST_NAME -> it.copy(dragAndDropState = dndState.copy(primaryColumnBounds = bounds))
                REST_LIST_NAME -> it.copy(dragAndDropState = dndState.copy(restColumnBounds = bounds))
                else -> it
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
