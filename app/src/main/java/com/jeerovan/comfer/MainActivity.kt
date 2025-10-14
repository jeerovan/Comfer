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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import java.io.File
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import kotlin.math.min
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.AlarmClock
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import com.jeerovan.comfer.utils.CommonUtil.stringToColor
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
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlin.math.atan2


@Composable
fun DraggableContainerWithViewModel(
    modifier: Modifier = Modifier,
    topColumnHeight: Dp,
    widgetIds: List<String>,
    widgetPositions: Map<String, Offset?>,
    hasPro: Boolean,
    onPositionChanged: (String, Offset) -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    composableContent: @Composable (String, Boolean) -> Unit
) {
    // Edit mode state
    var editMode by remember { mutableStateOf(false) }

    // Container dimensions
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Track measured sizes for initial column layout calculation
    val measuredSizes = remember { mutableStateMapOf<String, IntSize>() }

    // Calculate initial positions when all sizes are measured
    val initialPositions = remember { mutableStateMapOf<String, Offset>() }

    // Calculate centered column positions once container and all children are measured
    fun setInitialSizes(){
        if (containerSize.width > 0) {
            // Calculate positions for centered column layout
            val totalHeight = measuredSizes.filterKeys { it in widgetIds }.values.sumOf { it.height }
            var currentY = (containerSize.height - totalHeight) / 2f

            widgetIds.forEach { id ->
                if (widgetPositions[id] == null) {
                    val size = measuredSizes[id] ?: IntSize.Zero
                    val centerX = (containerSize.width - size.width) / 2f

                    initialPositions[id] = Offset(centerX, currentY)
                    currentY += size.height
                }
            }
        }
    }
    LaunchedEffect(widgetPositions.size,containerSize, measuredSizes.size) {
        setInitialSizes()
    }
    Box(
        modifier = modifier
            //.border(width = 1.dp, Color.Cyan)
            .fillMaxWidth()
            .height(topColumnHeight)
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                                if(editMode){
                                    editMode = false
                                    onEditModeChanged(false)
                                }
                            },
                    onDoubleTap = {},
                    onLongPress = {
                        editMode = !editMode
                        onEditModeChanged(editMode)
                    }
                )
            }
    ) {
        widgetIds.forEach { id ->
            key(id,widgetPositions[id]) {
                DraggableComposableWithViewModel(
                    id = id,
                    editMode = editMode,
                    containerSize = containerSize,
                    savedPosition = widgetPositions[id],
                    initialPosition = initialPositions[id],
                    onPositionChanged = onPositionChanged,
                    onSizeMeasured = { size ->
                        measuredSizes[id] = size
                        setInitialSizes()
                    },
                    content = { composableContent(id, editMode) }
                )
            }
        }
    }
}

@Composable
fun DraggableComposableWithViewModel(
    id: String,
    editMode: Boolean,
    containerSize: IntSize,
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

                            val newOffset = Offset(
                                x = currentOffset.x + dragAmount.x,
                                y = currentOffset.y + dragAmount.y
                            )

                            currentOffset = constrainToBoundary(
                                offset = newOffset,
                                composableSize = composableSize,
                                containerSize = containerSize
                            )
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

// Constrain offset to stay within container boundaries
private fun constrainToBoundary(
    offset: Offset,
    composableSize: IntSize,
    containerSize: IntSize
): Offset {
    val maxX = (containerSize.width - composableSize.width).toFloat()
    val maxY = (containerSize.height - composableSize.height).toFloat()

    return Offset(
        x = offset.x.coerceIn(0f, maxX.coerceAtLeast(0f)),
        y = offset.y.coerceIn(0f, maxY.coerceAtLeast(0f))
    )
}

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

class MainActivity : ComponentActivity(), UpdatedCustomerInfoListener {
    private val appInfoViewModel: AppInfoViewModel by viewModels()
    private val settingsViewModel:SettingsViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    // Widgets
    private lateinit var mainWidgetHost: AppWidgetHost
    private lateinit var leftSideWidgetHost: AppWidgetHost
    private lateinit var rightSideWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        mainWidgetHost = AppWidgetHost(applicationContext, MAIN_WIDGET_HOST_ID)
        leftSideWidgetHost = AppWidgetHost(applicationContext, LEFT_SIDE_WIDGET_HOST_ID)
        rightSideWidgetHost = AppWidgetHost(applicationContext, RIGHT_SIDE_WIDGET_HOST_ID)

        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(PurchasesConfiguration.Builder(this, "goog_alczWNGIWABONRuXvtRSKpPJFXi").build())
        Purchases.sharedInstance.updatedCustomerInfoListener = this
        checkSubscriptionStatus()

        setContent {
            ComferTheme {
                LauncherScreen(appInfoViewModel,
                    settingsViewModel,
                    mainViewModel,
                    appWidgetManager,
                    mainWidgetHost,
                    leftSideWidgetHost,
                    rightSideWidgetHost)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val logger = LoggerManager(applicationContext)
        logger.setLog("MainActivity","Started")
        mainWidgetHost.startListening()
        leftSideWidgetHost.startListening()
        rightSideWidgetHost.startListening()
        lifecycleScope.launch {
            appInfoViewModel.loadAppLists()
            settingsViewModel.loadSettings()
            mainViewModel.checkLoadWallpaper()
        }
    }

    override fun onStop(){
        super.onStop()
        val logger = LoggerManager(applicationContext)
        logger.setLog("MainActivity","Stopped")
        mainWidgetHost.stopListening()
        leftSideWidgetHost.stopListening()
        rightSideWidgetHost.stopListening()
        lifecycleScope.launch {
            //delay(1000) // Delay, does not stop main thread
            mainViewModel.loadImageData()
        }
    }

    override fun onReceived(customerInfo: CustomerInfo) {
        // This fires whenever CustomerInfo changes
        updateSubscriptionStatus(customerInfo)
    }

    private fun checkSubscriptionStatus() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                settingsViewModel.setPro(false)
            },
            onSuccess = { customerInfo ->
                updateSubscriptionStatus(customerInfo)
            }
        )
    }

    private fun updateSubscriptionStatus(customerInfo: CustomerInfo) {
        val isActive = customerInfo.entitlements.active.isNotEmpty()
        settingsViewModel.setPro(isActive)
    }
}

@Composable
fun WidgetHostScreen(
    appWidgetManager: AppWidgetManager,
    appWidgetHost: AppWidgetHost,
    widgetPrefsTitle: String,
    gridColumns: Int,
    fullScreen: Boolean,
    screenHeight: Dp,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val context = LocalContext.current
    val prefs: SharedPreferences = context.getSharedPreferences(widgetPrefsTitle, MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()
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
    // Load widgets from SharedPreferences on startup
    LaunchedEffect(Unit) {
        val loadedWidgets = loadWidgetsFromPrefs(prefs, appWidgetManager)
        boundWidgets.clear()
        boundWidgets.addAll(loadedWidgets)
    }
    // Hook into the onResume lifecycle event.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncAndRefreshProviders()
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
    LaunchedEffect(isDarkTheme) {
        // Find all unique provider classes from the currently bound widgets
        refreshWidgets()
    }

    val gapWidth = 8.dp
    val gapWidthPx = with(LocalDensity.current) { gapWidth.toPx() }
    // Calculate the total horizontal space available after accounting for all gaps
    val screenWidthDp = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp }
    val screenHeightDp = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp }
    val windowHeightDp = if (fullScreen) screenHeightDp else screenHeight
    val windowHeightPx = with(LocalDensity.current) { windowHeightDp.toPx()}
    val screenWidthPx = with(LocalDensity.current) { screenWidthDp.toPx()}
    val totalHorizontalGapPx = (gridColumns + 1) * gapWidthPx
    val totalAvailableWidth = screenWidthPx - totalHorizontalGapPx
    val cellWidthPx = totalAvailableWidth / gridColumns
    // 1. Estimate total rows based on square cells to start
    val minHeightPx = cellWidthPx
    val totalGridRows = floor(windowHeightPx / (minHeightPx + gapWidthPx)).toInt()
    // 2. Calculate the exact cell height required to fill the screen with that many rows
    // The total space for cells is the screen height minus all vertical gaps.
    // There is one gap for each row, plus one final gap at the bottom.
    val totalVerticalGapPx = (totalGridRows + 1) * gapWidthPx
    val totalAvailableHeight = windowHeightPx - totalVerticalGapPx
    val cellHeightPx = totalAvailableHeight / totalGridRows

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
                    LoggerManager(context).setLog("configureWidgetLauncher", "Creating Widget:$appWidgetId:${provider.provider}")
                    createWidgetView(provider, appWidgetId)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    LoggerManager(context).setLog("configureWidgetLauncher", "Provider is NULL")
                }
            } else {
                LoggerManager(context).setLog("configureWidgetLauncher","Invalid widgetId")
            }
        } else {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
            Toast.makeText(context, "Widget binding cancelled", Toast.LENGTH_SHORT).show()
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
                LoggerManager(context).setLog("CheckConfigureWidget","Running configureWidgetLauncher")
                configureWidgetLauncher.launch(intent)
            } catch (e:Exception){
                LoggerManager(context).setLog( "configureWidgetLauncher.launch failed",e.toString())
                createWidgetView(provider,appWidgetId)
            }
        } else {
            // No configuration needed, create the widget view directly
            LoggerManager(context).setLog("CheckConfigureWidget","Not Required")
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
                    LoggerManager(context).setLog("BindWidgetLauncher", "Checking configuration")
                    checkConfigureWidget(provider, appWidgetId)
                } else {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    LoggerManager(context).setLog("BindWidgetLauncher", "provider is null: $appWidgetId")
                }
            } else {
                LoggerManager(context).setLog("BindWidgetLauncher", "Invalid appWidgetId")
            }
        } else {
            // User cancelled the binding. Clean up the allocated ID.
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
            Toast.makeText(context, "Widget binding cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    val sizeModifier = if(fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(windowHeightDp)
    Box(
            modifier = sizeModifier
                //.border(width=1.dp,Color.White)
                .detectSwipes(
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight
                ).pointerInput(Unit){
                    detectTapGestures (
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
            if (boundWidgets.isEmpty() && !editMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp) // Keep horizontal padding for screen margins
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp)) // Clip the content to the rounded shape
                        .background(Color.Black.copy(alpha = 0.5f)), // Black background for the box
                    contentAlignment = Alignment.Center, // Center the Text inside the Box
                ) {
                    Text(
                        text = "Long press to add/edit widgets",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center, // Ensure placeholder text is centered
                        modifier = Modifier.padding(horizontal = 16.dp) // Inner padding for the text
                    )
                }
            }

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

            if (showPicker) {
                WidgetPickerFullScreen(
                    onDismiss = { showPicker = false },
                    onWidgetSelected = { provider ->
                        showPicker = false
                        val appWidgetId = appWidgetHost.allocateAppWidgetId()
                        LoggerManager(context).setLog("WidgetHost","Allocated WidgetId: $appWidgetId")
                        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider)
                        if (canBind) {
                            checkConfigureWidget(provider,appWidgetId)
                        } else {
                            LoggerManager(context).setLog("WidgetHost","Can NOT bind: $appWidgetId")
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                            }
                            try {
                                LoggerManager(context).setLog("WidgetHost","Calling bindWidgetLauncher")
                                bindWidgetLauncher.launch(intent)
                            } catch (e:Exception){
                                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                                }
                                LoggerManager(context).setLog("bindWidgetLauncher.launch failed", e.toString())
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
        //.border(width = 1.dp,Color.Red)
        .fillMaxSize()
        .padding(horizontal = gapWidth)
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
            .pointerInput(Unit){
                detectTapGestures (
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
                "Add Widget")
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
    // Initial grid-based calculations
    val initialX = (widget.gridX * (cellWidthPx + gapPx))
    val initialY = (widget.gridY * (cellHeightPx + gapPx)) + gapPx
    val initialWidth = (widget.spanX * cellWidthPx) + ((widget.spanX - 1) * gapPx)
    val initialHeight = (widget.spanY * cellHeightPx) + ((widget.spanY - 1) * gapPx)

    var position by remember { mutableStateOf(Offset(initialX, initialY)) }
    var size by remember { mutableStateOf(IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())) }
    var widgetUpdated by remember { mutableStateOf(false)}

    // Re-sync position and size if the widget's grid properties change externally
    LaunchedEffect(widget.gridX, widget.gridY, widget.spanX, widget.spanY) {
        position = Offset(initialX, initialY)
        size = IntSize(initialWidth.roundToInt(), initialHeight.roundToInt())
    }

    val density = LocalDensity.current
    val windowWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val minWidgetSizePx = with(density) { 40.dp.toPx() }

    val appWidgetProviderInfo = remember { widget.providerInfo }

    fun getBundleOptionsFromCurrentSize():Bundle{
        val width = with(density){size.width.toDp().value.toInt()}
        val height = with(density){size.height.toDp().value.toInt()}
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
    }
    var hasError by remember { mutableStateOf(false) }
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
                .pointerInput(editMode, allWidgets) { // Re-trigger pointer input if widgets change
                    if (editMode) {
                        detectDragGestures(
                            onDragEnd = {
                                // Snap to the final grid position after dragging
                                val finalGridX = ((position.x) / (cellWidthPx + gapPx)).roundToInt()
                                val finalGridY =
                                    ((position.y - gapPx) / (cellHeightPx + gapPx)).roundToInt()

                                if (widget.gridX != finalGridX || widget.gridY != finalGridY) {
                                    widget.gridX = finalGridX
                                    widget.gridY = finalGridY
                                    onUpdate()
                                }
                                // Ensure the visual position snaps perfectly to the grid
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

                            // Calculate proposed grid position
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

                            // Update position only if there's no collision
                            if (!isColliding(proposedRect, widget.widgetId, allWidgets)) {
                                position = newPos
                            }
                            beingRearranged(true)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Text(
                    text = "Could not load widget",
                    modifier = Modifier.fillMaxSize(),
                    textAlign = TextAlign.Center
                )
            } else {
                AndroidView(
                    factory = { context ->
                        val hostView = appWidgetHost.createView(context.applicationContext, widget.widgetId, appWidgetProviderInfo)
                        try {
                            hostView.setAppWidget(widget.widgetId, appWidgetProviderInfo)
                            hostView.updateAppWidgetOptions( getBundleOptionsFromCurrentSize())
                        } catch (_: Exception) {
                            hasError = true
                        }
                        hostView
                    },
                    update = { hostView ->
                        if (!widgetUpdated) {
                            widgetUpdated = true
                            try {
                                hostView.updateAppWidgetOptions(getBundleOptionsFromCurrentSize())
                            } catch (_: Exception) {

                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- Edit Mode Controls ---
        AnimatedVisibility(visible = editMode, enter = fadeIn(), exit = fadeOut()) {
            val handleSize = 12.dp
            val handleSizePx = with(density) { handleSize.toPx() }

            val onResizeEnd: () -> Unit = {
                // Calculate final spans from the current size
                val finalSpanX = max(1, (size.width / (cellWidthPx + gapPx)).roundToInt()).coerceAtMost(gridColumns - widget.gridX)
                val finalSpanY = max(1, (size.height / (cellHeightPx + gapPx)).roundToInt())

                // Calculate final grid position (important for top/left resize)
                val finalGridX = ((position.x) / (cellWidthPx + gapPx)).roundToInt()
                val finalGridY = ((position.y - gapPx) / (cellHeightPx + gapPx)).roundToInt()

                // Check if anything actually changed
                if (widget.spanX != finalSpanX || widget.spanY != finalSpanY || widget.gridX != finalGridX || widget.gridY != finalGridY) {
                    widget.spanX = finalSpanX
                    widget.spanY = finalSpanY
                    widget.gridX = finalGridX
                    widget.gridY = finalGridY
                    onUpdate()
                }

                // Snap back to grid-aligned position and size after resizing
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
                        with(LocalDensity.current) { size.width.toDp() },
                        with(LocalDensity.current) { size.height.toDp() })
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        onRemove(widget)
                              },
                    modifier = Modifier.align(Alignment.TopEnd),
                    shape = CircleShape,
                    // Set elevation to 0 to remove the default shadow, which might look odd in this context
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove"
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
    return allWidgets.any { other ->
        if (other.widgetId == currentWidgetId) return@any false
        val otherRect = IntRect(
            left = other.gridX,
            top = other.gridY,
            right = other.gridX + other.spanX,
            bottom = other.gridY + other.spanY
        )
        proposedRect.overlaps(otherRect)
    }
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
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(120.dp)
                                            .clickable {
                                                view.playSoundEffect(SoundEffectConstants.CLICK)
                                                onWidgetSelected(provider)
                                            }
                                    ) {
                                        val previewDrawable = remember(provider) {
                                            // Load the preview image with a fallback to the icon.
                                            provider.loadPreviewImage(context, 0)
                                                ?: provider.loadIcon(context, 0)
                                        }

                                        Image(
                                            painter = rememberDrawablePainter(drawable = previewDrawable),
                                            contentDescription = provider.loadLabel(context.packageManager),
                                            modifier = Modifier
                                                .height(100.dp)
                                                .fillMaxWidth(),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            provider.loadLabel(context.packageManager),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2
                                        )
                                    }
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
                    contentDescription = "Close",
                )
            }
        }
    }
}


// --- Utility & Persistence Functions ---

private fun getGroupedWidgetProviders(context: Context): List<WidgetProviderGroup> {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val packageManager = context.packageManager
    return appWidgetManager.installedProviders.groupBy { it.provider.packageName }
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
}

private fun saveWidgetsToPrefs(prefs: SharedPreferences, widgets: List<BoundWidget>) {
    val persistableList = widgets.map {
        PersistableBoundWidget(it.widgetId, it.providerInfo.provider.packageName, it.providerInfo.provider.className, it.gridX, it.gridY, it.spanX, it.spanY)
    }
    val jsonString = Json.encodeToString(persistableList)
    prefs.edit { putString(BOUND_WIDGETS_KEY, jsonString) }
}

private fun loadWidgetsFromPrefs(prefs: SharedPreferences, appWidgetManager: AppWidgetManager): List<BoundWidget> {
    val jsonString = prefs.getString(BOUND_WIDGETS_KEY, null) ?: return emptyList()
    return try {
        val persistableList = Json.decodeFromString<List<PersistableBoundWidget>>(jsonString)
        persistableList.mapNotNull { persist ->
            val provider = appWidgetManager.installedProviders.find {
                it.provider == ComponentName(persist.providerPackage, persist.providerClass)
            }
            if (provider != null) {
                BoundWidget(persist.widgetId, provider, persist.gridX, persist.gridY, persist.spanX, persist.spanY)
            } else {
                null // Provider not found, maybe app was uninstalled
            }
        }
    } catch (e: Exception) {
        Log.e("LoadWidgetsFromPrefs",e.toString())
        emptyList()
    }
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
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryState(-1, false)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryLevel = if (level != -1 && scale != -1) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    -1
                }

                val status: Int = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                batteryState.value = BatteryState(batteryLevel, isCharging)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}


@Composable
fun BatteryStatus(
    settings: SettingsUiState,
    defaultColor: Color,
    showBorder: Boolean
) {
    val customWallpaper = settings.wallpaperDirectory != null
    val themeColor = if(customWallpaper) settings.batteryColor else defaultColor
    val borderColor = if(showBorder) themeColor else Color.Transparent
    val showBatteryIcon = settings.showBatteryIcon
    val showBatteryPercentage = settings.showBatteryPercentage
    val fontFamily = settings.dateFontFamily
    val fontWeight = getFontWeightFromString(settings.dateFontWeight)
    val fontSize = settings.batterySize.sp
    val batteryState by rememberBatteryState()
    val batteryLevel = batteryState.level
    val isCharging = batteryState.isCharging
    val isLow = batteryLevel < 10
    val batteryLevelColor = if (isLow) Color.Red else themeColor

    // Calculate icon size based on font size
    val iconHeight = with(LocalDensity.current) { fontSize.toDp() * 0.6f}
    val iconWidth = iconHeight * 2 // Maintain a 2:1 aspect ratio

    Row(modifier = Modifier
        .border(width = 2.dp,color = borderColor,shape = RoundedCornerShape(8.dp))
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
            Text(
                text = "$batteryLevel%",
                color = themeColor,
                fontSize = fontSize, // Use the fontSize parameter
                fontFamily = fontFamily,
                fontWeight = fontWeight
            )
        }
    }

}

@Composable
fun QuickListOverlay(apps: List<AppInfo>,
                     appWidgetManager: AppWidgetManager,
                     mainWidgetHost: AppWidgetHost,
                     notificationIcons: List<Drawable>,
                     notificationPackages: List<String>,
                     imageData: ImageData?,
                     settingsModel: SettingsViewModel,
                     onSwipeUp: () -> Unit,
                     onSwipeRight: () -> Unit,
                     onSwipeLeft: () -> Unit,
                     onShowSearch:() -> Unit) {
    val context = LocalContext.current
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
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        isDefault = isDefaultLauncher(context)
        guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
        feedbackShown = PreferenceManager.getFeedbackDialogShown(context)
        delay(500)
        canShowGuide = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                iconShape = PreferenceManager.getIconShape(context)
                guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
                feedbackShown = PreferenceManager.getFeedbackDialogShown(context)
                isDefault = isDefaultLauncher(context)
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
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    if(!guideShown && canShowGuide)GuideDialog(
        onDismiss = {onGuideDismiss()},
        title = "Welcome to Comfer",
        steps = listOf(
            "Long press the screen to open menu and checkout how to..."
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
    val maxHeightDp = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp }
    val topColumnHeight = maxHeightDp.dp * 2/5
    var defaultColor = imageData?.color?.let { colorName ->
        stringToColor(colorName)
    } ?: Color.White
    if (!settings.wallpaperMotionEnabled && !isDefault) {
        defaultColor = Color.White
    }
    var showWidgetSettings by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column (modifier = Modifier) {
            if(settings.hasPro && settings.hasCustomWidgets) {
                WidgetHostScreen(
                    appWidgetManager,
                    mainWidgetHost,
                    "widgets_center",
                    gridColumns = 9,
                    fullScreen = false,
                    screenHeight = topColumnHeight,
                    onSwipeRight = {},
                    onSwipeLeft = {})
            } else {
                DraggableContainerWithViewModel (
                    topColumnHeight = topColumnHeight,
                    widgetIds = settings.widgetIds,
                    widgetPositions = settings.widgetPositions,
                    hasPro = settings.hasPro,
                    onPositionChanged = { id, offset ->
                        settingsModel.saveWidgetPosition(id, offset.x, offset.y)
                    },
                    onEditModeChanged = { editMode ->  showWidgetSettings = editMode},
                    composableContent = { id, editMode ->
                        when (id) {
                            "time" -> WidgetClock(
                                settings,
                                defaultColor,
                                editMode = editMode)
                            "date" -> WidgetDate(
                                settings,
                                defaultColor,
                                showBorder = editMode)
                            "battery" -> BatteryStatus(
                                settings,
                                defaultColor,
                                showBorder = editMode)
                            "notifications" -> NotificationIconRow(
                                notificationIcons,
                                settings = settings,
                                defaultColor =  defaultColor,
                                showBorder = editMode
                            )
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
                            //.border(1.dp, color = Color.Cyan)
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {},
                                    onDoubleTap = {},
                                    onLongPress = {
                                    }
                                )
                            }
                    ) {
                        ProSettingsScreen(settingsModel)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            //.border(1.dp, color = Color.Cyan)
                            .fillMaxSize()
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
                                        val swipeLeftPackage =
                                            PreferenceManager.getSwipeApp(context, "left")
                                        if (swipeLeftPackage != null) {
                                            val launchIntent: Intent? =
                                                context.packageManager.getLaunchIntentForPackage(
                                                    swipeLeftPackage
                                                )
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                    }
                                },
                                onSwipeRight = {
                                    val showWidget =
                                        PreferenceManager.getWidgetsOnSwipe(context, "right")
                                    if (showWidget) {
                                        onSwipeRight()
                                    } else {
                                        val swipeRightPackage =
                                            PreferenceManager.getSwipeApp(context, "right")
                                        if (swipeRightPackage != null) {
                                            val launchIntent: Intent? =
                                                context.packageManager.getLaunchIntentForPackage(
                                                    swipeRightPackage
                                                )
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                    }
                                },
                                onCircular = {
                                    val appOnCircularPattern = settings.patternApps["Center"]
                                    if(appOnCircularPattern != null && settings.hasPro) {
                                        val launchIntent: Intent? =
                                            context.packageManager.getLaunchIntentForPackage(
                                                appOnCircularPattern.packageName
                                            )
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    }
                                },
                                onLPatternDetected = { pattern ->
                                    val patternApp = settings.patternApps[pattern]
                                    if(patternApp != null && settings.hasPro) {
                                        val launchIntent: Intent? =
                                            context.packageManager.getLaunchIntentForPackage(
                                                patternApp.packageName
                                            )
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
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
                                        "Set default launcher",
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
                                    settings.showThemedIcons
                                )

                                "circular" -> CircularLayout(
                                    apps,
                                    notificationPackages,
                                    iconSize,
                                    iconShape,
                                    onShowSearch,
                                    settings.showThemedIcons
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
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
    val filteredApps by remember(inputText, apps) {
        derivedStateOf {
            if(activeTab == SearchTab.APPS) {
                searchApps(inputText.trim(), apps)
            } else {
                apps
            }
        }
    }
    val filteredContacts by remember(inputText, contacts) {
        derivedStateOf {
            if(activeTab == SearchTab.CONTACTS){
                searchContacts(inputText, contacts)
            } else {
                contacts
            }
        }
    }
    var selectedContactIndex by remember { mutableIntStateOf(0) }
    val selectedContact by remember(selectedContactIndex) {
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
                context.startActivity(launchIntent)
                // Optional: Clear the input text after launching
                inputText = ""
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                iconShape = PreferenceManager.getIconShape(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
    val scrollThreshold = 50f // The number of pixels to drag before the index changes
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
                lazyListState,
                filteredContacts.size
            ) { // Relaunch gesture detection if state or data changes
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val isAtTop = !lazyListState.canScrollBackward
                        val isAtBottom = !lazyListState.canScrollForward

                        when {
                            // Dragging Down (finger moves down, content tries to move up)
                            dragAmount > 0 -> {
                                if (isAtTop) {
                                    // Requirement 4: At the top, select previous item on drag.
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator > scrollThreshold) {
                                        selectedContactIndex =
                                            (selectedContactIndex - 1).coerceAtLeast(0)
                                        dragAccumulator = 0f
                                    }
                                } else {
                                    coroutineScope.launch {
                                        lazyListState.scrollBy(-2 * dragAmount)
                                    }
                                }
                            }
                            // Dragging Up (finger moves up, content tries to move down)
                            dragAmount < 0 -> {
                                if (isAtBottom) {
                                    // Requirement 3: At the bottom, select next item on drag.
                                    dragAccumulator += dragAmount
                                    if (dragAccumulator < -scrollThreshold) {
                                        selectedContactIndex =
                                            (selectedContactIndex + 1).coerceAtMost(filteredContacts.lastIndex)
                                        dragAccumulator = 0f
                                    }
                                } else {
                                    coroutineScope.launch {
                                        lazyListState.scrollBy(-2 * dragAmount)
                                    }
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
                        text = { Text("Apps") },
                        // Let the Tab itself handle color changes based on its 'selected' state
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // "Contacts" Tab
                    Tab(
                        selected = activeTab == SearchTab.CONTACTS,
                        onClick = { onTabSelected(SearchTab.CONTACTS) },
                        text = { Text("Contacts") },
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
                val maxWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp }
                val textBoxPadding = (maxWidth/5).dp
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
                        text = inputText.ifEmpty { "Type text" },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center, // Ensure placeholder text is centered
                        modifier = Modifier.padding(horizontal = 16.dp) // Inner padding for the text
                    )
                }
                // Circular Keyboard
                CircularKeyboard(
                    onChar = { char ->
                        inputText += char
                    },
                    onBackspace = {
                        if (inputText.isNotEmpty()) {
                            inputText = inputText.dropLast(1)
                        }
                    },
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
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            // Add spacing between the items
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(filteredApps) { app ->
                                AppIcon(app,
                                    notificationPackages,
                                    iconShape,
                                    iconSize=iconSize)
                            }
                        }
                    }
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
                text = "This feature requires permission to access your contacts.",
                textAlign = TextAlign.Center
            )
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
            Text(
                text = "We respect your privacy and do not collect any of your data. Tap here to read our privacy policy.",
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

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                iconShape = PreferenceManager.getIconShape(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollAnimatable = remember { Animatable(0f) }
    var centerAppIndex by remember { mutableIntStateOf(0) }
    var lastCenterAppIndex by remember { mutableIntStateOf(0) }
    var centerIconX by remember { mutableFloatStateOf(0f) }
    var centerIconY by remember { mutableFloatStateOf(0f) }
    var centerIconSize by remember { mutableFloatStateOf(0f) }

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
                                val launchIntent =
                                    packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    val opts = ActivityOptions.makeScaleUpAnimation(
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
                                dragAxis = if (dragAmount.x.absoluteValue > dragAmount.y.absoluteValue) {
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
                                val totalScrollWidth = apps.size * 20f

                                // Launching a coroutine is necessary to call the suspend function `snapTo`.
                                scope.launch {
                                    val newPosition = (scrollAnimatable.value + increment).wrap(totalScrollWidth)
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
                                        isSwipeDownTriggered = true // Prevents repeated calls in this gesture.
                                    }
                                }
                            }

                            null -> { /* Wait for axis to be locked */ }
                        }
                    },
                    onDragEnd = {
                        if (dragAxis == DragAxis.HORIZONTAL) {
                            val velocity = velocityTracker.calculateVelocity().x * 0.3f
                            scope.launch {
                                // Animate the fling with the calculated velocity.
                                val result = scrollAnimatable.animateDecay(velocity, exponentialDecay())

                                // After the decay animation, ensure the final value is wrapped correctly.
                                if (result.endReason == AnimationEndReason.Finished) {
                                    val totalScrollWidth = apps.size * 20f
                                    val finalValue = scrollAnimatable.value.wrap(totalScrollWidth)
                                    scrollAnimatable.snapTo(finalValue)
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
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
                            text = apps[targetIndex].label.toString(),
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 18.sp,
                            color = Color.White
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
                   appWidgetManager: AppWidgetManager,
                   mainWidgetHost: AppWidgetHost,
                   leftSideWidgetHost: AppWidgetHost,
                   rightSideWidgetHost: AppWidgetHost) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val logger = LoggerManager(context)

    var isAppListVisible by remember { mutableStateOf(false) }
    var isSearchListVisible by remember { mutableStateOf(false) }
    var areLeftWigetsVisible by remember { mutableStateOf(false) }
    var areRightWigetsVisible by remember { mutableStateOf(false) }
    var backgroundImage by remember { mutableStateOf<String?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }

    val appInfoUiState by appInfoViewModel.uiState.collectAsState()
    val settingInfoUiState by settingsViewModel.uiState.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()
    val notifications by MyNotificationListenerService.activeNotifications.collectAsState()

    val quickApps = appInfoUiState.quickApps
    val primaryApps = appInfoUiState.primaryApps
    val hiddenApps = appInfoUiState.restApps

    val sortedPrimaryApps = if(settingInfoUiState.arrangeInAlphabeticalOrder) primaryApps.sortedBy { it.label.toString() } else primaryApps

    val wallpaperMotionEnabled = settingInfoUiState.wallpaperMotionEnabled
    val hasNotificationAccess = settingInfoUiState.hasNotificationAccess

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
    val notificationIcons by rememberNotificationIcons(notifications,hasNotificationAccess,LocalContext.current)
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

    val imageData = mainUiState.imageData
    val cachedImagePath = mainUiState.imagePath

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
    fun fetchContacts(){
        coroutineScope.launch {
            val fetchedContacts = withContext(Dispatchers.IO) {
                // withContext(Dispatchers.IO) switches the coroutine to a background thread
                // ideal for disk or network I/O operations.
                val contactsList = mutableListOf<Contact>()
                val contentResolver = context.contentResolver
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
                        // Handle error: a required column is missing
                        return@withContext emptyList()
                    }

                    while (contactCursor.moveToNext()) {
                        val id = contactCursor.getLong(idIndex)
                        val name = contactCursor.getString(nameIndex)
                        val photoUriString = contactCursor.getString(photoUriIndex)
                        val photoUri = photoUriString?.toUri()
                        val hasPhoneNumber = contactCursor.getInt(hasPhoneNumberIndex) > 0

                        var number: String? = null
                        if (hasPhoneNumber) {
                            val phoneCursor = contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(id.toString()),
                                null
                            )

                            phoneCursor?.use {
                                if (it.moveToFirst()) {
                                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    number = it.getString(numberIndex)
                                }
                            }
                        }
                        if( name != null && number != null && name.isNotEmpty()) {
                            contactsList.add(Contact(id, name, photoUri, number))
                        }
                    }
                }
                contactsList // The result of the withContext block
            }
            // Update the state list on the main thread.
            // It is safe to update Compose's state objects from any thread.
            contacts.clear()
            val uniqueAndSortedContacts = fetchedContacts
                //.distinctBy { it.number } // First, create a new list with unique contacts based on their number
                .sortedBy { it.name }     // Then, sort the resulting unique list by name
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
    if (cachedImagePath != null){
        if(File(cachedImagePath).exists()) {
            backgroundImage = cachedImagePath
        } else {
            logger.setLog("LauncherScreen","$cachedImagePath does not exist")
        }
    } else {
        logger.setLog("LauncherScreen","cachedImagepath is NULL")
    }

    Box(
        modifier = Modifier
            //.border(width=1.dp,Color.White)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    onDoubleTap = {
                        if (isAccessibilityServiceEnabled(
                                context,
                                RecentsAccessibilityService::class.java
                            )
                        ) {
                            showRecentApps()
                        } else {
                            showDisclosure = true
                        }
                    }
                )
            }) {
        val maxWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val maxHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

        AnimatedBackground(backgroundImage,wallpaperMotionEnabled,maxWidthPx,maxHeightPx)

        // Quick-list layer, goes up and hides, come down and shows up
        AnimatedVisibility(
            visible = !isAppListVisible && !isSearchListVisible && !areLeftWigetsVisible && !areRightWigetsVisible,
            enter = enterTransition,
            exit = exitTransition
        ) {
            QuickListOverlay(apps = quickApps,
                appWidgetManager,
                mainWidgetHost,
                notificationIcons = notificationIcons,
                notificationPackages = notificationPackages,
                imageData = imageData,
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
            AppListOverlay(apps = sortedPrimaryApps,
                notificationPackages,
                onSwipeDown = { isAppListVisible = false })
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
                appWidgetManager,
                leftSideWidgetHost,
                "widgets_prefs_left",
                gridColumns = 7,
                fullScreen = true,
                screenHeight = 0.dp,
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
                appWidgetManager,
                rightSideWidgetHost,
                "widgets_prefs_right",
                gridColumns = 7,
                fullScreen = true,
                screenHeight = 0.dp,
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
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
fun AnimatedBackground(
    backgroundImage: Any?, // Can be a URL, URI, or other data Coil can handle
    wallpaperMotionEnabled: Boolean,
    maxWidthPx: Float,
    maxHeightPx: Float
) {
    if (backgroundImage != null && wallpaperMotionEnabled) {
        // 1. Create an infinite transition
        val infiniteTransition = rememberInfiniteTransition(label = "background-animation")

        // 2. Animate the angle from 0 to 2*PI over 60 seconds
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle-animation"
        )

        // 3. Calculate offsets based on the animated angle
        val xOffset = cos(angle) * maxWidthPx * 0.08f
        val yOffset = sin(angle) * maxHeightPx * 0.08f

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backgroundImage)
                .crossfade(true)
                .build(),
            contentDescription = "Background",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.2f)
                .graphicsLayer {
                    translationX = xOffset
                    translationY = yOffset
                },
            contentScale = ContentScale.Crop
        )
    }
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
        val width = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val height = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

        val sidePaddingPx = with(density) { sidePadding.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val smallIconPx = with(density) { smallIconSize.toPx() }
        val largeIconPx = with(density) { largeIconSize.toPx() }
        val minimumGapPx = with(density) { minimumGap.toPx() }

        val arcRadius = width / 2f - sidePaddingPx - smallIconPx / 2f
        val numTopIcons =
            2 * floor(PI / (4 * asin((smallIconPx / 2 + minimumGapPx / 2) / (arcRadius - smallIconPx / 2)))).toInt() + 1

        val arcCenterY = topPaddingPx + arcRadius

        val verticalSpacingPx =
            2 * (2 * (arcRadius - smallIconPx / 2) * sin(PI / (2 * (numTopIcons - 1))).toFloat() - smallIconPx).absoluteValue
        // Calculate the actual angular spacing based on the final number of top icons
        val angularSpacingRad = PI / (numTopIcons - 1)

        val sideColumnY = arcCenterY + smallIconPx / 2
        val availableHeight = height - sideColumnY
        val iconWithSpace = smallIconPx + verticalSpacingPx
        val numSideIcons = ceil(availableHeight / iconWithSpace).toInt()

        if (numSideIcons <= 0 || numTopIcons <= 1) {
            // Not enough space to draw a meaningful shape
            return@Box
        }

        val numVisibleIcons = numSideIcons * 2 + numTopIcons + 1

        val smoothScrollIndex = scrollOffset / 20f
        val baseScrollIndex = floor(smoothScrollIndex)
        val scrollFraction = smoothScrollIndex - baseScrollIndex
        val intScrollIndex = baseScrollIndex.toInt()
        val startIndex = (intScrollIndex - numVisibleIcons / 2 + totalIcons) % totalIcons

        fun getPositionForSlot(slot: Int, center: Int): Pair<Float, Float> {
            return when {
                slot < numSideIcons -> {
                    // Left side (bottom to top)
                    val xPos = sidePaddingPx
                    val yPos =
                        sideColumnY + (numSideIcons - 1) * iconWithSpace - (slot - 1) * verticalSpacingPx - slot * smallIconPx
                    Pair(xPos, yPos)
                }

                slot < numSideIcons + numTopIcons -> {
                    // Top arc
                    val arcIndex = slot - numSideIcons
                    val angle = PI - arcIndex * angularSpacingRad
                    var xPos = width / 2 - smallIconPx / 2 + arcRadius * cos(angle).toFloat()
                    var yPos = arcCenterY - arcRadius * sin(angle).toFloat() - smallIconPx / 2
                    if (slot == center) {
                        xPos = xPos + smallIconPx / 2 - largeIconPx / 2
                        yPos = yPos + smallIconPx / 2 - largeIconPx / 2
                    }
                    Pair(xPos, yPos)
                }

                else -> {
                    // Right side (bottom to top)
                    val sideIndex = slot - numSideIcons - numTopIcons
                    val xPos = width - sidePaddingPx - smallIconPx
                    val yPos =
                        sideColumnY + verticalSpacingPx + sideIndex * verticalSpacingPx + sideIndex * smallIconPx
                    Pair(xPos, yPos)
                }
            }
        }

        val centerSlot = numSideIcons + numTopIcons / 2

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
                    iconSize = size
                )
            }
        }
    }
}

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
    Box (modifier = Modifier
        .offset(x = x, y = y),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(iconShape)
                .pointerInput(Unit) {
                    if(clickable)detectTapGestures(
                        onTap = {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            val launchIntent: Intent? =
                                context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = "package:${app.packageName}".toUri()
                            context.startActivity(intent)
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
                    contentDescription = app.label.toString(),
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
    showThemedIcon: Boolean
){
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val backgroundColor: Color =
        if (showThemedIcon && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDarkMode) {
                Color(context.getColor(android.R.color.system_accent1_800))
            } else {
                Color(context.getColor(android.R.color.system_accent1_100))
            }
        } else {
            if (isDarkMode){
                Color.Black.copy(alpha = 0.5f)
            } else {
                Color.White.copy(alpha = 0.5f)
            }
        }
    val foregroundColor: Color =
        if(showThemedIcon && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDarkMode) {
                Color(context.getColor(android.R.color.system_accent1_200))
            } else {
                Color(context.getColor(android.R.color.system_accent1_600))
            }
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val view = LocalView.current
    Box(
        modifier = Modifier
            .clip(getShapeFromShape(iconShape, iconSize))
            .background(color=backgroundColor)
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
            contentDescription = "Search",
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
    dialogTitle: String = "Enjoying Comfer?",
    dialogText: String = "This app will never have ads.",
    icon: ImageVector = Icons.Outlined.Star
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Dialog Icon")
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
                Text("Rate It")
            }
        },
        dismissButton = {
            // The secondary action button, with less emphasis
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Later")
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
                text = "Permission Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To enable the 'Recent Apps' shortcut, this launcher needs you to activate its Accessibility Service.",
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "What this service does:",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "• Triggers the system's 'Recent Apps' screen.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "This service does NOT:",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "• Collect any personal data.\n• Monitor your actions or text you type.\n• Read your screen content.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row {
                Button(onClick = onContinue) {
                    Text("Continue")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}
fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    context.startActivity(intent)
}

@Composable
fun CircularButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    char: Char? = null,
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
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple to use our custom feedback
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (char != null) {
            Text(
                text = char.toString().uppercase(),
                color = Color.White,
                fontSize = (size.value / 2.5).sp, // Slightly smaller font for better padding
                fontWeight = FontWeight.W300 // A lighter font weight can look more modern
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Backspace",
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f) // Adjust icon size
            )
        }
    }
}
@Composable
fun CircularKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val layer1Chars = charArrayOf('g','h','j','k','m','n','z','s','d','f').toList()
    val layer2Chars = charArrayOf('t','y','u','i','o','p','l','b','v','c','x','a','q','w','e','r').toList()

    Box (
        modifier = Modifier
            .wrapContentSize(Alignment.Center)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // This tap is handled and consumed here, so it won't propagate
                        // to the parent Box.
                    }
                )
            }
            .detectSwipes(
                {},
                onSwipeDown,
                onSwipeLeft,
                onSwipeRight
            ),
        contentAlignment = Alignment.Center
    ) {

        val maxWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp }
        val maxHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp }

        val maxDiameter = min(maxWidth, maxHeight)
        val keyboardDiameter = (maxDiameter * 0.7f).dp

        Box(
            modifier = Modifier.size(keyboardDiameter),
            contentAlignment = Alignment.Center
        ) {
            // Define sizes relative to the available space for responsiveness
            val buttonSize = (keyboardDiameter / 7)
            val centerButtonSize = buttonSize * 1.3f

            // Define layer radii
            val radiusLayer2 = centerButtonSize * 0.9f + buttonSize + buttonSize / 2
            val radiusLayer1 = centerButtonSize * 0.9f + buttonSize / 2

            // Layer 2 (Outer Ring - 16 characters)
            val angleStep2 = 2 * Math.PI / layer2Chars.size
            layer2Chars.forEachIndexed { index, char ->
                val angle = angleStep2 * index - (Math.PI / 2) // Start from top
                val x = (radiusLayer2.value * cos(angle)).dp
                val y = (radiusLayer2.value * sin(angle)).dp

                CircularButton(
                    onClick = { onChar(char) },
                    char = char,
                    size = buttonSize,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = x, y = y)
                )
            }

            // Layer 1 (Inner Ring - 10 characters)
            val angleStep1 = 2 * Math.PI / layer1Chars.size
            layer1Chars.forEachIndexed { index, char ->
                val angle = angleStep1 * index - (Math.PI / 2) // Start from top
                val x = (radiusLayer1.value * cos(angle)).dp
                val y = (radiusLayer1.value * sin(angle)).dp

                CircularButton(
                    onClick = { onChar(char) },
                    char = char,
                    size = buttonSize,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = x, y = y)
                )
            }

            // Center Backspace Button
            CircularButton(
                onClick = onBackspace,
                size = centerButtonSize,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
fun searchApps(text: String, appList: List<AppInfo>): List<AppInfo> {
    if (text.isBlank()) {
        return appList // Return the full list if search text is empty
    }
    return appList.filter { app ->
        app.label.contains(text, ignoreCase = true)
    }
}
fun searchContacts(text: String, contactList: List<Contact>): List<Contact> {
    if (text.isBlank()) {
        return contactList // Return the full list if search text is empty
    }
    return contactList.filter { contact ->
        contact.name?.contains(text, ignoreCase = true) ?: false
    }
}
fun placeCallWithDialer(context: Context, number: String?) {
    if (number.isNullOrBlank()) {
        Toast.makeText(context, "Contact number is not available", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = "tel:$number".toUri()
    }

    // Check if there's an app that can handle this intent
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "No app found to handle making phone calls", Toast.LENGTH_SHORT).show()
    }
}

fun Modifier.detectSwipes(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
) : Modifier =
    this.pointerInput(Unit) {
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
    showThemedIcon: Boolean
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
            showThemedIcon)

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
                     showThemedIcon: Boolean
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
            showThemedIcon)
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
fun NotificationIconRow(
    notificationIcons: List<Drawable>,
    modifier: Modifier = Modifier,
    maxVisibleIcons: Int = 5,
    settings: SettingsUiState,
    defaultColor: Color,
    showBorder: Boolean
) {
    val customWallpaper = settings.wallpaperDirectory != null
    val iconSize = settings.notificationSize.dp
    val iconColor = if(customWallpaper) settings.notificationColor else defaultColor
    val borderColor = if(showBorder) iconColor else Color.Transparent
    val rowHeight = iconSize + 16.dp
    val rowWidth = iconSize * 10 + 8.dp
    if (settings.hasNotificationAccess && settings.showNotificationRow && notificationIcons.isNotEmpty()) {
        Box(modifier = Modifier
            .size(width = rowWidth, height = rowHeight)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        )
        {
            Row(
                modifier = modifier
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconsToShow = if (notificationIcons.size > maxVisibleIcons + 1) {
                    notificationIcons.take(maxVisibleIcons)
                } else {
                    notificationIcons
                }

                iconsToShow.forEach { drawable ->
                    Image(
                        painter = rememberDrawablePainter(drawable = drawable),
                        contentDescription = "Notification Icon",
                        // Apply a tint to make the icon visible
                        colorFilter = ColorFilter.tint(iconColor),
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Overflow badge remains the same
                if (notificationIcons.size > maxVisibleIcons + 1) {
                    val overflowCount = notificationIcons.size - maxVisibleIcons
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(CircleShape)
                            .background(iconColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$overflowCount",
                            color = if (iconColor == Color.White) Color.Black else Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun WidgetDate(
    settings: SettingsUiState,
    defaultColor: Color,
    showBorder: Boolean
){
    val dateFormat = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    }
    var date by remember { mutableStateOf("") }
    // This effect now restarts whenever `timeFormat` changes
    LaunchedEffect(dateFormat) {
        while (true) {
            val now = System.currentTimeMillis()
            date = dateFormat.format(Date(now))
            delay(1000)
        }
    }
    val customWallpaper = settings.wallpaperDirectory != null
    val borderColor = if(showBorder) {
        if(customWallpaper) settings.dateFontColor else defaultColor
    } else Color.Transparent
    Box(modifier = Modifier
        .border(width = 2.dp,color = borderColor,shape = RoundedCornerShape(8.dp))
        .padding(4.dp)){
        Text(
            text = date,
            color = if(customWallpaper) settings.dateFontColor else defaultColor,
            fontSize = settings.dateFontSize.sp,
            fontWeight = getFontWeightFromString(settings.dateFontWeight),
            fontFamily = settings.dateFontFamily,
        )
    }
}
@Composable
fun WidgetClock(
    settings: SettingsUiState,
    defaultColor: Color,
    editMode: Boolean
){
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val customWallpaper = settings.wallpaperDirectory != null
    val timeFormat = remember(settings.timeFormat, settings.showAmPm) {
        // Build your pattern based on the settings
        val pattern = if (settings.timeFormat == "H12") {
            if (settings.showAmPm) "h:mm a" else "h:mm"
        } else { // "H24"
            "HH:mm"
        }
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val borderColor = if (editMode) {
        if (customWallpaper) {
            if (settings.showAnalog) settings.clockHourColor else settings.timeFontColor
        } else defaultColor
    } else Color.Transparent
    Box(modifier = Modifier
        .border(width = 2.dp,color = borderColor,shape = RoundedCornerShape(8.dp))
        .pointerInput(editMode) {
            if(!editMode) {
                detectTapGestures(
                    onTap = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        // Open Alarms
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
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
                            context.startActivity(calendarIntent)
                        }
                    }
                )
            }
        }
    ) {
        if (settings.showAnalog) {
            AnalogClock(
                settings.clockSize.dp,
                if (customWallpaper) settings.clockBgColor else Color.Black,
                if (customWallpaper) settings.clockBgAlpha else 0f,
                if (customWallpaper) settings.clockMinuteColor else defaultColor,
                if (customWallpaper) settings.clockHourColor else defaultColor
            )
        } else {
            TextClock(
                timeFormat,
                color = if (customWallpaper) settings.timeFontColor else defaultColor,
                fontSize = settings.timeFontSize.sp,
                fontWeight = getFontWeightFromString(settings.timeFontWeight),
                fontFamily = settings.timeFontFamily
            )
        }
    }
}
@Composable
fun TextClock(
    timeFormat: SimpleDateFormat,
    color: Color,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    fontFamily: FontFamily
) {
    var time by remember { mutableStateOf("") }

    // This effect now restarts whenever `timeFormat` changes
    LaunchedEffect(timeFormat) {
        while (true) {
            val now = System.currentTimeMillis()
            // Update the state variables, triggering recomposition for the Text composables
            time = timeFormat.format(Date(now))
            delay(60000L - (System.currentTimeMillis() % 60000L))
        }
    }
    Box(
        modifier = Modifier
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily
        )
    }
}
@Composable
fun AnalogClock(
    size: Dp,
    backgroundColor: Color,
    backgroundAlpha: Float = 1f,
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
            .background(color = backgroundColor.copy(alpha = backgroundAlpha), shape = CircleShape),
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
fun rememberNotificationIcons(
    notifications: List<StatusBarNotification>,
    hasNotificationAccess: Boolean,
    context: Context
): State<List<Drawable>> {
    val notificationIcons = remember { mutableStateOf<List<Drawable>>(emptyList()) }

    // Use LaunchedEffect to perform the background loading.
    // It will re-run whenever notifications or hasNotificationAccess changes.
    LaunchedEffect(notifications, hasNotificationAccess) {
        if (!hasNotificationAccess) {
            notificationIcons.value = emptyList()
            return@LaunchedEffect
        }

        // Switch to the IO dispatcher for background work.
        val icons = withContext(Dispatchers.IO) {
            notifications.mapNotNull { sbn ->
                try {
                    // Safely load the drawable on a background thread.
                    sbn.notification.smallIcon?.loadDrawable(context)
                } catch (_: Exception) {
                    // Gracefully handle cases where the icon can't be loaded.
                    null
                }
            }
        }

        // Update the state with the result on the main thread.
        notificationIcons.value = icons
    }

    return notificationIcons
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
): Modifier = this.pointerInput(Unit) {
    val path = mutableListOf<Offset>()
    val swipeThreshold = 50.dp.toPx() // Minimum distance for swipe detection

    detectDragGestures(
        onDragStart = { offset ->
            path.clear()
            path.add(offset)
        },
        onDrag = { change, _ ->
            change.consume()
            path.add(change.position)
        },
        onDragEnd = {
            if (path.size >= 2) {
                val circular = detectCircularPattern(path,swipeThreshold)
                val pattern = detectLPatternWithCorner(path)
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
        }
    )
}
private fun detectLPatternWithCorner(points: List<Offset>): LPatternType? {
    if (points.size < 10) return null

    // Find the corner point (where direction changes most)
    var maxDirectionChange = 0f
    var cornerIndex = 0

    for (i in 5 until points.size - 5) {
        val before = points.subList(i - 5, i)
        val after = points.subList(i, i + 5)

        val beforeAngle = atan2(
            before.last().y - before.first().y,
            before.last().x - before.first().x
        )
        val afterAngle = atan2(
            after.last().y - after.first().y,
            after.last().x - after.first().x
        )

        val directionChange = abs(beforeAngle - afterAngle)
        if (directionChange > maxDirectionChange) {
            maxDirectionChange = directionChange
            cornerIndex = i
        }
    }

    // Require significant direction change (close to 90 degrees)
    if (maxDirectionChange < PI / 3) return null

    val firstSegment = points.subList(0, cornerIndex)
    val secondSegment = points.subList(cornerIndex, points.size)

    // Calculate movements
    val firstVertical = firstSegment.last().y - firstSegment.first().y
    val firstHorizontal = firstSegment.last().x - firstSegment.first().x
    val secondVertical = secondSegment.last().y - secondSegment.first().y
    val secondHorizontal = secondSegment.last().x - secondSegment.first().x

    val threshold = 1.5f

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
