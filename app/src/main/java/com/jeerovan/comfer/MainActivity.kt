package com.jeerovan.comfer

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import com.jeerovan.comfer.utils.GuideUtil.GuideDialog
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.text.TextUtils
import android.view.SoundEffectConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Shape
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.view.ContextThemeWrapper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.core.content.edit
import kotlin.text.ifEmpty
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.StrokeCap
import java.util.Calendar

import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.atan2
import com.jeerovan.comfer.utils.CommonUtil.handleStartActivity

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.google.android.play.core.install.model.InstallStatus
import com.jeerovan.comfer.utils.CommonUtil.doesMatchSearch
import com.jeerovan.comfer.utils.KeyboardLayoutEngine
import com.jeerovan.comfer.utils.KeyboardLocale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.resolveAsTypeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

// 1. Define a custom exception to safely interrupt the animation
private class SnapEarlyException : CancellationException("Handing off to snap phase")

data class Contact(
    val id: Long,
    val name: String?,
    val photoUri: Uri?,
    val number: String?
)

// Enum to manage the active tab state
enum class SearchTab {
    APPS, CONTACTS
}

data class BatteryState(val level: Int, val isCharging: Boolean)

private const val MAIN_WIDGET_HOST_ID = 1025
private const val LEFT_SIDE_WIDGET_HOST_ID = 1024
private const val RIGHT_SIDE_WIDGET_HOST_ID = 1026
private const val BOUND_WIDGETS_KEY = "bound_widgets_v2"

@Serializable
data class PersistableBoundWidget(
    val widgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val gridX: Int,
    val gridY: Int,
    val spanX: Int,
    val spanY: Int
)

data class BoundWidget(
    val widgetId: Int,
    val providerInfo: AppWidgetProviderInfo,
    var gridX: Int,
    var gridY: Int,
    var spanX: Int,
    var spanY: Int
)

data class WidgetProviderGroup(
    val appName: String,
    val appIcon: Drawable?,
    val providers: List<AppWidgetProviderInfo>
)

class MainActivity : AppCompatActivity() {
    private val appInfoViewModel: AppInfoViewModel by viewModels()
    private val settingsViewModel:SettingsViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    // Widgets
    private lateinit var widgetHosts: WidgetHostManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Consolidated Widget Host Management
        widgetHosts = WidgetHostManager(applicationContext).apply {
            initHosts()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mainViewModel.onBackButtonPressed()
            }
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Only set colors for Android 14 and below to avoid deprecation warnings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        // Handle display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            ComferTheme {
                LauncherScreen(
                    appInfoViewModel,
                    settingsViewModel,
                    mainViewModel,
                    widgetHosts = widgetHosts
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            settingsViewModel.loadSettings()
            mainViewModel.reloadImagePath()
        }
        try {
            widgetHosts.startListening()
        } catch (e: RuntimeException) {
            // Log the error if needed, but safe to ignore as it's a system-side failure
            Log.e("MainActivity", "System widget service crash in stopListening", e)
        } catch (e: NullPointerException) {
            // Some variations of this crash might throw NPE directly
            Log.e("MainActivity", "System widget service NPE in stopListening", e)
        }
    }

    override fun onStop(){
        super.onStop()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Check if the screen is ON or OFF
        if (powerManager.isInteractive) {
            lifecycleScope.launch {
                mainViewModel.clearImagePath()
            }
        }
        try {
            widgetHosts.stopListening()
        } catch (e: RuntimeException) {
            // Log the error if needed, but safe to ignore as it's a system-side failure
            Log.e("MainActivity", "System widget service crash in stopListening", e)
        } catch (e: NullPointerException) {
            // Some variations of this crash might throw NPE directly
            Log.e("MainActivity", "System widget service NPE in stopListening", e)
        }
    }
}

@Composable
fun WidgetHostScreen(
    modifier:Modifier = Modifier,
    appWidgetManager: AppWidgetManager,
    appWidgetHost: AppWidgetHost,
    widgetPrefsTitle: String,
    gridColumns: Int,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stringWidgetBindingCancelled = stringResource(R.string.widget_binding_cancelled)
    val prefs = remember {
        context.getSharedPreferences(widgetPrefsTitle, MODE_PRIVATE)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    var editMode by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val boundWidgets = remember { mutableStateListOf<BoundWidget>() }
    val allWidgetProviderGroups = remember { mutableStateListOf<WidgetProviderGroup>() }
    val widgetProviderGroups = remember { mutableStateListOf<WidgetProviderGroup>() }
    val isDarkTheme = isSystemInDarkTheme()
    val isFirstOnResume = remember { mutableStateOf(true) }

    fun updateWidgetGroups (){
        // Create a set of provider ComponentNames that are already bound for efficient lookup.
        val boundProviderNames = boundWidgets.map { it.providerInfo.provider }.toSet()

        // Map over the original list of all providers to create a new filtered list.
        val filteredGroups = allWidgetProviderGroups.mapNotNull { group ->
            // For each group, filter its list of providers to exclude the ones already bound.
            val availableProviders = group.providers.filter { providerInfo ->
                providerInfo.provider !in boundProviderNames
            }

            // If the group still has available providers after filtering, create a new
            // group object with the filtered list. Otherwise (if the group is now empty),
            // return null to have it removed from the final list by mapNotNull.
            if (availableProviders.isNotEmpty()) {
                group.copy(providers = availableProviders) // Assumes WidgetProviderGroup is a data class
            } else {
                null
            }
        }
        // Atomically update the state list that is passed to the picker.
        widgetProviderGroups.clear()
        widgetProviderGroups.addAll(filteredGroups)
    }
    /**
     * Fetches the latest widget providers and syncs the app's state. It cleans up
     * any bound widgets whose provider is no longer available (e.g., app uninstalled).
     */
    fun syncAndRefreshProviders() {
        coroutineScope.launch {
            // Fetch the current list of all available widget providers from the system.
            val newProviderGroups = withContext(Dispatchers.IO) {
                getGroupedWidgetProviders(context)
            }

            // On subsequent onResume calls (not the first), sync state and clean up.
            if (!isFirstOnResume.value && allWidgetProviderGroups.isNotEmpty()) {
                // Create a set of all currently available provider ComponentNames for fast lookups.
                val availableProviderNames = newProviderGroups.flatMap { it.providers }.map { it.provider }.toSet()

                // Identify bound widgets whose providers are no longer in the available list.
                val widgetsToRemove = boundWidgets.filter { widget ->
                    widget.providerInfo.provider !in availableProviderNames
                }

                // If there are orphaned widgets, remove them.
                if (widgetsToRemove.isNotEmpty()) {
                    widgetsToRemove.forEach { widget ->
                        appWidgetHost.deleteAppWidgetId(widget.widgetId)
                    }
                    boundWidgets.removeAll(widgetsToRemove)
                    // Persist the cleaned list to SharedPreferences.
                    saveWidgetsToPrefs(prefs, boundWidgets)
                }
            }

            // Update the master list of all providers with the newly fetched data.
            allWidgetProviderGroups.clear()
            allWidgetProviderGroups.addAll(newProviderGroups)

            // If this was the first onResume, flip the flag.
            if (isFirstOnResume.value) {
                isFirstOnResume.value = false
            }

            // Finally, refresh the UI-facing list.
            updateWidgetGroups()
        }
    }
    // Hook into the onResume lifecycle event.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    syncAndRefreshProviders()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    fun refreshWidgets(){
        val providerClasses = boundWidgets
            .map { it.providerInfo.provider.className }
            .distinct()

        // Send an update broadcast for each provider class
        providerClasses.forEach { className ->
            try {
                val providerClass = Class.forName(className)
                val componentName = ComponentName(context, providerClass)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, providerClass).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (_: ClassNotFoundException) {
            // Handle case where the provider class can't be found, if necessary
            }
        }
    }
    // This effect runs when the composable first launches and any time isDarkTheme changes.
    LaunchedEffect(isDarkTheme, ) {
        // Find all unique provider classes from the currently bound widgets
        withContext(Dispatchers.IO) {
            refreshWidgets()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            //.border(width=1.dp,Color.Blue)
            .detectSwipes(
                Unit,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (editMode) {
                            editMode = false
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        editMode = !editMode
                    }
                )
            }
    ) {
        // 1. Get exact dimensions available to this Box
        // maxHeight and maxWidth are provided by BoxWithConstraintsScope
        val containerHeight = maxHeight
        val containerWidth = maxWidth

        // 2. Perform your Grid Calculations here (inside the scope)
        val gapWidth = 8.dp

        // Convert Dp to Px for math
        // LocalDensity is available here
        val density = LocalDensity.current

        val windowHeightPx = with(density) { containerHeight.toPx() }
        val screenWidthPx = with(density) { containerWidth.toPx() }
        val gapWidthPx = with(density) { gapWidth.toPx() }

        val totalHorizontalGapPx = (gridColumns + 1) * gapWidthPx
        val totalAvailableWidth = screenWidthPx - totalHorizontalGapPx
        val cellWidthPx = totalAvailableWidth / gridColumns

        val minHeightPx = cellWidthPx // Assuming square cells
        val totalGridRows = floor(windowHeightPx / (minHeightPx + gapWidthPx)).toInt()

        val totalVerticalGapPx = (totalGridRows + 1) * gapWidthPx
        val totalAvailableHeight = windowHeightPx - totalVerticalGapPx
        val cellHeightPx = if (totalGridRows > 0) totalAvailableHeight / totalGridRows else 0f

        val currentGridRows by rememberUpdatedState(totalGridRows)

        fun createWidgetView(provider: AppWidgetProviderInfo,widgetId:Int){
            val position = findNextAvailableCell(boundWidgets, gridColumns,totalGridRows)
            if(position != null) {
                val newWidget = BoundWidget(widgetId, provider, position.first, position.second, 3, 3)
                boundWidgets.add(newWidget)
                coroutineScope.launch {
                    saveWidgetsToPrefs(prefs, boundWidgets)
                    updateWidgetGroups()
                }
            } else {
                appWidgetHost.deleteAppWidgetId(widgetId)
            }
        }

        val configureWidgetLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val appWidgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (result.resultCode == Activity.RESULT_OK) {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (provider != null) {
                        Log.i("configureWidgetLauncher", "Creating Widget:$appWidgetId:${provider.provider}")
                        createWidgetView(provider, appWidgetId)
                    } else {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                        Log.i("configureWidgetLauncher", "Provider is NULL")
                    }
                } else {
                    Log.i("configureWidgetLauncher","Invalid widgetId")
                }
            } else {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                }
                Toast.makeText(context, stringWidgetBindingCancelled, Toast.LENGTH_SHORT).show()
            }
        }
        fun checkConfigureWidget(provider: AppWidgetProviderInfo,appWidgetId:Int) {
            if (provider.configure != null) {
                // This widget needs configuration
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                intent.component = provider.configure
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                // Use ActivityResultLauncher to start the activity and handle the result
                try {
                    Log.i("CheckConfigureWidget","Running configureWidgetLauncher")
                    configureWidgetLauncher.launch(intent)
                } catch (e:Exception){
                    Log.e( "configureWidgetLauncher.launch failed",e.toString())
                    createWidgetView(provider,appWidgetId)
                }
            } else {
                // No configuration needed, create the widget view directly
                Log.i("CheckConfigureWidget","Not Required")
                createWidgetView(provider, appWidgetId)
            }
        }
        val bindWidgetLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val appWidgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (result.resultCode == Activity.RESULT_OK) {
                if( appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (provider != null) {
                        Log.i("BindWidgetLauncher", "Checking configuration")
                        checkConfigureWidget(provider, appWidgetId)
                    } else {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                        Log.i("BindWidgetLauncher", "provider is null: $appWidgetId")
                    }
                } else {
                    Log.i("BindWidgetLauncher", "Invalid appWidgetId")
                }
            } else {
                // User cancelled the binding. Clean up the allocated ID.
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                }
                Toast.makeText(context, stringWidgetBindingCancelled, Toast.LENGTH_SHORT).show()
            }
        }

        // Load widgets from SharedPreferences on startup
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val loadedWidgets = loadWidgetsFromPrefs(prefs, appWidgetManager)
                withContext(Dispatchers.Main) {
                    boundWidgets.clear()
                    boundWidgets.addAll(loadedWidgets)
                }
            }
        }

        if (boundWidgets.isEmpty() && !editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center, // Center the Text inside the Box
            ) {
                Text(
                    text = stringResource(R.string.long_press_to_add_edit_widgets),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center, // Ensure placeholder text is centered
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp) // Inner padding for the text
                )
            }
        }
        if (currentGridRows > 0) {
            WidgetGrid(
                boundWidgets = boundWidgets,
                appWidgetHost = appWidgetHost,
                gapWidth,
                gridColumns,
                windowHeightPx,
                cellWidthPx,
                cellHeightPx,
                totalGridRows,
                editMode = editMode,
                onWidgetUpdate = {
                    coroutineScope.launch { saveWidgetsToPrefs(prefs, boundWidgets) }
                },
                onWidgetRemove = { widgetToRemove ->
                    appWidgetHost.deleteAppWidgetId(widgetToRemove.widgetId)
                    boundWidgets.remove(widgetToRemove)
                    coroutineScope.launch {
                        saveWidgetsToPrefs(prefs, boundWidgets)
                        updateWidgetGroups()
                    }
                },
                onAddClick = { showPicker = true }
            )
        }

        if (showPicker) {
            WidgetPickerFullScreen(
                onDismiss = { showPicker = false },
                onWidgetSelected = { provider ->
                    showPicker = false
                    val appWidgetId = appWidgetHost.allocateAppWidgetId()
                    Log.i("WidgetHost","Allocated WidgetId: $appWidgetId")
                    val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
                    if (canBind) {
                        checkConfigureWidget(provider,appWidgetId)
                    } else {
                        Log.i("WidgetHost","Can NOT bind: $appWidgetId")
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                        }
                        try {
                            Log.i("WidgetHost","Calling bindWidgetLauncher")
                            bindWidgetLauncher.launch(intent)
                        } catch (e:Exception){
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                appWidgetHost.deleteAppWidgetId(appWidgetId)
                            }
                            Log.i("bindWidgetLauncher.launch failed", e.toString())
                        }
                    }
                },
                widgetProviderGroups = widgetProviderGroups
            )
        }
    }
}

// --- Widget Grid ---
@Composable
fun WidgetGrid(
    boundWidgets: List<BoundWidget>,
    appWidgetHost: AppWidgetHost,
    gapWidth: Dp,
    gridColumns: Int,
    windowHeightPx: Float,
    cellWidthPx: Float,
    cellHeightPx: Float,
    totalGridRows: Int,
    editMode: Boolean,
    onWidgetUpdate: () -> Unit,
    onWidgetRemove: (BoundWidget) -> Unit,
    onAddClick: () -> Unit
) {
    var beingRearranged by remember { mutableStateOf(false) }
    val gapWidthPx = with(LocalDensity.current) { gapWidth.toPx() }

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = gapWidth)
        //.border(width = 1.dp,Color.Red)
    ) {
        boundWidgets.forEach { widget ->
            key(widget.widgetId) {
                WidgetInstance(
                    widget = widget,
                    allWidgets = boundWidgets,
                    appWidgetHost = appWidgetHost,
                    editMode = editMode,
                    gridColumns,
                    windowHeightPx,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    gapPx = gapWidthPx,
                    onUpdate = onWidgetUpdate,
                    onRemove = onWidgetRemove,
                    beingRearranged = {flag:Boolean -> beingRearranged = flag}
                )
            }
        }
        if(editMode && !beingRearranged){
            val position = findNextAvailableCell(boundWidgets,gridColumns,totalGridRows)
            if(position != null) {
                WidgetAddButton(
                    position,
                    cellWidthPx,
                    cellHeightPx,
                    gapWidthPx,
                    onAddClick
                )
            }
        }
    }
}
@Composable
fun WidgetAddButton(
    buttonPosition: Pair<Int,Int>,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gapPx: Float,
    onAddClick: () -> Unit
) {

    val view = LocalView.current
    val gridX = buttonPosition.first
    val gridY = buttonPosition.second
    val spanX = 3
    val spanY = 3
    val initialX = (gridX * (cellWidthPx + gapPx))
    val initialY = (gridY * (cellHeightPx + gapPx)) + gapPx
    val initialWidth = (spanX * cellWidthPx) + ((spanX - 1) * gapPx)
    val initialHeight = (spanY * cellHeightPx) + ((spanY - 1) * gapPx)

    var position by remember { mutableStateOf(Offset(initialX, initialY)) }
    var size by remember { mutableStateOf(IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())) }

    // Re-sync position and size if the widget's grid properties change externally
    LaunchedEffect(gridX, gridY) {
        position = Offset(initialX, initialY)
        size = IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())
    }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        onAddClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ){
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                stringResource(R.string.add_widget))
        }
    }
}

@Composable
private fun WidgetInstance(
    widget: BoundWidget,
    allWidgets: List<BoundWidget>,
    appWidgetHost: AppWidgetHost,
    editMode: Boolean,
    gridColumns: Int,
    windowHeightPx: Float,
    cellWidthPx: Float,
    cellHeightPx: Float,
    gapPx: Float,
    onUpdate: () -> Unit,
    onRemove: (BoundWidget) -> Unit,
    beingRearranged: (Boolean) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    // Capture density and providerInfo during composition
    val density = LocalDensity.current
    val appWidgetProviderInfo = remember { widget.providerInfo }

    // Initial grid-based calculations
    val initialX = (widget.gridX * (cellWidthPx + gapPx))
    val initialY = (widget.gridY * (cellHeightPx + gapPx)) + gapPx
    val initialWidth = (widget.spanX * cellWidthPx) + ((widget.spanX - 1) * gapPx)
    val initialHeight = (widget.spanY * cellHeightPx) + ((widget.spanY - 1) * gapPx)

    var position by remember { mutableStateOf(Offset(initialX, initialY)) }
    var size by remember { mutableStateOf(IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())) }
    var widgetUpdated by remember { mutableStateOf(false) }

    // Widget view state management
    var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Re-sync position and size if the widget's grid properties change externally
    LaunchedEffect(widget.gridX, widget.gridY, widget.spanX, widget.spanY) {
        position = Offset(initialX, initialY)
        size = IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())
    }

    // Async widget initialization to prevent ANR
    LaunchedEffect(widget.widgetId) {
        isLoading = true
        hasError = false

        // Capture size values during composition context
        val width = with(density) { size.width.toDp().value.toInt() }
        val height = with(density) { size.height.toDp().value.toInt() }

        withContext(Dispatchers.Default) {
            try {
                // Create themed context off main thread
                val themedContext = ContextThemeWrapper(
                    context.applicationContext,
                    android.R.style.Theme_DeviceDefault
                )

                // Switch to main thread for widget view creation
                withContext(Dispatchers.Main) {
                    try {
                        // Create widget view with providerInfo
                        val view = appWidgetHost.createView(
                            themedContext,
                            widget.widgetId,
                            appWidgetProviderInfo  // This was missing!
                        )

                        val options = Bundle().apply {
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                        }

                        view.updateAppWidgetOptions(options)
                        view.setAppWidget(widget.widgetId, appWidgetProviderInfo)

                        hostView = view
                        isLoading = false
                    } catch (e: Exception) {
                        Log.e("WidgetInstance", "Error setting up widget", e)
                        hasError = true
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                Log.e("WidgetInstance", "Error creating widget view", e)
                withContext(Dispatchers.Main) {
                    hasError = true
                    isLoading = false
                }
            }
        }
    }

    val windowWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val minWidgetSizePx = with(density) { 40.dp.toPx() }

    fun getBundleOptionsFromCurrentSize(): Bundle {
        val width = with(density) { size.width.toDp().value.toInt() }
        val height = with(density) { size.height.toDp().value.toInt() }
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
    }

    Box { // Parent container for the widget and its handles
        // Main widget Box, which is also the repositioning drag area
        Box(
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                .border(
                    width = if (editMode) 1.dp else 0.dp,
                    color = if (editMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .pointerInput(editMode, allWidgets) {
                    if (editMode) {
                        detectDragGestures(
                            onDragEnd = {
                                val finalGridX = ((position.x) / (cellWidthPx + gapPx)).roundToInt()
                                val finalGridY =
                                    ((position.y - gapPx) / (cellHeightPx + gapPx)).roundToInt()

                                if (widget.gridX != finalGridX || widget.gridY != finalGridY) {
                                    widget.gridX = finalGridX
                                    widget.gridY = finalGridY
                                    onUpdate()
                                }
                                position = Offset(
                                    widget.gridX * (cellWidthPx + gapPx),
                                    widget.gridY * (cellHeightPx + gapPx) + gapPx
                                )
                                beingRearranged(false)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val newPos = Offset(
                                x = (position.x + dragAmount.x).coerceIn(
                                    0f,
                                    windowWidthPx - size.width
                                ),
                                y = (position.y + dragAmount.y).coerceIn(
                                    gapPx,
                                    windowHeightPx - size.height
                                )
                            )

                            val newGridX = ((newPos.x) / (cellWidthPx + gapPx)).roundToInt()
                                .coerceIn(0, gridColumns - widget.spanX)
                            val newGridY =
                                ((newPos.y - gapPx) / (cellHeightPx + gapPx)).roundToInt()
                                    .coerceAtLeast(0)
                            val proposedRect = IntRect(
                                newGridX,
                                newGridY,
                                newGridX + widget.spanX,
                                newGridY + widget.spanY
                            )

                            if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                                position = newPos
                            }
                            beingRearranged(true)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                hasError -> {
                    Text(
                        text = stringResource(R.string.could_not_load_widget),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                hostView != null -> {
                    AndroidView(
                        factory = { hostView!! },
                        update = { view ->
                            if (!widgetUpdated) {
                                widgetUpdated = true
                                try {
                                    view.updateAppWidgetOptions(getBundleOptionsFromCurrentSize())
                                } catch (e: Exception) {
                                    Log.e("WidgetInstance", "Error updating widget options", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // --- Edit Mode Controls ---
        AnimatedVisibility(visible = editMode, enter = fadeIn(), exit = fadeOut()) {
            val handleSize = 12.dp
            val handleSizePx = with(density) { handleSize.toPx() }

            val onResizeEnd: () -> Unit = {
                val finalSpanX = max(1, (size.width / (cellWidthPx + gapPx)).roundToInt()).coerceAtMost(gridColumns - widget.gridX)
                val finalSpanY = max(1, (size.height / (cellHeightPx + gapPx)).roundToInt())

                val finalGridX = ((position.x) / (cellWidthPx + gapPx)).roundToInt()
                val finalGridY = ((position.y - gapPx) / (cellHeightPx + gapPx)).roundToInt()

                if (widget.spanX != finalSpanX || widget.spanY != finalSpanY || widget.gridX != finalGridX || widget.gridY != finalGridY) {
                    widget.spanX = finalSpanX
                    widget.spanY = finalSpanY
                    widget.gridX = finalGridX
                    widget.gridY = finalGridY
                    onUpdate()
                }

                position = Offset(widget.gridX * (cellWidthPx + gapPx), widget.gridY * (cellHeightPx + gapPx) + gapPx)
                size = IntSize(
                    (widget.spanX * cellWidthPx + (widget.spanX - 1) * gapPx).roundToInt(),
                    (widget.spanY * cellHeightPx + (widget.spanY - 1) * gapPx).roundToInt()
                )
                beingRearranged(false)
                widgetUpdated = false
            }

            val resizeModifier = Modifier
                .size(handleSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)

            // Right (Resize)
            Box(modifier = Modifier
                .offset(
                    x = with(density) { (position.x + size.width - handleSizePx / 2).toDp() },
                    y = with(density) { (position.y + size.height / 2 - handleSizePx / 2).toDp() }
                )
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onResizeEnd() }) { change, dragAmount ->
                        change.consume()
                        val newWidth = (size.width + dragAmount.x).coerceIn(
                            minWidgetSizePx,
                            windowWidthPx - position.x
                        )
                        val newSpanX =
                            max(1, (newWidth / (cellWidthPx + gapPx)).roundToInt()).coerceAtMost(
                                gridColumns - widget.gridX
                            )
                        val proposedRect = IntRect(
                            widget.gridX,
                            widget.gridY,
                            widget.gridX + newSpanX,
                            widget.gridY + widget.spanY
                        )

                        if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                            size = IntSize(newWidth.roundToInt(), size.height)
                        }
                        beingRearranged(true)
                    }
                }
                .then(resizeModifier)
            )

            // Left (Resize)
            Box(modifier = Modifier
                .offset(
                    x = with(density) { (position.x - handleSizePx / 2).toDp() },
                    y = with(density) { (position.y + size.height / 2 - handleSizePx / 2).toDp() }
                )
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onResizeEnd() }) { change, dragAmount ->
                        change.consume()
                        val newX = (position.x + dragAmount.x).coerceAtLeast(0f)
                        val widthChange = position.x - newX
                        val newWidth = (size.width + widthChange).coerceAtLeast(minWidgetSizePx)

                        val newGridX =
                            ((newX) / (cellWidthPx + gapPx)).roundToInt().coerceAtLeast(0)
                        val newSpanX = max(1, (newWidth / (cellWidthPx + gapPx)).roundToInt())

                        if (newGridX + newSpanX <= gridColumns) {
                            val proposedRect = IntRect(
                                newGridX,
                                widget.gridY,
                                newGridX + newSpanX,
                                widget.gridY + widget.spanY
                            )
                            if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                                position = Offset(newX, position.y)
                                size = IntSize(newWidth.roundToInt(), size.height)
                            }
                        }
                        beingRearranged(true)
                    }
                }
                .then(resizeModifier)
            )

            // Bottom (Resize)
            Box(modifier = Modifier
                .offset(
                    x = with(density) { (position.x + size.width / 2 - handleSizePx / 2).toDp() },
                    y = with(density) { (position.y + size.height - handleSizePx / 2).toDp() }
                )
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onResizeEnd() }) { change, dragAmount ->
                        change.consume()
                        val newHeight = (size.height + dragAmount.y).coerceIn(
                            minWidgetSizePx,
                            windowHeightPx - position.y
                        )
                        val newSpanY = max(1, (newHeight / (cellHeightPx + gapPx)).roundToInt())
                        val proposedRect = IntRect(
                            widget.gridX,
                            widget.gridY,
                            widget.gridX + widget.spanX,
                            widget.gridY + newSpanY
                        )

                        if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                            size = IntSize(size.width, newHeight.roundToInt())
                        }
                        beingRearranged(true)
                    }
                }
                .then(resizeModifier)
            )

            // Top (Resize)
            Box(modifier = Modifier
                .offset(
                    x = with(density) { (position.x + size.width / 2 - handleSizePx / 2).toDp() },
                    y = with(density) { (position.y - handleSizePx / 2).toDp() }
                )
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onResizeEnd() }) { change, dragAmount ->
                        change.consume()
                        val newY = (position.y + dragAmount.y).coerceAtLeast(gapPx)
                        val heightChange = position.y - newY
                        val newHeight = (size.height + heightChange).coerceAtLeast(minWidgetSizePx)

                        val newGridY =
                            ((newY - gapPx) / (cellHeightPx + gapPx)).roundToInt().coerceAtLeast(0)
                        val newSpanY = max(1, (newHeight / (cellHeightPx + gapPx)).roundToInt())

                        val proposedRect = IntRect(
                            widget.gridX,
                            newGridY,
                            widget.gridX + widget.spanX,
                            newGridY + newSpanY
                        )
                        if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                            position = Offset(position.x, newY)
                            size = IntSize(size.width, newHeight.roundToInt())
                        }
                        beingRearranged(true)
                    }
                }
                .then(resizeModifier)
            )

            // Remove Button
            Box(
                modifier = Modifier
                    .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .size(
                        with(density) { size.width.toDp() },
                        with(density) { size.height.toDp() })
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        onRemove(widget)
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.remove_widget)
                    )
                }
            }
        }
    }
}

private fun isColliding(
    proposedRect: IntRect,
    currentWidgetId: Int,
    allWidgets: List<BoundWidget>
): Boolean {
    for (other in allWidgets) {
        if (other.widgetId == currentWidgetId) continue

        if (proposedRect.left >= other.gridX + other.spanX ||
            proposedRect.right <= other.gridX ||
            proposedRect.top >= other.gridY + other.spanY ||
            proposedRect.bottom <= other.gridY) {
            continue
        }
        return true
    }
    return false
}

// --- Full-Screen Widget Picker ---
@Composable
fun WidgetPickerFullScreen(
    onDismiss: () -> Unit,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit,
    widgetProviderGroups: List<WidgetProviderGroup>
) {
    val context = LocalContext.current
    val view = LocalView.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(widgetProviderGroups) { group ->
                    Card(elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = group.appIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(group.appName, style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.height(16.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(group.providers) { provider ->
                                    WidgetPreviewItem(
                                        provider = provider,
                                        onSelected = {
                                            view.playSoundEffect(SoundEffectConstants.CLICK)
                                            onWidgetSelected(provider)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
        Box(modifier = Modifier.fillMaxSize()) { // Use a Box to control alignment
            SmallFloatingActionButton(
                onClick = {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    onDismiss()
                },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd) // Align to the bottom-right corner
                    .padding(16.dp) // Add standard margin from the edges
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
    }
}
@Composable
private fun WidgetPreviewItem(
    provider: AppWidgetProviderInfo,
    onSelected: () -> Unit
) {
    val context = LocalContext.current
    var previewDrawable by remember { mutableStateOf<Drawable?>(null) }
    val label = remember(provider) { provider.loadLabel(context.packageManager) }

    LaunchedEffect(provider) {
        withContext(Dispatchers.IO) {
            val drawable = provider.loadPreviewImage(context, 0)
                ?: provider.loadIcon(context, 0)
            withContext(Dispatchers.Main) {
                previewDrawable = drawable
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .clickable(enabled = previewDrawable != null) { onSelected() }
    ) {
        if (previewDrawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable = previewDrawable),
                contentDescription = label,
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Show placeholder while loading
            Box(
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
    }
}

// --- Utility & Persistence Functions ---
private suspend fun getGroupedWidgetProviders(context: Context): List<WidgetProviderGroup> = withContext(Dispatchers.IO) {
    try {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val packageManager = context.packageManager

        // SAFEGUARD: This IPC call is where the DeadSystemException happens
        val installedProviders = try {
            appWidgetManager.installedProviders
        } catch (e: RuntimeException) {
            return@withContext emptyList()
        }

        installedProviders.groupBy { it.provider.packageName }
            .map { (packageName, providers) ->
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    WidgetProviderGroup(
                        appName = appInfo.loadLabel(packageManager).toString(),
                        appIcon = appInfo.loadIcon(packageManager),
                        providers = providers
                    )
                } catch (_: Exception) {
                    null // App might have been uninstalled
                }
            }.filterNotNull()

    } catch (e: Exception) {
        // Catch-all for other unexpected errors during mapping
        emptyList()
    }
}

private suspend fun loadWidgetsFromPrefs(
    prefs: SharedPreferences,
    appWidgetManager: AppWidgetManager
): List<BoundWidget> = withContext(Dispatchers.IO) {
    val jsonString = prefs.getString(BOUND_WIDGETS_KEY, null) ?: return@withContext emptyList()
    try {
        val persistableList = Json.decodeFromString<List<PersistableBoundWidget>>(jsonString)

        // SAFEGUARD: Isolate the risky IPC call
        val installedProviders = try {
            appWidgetManager.installedProviders
        } catch (e: RuntimeException) {
            return@withContext emptyList()
        }

        persistableList.mapNotNull { persist ->
            val provider = installedProviders.find {
                it.provider == ComponentName(persist.providerPackage, persist.providerClass)
            }
            if (provider != null) {
                BoundWidget(persist.widgetId, provider, persist.gridX, persist.gridY, persist.spanX, persist.spanY)
            } else {
                null // Provider not found, maybe app was uninstalled
            }
        }
    } catch (e: Exception) {
        Log.e("LoadWidgetsFromPrefs", "Error loading widgets", e)
        emptyList()
    }
}
private suspend fun saveWidgetsToPrefs(
    prefs: SharedPreferences,
    widgets: List<BoundWidget>
) = withContext(Dispatchers.IO) {
    val persistableList = widgets.map {
        PersistableBoundWidget(
            it.widgetId,
            it.providerInfo.provider.packageName,
            it.providerInfo.provider.className,
            it.gridX,
            it.gridY,
            it.spanX,
            it.spanY
        )
    }
    val jsonString = Json.encodeToString(persistableList)
    prefs.edit { putString(BOUND_WIDGETS_KEY, jsonString) }
}


private fun findNextAvailableCell(widgets: List<BoundWidget>,
                                  gridColumns: Int,
                                  gridRows: Int): Pair<Int, Int>? {
    val spanX = 3
    val spanY = 3
    // Iterate through rows up to the calculated max
    for (y in 0..(gridRows - spanY)) {
        for (x in 0..(gridColumns - spanX)) {
            val rect = IntRect(x, y, x + spanX, y + spanY)
            val collision = widgets.any {
                val otherRect = IntRect(it.gridX, it.gridY, it.gridX + it.spanX, it.gridY + it.spanY)
                rect.overlaps(otherRect)
            }
            if (!collision) {
                return Pair(x, y)
            }
        }
    }
    // No space was found within the dynamic grid bounds
    return null
}

@Immutable
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun overlaps(other: IntRect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }
}

@Composable
fun rememberBatteryState(): State<BatteryState> {
    // Use ApplicationContext to avoid Activity leaks and decouple from Activity lifecycle
    val context = LocalContext.current.applicationContext

    return produceState(initialValue = BatteryState(-1, false), key1 = context) {
        // callbackFlow adapts the callback-based BroadcastReceiver to a Coroutine Flow
        val batteryFlow = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryLevel = if (level != -1 && scale != -1) {
                        (level * 100 / scale.toFloat()).toInt()
                    } else {
                        -1
                    }

                    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    trySend(BatteryState(batteryLevel, isCharging))
                }
            }

            try {
                // Registering receiver is an IPC call; doing it on IO prevents Main Thread ANRs
                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (e: Exception) {
                // Catches DeadSystemRuntimeException (Crash fix) and SecurityException
                e.printStackTrace()
            }

            awaitClose {
                try {
                    // Unregistering is also an IPC call; safe to do here as flowOn(IO) handles the context
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore exceptions during unregistration (e.g., if system service is dead)
                }
            }
        }.flowOn(Dispatchers.IO) // CRITICAL: Moves register/unregister/onReceive ops to background thread

        // Collect the flow on the Main dispatcher (default for produceState) to update State safely
        batteryFlow.collect { newState ->
            value = newState
        }
    }
}



@Composable
fun BatteryStatus(
    settings: SettingsUiState,
    foregroundColor: Color,
    showBorder: Boolean,
    backgroundColor: Color = Color.Black
) {
    val customColor = !settings.autoWallpapers && !settings.monochrome
    val themeColor = if(customColor) settings.batteryColor.copy(alpha = settings.batteryAlpha/100f) else foregroundColor
    val shadowColor = if(customColor) Color.Transparent.toArgb() else backgroundColor.toArgb()
    val borderColor = if(showBorder) themeColor else Color.Transparent
    val showBatteryIcon = settings.showBatteryIcon
    val showBatteryPercentage = settings.showBatteryPercentage
    val fontFamily = settings.batteryFontFamily
    val fontWeight = getFontWeightFromString(settings.batteryFontWeight)
    val fontSize = settings.batteryFontSize.sp
    val batteryState by rememberBatteryState()
    val batteryLevel = batteryState.level
    val isCharging = batteryState.isCharging
    val isLow = batteryLevel < 10
    val batteryLevelColor = if (isLow) Color.Red else themeColor

    // Calculate icon size based on font size
    val iconHeight = with(LocalDensity.current) { fontSize.toDp() * 0.6f}
    val iconWidth = iconHeight * 2 // Maintain a 2:1 aspect ratio

    Row(modifier = Modifier
        .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
        .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (showBatteryIcon) Box(
            modifier = Modifier
                .size(width = iconWidth, height = iconHeight)
                .padding(end = 4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                // Battery body
                drawRoundRect(
                    color = themeColor,
                    size = Size(size.width - strokeWidth, size.height),
                    style = Stroke(width = strokeWidth),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Battery terminal
                drawRoundRect(
                    color = themeColor,
                    topLeft = Offset(size.width - strokeWidth, size.height / 4),
                    size = Size(strokeWidth, size.height / 2),
                    style = Fill
                )

                if (batteryLevel > 0) {
                    // Battery level
                    val levelWidth = (size.width - strokeWidth * 3) * (batteryLevel / 100f)
                    drawRoundRect(
                        color = batteryLevelColor,
                        topLeft = Offset(strokeWidth * 1.5f, strokeWidth * 1.5f),
                        size = Size(levelWidth, size.height - strokeWidth * 3),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }

                if (isCharging) {
                    val path = Path().apply {
                        val w = size.width
                        val h = size.height
                        moveTo(w * 0.7f, h * 0.15f)
                        lineTo(w * 0.4f, h * 0.6f)
                        lineTo(w * 0.55f, h * 0.6f)
                        lineTo(w * 0.3f, h * 0.85f)
                        lineTo(w * 0.6f, h * 0.4f)
                        lineTo(w * 0.45f, h * 0.4f)
                        close()
                    }
                    drawPath(path, color = Color.Red)
                }
            }
        }
        if (batteryLevel > 0 && showBatteryPercentage) {
            EffectTextBlock(
                text = "$batteryLevel%",
                color = themeColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                shadowColor = shadowColor
            )
        }
    }
}

@Composable
fun QuickListOverlay(apps: List<AppInfo>,
                     appWidgetManager: AppWidgetManager,
                     mainWidgetHost: AppWidgetHost,
                     notificationIcons: List<Pair<String, Drawable>>,
                     notificationPackages: List<String>,
                     settingsModel: SettingsViewModel,
                     onSwipeUp: () -> Unit,
                     onSwipeRight: () -> Unit,
                     onSwipeLeft: () -> Unit,
                     onShowSearch:() -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape)}
    var isDefault by remember { mutableStateOf(false) }
    var guideShown by remember { mutableStateOf(true) }
    var feedbackShown by remember { mutableStateOf(true)}
    val guideKeyword = "quick_guide_1"
    var canShowGuide by remember { mutableStateOf(false) }
    val settings by settingsModel.uiState.collectAsState()

    fun openDefaultLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        handleStartActivity(context,intent,null)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isDefault = isDefaultLauncher(context)
            guideShown = PreferenceManager.getBoolean(context, guideKeyword, false)
            feedbackShown = PreferenceManager.getFeedbackDialogShown(context)
        }
        delay(500)
        canShowGuide = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val newIconSize = PreferenceManager.getIconSize(context).dp
                    val newIconShape = PreferenceManager.getIconShape(context)
                    withContext(Dispatchers.Main) {
                        iconSize = newIconSize
                        iconShape = newIconShape
                    }
                    guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
                    feedbackShown = PreferenceManager.getFeedbackDialogShown(context)
                    isDefault = isDefaultLauncher(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun onGuideDismiss(){
        PreferenceManager.setBoolean(context,guideKeyword,true)
        guideShown = true
        val intent = Intent(context, SettingsActivity::class.java)
        handleStartActivity(context,intent,null)
    }

    if(!guideShown && canShowGuide)GuideDialog(
        onDismiss = {onGuideDismiss()},
        title = stringResource(R.string.welcome),
        steps = listOf(
            stringResource(R.string.checkout_how_to_guide)
        )
    )

    fun onFeedbackDismiss(){
        feedbackShown = true
        PreferenceManager.setFeedbackDialogShown(context)
    }
    fun onFeedbackRateIt(){
        val packageName = context.packageName
        feedbackShown = true
        PreferenceManager.setFeedbackDialogShown(context)
        try {
            // Try to open the Play Store app directly
            val playStoreIntent = Intent(Intent.ACTION_VIEW,
                "market://details?id=$packageName".toUri())
            context.startActivity(playStoreIntent)
        } catch (_: Exception) {
            // If Play Store is not installed, open in a web browser
            val webIntent = Intent(Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri())
            context.startActivity(webIntent)
        }
    }
    if (guideShown && canShowGuide && !feedbackShown && isDefault)
        FeedbackDialog(
        {onFeedbackDismiss()},
        {onFeedbackRateIt()}
    )
    val lowerPartHeight = 400.dp

    val isLightHour = PreferenceManager.isLightHour(context)
    val hourFgColor = if (isLightHour) {
        if(settings.monochrome) Color.Black else Color.White
    } else Color.White.copy(alpha = 0.7f)
    val hourBgColor = if(settings.monochrome) Color.Transparent else Color.Black
    val foregroundColor = if(settings.showThemedText && settings.themedColors != null) {
        Color(settings.themedColors!!.textFg)
    } else {
        hourFgColor
    }
    val backgroundColor = if(settings.showThemedText && settings.themedColors != null) {
        Color(settings.themedColors!!.textBg)
    } else {
        hourBgColor
    }

    var showWidgetSettings by remember { mutableStateOf(false) }
    val showThemedIcon = settings.showThemedIcons && settings.autoWallpapers
    fun exitWidgetSettings() {
        showWidgetSettings = false
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    val launchSwipeIntentsCache = remember {
        mutableMapOf<String, Intent?>()
    }
    LaunchedEffect(settings.rightSwipeApp,settings.leftSwipeApp) {
        withContext(Dispatchers.IO) {
            val leftPackage = PreferenceManager.getSwipeApp(context, "left")
            if (leftPackage != null) {
                launchSwipeIntentsCache["left"] = context.packageManager.getLaunchIntentForPackage(leftPackage)
            }
            val rightPackage = PreferenceManager.getSwipeApp(context, "right")
            if (rightPackage != null) {
                launchSwipeIntentsCache["right"] = context.packageManager.getLaunchIntentForPackage(rightPackage)
            }
        }
    }
    Box(modifier = Modifier
        .fillMaxSize()
    ) {
        Column (modifier = Modifier) {
            if(settings.hasCustomWidgets) {
                WidgetHostScreen(
                    modifier = Modifier.weight(1f),
                    appWidgetManager,
                    mainWidgetHost,
                    "widgets_center",
                    gridColumns = 9,
                    onSwipeRight = {},
                    onSwipeLeft = {})
            } else {
                DraggableQuickWidgetsContainer (
                    modifier = Modifier.weight(1f),
                    editMode = showWidgetSettings,
                    widgetIds = settings.widgetIds,
                    widgetPositions = settings.widgetPositions,
                    onPositionChanged = { id, offset ->
                        settingsModel.saveWidgetPosition(id, offset.x, offset.y)
                    },
                    onEditModeChanged = { editMode ->  showWidgetSettings = editMode},
                    composableContent = { id, editMode ->
                        when (id) {
                            "time" -> WidgetClock(
                                settings,
                                foregroundColor,
                                editMode = editMode,
                                backgroundColor)
                            "date" -> WidgetDate(
                                settings,
                                foregroundColor,
                                showBorder = editMode,
                                backgroundColor)
                            "battery" -> BatteryStatus(
                                settings,
                                foregroundColor,
                                showBorder = editMode,
                                backgroundColor)
                            "notifications" -> NotificationIconRow(
                                notificationIcons,
                                settings = settings,
                                foregroundColor =  foregroundColor,
                                showBorder = editMode,
                                backgroundColor = backgroundColor)
                        }
                    },
                )
            }
            AnimatedContent(
                targetState = showWidgetSettings,
                transitionSpec = {
                    if (targetState) {
                        slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                                slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                    } else {
                        slideInVertically(initialOffsetY = { -it }) + fadeIn() togetherWith
                                slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    }
                }
            ) { isShowingSettings ->
                if (isShowingSettings) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lowerPartHeight)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {},
                                    onDoubleTap = {},
                                    onLongPress = {
                                    }
                                )
                            }
                    ) {
                        ProSettingsScreen(settingsModel,
                            { exitWidgetSettings() })
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            //.border(1.dp, color = Color.Cyan)
                            .height(lowerPartHeight)
                            .detectGestures(
                                onSwipeUp = onSwipeUp,
                                onSwipeDown = {
                                    try {
                                        @SuppressLint("WrongConstant")
                                        val statusBarService =
                                            context.getSystemService("statusbar")
                                        val statusBarManager =
                                            Class.forName("android.app.StatusBarManager")
                                        val method =
                                            statusBarManager.getMethod("expandNotificationsPanel")
                                        method.invoke(statusBarService)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                onSwipeLeft = {
                                    val showWidget =
                                        PreferenceManager.getWidgetsOnSwipe(context, "left")
                                    if (showWidget) {
                                        onSwipeLeft()
                                    } else {
                                        val launchIntent = launchSwipeIntentsCache["left"]
                                        if (launchIntent != null) {
                                            handleStartActivity(context, launchIntent, null)
                                        }
                                    }
                                },
                                onSwipeRight = {
                                    val showWidget =
                                        PreferenceManager.getWidgetsOnSwipe(context, "right")
                                    if (showWidget) {
                                        onSwipeRight()
                                    } else {
                                        val launchIntent = launchSwipeIntentsCache["right"]
                                        if (launchIntent != null) {
                                            handleStartActivity(context, launchIntent, null)
                                        }
                                    }
                                },
                                onCircular = {
                                    val appOnCircularPattern = settings.patternApps["Center"]
                                    if (appOnCircularPattern != null ) {
                                        val launchIntent: Intent? =
                                            context.packageManager.getLaunchIntentForPackage(
                                                appOnCircularPattern.packageName
                                            )
                                        handleStartActivity(context, launchIntent, null)
                                    }
                                },
                                onLPatternDetected = { pattern ->
                                    val patternApp = settings.patternApps[pattern]
                                    if (patternApp != null) {
                                        val launchIntent: Intent? =
                                            context.packageManager.getLaunchIntentForPackage(
                                                patternApp.packageName
                                            )
                                        handleStartActivity(context, launchIntent, null)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 64.dp)
                        ) {
                            if (!isDefault) {
                                OutlinedButton(
                                    onClick = { openDefaultLauncherSettings() },
                                    border = null,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.5f) // Text color
                                    )
                                ) {
                                    Text(
                                        stringResource(R.string.set_default_launcher),
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                            }
                            when (settings.quickAppsLayout) {
                                "linear" -> FiveColumnLayout(
                                    apps,
                                    notificationPackages,
                                    iconSize,
                                    iconShape,
                                    onShowSearch,
                                    showThemedIcon,
                                    settings.themedColors,
                                    settings.isLightHour
                                )

                                "circular" -> CircularLayout(
                                    apps,
                                    notificationPackages,
                                    iconSize,
                                    iconShape,
                                    onShowSearch,
                                    showThemedIcon,
                                    settings.themedColors,
                                    settings.isLightHour
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchListOverlay(apps: List<AppInfo>,
                      notificationPackages: List<String>,
                      contacts: List<Contact>,
                      onRequestContactsPermission: () -> Unit,
                      onSwipeDown: () -> Unit,
                      hasContactPermission: Boolean) {
    val context = LocalContext.current
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape) }
    var inputText by remember { mutableStateOf("") }

    var activeTab: SearchTab by remember { mutableStateOf(SearchTab.APPS) }
    val filteredApps by remember {
        derivedStateOf {
            if(activeTab == SearchTab.APPS && inputText.isNotBlank()) {
                apps.filter { app -> doesMatchSearch(inputText.trim(), app.label) }
            } else {
                apps
            }
        }
    }
    val filteredContacts by remember {
        derivedStateOf {
            if(activeTab == SearchTab.CONTACTS){
                searchContacts(inputText, contacts)
            } else {
                contacts
            }
        }
    }
    var selectedContactIndex by remember { mutableIntStateOf(0) }
    val selectedContact by remember {
        derivedStateOf {
            filteredContacts.getOrNull(selectedContactIndex)
        }
    }
    // Coroutine scope to run suspend functions like scrolling
    val coroutineScope = rememberCoroutineScope()

    // The scroll handle for the LazyColumn
    val lazyListState = rememberLazyListState()

    // Function to handle the double-tap action
    fun onTapSelectedContact() {
        placeCallWithDialer(context,selectedContact?.number)
    }

    fun onTabSelected(tab:SearchTab){
        if(activeTab != tab) {
            inputText = ""
        }
        activeTab = tab
    }
    fun swipeRightOnKeyboard() {
        activeTab = SearchTab.CONTACTS
        inputText = ""
    }
    fun swipeLeftOnKeyboard() {
        activeTab = SearchTab.APPS
        inputText = ""
    }

    LaunchedEffect(filteredApps) {
        if (filteredApps.size == 1) {
            val singleApp = filteredApps.first()
            // Launch the app
            val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(singleApp.packageName)
            if (launchIntent != null) {
                handleStartActivity(context,launchIntent,null)
                // Optional: Clear the input text after launching
                inputText = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            iconSize = PreferenceManager.getIconSize(context).dp
            iconShape = PreferenceManager.getIconShape(context)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                if (index == 0 && offset == 0) {
                    // When at the absolute top, select the first item.
                    0
                } else {
                    // Otherwise, select the second visible item.
                    // Fallback to the last visible item if there's no second one.
                    lazyListState.layoutInfo.visibleItemsInfo.getOrNull(1)?.index
                        ?: lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?: 0
                }
            }
            .distinctUntilChanged()
            .collect { index ->
                selectedContactIndex = index
            }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val scrollThreshold = with(LocalDensity.current) { 50.dp.toPx() }

    var showLocaleSelection by remember { mutableStateOf(false)}
    var keyboardLocale by remember { mutableStateOf(PreferenceManager.getKeyboardLocale(context)) }
    fun onLocaleSelected(locale:Locale) {
        keyboardLocale = locale
        PreferenceManager.setKeyboardLocale(context,locale)
        showLocaleSelection = false
    }
    fun onLocaleSelection(){
        showLocaleSelection = true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Combine multiple gesture detectors in one pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        if (selectedContact != null) {
                            onTapSelectedContact()
                        }
                    }
                )
            }
            .pointerInput(
                lazyListState
            ) { // Relaunch gesture detection if state or data changes
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val isAtTop = !lazyListState.canScrollBackward
                        val isAtBottom = !lazyListState.canScrollForward

                        when {
                            dragAmount > 0 -> { // Dragging Down
                                if (isAtTop) {
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator > scrollThreshold) {
                                        selectedContactIndex = (selectedContactIndex - 1).coerceAtLeast(0)
                                        dragAccumulator = 0f
                                    }
                                } else {
                                    lazyListState.dispatchRawDelta(-2 * dragAmount)
                                }
                            }
                            dragAmount < 0 -> { // Dragging Up
                                if (isAtBottom) {
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator < -scrollThreshold) {
                                        selectedContactIndex = (selectedContactIndex + 1).coerceAtMost(filteredContacts.lastIndex)
                                        dragAccumulator = 0f
                                    }
                                } else {
                                    lazyListState.dispatchRawDelta(-2 * dragAmount)
                                }
                            }
                        }
                    }

                )
            },
        contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), // Adapts to theme
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant, // Default for unselected tabs
                    indicator = { tabPositions ->
                        // A more modern, pill-shaped indicator
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[activeTab.ordinal])
                                .clip(RoundedCornerShape(100)),
                            height = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {
                        // A subtle divider for better visual separation
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                ) {
                    // "Apps" Tab
                    Tab(
                        selected = activeTab == SearchTab.APPS,
                        onClick = { onTabSelected(SearchTab.APPS) },
                        text = { Text(stringResource(R.string.applications)) },
                        // Let the Tab itself handle color changes based on its 'selected' state
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // "Contacts" Tab
                    Tab(
                        selected = activeTab == SearchTab.CONTACTS,
                        onClick = { onTabSelected(SearchTab.CONTACTS) },
                        text = { Text(stringResource(R.string.contacts)) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedContent(
                    targetState = activeTab,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        // Determine animation direction based on tab switch
                        if (targetState == SearchTab.CONTACTS && initialState == SearchTab.APPS) {
                            // Apps -> Contacts: Contacts slide in from right, Apps slide out to left
                            (slideInHorizontally(initialOffsetX = { -it }) + fadeIn()).togetherWith(
                                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                            )
                        } else {
                            // Contacts -> Apps: Apps slide in from left, Contacts slide out to right
                            (slideInHorizontally(initialOffsetX = { it }) + fadeIn()).togetherWith(
                                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                            )
                        }
                    }
                ) { targetTab ->
                    if (targetTab == SearchTab.CONTACTS) {
                        if (hasContactPermission) {
                            if(contacts.isEmpty()){
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    items(filteredContacts) { contact ->
                                        ContactListItem(contact,
                                            isSelected = (contact.id == selectedContact?.id))
                                    }
                                }
                            }
                        } else {
                            PermissionRequestView { onRequestContactsPermission() }
                        }
                    }
                }
                val textBoxPadding = (LocalConfiguration.current.screenWidthDp / 5).dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = textBoxPadding) // Keep horizontal padding for screen margins
                        .height(40.dp)
                        .clip(RoundedCornerShape(16.dp)) // Clip the content to the rounded shape
                        .background(Color.Black.copy(alpha = 0.5f)), // Black background for the box
                    contentAlignment = Alignment.Center // Center the Text inside the Box
                ) {
                    Text(
                        text = inputText.ifEmpty { stringResource(R.string.search) },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center, // Ensure placeholder text is centered
                        modifier = Modifier.padding(horizontal = 16.dp) // Inner padding for the text
                    )
                }
                // Circular Keyboard
                CircularKeyboard(
                    locale = keyboardLocale,
                    onChar = { char ->
                        inputText += char
                    },
                    onBackspace = {
                        if (inputText.isNotEmpty()) {
                            inputText = inputText.dropLast(1)
                        }
                    },
                    {onLocaleSelection()},
                    onSwipeDown = onSwipeDown,
                    onSwipeRight = { swipeRightOnKeyboard() },
                    onSwipeLeft = { swipeLeftOnKeyboard() }
                )

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        // Determine animation direction based on tab switch
                        if (targetState == SearchTab.CONTACTS && initialState == SearchTab.APPS) {
                            // Apps -> Contacts: Contacts slide in from right, Apps slide out to left
                            (slideInHorizontally(initialOffsetX = { -it }) + fadeIn()).togetherWith(
                                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                            )
                        } else {
                            // Contacts -> Apps: Apps slide in from left, Contacts slide out to right
                            (slideInHorizontally(initialOffsetX = { it }) + fadeIn()).togetherWith(
                                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                            )
                        }
                    }
                ) { targetTab ->
                    if (targetTab == SearchTab.APPS) {
                        LazyRow(
                            Modifier.height(iconSize + 20.dp),
                            // Add some padding around the content
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            // Add spacing between the items
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(filteredApps,key = { app -> app.packageName }) { app ->
                                AppIcon(app,
                                    notificationPackages,
                                    iconShape,
                                    iconSize=iconSize)
                            }
                        }
                    }
                }
                if(showLocaleSelection){
                    LocaleSelectionDialog(
                        { showLocaleSelection = false},
                        { locale -> onLocaleSelected(locale) }
                    )
                }
            }
        }
}

@Composable
fun ContactListItem(contact: Contact,isSelected:Boolean) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        placeCallWithDialer(context, contact.number)
                    }
                )
            }, // Rounded corners for each item
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        headlineContent = {
            Text(
                text = contact.name ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            // Only display the subtitle if a number is present
            contact.number?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (contact.photoUri != null) {
                    // Use AsyncImage from Coil to load the photo
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = "${contact.name}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(CircleShape)
                    )
                } else {
                    // If no photo, display the first initial in a colored circle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name?.firstOrNull()?.toString() ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    )
}
@Composable
fun PermissionRequestView(onRequestPermission: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = "https://comfer.jeerovan.com/privacy"
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.requires_contact_permission),
                textAlign = TextAlign.Center
            )
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.grant_permission))
            }
            Text(
                text = stringResource(R.string.privacy_policy_text),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                    uriHandler.openUri(privacyPolicyUrl)
                }
            )
        }
    }
}

@Composable
fun AppListOverlay(apps: List<AppInfo>,
                   notificationPackages: List<String>,
                   onSwipeDown: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            iconSize = PreferenceManager.getIconSize(context).dp
            iconShape = PreferenceManager.getIconShape(context)
        }
    }

    val scrollAnimatable = remember { Animatable(0f) }
    val flingDecay = rememberSplineBasedDecay<Float>()
    var centerAppIndex by remember { mutableIntStateOf(0) }
    var lastCenterAppIndex by remember { mutableIntStateOf(0) }
    var centerIconX by remember { mutableFloatStateOf(0f) }
    var centerIconY by remember { mutableFloatStateOf(0f) }
    var centerIconSize by remember { mutableFloatStateOf(0f) }
    val snapSpacing = 20f

    fun updateCenterAppIndex(index:Int){
        centerAppIndex = index
        if(centerAppIndex != lastCenterAppIndex) {
            lastCenterAppIndex = centerAppIndex
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    LaunchedEffect(apps) {
        // If the current index is now out of bounds, clamp it to the last valid index
        if (centerAppIndex >= apps.size) {
            centerAppIndex = apps.lastIndex.coerceAtLeast(0)
        }
    }
    // A robust helper function to wrap a value within a given range [0, max)
    fun Float.wrap(max: Float): Float {
        if (max <= 0f) return 0f // Avoid division by zero
        return (this % max + max) % max
    }

    suspend fun settleOnNearestApp(initialVelocity: Float = 0f) {
        if (apps.isEmpty()) return

        val totalScrollWidth = apps.size * snapSpacing
        var handoffVelocity = initialVelocity

        // Phase 1: Natural smooth fling
        if (abs(initialVelocity) > 10f) {
            try {
                scrollAnimatable.animateDecay(
                    initialVelocity = initialVelocity,
                    animationSpec = exponentialDecay()
                ) {
                    // 'this' is the Animatable. Monitor velocity frame-by-frame.
                    // When velocity drops we abort the decay to start the snap.
                    if (abs(velocity) < 10f) {
                        throw SnapEarlyException()
                    }
                }
            } catch (e: SnapEarlyException) {
                // Animation gracefully interrupted exactly when we wanted
            }

            // Capture the exact velocity at the exact frame the decay stopped
            handoffVelocity = scrollAnimatable.velocity
        }

        // Phase 2: Settle precisely onto the nearest app icon
        val currentOffset = scrollAnimatable.value
        val snapTarget = (currentOffset / snapSpacing).roundToInt() * snapSpacing

        if (currentOffset != snapTarget) {
            scrollAnimatable.animateTo(
                targetValue = snapTarget,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                initialVelocity = handoffVelocity // Seamless transfer of momentum
            )
        }

        // Wrap the values correctly to maintain the infinite loop illusion
        scrollAnimatable.snapTo(scrollAnimatable.value.wrap(totalScrollWidth))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch {
                            scrollAnimatable.stop()
                        }
                    },
                    onDoubleTap = {
                        if (apps.isNotEmpty()) {
                            if (centerAppIndex < apps.size) {
                                val app = apps[centerAppIndex]
                                scope.launch(Dispatchers.Default) {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        withContext(Dispatchers.Main) {
                                            val opts = ActivityOptions.makeClipRevealAnimation(
                                                view,
                                                centerIconX.toInt(),
                                                centerIconY.toInt(),
                                                centerIconSize.toInt(),
                                                centerIconSize.toInt()
                                            )
                                            context.startActivity(launchIntent, opts.toBundle())
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // These state variables are scoped to the gesture detection session
                val velocityTracker = VelocityTracker()
                var dragAxis: DragAxis? = null
                var verticalDragAmount = 0f
                var isSwipeDownTriggered = false

                detectDragGestures(
                    onDragStart = {
                        // Reset state for the new gesture
                        dragAxis = null
                        verticalDragAmount = 0f
                        isSwipeDownTriggered = false
                        velocityTracker.resetTracking()
                        scope.launch {
                            scrollAnimatable.stop() // Stop any ongoing animation
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        // Lock the drag axis after a small initial movement
                        if (dragAxis == null) {
                            if (dragAmount.x.absoluteValue > 4f || dragAmount.y.absoluteValue > 4f) {
                                dragAxis =
                                    if (dragAmount.x.absoluteValue > dragAmount.y.absoluteValue) {
                                        DragAxis.HORIZONTAL
                                    } else {
                                        DragAxis.VERTICAL
                                    }
                            }
                        }

                        when (dragAxis) {
                            DragAxis.HORIZONTAL -> {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                val increment = dragAmount.x * 0.3f
                                val totalScrollWidth = apps.size * snapSpacing

                                // Launching a coroutine is necessary to call the suspend function `snapTo`.
                                scope.launch {
                                    val newPosition =
                                        (scrollAnimatable.value + increment).wrap(totalScrollWidth)
                                    scrollAnimatable.snapTo(newPosition)
                                }
                            }

                            DragAxis.VERTICAL -> {
                                // Only process vertical drag if the action hasn't been triggered yet.
                                if (!isSwipeDownTriggered) {
                                    verticalDragAmount += dragAmount.y
                                    // Trigger the action once the threshold is passed.
                                    if (verticalDragAmount > 80f) {
                                        onSwipeDown()
                                        isSwipeDownTriggered =
                                            true // Prevents repeated calls in this gesture.
                                    }
                                }
                            }

                            null -> { /* Wait for axis to be locked */
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragAxis == DragAxis.HORIZONTAL) {
                            val velocity = velocityTracker.calculateVelocity().x * 0.3f
                            scope.launch {
                                settleOnNearestApp(velocity)
                            }
                        }
                        velocityTracker.resetTracking()
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        if (dragAxis == DragAxis.HORIZONTAL) {
                            scope.launch {
                                settleOnNearestApp()
                            }
                        }
                    }
                )
            }
    ) {
        if (apps.isNotEmpty()) {
            UshapedAppList(
                apps = apps,
                notificationPackages,
                updateCenterIndex = { updateCenterAppIndex(it) },
                scrollOffset = -scrollAnimatable.value,
                iconSize = iconSize,
                iconShape = iconShape,
                updateCenterIconGeom = { x, y, size ->
                    centerIconX = x
                    centerIconY = y
                    centerIconSize = size
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Takes up the full screen width
                    .padding(top = 130.dp),
                horizontalArrangement = Arrangement.Center // Centers the content within the Row
            ) {
                AnimatedContent(
                    targetState = centerAppIndex,
                    transitionSpec = {
                        // Defines the animation: fade in new content while fading out old content
                        fadeIn(animationSpec = tween(200, 100)) togetherWith
                                fadeOut(animationSpec = tween(100))
                    },
                    label = "AppNameAnimation"
                ) { targetIndex ->
                    // The content lambda provides the updated index
                    if(targetIndex < apps.size) {
                        Text(
                            text = apps[targetIndex].label.replace(" ", "\n"),
                            textAlign = TextAlign.Center, // 1. Centers the text lines horizontally
                            style = MaterialTheme.typography.titleMedium, // 2. Use Theme instead of hardcoded 18.sp
                            color = Color.White,
                            lineHeight = 20.sp, // 3. Adjusts spacing between the stacked words
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
private enum class DragAxis { HORIZONTAL, VERTICAL }

@Composable
fun LauncherScreen(appInfoViewModel: AppInfoViewModel,
                   settingsViewModel: SettingsViewModel,
                   mainViewModel: MainViewModel,
                   widgetHosts: WidgetHostManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAppListVisible by remember { mutableStateOf(false) }
    var isSearchListVisible by remember { mutableStateOf(false) }
    var areLeftWigetsVisible by remember { mutableStateOf(false) }
    var areRightWigetsVisible by remember { mutableStateOf(false) }
    //var backgroundImage by remember { mutableStateOf<String?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }

    val appInfoUiState by appInfoViewModel.uiState.collectAsState()
    val settingInfoUiState by settingsViewModel.uiState.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()
    val notifications by MyNotificationListenerService.activeNotifications.collectAsState()

    val quickApps = appInfoUiState.quickApps
    val primaryApps = appInfoUiState.primaryApps
    val hiddenApps = appInfoUiState.restApps

    val sortedPrimaryApps = if(settingInfoUiState.arrangeInAlphabeticalOrder) primaryApps.sortedBy { it.label.toString() } else primaryApps

    val wallpaperMotionEnabled = settingInfoUiState.autoWallpapers && settingInfoUiState.wallpaperMotionEnabled
    val hasNotificationAccess = settingInfoUiState.hasNotificationAccess

    LaunchedEffect(mainUiState.iconVersion, // after changing background, update app icons.
        settingInfoUiState.appListsVersion, // after modifying app list: quick list <-> primary list (in or between)
        settingInfoUiState.showThemedIcons,
        settingInfoUiState.autoWallpapers,
        settingInfoUiState.monochrome,
        settingInfoUiState.iconPackPackage) {
        appInfoViewModel.reloadList()
    }

    val notificationPackages by remember(notifications, hasNotificationAccess) {
        derivedStateOf {
            // First, check if notification access is even enabled
            if (!hasNotificationAccess) {
                emptyList() // Return an empty list if access is not granted
            } else {
                // If access is granted, proceed with mapping the packages
                notifications.mapNotNull { sbn ->
                    try {
                        sbn.packageName
                    } catch (_: Exception) {
                        null // Gracefully handle any exceptions
                    }
                }
            }
        }
    }
    val notificationIcons by rememberNotificationDrawables(notifications,hasNotificationAccess,LocalContext.current)

    LaunchedEffect(Unit) {
        mainViewModel.backPressEvent.collect {
            if(areLeftWigetsVisible) {
                areLeftWigetsVisible = false
            }
            if(areRightWigetsVisible){
                areRightWigetsVisible = false
            }
            if (isSearchListVisible) {
                isSearchListVisible = false
            }
            if (isAppListVisible){
                isAppListVisible = false
            }
        }
    }
    // 1. Define all possible enter and exit animations
    val slideUpExit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    val slideDownEnter = slideInVertically(initialOffsetY = { -it }) + fadeIn()

    val slideLeftExit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    val slideRightEnter = slideInHorizontally(initialOffsetX = { it }) + fadeIn()

    val slideRightExit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    val slideLeftEnter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn()

    // 2. Create state variables to hold the current enter/exit transitions.
    //    Initialize them with the default (vertical) animations.
    var enterTransition by remember { mutableStateOf(slideDownEnter) }
    var exitTransition by remember { mutableStateOf(slideUpExit) }
    // --- Transitions for AppList and SearchList (Second Layer) ---

    // These overlays always enter from the bottom and exit to the bottom
    val layer2Enter = slideInVertically(initialOffsetY = { it }) + fadeIn()
    val layer2Exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()

    val backgroundImage = mainUiState.imagePath

    val contacts = remember {
        mutableStateListOf<Contact>()
    }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasContactsPermission = isGranted
        }
    )
    fun onRequestContactsPermission(){
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
    fun fetchContacts() {
        coroutineScope.launch {
            val fetchedContacts = withContext(Dispatchers.IO) {
                val contactsList = mutableListOf<Contact>()
                val contentResolver = context.contentResolver

                // Step 1: Query all phone numbers in a single batch query
                val phoneMap = mutableMapOf<Long, String>()
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )

                phoneCursor?.use { cursor ->
                    val contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (contactIdIndex != -1 && numberIndex != -1) {
                        while (cursor.moveToNext()) {
                            val contactId = cursor.getLong(contactIdIndex)
                            val number = cursor.getString(numberIndex)
                            // Store first phone number for each contact (if not already stored)
                            if (!phoneMap.containsKey(contactId) && number != null) {
                                phoneMap[contactId] = number
                            }
                        }
                    }
                }

                // Step 2: Query all contacts
                val projection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                )

                val cursor = contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )

                cursor?.use { contactCursor ->
                    val idIndex = contactCursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoUriIndex = contactCursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val hasPhoneNumberIndex = contactCursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    // Check if any column is not found
                    if (idIndex == -1 || nameIndex == -1 || photoUriIndex == -1 || hasPhoneNumberIndex == -1) {
                        return@withContext emptyList()
                    }

                    while (contactCursor.moveToNext()) {
                        val id = contactCursor.getLong(idIndex)
                        val name = contactCursor.getString(nameIndex)
                        val photoUriString = contactCursor.getString(photoUriIndex)
                        val photoUri = photoUriString?.toUri()
                        val hasPhoneNumber = contactCursor.getInt(hasPhoneNumberIndex) > 0

                        // Look up the phone number from our pre-built map
                        val number = if (hasPhoneNumber) phoneMap[id] else null

                        if (name != null && number != null && name.isNotEmpty()) {
                            contactsList.add(Contact(id, name, photoUri, number))
                        }
                    }
                }

                contactsList
            }

            // Update the state list on the main thread
            contacts.clear()
            val uniqueAndSortedContacts = fetchedContacts
                .sortedBy { it.name }
            contacts.addAll(uniqueAndSortedContacts)
        }
    }


    DisposableEffect(lifecycleOwner, hasContactsPermission) {
        val observer = LifecycleEventObserver { _, event ->
            // Trigger on resume
            if (event == Lifecycle.Event.ON_RESUME) {
                // Only fetch if permission has been granted
                if (hasContactsPermission) {
                    fetchContacts()
                }
            }
        }
        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            //.border(width=1.dp,Color.White)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = Intent(context, SettingsActivity::class.java)
                        handleStartActivity(context,intent,null)
                    },
                    onDoubleTap = {
                        coroutineScope.launch(Dispatchers.Default) {
                            val hasAccess = isAccessibilityServiceEnabled(
                                context,
                                RecentsAccessibilityService::class.java
                            )
                            if (hasAccess) {
                                withContext(Dispatchers.Main) {
                                    showRecentApps()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showDisclosure = true
                                }
                            }
                        }
                    }
                )
            }) {
        val maxWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val maxHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

        if(settingInfoUiState.autoWallpapers || settingInfoUiState.monochrome){
            AnimatedBackground(backgroundImage,wallpaperMotionEnabled,maxWidthPx,maxHeightPx)
        }

        // Quick-list layer, goes up and hides, come down and shows up
        AnimatedVisibility(
            visible = !isAppListVisible && !isSearchListVisible && !areLeftWigetsVisible && !areRightWigetsVisible,
            enter = enterTransition,
            exit = exitTransition
        ) {
            QuickListOverlay(apps = quickApps,
                widgetHosts.appWidgetManager,
                widgetHosts.mainHost,
                notificationIcons = notificationIcons,
                notificationPackages = notificationPackages,
                settingsModel = settingsViewModel,
                onSwipeUp = {
                    // Set transitions for vertical exit, then hide
                    enterTransition = slideDownEnter
                    exitTransition = slideUpExit
                    isAppListVisible = true
                },
                onSwipeLeft = {
                    // Set transitions for sliding left, then hide
                    enterTransition = slideLeftEnter
                    exitTransition = slideLeftExit
                    areRightWigetsVisible = true
                },
                onSwipeRight = {
                    // Set transitions for sliding right, then hide
                    enterTransition = slideRightEnter
                    exitTransition = slideRightExit
                    areLeftWigetsVisible = true
                },
                onShowSearch = {
                    // Set transitions for vertical exit, then hide
                    enterTransition = slideDownEnter
                    exitTransition = slideUpExit
                    isSearchListVisible = true
                }
            )
        }

        // app list - second layer
        AnimatedVisibility(
            visible = isAppListVisible,
            enter = layer2Enter,
            exit = layer2Exit
        ) {
            if (settingInfoUiState.appDrawerLayout == "circular") {
                AppListOverlay(
                    apps = sortedPrimaryApps,
                    notificationPackages,
                    onSwipeDown = { isAppListVisible = false })
            } else {
                AppDrawerScreen(
                    notificationPackages,
                    settingsViewModel,
                    appInfoViewModel,
                    onSwipeDown = { isAppListVisible = false})
            }
        }

        // search list
        AnimatedVisibility(
            visible = isSearchListVisible,
            enter = layer2Enter,
            exit = layer2Exit
        ) {
            SearchListOverlay (apps = primaryApps+hiddenApps,
                notificationPackages,
                contacts,
                onSwipeDown = { isSearchListVisible = false },
                onRequestContactsPermission = { onRequestContactsPermission() },
                hasContactPermission = hasContactsPermission
            )
        }

        // left widgets, add enter, exit animation. Enter from left, exit to left
        AnimatedVisibility(
            visible = areLeftWigetsVisible,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            WidgetHostScreen(
                modifier = Modifier,
                widgetHosts.appWidgetManager,
                widgetHosts.leftHost,
                "widgets_prefs_left",
                gridColumns = 7,
                onSwipeLeft = { areLeftWigetsVisible = false},
                onSwipeRight = {}
            )
        }
        // right widgets, add enter, exit animation. Enter from right, exit to right
        AnimatedVisibility(
            visible = areRightWigetsVisible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            WidgetHostScreen(
                modifier = Modifier,
                widgetHosts.appWidgetManager,
                widgetHosts.rightHost,
                "widgets_prefs_right",
                gridColumns = 7,
                onSwipeLeft = { },
                onSwipeRight = { areRightWigetsVisible = false}
            )
        }

        if (showDisclosure) {
            AccessibilityPermissionDisclosureScreen(
                onContinue = {
                    // The user consented. Now we can send them to the settings.
                    showDisclosure = false
                    requestAccessibilityPermission(context)
                },
                onCancel = {
                    // The user declined. Just hide the dialog.
                    showDisclosure = false
                }
            )
        }

        AutoUpdateManager(snackbarHostState,settingInfoUiState.shouldAppUpdatePromptUserCounter)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // detailed padding ensures it doesn't overlap with navigation gestures/bars
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
fun AnimatedBackground(
    background: Any?,
    wallpaperMotionEnabled: Boolean,
    maxWidthPx: Float,
    maxHeightPx: Float
) {
    val context = LocalContext.current

    // 1. Optimize Image Request: Remember it so it doesn't rebuild constantly.
    // Also, limit memory usage by resizing the image to the screen dimensions (fixes "Unresponsive GPU").
    val imageRequest = remember(background, context, maxWidthPx, maxHeightPx) {
        ImageRequest.Builder(context)
            .data(background)
            .size(width = maxWidthPx.toInt(), height = maxHeightPx.toInt()) // Crucial for GPU performance
            .crossfade(true)
            .build()
    }

    // 2. Animation State: Do NOT use 'by' delegation here.
    // Keep it as a State<Float> object to read it later.
    val infiniteTransition = rememberInfiniteTransition(label = "wallpaper_motion")
    val angleState = if (wallpaperMotionEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(60000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle-animation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = stringResource(R.string.background_image),
        modifier = Modifier
            .fillMaxSize()
            .scale(if (wallpaperMotionEnabled) 1.2f else 1f)
            .graphicsLayer {
                // 3. Defer Read: Only read the state INSIDE this block.
                // This runs on the RenderThread/Layout phase, NOT the Main Thread composition.
                if (wallpaperMotionEnabled) {
                    val angle = angleState.value // Reading here is safe
                    val x = kotlin.math.cos(angle) * maxWidthPx * 0.08f
                    val y = kotlin.math.sin(angle) * maxHeightPx * 0.08f
                    translationX = x
                    translationY = y
                } else {
                    translationX = 0f
                    translationY = 0f
                }
            },
        contentScale = ContentScale.Crop
    )
}


private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
fun UshapedAppList(
    apps: List<AppInfo>,
    notificationPackages: List<String>,
    updateCenterIndex: (Int) -> Unit,
    scrollOffset: Float,
    iconSize: Dp,
    iconShape: Shape,
    updateCenterIconGeom: (x: Float, y: Float, size: Float) -> Unit
) {
    val sidePadding = 18.dp
    val topPadding = 70.dp
    val smallIconSize = iconSize
    val largeIconSize = smallIconSize + 30.dp
    val minimumGap = 6.dp

    val totalIcons = apps.size
    if (totalIcons == 0) return

    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current

        // Cache all expensive calculations that don't depend on scrollOffset
        val layoutParams = remember(configuration.screenWidthDp, configuration.screenHeightDp, iconSize) {
            val width = with(density) { configuration.screenWidthDp.dp.toPx() }
            val height = with(density) { configuration.screenHeightDp.dp.toPx() }
            val sidePaddingPx = with(density) { sidePadding.toPx() }
            val topPaddingPx = with(density) { topPadding.toPx() }
            val smallIconPx = with(density) { smallIconSize.toPx() }
            val largeIconPx = with(density) { largeIconSize.toPx() }
            val minimumGapPx = with(density) { minimumGap.toPx() }

            val arcRadius = width / 2f - sidePaddingPx - smallIconPx / 2f
            val numTopIcons = 2 * floor(
                PI / (4 * asin((smallIconPx / 2 + minimumGapPx / 2) / (arcRadius - smallIconPx / 2)))
            ).toInt() + 1

            val arcCenterY = topPaddingPx + arcRadius
            val verticalSpacingPx = 2 * (2 * (arcRadius - smallIconPx / 2) *
                    sin(PI / (2 * (numTopIcons - 1))).toFloat() - smallIconPx).absoluteValue
            val angularSpacingRad = PI / (numTopIcons - 1)

            val sideColumnY = arcCenterY + smallIconPx / 2
            val availableHeight = height - sideColumnY
            val iconWithSpace = smallIconPx + verticalSpacingPx
            val numSideIcons = ceil(availableHeight / iconWithSpace).toInt()

            LayoutParams(
                width = width,
                height = height,
                sidePaddingPx = sidePaddingPx,
                smallIconPx = smallIconPx,
                largeIconPx = largeIconPx,
                arcRadius = arcRadius,
                arcCenterY = arcCenterY,
                verticalSpacingPx = verticalSpacingPx,
                angularSpacingRad = angularSpacingRad,
                sideColumnY = sideColumnY,
                iconWithSpace = iconWithSpace,
                numTopIcons = numTopIcons,
                numSideIcons = numSideIcons
            )
        }

        if (layoutParams.numSideIcons <= 0 || layoutParams.numTopIcons <= 1) {
            return@Box
        }

        val numVisibleIcons = layoutParams.numSideIcons * 2 + layoutParams.numTopIcons + 1

        // Direct calculation - scrollOffset changes frequently so no derivedStateOf needed
        val smoothScrollIndex = scrollOffset / 20f
        val baseScrollIndex = floor(smoothScrollIndex)
        val scrollFraction = smoothScrollIndex - baseScrollIndex
        val intScrollIndex = baseScrollIndex.toInt()
        val startIndex = (intScrollIndex - numVisibleIcons / 2 + totalIcons) % totalIcons

        // Cache position calculation function
        val getPositionForSlot = remember(layoutParams) {
            { slot: Int, center: Int ->
                when {
                    slot < layoutParams.numSideIcons -> {
                        val xPos = layoutParams.sidePaddingPx
                        val yPos = layoutParams.sideColumnY +
                                (layoutParams.numSideIcons - 1) * layoutParams.iconWithSpace -
                                (slot - 1) * layoutParams.verticalSpacingPx -
                                slot * layoutParams.smallIconPx
                        Pair(xPos, yPos)
                    }
                    slot < layoutParams.numSideIcons + layoutParams.numTopIcons -> {
                        val arcIndex = slot - layoutParams.numSideIcons
                        val angle = PI - arcIndex * layoutParams.angularSpacingRad
                        var xPos = layoutParams.width / 2 - layoutParams.smallIconPx / 2 +
                                layoutParams.arcRadius * cos(angle).toFloat()
                        var yPos = layoutParams.arcCenterY -
                                layoutParams.arcRadius * sin(angle).toFloat() -
                                layoutParams.smallIconPx / 2
                        if (slot == center) {
                            xPos = xPos + layoutParams.smallIconPx / 2 - layoutParams.largeIconPx / 2
                            yPos = yPos + layoutParams.smallIconPx / 2 - layoutParams.largeIconPx / 2
                        }
                        Pair(xPos, yPos)
                    }
                    else -> {
                        val sideIndex = slot - layoutParams.numSideIcons - layoutParams.numTopIcons
                        val xPos = layoutParams.width - layoutParams.sidePaddingPx - layoutParams.smallIconPx
                        val yPos = layoutParams.sideColumnY + layoutParams.verticalSpacingPx +
                                sideIndex * layoutParams.verticalSpacingPx +
                                sideIndex * layoutParams.smallIconPx
                        Pair(xPos, yPos)
                    }
                }
            }
        }

        val centerSlot = layoutParams.numSideIcons + layoutParams.numTopIcons / 2

        for (i in 0 until numVisibleIcons) {
            val appIndex = (startIndex + i + totalIcons) % totalIcons

            val posCurrent = getPositionForSlot(i, centerSlot)
            val posPrev = getPositionForSlot(i - 1, centerSlot)

            val x = lerp(posCurrent.first, posPrev.first, scrollFraction)
            val y = lerp(posCurrent.second, posPrev.second, scrollFraction)

            val sizeCurrent = if (i == centerSlot) largeIconSize else smallIconSize
            val sizePrev = if ((i - 1) == centerSlot) largeIconSize else smallIconSize
            val size = lerp(sizeCurrent.value, sizePrev.value, scrollFraction).dp

            if (size > largeIconSize - 10.dp) {
                updateCenterIndex(appIndex)
                val sizePx = with(density) { size.toPx() }
                updateCenterIconGeom(x + sizePx / 2, y + sizePx / 2, sizePx)
            }

            key(apps[appIndex].packageName) {
                AppIcon(
                    app = apps[appIndex],
                    notificationPackages,
                    shape = iconShape,
                    x = x.toDp(),
                    y = y.toDp(),
                    iconSize = size,
                )
            }
        }
    }
}
// Data classes to hold cached values
private data class LayoutParams(
    val width: Float,
    val height: Float,
    val sidePaddingPx: Float,
    val smallIconPx: Float,
    val largeIconPx: Float,
    val arcRadius: Float,
    val arcCenterY: Float,
    val verticalSpacingPx: Float,
    val angularSpacingRad: Double,
    val sideColumnY: Float,
    val iconWithSpace: Float,
    val numTopIcons: Int,
    val numSideIcons: Int
)


@Composable
fun AppIcon(app: AppInfo,
            notificationPackages: List<String>,
            shape: Shape,
            x: Dp = 0.dp,
            y: Dp = 0.dp,
            iconSize: Dp,
            clickable: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val iconShape = getShapeFromShape(shape,iconSize)
    var iconBounds by remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    Box (modifier = Modifier
        .offset(x = x, y = y),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(iconShape)
                .onGloballyPositioned { coordinates ->
                    // Capture the position of the icon on the screen
                    val position = coordinates.positionInWindow()
                    val size = coordinates.size
                    iconBounds = Rect(
                        position.x,
                        position.y,
                        position.x + size.width,
                        position.y + size.height
                    )
                }
                .pointerInput(app.packageName) {
                    if (clickable) detectTapGestures(
                        onTap = {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            scope.launch(Dispatchers.IO) {
                                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (intent != null) {
                                    val boundedRect = android.graphics.Rect(
                                        iconBounds.left.toInt(),
                                        iconBounds.top.toInt(),
                                        iconBounds.right.toInt(),
                                        iconBounds.bottom.toInt()
                                    )
                                    intent.sourceBounds = boundedRect

                                    withContext(Dispatchers.Main) {
                                        val options = ActivityOptions.makeClipRevealAnimation(
                                            view,
                                            iconBounds.left.toInt(),
                                            iconBounds.top.toInt(),
                                            iconBounds.width.toInt(),
                                            iconBounds.height.toInt()
                                        )
                                        handleStartActivity(context, intent, options)
                                    }
                                }
                            }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = "package:${app.packageName}".toUri()
                            handleStartActivity(context, intent, null)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Layer
            if (app.background != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.background),
                    contentDescription = "${app.label} background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Foreground Layer
            if (app.foreground != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.foreground),
                    contentDescription = app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(app.scale),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        if (app.packageName in notificationPackages) {
            val badgeSize = iconSize / 4
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
@Composable
fun SearchIcon(
    iconSize: Dp,
    iconShape: Shape,
    onShowSearch: () -> Unit,
    showThemedIcon: Boolean,
    themedColors: WallpaperThemeColors?,
    isLightMode: Boolean
) {
    val view = LocalView.current

    // Cache colors to avoid recalculation
    val backgroundColor = remember(showThemedIcon, themedColors, isLightMode) {
        if (showThemedIcon && themedColors != null) {
            Color(getThemedBackgroundColor(themedColors, isLightMode))
        } else {
            getBackgroundColor(isLightMode)
        }
    }

    val foregroundColor = remember(showThemedIcon, themedColors, isLightMode) {
        if (showThemedIcon && themedColors != null) {
            Color(getThemedIconColor(themedColors, isLightMode))
        } else {
            if (isLightMode) Color.Black else Color.White.copy(alpha = 0.7f)
        }
    }

    val shape = remember(iconShape, iconSize) { getShapeFromShape(iconShape, iconSize) }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(color = backgroundColor)
            .size(iconSize)
            .scale(0.8f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    onShowSearch()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.outline_search_24),
            contentDescription = stringResource(R.string.search),
            modifier = Modifier.size(iconSize),
            tint = foregroundColor
        )
    }
}

private fun Float.toDp(): Dp {
    return (this / Resources.getSystem().displayMetrics.density).dp
}
fun showRecentApps() {
    RecentsAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
}
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val service = "${context.packageName}/${serviceClass.canonicalName}"
    try {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
    } catch (_: Settings.SettingNotFoundException) {
        // Handle exception
    }
    return false
}


@Composable
fun FeedbackDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String = stringResource(R.string.feedback),
    dialogText: String = stringResource(R.string.feedback_text),
    icon: ImageVector = Icons.Outlined.Star
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = stringResource(R.string.feedback_icon))
        },
        title = {
            Text(text = dialogTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = dialogText, style = MaterialTheme.typography.bodyMedium)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            // The primary action button, styled to stand out
            Button(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text(stringResource(R.string.rate_comfer))
            }
        },
        dismissButton = {
            // The secondary action button, with less emphasis
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}

@Composable
fun AccessibilityPermissionDisclosureScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.permission_required),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recent_apps_permission_title),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.what_recent_apps_short_do_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.what_recent_apps_short_do_content),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.what_recent_apps_short_do_not_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.what_recent_apps_short_do_not_content),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row {
                Button(onClick = onContinue) {
                    Text(stringResource(R.string.continue_text))
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel_text))
                }
            }
        }
    }
}
fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    handleStartActivity(context,intent,null)
}

@Composable
fun CircularButton(
    onClick: () -> Unit,
    showLocaleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    char: String? = null,
    size: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1. Refined Animation: Scale down for a more natural "push" effect
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f, label = "scale")
    val shadowElevation by animateFloatAsState(targetValue = if (isPressed) 4f else 8f, label = "shadow")

    // 2. Haptic and Auditory Feedback
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    // 3. Sophisticated Color Palette with Gradients
    val buttonColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF2C2C2E) else Color(0xFF1C1C1E),
        label = "color"
    )
    val gradient = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
        radius = size.value * 0.8f
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = shadowElevation.dp,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(buttonColor)
            .background(gradient) // Subtle gradient for a "sheen" effect
            .pointerInput(char) {
            detectTapGestures(
                onTap = {
                    // Handle Click
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    onClick()
                },
                onLongPress = {
                    // Handle Long Press
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLocaleSelection()
                },
                onPress = { press ->
                    // Handle Press state for animation manually
                    val pressInteraction = PressInteraction.Press(press)
                    interactionSource.emit(pressInteraction)
                    tryAwaitRelease()
                    interactionSource.emit(PressInteraction.Release(pressInteraction))
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(
                text = char.uppercase(),
                color = Color.White,
                fontSize = (size.value / 1.5).sp, // Slightly smaller font for better padding
                fontWeight = FontWeight.W300 // A lighter font weight can look more modern
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.backspace_key),
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f) // Adjust icon size
            )
        }
    }
}
@Composable
fun CircularKeyboard(
    locale: Locale,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    showLocaleSelection: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val availableListOfChars by rememberUpdatedState(KeyboardLocale.getCharsForLocale(locale))

    // [LOG 1] Check if state is resetting or persisting correctly
    var charsListIndex by remember(locale) {
        mutableIntStateOf(0)
    }

    // Calculate list based on index
    val charsList = availableListOfChars.getOrElse(charsListIndex) {
        availableListOfChars.firstOrNull() ?: emptyList()
    }

    val layers = remember(charsList) {
        KeyboardLayoutEngine.distributeCharsToLayers(charsList)
    }

    fun onSwipeUp() {
        if (availableListOfChars.size > 1) {
            charsListIndex = (charsListIndex + 1) % availableListOfChars.size
        }
    }
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.Center)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { })
            }
            .detectSwipes(
                locale,
                onSwipeUp = {onSwipeUp()},
                onSwipeDown = onSwipeDown,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight
            ),
        contentAlignment = Alignment.Center
    ) {
        val numLayers = layers.size
        val totalRadius = (KeyboardLayoutEngine.CenterButtonSize / 2) +
                (KeyboardLayoutEngine.KeyButtonSize + KeyboardLayoutEngine.Spacing) * numLayers +
                KeyboardLayoutEngine.KeyButtonSize / 2 // Add half button for outer edge

        Box(
            modifier = Modifier.size(totalRadius * 2),
            contentAlignment = Alignment.Center
        ) {
            // --- Render Layers (Inner to Outer) ---
            layers.forEachIndexed { layerIndex, charsInLayer ->

                // Calculate precise radius for this layer
                val radius = (KeyboardLayoutEngine.CenterButtonSize / 2) +
                        KeyboardLayoutEngine.Spacing +
                        (KeyboardLayoutEngine.KeyButtonSize / 2) +
                        ((KeyboardLayoutEngine.KeyButtonSize + KeyboardLayoutEngine.Spacing) * layerIndex)

                val angleStep = (2 * Math.PI) / charsInLayer.size

                charsInLayer.forEachIndexed { charIndex, char ->
                    // Distribute from top (-PI/2)
                    val angle = angleStep * charIndex - (Math.PI / 2)

                    val x = (radius.value * cos(angle)).dp
                    val y = (radius.value * sin(angle)).dp

                    CircularButton(
                        onClick = { onChar(char) },
                        showLocaleSelection = {},
                        char = char,
                        size = KeyboardLayoutEngine.KeyButtonSize, // Fixed optimal size
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = x, y = y)
                    )
                }
            }

            // --- Render Center Backspace Button ---
            // Placed last to ensure it's on top or distinct (though minimal overlap due to math)
            CircularButton(
                onClick = onBackspace,
                showLocaleSelection,
                size = KeyboardLayoutEngine.CenterButtonSize, // Fixed largest size
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

fun searchContacts(query: String, contactList: List<Contact>): List<Contact> {
    if (query.isBlank()) {
        return contactList // Return the full list if search text is empty
    }
    return contactList.filter {
        contact -> doesMatchSearch(query,contact.name)
    }
}
fun placeCallWithDialer(context: Context, number: String?) {
    if (number.isNullOrBlank()) {
        Toast.makeText(context, context.getString(R.string.contact_number_not_available), Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = "tel:$number".toUri()
    }

    // Check if there's an app that can handle this intent
    if (intent.resolveActivity(context.packageManager) != null) {
        handleStartActivity(context,intent,null)
    } else {
        Toast.makeText(context, context.getString(R.string.no_app_to_place_calls), Toast.LENGTH_SHORT).show()
    }
}

fun Modifier.detectSwipes(
    updateOn: Any,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
) : Modifier =
    this.pointerInput(updateOn) {
        val swipeThreshold = 50.dp.toPx()
        var totalHorizontalDrag = 0f
        var totalVerticalDrag = 0f

        detectDragGestures(
            onDragStart = {
                // Reset drag totals when a new gesture starts
                totalHorizontalDrag = 0f
                totalVerticalDrag = 0f
            },
            onDrag = { change, dragAmount ->
                // Consume the pointer input
                change.consume()
                // Accumulate the drag amounts on both axes
                totalHorizontalDrag += dragAmount.x
                totalVerticalDrag += dragAmount.y
            },
            onDragEnd = {
                val absHorizontal = abs(totalHorizontalDrag)
                val absVertical = abs(totalVerticalDrag)

                // Determine if the swipe was primarily horizontal or vertical
                if (absHorizontal > absVertical) {
                    // Horizontal swipe detected
                    if (absHorizontal > swipeThreshold) {
                        if (totalHorizontalDrag > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                    }
                } else {
                    // Vertical swipe detected
                    if (absVertical > swipeThreshold) {
                        if (totalVerticalDrag > 0) {
                            onSwipeDown()
                        } else {
                            onSwipeUp()
                        }
                    }
                }
            }
        )
    }



@Composable
fun CircularLayout(
    apps: List<AppInfo>,
    notificationPackages: List<String>,
    iconSize: Dp,
    iconShape: Shape,
    onShowSearch: () -> Unit,
    showThemedIcon: Boolean,
    themedColors: WallpaperThemeColors?,
    isLightMode: Boolean
) {
    // Radius calculated to maintain a 20.dp gap between 56.dp icons.
    val radius =  iconSize * 1.768f
    // Angles in degrees for each app icon, corresponding to the apps list index.
    // Assumes a coordinate system where 0° is right and 90° is down.
    val angles = listOf(
        180f, // apps[0]: left
        0f,   // apps[1]: right
        270f, // apps[2]: top
        90f,  // apps[3]: bottom
        225f, // apps[4]: top-left (-45° from top)
        315f, // apps[5]: top-right (+45° from top)
        135f, // apps[6]: bottom-left (-45° from left)
        45f   // apps[7]: bottom-right (+45° from right)
    )
    val boxSize = iconSize * 4.8f
    Box(
        modifier = Modifier
            .size(boxSize),
        contentAlignment = Alignment.Center
    ) {
        SearchIcon(iconSize,
            iconShape,
            onShowSearch,
            showThemedIcon,
            themedColors,
            isLightMode)

        // Place up to 8 app icons in a circle
        apps.take(8).forEachIndexed { index, app ->
            val angleRad = Math.toRadians(angles[index].toDouble())
            val xOffset = (radius.value * cos(angleRad)).dp
            val yOffset = (radius.value * sin(angleRad)).dp

            Box(
                modifier = Modifier.offset(x = xOffset, y = yOffset)
            ) {
                AppIcon(iconSize = iconSize,
                    shape = iconShape,
                    notificationPackages = notificationPackages,
                    app = app)
            }
        }
    }
}

@Composable
fun FiveColumnLayout(apps:List<AppInfo>,
                     notificationPackages: List<String>,
                     iconSize: Dp,
                     iconShape: Shape,
                     onShowSearch: () -> Unit,
                     showThemedIcon: Boolean,
                     themedColors: WallpaperThemeColors?,
                     isLightMode: Boolean
) {
    val gap = 20.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically // Aligns the middle box with the stacked columns
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if(apps.size >= 3)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[2])
            if(apps.size >= 7)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[6])
        }
        Box(modifier = Modifier.size(width = gap, height = 1.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if(apps.isNotEmpty())AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[0])
            if(apps.size >= 5)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[4])
        }
        Box(modifier = Modifier.size(width = gap, height = 1.dp))
        // --- Middle column (single box) ---
        SearchIcon(iconSize,
            iconShape,
            onShowSearch,
            showThemedIcon,
            themedColors,
            isLightMode)
        Box(modifier = Modifier.size(width = gap, height = 1.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if(apps.size >= 2)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[1])
            if(apps.size >= 6)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[5])
        }
        Box(modifier = Modifier.size(width = gap, height = 1.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if(apps.size >= 4)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[3])
            if(apps.size >= 8)AppIcon(iconSize=iconSize,shape=iconShape, notificationPackages = notificationPackages,app=apps[7])
        }
    }
}
@Composable
fun rememberNotificationDrawables(
    notifications: List<StatusBarNotification>,
    hasNotificationAccess: Boolean,
    context: Context
): State<List<Pair<String, Drawable>>> {
    // produceState is a cleaner, more concise way to handle this pattern
    return produceState<List<Pair<String, Drawable>>>(
        initialValue = emptyList(),
        notifications,
        hasNotificationAccess
    ) {
        if (!hasNotificationAccess) {
            value = emptyList()
            return@produceState
        }
        // The loading logic remains on a background thread
        value = withContext(Dispatchers.IO) {
            notifications.mapNotNull { sbn ->
                // Try to load the drawable and handle exceptions *outside* the final expression.
                val drawable: Drawable? = try {
                    sbn.notification.smallIcon.loadDrawable(context)
                } catch (e: Exception) {
                    Log.e("rememberDrawables", "Failed to load drawable for ${sbn.key}", e)
                    null
                }
                // Now, work with the nullable `drawable`.
                // If it's not null, create the Pair. If it is null, this whole expression
                // evaluates to null, which `mapNotNull` then correctly discards.
                drawable?.let { sbn.key to it }
            }
        }
    }
}
@Composable
fun NotificationIconRow(
    notificationIcons: List<Pair<String, Drawable>>,
    modifier: Modifier = Modifier,
    maxVisibleIcons: Int = 5,
    settings: SettingsUiState,
    foregroundColor: Color,
    showBorder: Boolean,
    backgroundColor: Color = Color.Black
) {
    // 1. Guard Clauses: Return early if nothing to show
    if (!settings.hasNotificationAccess || !settings.showNotificationRow || notificationIcons.isEmpty()) {
        return
    }

    // 2. Prepare visual properties
    val isHorizontal = settings.notificationLayoutId == 1
    val iconSize = settings.notificationSize.dp

    val customColor = !settings.autoWallpapers && !settings.monochrome

    val iconColor = if (customColor) {
        settings.notificationColor.copy(alpha = settings.notificationAlpha / 100f)
    } else {
        foregroundColor
    }

    val borderColor = if (showBorder) iconColor else Color.Transparent

    // 3. Calculate Container Dimensions
    // Note: Kept original logic, though hardcoded '10' implies a specific design constraint
    val baseLongSide = iconSize * 10 + 8.dp
    val baseShortSide = iconSize + 16.dp

    val containerWidth = if (isHorizontal) baseLongSide else baseShortSide
    val containerHeight = if (isHorizontal) baseShortSide else baseLongSide

    // 4. Prepare Data
    val iconsToShow = if (notificationIcons.size > maxVisibleIcons + 1) {
        notificationIcons.take(maxVisibleIcons)
    } else {
        notificationIcons
    }
    val overflowCount = notificationIcons.size - maxVisibleIcons
    val hasOverflow = notificationIcons.size > maxVisibleIcons + 1

    Box(
        modifier = Modifier
            .size(width = containerWidth, height = containerHeight)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        // 5. Shared Content Logic
        // We define the list content once, then reuse it in either Row or Column
        val listContent: LazyListScope.() -> Unit = {
            notificationItems(
                icons = iconsToShow,
                iconSize = iconSize,
                iconColor = iconColor,
                hasOverflow = hasOverflow,
                overflowCount = overflowCount
            )
        }

        if (isHorizontal) {
            LazyRow(
                modifier = modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = listContent
            )
        } else {
            LazyColumn(
                modifier = modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = listContent
            )
        }
    }
}

/**
 * Extension on LazyListScope to share the "Items + Overflow" logic
 * between both LazyRow and LazyColumn.
 */
private fun LazyListScope.notificationItems(
    icons: List<Pair<String, Drawable>>,
    iconSize: Dp,
    iconColor: Color,
    hasOverflow: Boolean,
    overflowCount: Int
) {
    items(
        items = icons,
        key = { (key, _) -> key }
    ) { (_, drawable) ->
        NotificationIcon(
            drawable = drawable,
            iconColor = iconColor,
            size = iconSize
        )
    }

    if (hasOverflow) {
        item {
            NotificationRowOverflowBadge(
                count = overflowCount,
                color = iconColor,
                size = iconSize
            )
        }
    }
}

@Composable
private fun NotificationIcon(
    drawable: Drawable,
    iconColor: Color,
    size: Dp
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { view ->
            // Update drawable if it changes
            if (view.drawable != drawable) {
                view.setImageDrawable(drawable)
            }
            view.setColorFilter(iconColor.toArgb())
        },
        modifier = Modifier.size(size)
    )
}

@Composable
private fun NotificationRowOverflowBadge(
    count: Int,
    color: Color,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            // Select text color for contrast
            color = if (color == Color.White) Color.Black else Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun WidgetDate(
    settings: SettingsUiState,
    foregroundColor: Color,
    showBorder: Boolean,
    backgroundColor: Color = Color.Black
){
    var date by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            // Create new SimpleDateFormat instance in coroutine scope
            val dateFormat = SimpleDateFormat("EEE MMM d", Locale.getDefault())
            val now = System.currentTimeMillis()
            date = dateFormat.format(now)  // No Date object needed
            delay(1000)
        }
    }
    val customColor = !settings.autoWallpapers && !settings.monochrome
    val borderColor = if(showBorder) {
        if(customColor) settings.dateFontColor else foregroundColor
    } else Color.Transparent
    val textColor = if(customColor) settings.dateFontColor.copy(alpha = settings.dateFontAlpha/100f) else foregroundColor
    val shadowColor = if(customColor){
        if(settings.dateHasShadow)settings.dateShadowColor.toArgb()
        else Color.Transparent.toArgb()
    } else backgroundColor.toArgb()
    val dateParts = remember(date) { date.split(" ") }
    Box(modifier = Modifier
        .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
        .padding(4.dp)){
        when (settings.dateLayoutId) {
            1 ->
                EffectTextBlock(
                    text = date,
                    color = textColor,
                    fontSize = settings.dateFontSize.sp,
                    fontWeight = getFontWeightFromString(settings.dateFontWeight),
                    fontFamily = settings.dateFontFamily,
                    angle = settings.dateAngle.toFloat(),
                    radius = settings.dateRadius.toFloat(),
                    shadowColor = shadowColor
                )
            2 ->
                if(dateParts.size == 3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        dateParts.forEach { part ->
                            EffectTextBlock(
                                text = part,
                                color = textColor,
                                fontSize = settings.dateFontSize.sp,
                                fontWeight = getFontWeightFromString(settings.dateFontWeight),
                                fontFamily = settings.dateFontFamily,
                                angle = settings.dateAngle.toFloat(),
                                radius = settings.dateRadius.toFloat(),
                                shadowColor = shadowColor
                            )
                        }
                    }
                }
        }
    }
}
@Composable
fun WidgetClock(
    settings: SettingsUiState,
    foregroundColor: Color,
    editMode: Boolean,
    backgroundColor: Color = Color.Black
){
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val customColor = !settings.autoWallpapers && !settings.monochrome
    val borderColor = if (editMode) {
        if (customColor) {
            if (settings.showAnalog) settings.clockHourColor else settings.timeFontColor
        } else foregroundColor
    } else Color.Transparent
    Box(modifier = Modifier
        .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
        .pointerInput(editMode) {
            if (!editMode) {
                detectTapGestures(
                    onTap = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        // Open Alarms
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            handleStartActivity(context, intent, null)
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Open Calendar
                        val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_CALENDAR)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        if (calendarIntent.resolveActivity(context.packageManager) != null) {
                            handleStartActivity(context, calendarIntent, null)
                        }
                    }
                )
            }
        }
    ) {
        if (settings.showAnalog) {
            AnalogClock(
                settings.clockSize.dp,
                if (customColor) settings.clockBgColor.copy(alpha = settings.clockBgAlpha/100f) else backgroundColor,
                if (customColor) settings.clockMinuteColor.copy(alpha = settings.clockMinuteAlpha/100f) else foregroundColor,
                if (customColor) settings.clockHourColor.copy(alpha = settings.clockHourAlpha/100f) else foregroundColor
            )
        } else {
            TextClock(
                settings,
                foregroundColor,
                customColor,
                backgroundColor
            )
        }
    }
}
@Composable
fun TextClock(
    settings: SettingsUiState,
    foregroundColor: Color,
    customColor: Boolean,
    backgroundColor: Color = Color.Black
) {
    val color = if (customColor) settings.timeFontColor.copy(alpha=settings.timeFontAlpha/100f) else foregroundColor
    val shadowColor = if(customColor) {
        if(settings.timeHasShadow) settings.timeShadowColor.toArgb()
        else Color.Transparent.toArgb()
    } else backgroundColor.toArgb()
    val fontWeight = getFontWeightFromString(settings.timeFontWeight)
    val fontFamily = settings.timeFontFamily
    var time by remember { mutableStateOf("") }
    LaunchedEffect(settings.timeFormat) {
        while (true) {
            val pattern = if (settings.timeFormat == "H12") "hh:mm" else "HH:mm"
            val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
            val now = System.currentTimeMillis()
            time = timeFormat.format(now)
            delay(60000L - (System.currentTimeMillis() % 60000L))
        }
    }
    val timeParts = remember(time) { time.split(":") }
    Box(
        modifier = Modifier
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (settings.timeLayoutId){
            1 ->
                EffectTextBlock(
                    text = time,
                    color = color,
                    fontSize = settings.timeFontSize.sp,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    angle = settings.timeAngle.toFloat(),
                    radius = settings.timeRadius.toFloat(),
                    shadowColor = shadowColor
                )
            2 ->
                EffectTextBlock(
                    text = time.replace(":"," "),
                    color = color,
                    fontSize = settings.timeFontSize.sp,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    angle = settings.timeAngle.toFloat(),
                    radius = settings.timeRadius.toFloat(),
                    shadowColor = shadowColor
                )
            3 ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EffectTextBlock(
                        text = timeParts.first(),
                        color = color,
                        fontSize = settings.timeFontSize.sp,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        angle = settings.timeAngle.toFloat(),
                        radius = settings.timeRadius.toFloat(),
                        shadowColor = shadowColor
                    )
                    EffectTextBlock(
                        text = timeParts.last(),
                        color = color,
                        fontSize = (settings.timeFontSize-10).sp,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        angle = settings.timeAngle.toFloat(),
                        radius = settings.timeRadius.toFloat(),
                        shadowColor = shadowColor
                    )
                }
        }
    }
}
@Composable
fun AnalogClock(
    size: Dp,
    backgroundColor: Color,
    minuteHandColor: Color,
    hourHandColor: Color
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L - (System.currentTimeMillis() % 60000L))
            currentTime = System.currentTimeMillis()
        }
    }

    val calendar = remember { Calendar.getInstance() }
    calendar.timeInMillis = currentTime

    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)

    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .background(color = backgroundColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = this.size.width / 2

            // Hour Hand
            val hourAngle = (hours + minutes / 60f) * 30f - 90
            val hourHandLength = radius * 0.5f
            val hourHandEndX = centerX + hourHandLength * cos(Math.toRadians(hourAngle.toDouble())).toFloat()
            val hourHandEndY = centerY + hourHandLength * sin(Math.toRadians(hourAngle.toDouble())).toFloat()

            drawLine(
                color = hourHandColor,
                start = Offset(centerX, centerY),
                end = Offset(hourHandEndX, hourHandEndY),
                strokeWidth = size.toPx() * 0.05f,
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minuteAngle = minutes * 6f - 90
            val minuteHandLength = radius * 0.8f
            val minuteHandEndX = centerX + minuteHandLength * cos(Math.toRadians(minuteAngle.toDouble())).toFloat()
            val minuteHandEndY = centerY + minuteHandLength * sin(Math.toRadians(minuteAngle.toDouble())).toFloat()

            drawLine(
                color = minuteHandColor,
                start = Offset(centerX, centerY),
                end = Offset(minuteHandEndX, minuteHandEndY),
                strokeWidth = size.toPx() * 0.03f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AutoUpdateManager(
    snackbarHostState: SnackbarHostState,
    shouldPromptUserCounter: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateHandler = remember { AppUpdateHandler(context) }

    // Handle "Update" button click from Prompt 1
    val updateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            updateHandler.saveLastPromptTime()
        }
    }
    val updateMessage = stringResource(R.string.update_available)
    val updateAction = stringResource(R.string.update_action)
    val updateDownloaded = stringResource(R.string.update_downloaded)
    val updateInstall = stringResource(R.string.update_install)
    // 1. Check availability on trigger
    LaunchedEffect(shouldPromptUserCounter) {
        updateHandler.checkForUpdate(
            onUpdateAvailable = { updateInfo ->
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = updateMessage,
                        actionLabel = updateAction,
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                    when (result) {
                        SnackbarResult.ActionPerformed -> {
                            updateHandler.startUpdate(updateLauncher, updateInfo)
                        }
                        SnackbarResult.Dismissed -> {
                            updateHandler.saveLastPromptTime()
                        }
                    }
                }
            },
            onUpdateDownloaded = {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = updateDownloaded,
                        actionLabel = updateInstall,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        updateHandler.completeUpdate()
                    }
                }
            }
        )
    }

    // 2. Listen for background download progress
    LaunchedEffect(Unit) {
        updateHandler.registerDownloadListener().collect { status ->
            if (status == InstallStatus.DOWNLOADED) {
                // FIX: Launch in scope to prevent blocking the flow collector
                scope.launch {
                    // Standard behavior: showSnackbar cancels any existing snackbar
                    val result = snackbarHostState.showSnackbar(
                        message = updateDownloaded,
                        actionLabel = updateInstall,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        updateHandler.completeUpdate()
                    }
                }
            }
        }
    }
}


// Gestures
enum class LPatternType {
    DOWN_RIGHT,  // ↓→ Original L: down then right
    DOWN_LEFT,   // ↓← down then left
    UP_RIGHT,    // ↑→ up then right
    UP_LEFT,     // ↑← up then left
    RIGHT_DOWN,  // →↓ right then down
    RIGHT_UP,    // →↑ right then up
    LEFT_DOWN,   // ←↓ left then down
    LEFT_UP      // ←↑ left then up
}
fun Modifier.detectGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onCircular: () -> Unit = {},
    onLPatternDetected: (String) -> Unit = {}
): Modifier = this.pointerInput(onSwipeUp, onSwipeDown, onSwipeLeft, onSwipeRight,
    onCircular, onLPatternDetected) {
    val path = mutableListOf<Offset>()
    val pathSimplificationDistance = 5f
    val swipeThreshold = 50.dp.toPx() // Minimum distance for swipe detection
    var currentPointerId: PointerId? = null
    detectDragGestures(
        onDragStart = { offset ->
            if (currentPointerId == null) {
                path.clear()
                path.add(offset)
            }
        },
        onDrag = { change, _ ->
            change.consume()
            if (currentPointerId == null || currentPointerId == change.id) {
                currentPointerId = change.id
                change.consume()
                val lastPoint = path.lastOrNull()
                if (lastPoint == null || (change.position - lastPoint).getDistance() > pathSimplificationDistance) {
                    path.add(change.position)
                }
            }
        },
        onDragEnd = {
            if (path.size >= 2) {
                val circular = detectCircularPattern(path,swipeThreshold)
                val pattern = detectLPatternWithCorner(path,swipeThreshold)
                if(circular != null){
                    onCircular()
                    Log.d("GesturePattern","Detected: Circular")
                } else if (pattern != null) {
                    val area = when (pattern) {
                        LPatternType.LEFT_UP -> "TopRight"
                        LPatternType.UP_LEFT -> "BottomLeft"
                        LPatternType.RIGHT_UP -> "TopLeft"
                        LPatternType.UP_RIGHT -> "BottomRight"
                        LPatternType.DOWN_RIGHT -> "TopRight"
                        LPatternType.DOWN_LEFT -> "TopLeft"
                        LPatternType.RIGHT_DOWN -> "BottomLeft"
                        LPatternType.LEFT_DOWN -> "BottomRight"
                    }
                    onLPatternDetected(area)
                    Log.d("GesturePattern","Detected: $area")
                } else {
                    // Fall back to simple swipe detection
                    detectSimpleSwipe(
                        path = path,
                        threshold = swipeThreshold,
                        onSwipeUp = onSwipeUp,
                        onSwipeDown = onSwipeDown,
                        onSwipeLeft = onSwipeLeft,
                        onSwipeRight = onSwipeRight
                    )
                }
            }
            path.clear()
            currentPointerId = null
        }
    )
}
private fun detectLPatternWithCorner(points: List<Offset>,swipeThreshold: Float): LPatternType? {
    if (points.size < 15) return null

    // Helper function to normalize angle difference
    fun normalizeAngleDiff(angle1: Float, angle2: Float): Float {
        val diff = abs(angle1 - angle2)
        return minOf(diff, (2 * PI).toFloat() - diff)
    }

    // Find the corner point (where direction changes most)
    var maxDirectionChange = 0f
    var cornerIndex = 0

    for (i in 5 until points.size - 5) {
        val beforeStartIdx = i - 5
        val afterEndIdx = i + 5

        val beforeAngle = atan2(
            points[i - 1].y - points[beforeStartIdx].y,
            points[i - 1].x - points[beforeStartIdx].x
        )
        val afterAngle = atan2(
            points[afterEndIdx - 1].y - points[i].y,
            points[afterEndIdx - 1].x - points[i].x
        )

        val directionChange = normalizeAngleDiff(beforeAngle, afterAngle)
        if (directionChange > maxDirectionChange) {
            maxDirectionChange = directionChange
            cornerIndex = i
        }
    }

    // Require significant direction change (close to 90 degrees)
    if (maxDirectionChange < PI / 3) return null

    // Calculate movements without creating sublists
    val firstStartIdx = 0
    val firstEndIdx = cornerIndex
    val secondStartIdx = cornerIndex
    val secondEndIdx = points.size

    val firstVertical = points[firstEndIdx - 1].y - points[firstStartIdx].y
    val firstHorizontal = points[firstEndIdx - 1].x - points[firstStartIdx].x
    val secondVertical = points[secondEndIdx - 1].y - points[secondStartIdx].y
    val secondHorizontal = points[secondEndIdx - 1].x - points[secondStartIdx].x

    val threshold = 1.5f

    // Check if segments are long enough
    val firstSegmentLength = kotlin.math.sqrt(firstVertical * firstVertical + firstHorizontal * firstHorizontal)
    val secondSegmentLength = kotlin.math.sqrt(secondVertical * secondVertical + secondHorizontal * secondHorizontal)

    if (firstSegmentLength < swipeThreshold || secondSegmentLength < swipeThreshold) {
        return null
    }
    // Determine pattern type
    if (abs(firstVertical) > abs(firstHorizontal) * threshold &&
        abs(secondHorizontal) > abs(secondVertical) * threshold) {

        return when {
            firstVertical > 0 && secondHorizontal > 0 -> LPatternType.DOWN_RIGHT
            firstVertical > 0 && secondHorizontal < 0 -> LPatternType.DOWN_LEFT
            firstVertical < 0 && secondHorizontal > 0 -> LPatternType.UP_RIGHT
            firstVertical < 0 && secondHorizontal < 0 -> LPatternType.UP_LEFT
            else -> null
        }
    }

    if (abs(firstHorizontal) > abs(firstVertical) * threshold &&
        abs(secondVertical) > abs(secondHorizontal) * threshold) {

        return when {
            firstHorizontal > 0 && secondVertical > 0 -> LPatternType.RIGHT_DOWN
            firstHorizontal > 0 && secondVertical < 0 -> LPatternType.RIGHT_UP
            firstHorizontal < 0 && secondVertical > 0 -> LPatternType.LEFT_DOWN
            firstHorizontal < 0 && secondVertical < 0 -> LPatternType.LEFT_UP
            else -> null
        }
    }

    return null
}

private fun detectCircularPattern(path: List<Offset>,swipeThreshold:Float): String? {
    if (path.size < 10) return null

    // Normalize path to bounding box
    val minX = path.minOf { it.x }
    val maxX = path.maxOf { it.x }
    val minY = path.minOf { it.y }
    val maxY = path.maxOf { it.y }

    val width = maxX - minX
    val height = maxY - minY

    // Need minimum gesture size
    if (width < swipeThreshold || height < swipeThreshold) return null

    // Normalize points to 0-1 range
    val normalized = path.map {
        Offset(
            (it.x - minX) / width,
            (it.y - minY) / height
        )
    }

    // Detect patterns
    return when {
        isCircularPattern(normalized, width, height) -> "O"
        else -> null
    }
}

private fun isCircularPattern(points: List<Offset>, width: Float, height: Float): Boolean {
    // Check if aspect ratio is close to square
    val aspectRatio = width / height
    if (aspectRatio < 0.7f || aspectRatio > 1.3f) return false

    // Calculate center
    val centerX = points.map { it.x }.average().toFloat()
    val centerY = points.map { it.y }.average().toFloat()
    val center = Offset(centerX, centerY)

    // Calculate distances from center
    val distances = points.map { point ->
        kotlin.math.sqrt(
            (point.x - center.x) * (point.x - center.x) +
                    (point.y - center.y) * (point.y - center.y)
        )
    }

    val avgDistance = distances.average()
    val variance = distances.map { (it - avgDistance) * (it - avgDistance) }.average()
    val stdDev = kotlin.math.sqrt(variance)

    // Low standard deviation indicates circular path
    return stdDev / avgDistance < 0.25
}
private fun detectSimpleSwipe(
    path: List<Offset>,
    threshold: Float,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val startPoint = path.first()
    val endPoint = path.last()

    val deltaX = endPoint.x - startPoint.x
    val deltaY = endPoint.y - startPoint.y

    val absDeltaX = abs(deltaX)
    val absDeltaY = abs(deltaY)

    // Determine if swipe is primarily horizontal or vertical
    if (absDeltaX > threshold || absDeltaY > threshold) {
        if (absDeltaX > absDeltaY) {
            // Horizontal swipe
            if (deltaX > 0) {
                onSwipeRight()
            } else {
                onSwipeLeft()
            }
        } else {
            // Vertical swipe
            if (deltaY > 0) {
                onSwipeDown()
            } else {
                onSwipeUp()
            }
        }
    }
}
class WidgetHostManager(private val context: Context) {
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    lateinit var mainHost: AppWidgetHost
    lateinit var leftHost: AppWidgetHost
    lateinit var rightHost: AppWidgetHost

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initHosts() {
        mainHost = AppWidgetHost(context, MAIN_WIDGET_HOST_ID)
        leftHost = AppWidgetHost(context, LEFT_SIDE_WIDGET_HOST_ID)
        rightHost = AppWidgetHost(context, RIGHT_SIDE_WIDGET_HOST_ID)
    }

    fun startListening() {
        scope.launch {
            // Offload binder calls to background thread
            withContext(Dispatchers.IO) {
                try {
                    mainHost.startListening()
                    leftHost.startListening()
                    rightHost.startListening()
                } catch (e: Exception) {
                    // Log error - widget updates may not work
                    Log.e("WidgetHostManager", "Error starting widget hosts", e)
                }
            }
        }
    }

    fun stopListening() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    mainHost.stopListening()
                    leftHost.stopListening()
                    rightHost.stopListening()
                } catch (e: Exception) {
                    Log.e("WidgetHostManager", "Error stopping widget hosts", e)
                }
            }
        }
    }

    fun cleanup() {
        scope.cancel()
    }
}


@Composable
fun DraggableQuickWidgetsContainer(
    modifier: Modifier = Modifier,
    editMode: Boolean,
    widgetIds: List<String>,
    widgetPositions: Map<String, Offset?>,
    onPositionChanged: (String, Offset) -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    composableContent: @Composable (String, Boolean) -> Unit
) {
    val hapticService = LocalHapticFeedback.current
    // Track measured sizes for initial column layout calculation
    val measuredSizes = remember { mutableStateMapOf<String, IntSize>() }

    // Calculate initial positions when all sizes are measured
    val initialPositions = remember { mutableStateMapOf<String, Offset>() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            //.border(width = 1.dp, Color.Cyan)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onEditModeChanged(false)
                    },
                    onDoubleTap = {},
                    onLongPress = {
                        hapticService.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEditModeChanged(true)
                    }
                )
            }
    ) {
        // Convert constraints to Px for calculations
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        // Function to calculate centered column positions
        // This is now inside the scope so it has access to containerWidthPx/HeightPx directly
        fun calculateInitialPositions() {
            if (containerWidthPx > 0 && containerHeightPx > 0) {
                val totalHeight = measuredSizes.filterKeys { it in widgetIds }.values.sumOf { it.height }
                var currentY = (containerHeightPx - totalHeight) / 2f

                widgetIds.forEach { id ->
                    if (widgetPositions[id] == null) {
                        val size = measuredSizes[id] ?: IntSize.Zero
                        val centerX = (containerWidthPx - size.width) / 2f

                        // Only update if it's different to avoid loops (though remember check handles this implicitly)
                        initialPositions[id] = Offset(centerX, currentY)
                        currentY += size.height
                    }
                }
            }
        }

        // Trigger calculation when relevant sizes or constraints change
        LaunchedEffect(widgetPositions.size, containerWidthPx, containerHeightPx, measuredSizes.size) {
            calculateInitialPositions()
        }

        widgetIds.forEach { id ->
            key(id, widgetPositions[id]) {
                DraggableQuickWidgets(
                    id = id,
                    editMode = editMode,
                    savedPosition = widgetPositions[id],
                    initialPosition = initialPositions[id],
                    onPositionChanged = onPositionChanged,
                    onSizeMeasured = { size ->
                        measuredSizes[id] = size
                        // Re-trigger calculation when a child reports its size
                        calculateInitialPositions()
                    },
                    content = { composableContent(id, editMode) }
                )
            }
        }
    }
}


@Composable
fun DraggableQuickWidgets(
    id: String,
    editMode: Boolean,
    savedPosition: Offset?,
    initialPosition: Offset?,
    onPositionChanged: (String, Offset) -> Unit,
    onSizeMeasured: (IntSize) -> Unit,
    content: @Composable () -> Unit
) {
    var currentOffset by remember {
        mutableStateOf(savedPosition ?: initialPosition ?: Offset.Zero)
    }

    // Update offset when saved position changes
    LaunchedEffect(savedPosition) {
        if (savedPosition != null) {
            currentOffset = savedPosition
        }
    }

    // Update offset when initial position is calculated
    LaunchedEffect(initialPosition) {
        if (savedPosition == null && initialPosition != null) {
            currentOffset = initialPosition
        }
    }

    // Track child composable size dynamically
    var composableSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    currentOffset.x.roundToInt(),
                    currentOffset.y.roundToInt()
                )
            }
            .onGloballyPositioned { coordinates ->
                val newSize = coordinates.size
                if (composableSize != newSize) {
                    composableSize = newSize
                    onSizeMeasured(newSize)
                }
            }
            .pointerInput(editMode) {
                if (editMode) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()

                            currentOffset = Offset(
                                x = currentOffset.x + dragAmount.x,
                                y = currentOffset.y + dragAmount.y
                            )

                            /*currentOffset = constrainToBoundary(
                                offset = newOffset,
                                composableSize = composableSize,
                                containerSize = containerSize
                            )*/
                        },
                        onDragEnd = {
                            onPositionChanged(id, currentOffset)
                        }
                    )
                }
            }
    ) {
        content()
    }
}


@Composable
fun LocaleSelectionDialog(
    onDismissRequest: () -> Unit,
    onLocaleSelected: (Locale) -> Unit
) {
    val locales = KeyboardLocale.getSupportedLocales()
    Dialog(onDismissRequest = onDismissRequest) {
        // A Surface to provide a background and shape for the dialog
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            LazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                items(locales) { locale ->

                    // A list item that is clickable to select
                    ListItem(
                        headlineContent = {
                            Text(
                                text = locale.getDisplayName(locale),
                                fontSize = 30.sp
                            )
                        },
                        modifier = Modifier.clickable {
                            onLocaleSelected(locale)
                            onDismissRequest() // Close the dialog on selection
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )
                }
            }
        }
    }
}

@Composable
fun EffectTextBlock(
    text: String,
    fontSize: TextUnit = 30.sp,
    color: Color = Color.Blue,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    fontFamily: FontFamily = FontFamily.Default,
    angle: Float = 0f,
    radius: Float = 0f,
    shadowColor: Int = Color.Black.toArgb()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val themedColors = PreferenceManager.getThemedColors(context)
    val isLightHour = PreferenceManager.isLightHour(context)
    val backgroundColorInt = getThemedBackgroundColor(themedColors,isLightHour)
    val foregroundColorInt = getThemedIconColor(themedColors,isLightHour)
    val reverse = radius < 0
    val absRadius = if (radius < 0) {
        500f + radius
    } else {
        500f - radius
    }
    val curveRadius = (1.02f).pow(absRadius) - 1
    // Resolve Typeface
    val typefaceState = remember(resolver, fontFamily, fontWeight, fontStyle) {
        resolver.resolveAsTypeface(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle
        )
    }
    val typeface = typefaceState.value
    // 1. Calculate the required size
    // For text on a path (Arc), the width is roughly the chord length or diameter,
    // and height depends on the font size + curve height.
    // Since curveRadius is usually huge (10000f) for slight bends, we shouldn't use it directly for size.
    // Instead, we measure the text width using Paint.
    val textPaint = remember(fontSize, typeface, fontStyle, fontWeight) {
        android.graphics.Paint().apply {
            this.textSize = with(density) { fontSize.toPx() }
            this.typeface = typeface
            this.isFakeBoldText = fontWeight.weight >= FontWeight.Bold.weight && !typeface.isBold
            this.textSkewX = if (fontStyle == FontStyle.Italic && !typeface.isItalic) -0.25f else 0f
        }
    }

    // Measure text dimensions
    val textWidth = remember(text, textPaint) { textPaint.measureText(text) }
    val fontMetrics = remember(textPaint) { textPaint.fontMetrics }
    val textHeight = remember(fontMetrics) {
        (fontMetrics.descent - fontMetrics.ascent)
    }

    // Determine Canvas Size
    // If we rotate, the bounding box changes. For simplicity, we create a box large enough
    // to hold the text width and height plus some padding for the shadow and curve.
    // A more complex math solution would calculate exact rotated bounds.
    val canvasWidth = with(density) { textWidth.toDp() } // 20% padding
    val canvasHeight = with(density) { (textHeight*0.7f).toDp() } // 30% padding for arc/shadow

    // 2. Use the calculated size modifiers
    Canvas(
        modifier = Modifier
            .size(width = canvasWidth, height = canvasHeight)
        //.border(width=1.dp,Color.Blue)
        // Optional: wrapContentSize if you want it to center in a larger parent
        // .wrapContentSize()
    ) {
        val paint = textPaint.apply {
            this.color = color.toArgb()
            this.textAlign = android.graphics.Paint.Align.CENTER
            this.isAntiAlias = true
            setShadowLayer(10f,
                5f,
                5f,
                shadowColor
            )
        }

        // Center the arc in the new dynamic size
        val cx = size.width / 2
        val cy = size.height / 2

        // Adjust the path to curve around the center of our canvas
        var top = cy
        var bottom = cy + (curveRadius * 2)
        var sweepAngle = 180f
        if(reverse){
            top = cy - (curveRadius * 2)
            bottom  = cy
            sweepAngle = -180f
        }
        val path = android.graphics.Path().apply {
            addArc(
                RectF(
                    cx - curveRadius,
                    top,
                    cx + curveRadius,
                    bottom
                ),
                180f,
                sweepAngle
            )
        }

        val vOffsetCorrection = -((textPaint.descent() + textPaint.ascent()) / 2)

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.save()

            // Rotate around the calculated center
            nativeCanvas.rotate(angle, cx, cy)

            // Draw text centered on the path (0 offset)
            // Note: Since we use Align.CENTER, hOffset should be 0 to center on the path's top point
            nativeCanvas.drawTextOnPath(text,
                path,
                0f,
                vOffsetCorrection,
                paint)

            nativeCanvas.restore()
        }
    }
}
