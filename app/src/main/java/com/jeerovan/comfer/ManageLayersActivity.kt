package com.jeerovan.comfer

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlin.math.roundToInt

class ManageLayersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManageLayersScreen()
        }
    }
}

data class DraggableApp(
    val appInfo: AppInfo,
    val listName: String
)

@Composable
fun ManageLayersScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var quickApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var primaryApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val allAppNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME) ?: emptySet()
        allApps = allAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

        val quickAppNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME) ?: emptySet()
        quickApps = quickAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

        val primaryAppNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME) ?: emptySet()
        primaryApps = primaryAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }
    }

    var draggedApp by remember { mutableStateOf<DraggableApp?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppListColumn(
                title = "Quick Apps",
                apps = quickApps,
                listName = AppInfoManager.QUICK_APPS_LIST_NAME,
                onDragStart = { app, offset ->
                    draggedApp = DraggableApp(app, AppInfoManager.QUICK_APPS_LIST_NAME)
                    dragOffset = offset
                },
                onDropTargetChanged = { isOver, listName ->
                    dropTarget = if (isOver) listName else null
                }
            )
            AppListColumn(
                title = "Primary Apps",
                apps = primaryApps,
                listName = AppInfoManager.PRIMARY_APPS_LIST_NAME,
                onDragStart = { app, offset ->
                    draggedApp = DraggableApp(app, AppInfoManager.PRIMARY_APPS_LIST_NAME)
                    dragOffset = offset
                },
                onDropTargetChanged = { isOver, listName ->
                    dropTarget = if (isOver) listName else null
                }
            )
            AppListColumn(
                title = "All Apps",
                apps = allApps,
                listName = AppInfoManager.ALL_APPS_LIST_NAME,
                onDragStart = { app, offset ->
                    draggedApp = DraggableApp(app, AppInfoManager.ALL_APPS_LIST_NAME)
                    dragOffset = offset
                },
                onDropTargetChanged = { isOver, listName ->
                    dropTarget = if (isOver) listName else null
                }
            )
        }

        draggedApp?.let { app ->
            Image(
                painter = rememberDrawablePainter(drawable = app.appInfo.icon),
                contentDescription = app.appInfo.label.toString(),
                modifier = Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .size(48.dp)
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                dropTarget?.let { targetListName ->
                                    if (targetListName != app.listName) {
                                        // Move app
                                        AppInfoManager.removeAppFromLayer(context, app.listName, app.appInfo.packageName)
                                        AppInfoManager.addAppToLayer(context, targetListName, app.appInfo.packageName)

                                        // Refresh lists
                                        val quickAppNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME) ?: emptySet()
                                        quickApps = quickAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }

                                        val primaryAppNames = AppInfoManager.getAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME) ?: emptySet()
                                        primaryApps = primaryAppNames.mapNotNull { mapPackageNameToAppInfo(packageManager, it) }
                                    }
                                }
                                draggedApp = null
                            }
                        )
                    }
            )
        }
    }
}

@Composable
fun AppListColumn(
    title: String,
    apps: List<AppInfo>,
    listName: String,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDropTargetChanged: (Boolean, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(120.dp)
            .padding(8.dp)
            .onGloballyPositioned { layoutCoordinates ->
                onDropTargetChanged(false, listName) // Reset when not dragging over
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppIconDraggable(
                    app = app,
                    onDragStart = { offset -> onDragStart(app, offset) }
                )
            }
        }
    }
}

@Composable
fun AppIconDraggable(app: AppInfo, onDragStart: (Offset) -> Unit) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, _ -> change.consume() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label.toString(),
            modifier = Modifier.padding(4.dp)
        )
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
