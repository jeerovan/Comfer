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
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.Arrangement
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
import com.jeerovan.comfer.utils.CommonUtil.alignmentFromString
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import com.jeerovan.comfer.utils.CommonUtil.stringToColor
import com.jeerovan.comfer.utils.GuideUtil.GuideDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import kotlin.math.min
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

// Placeholder for your contact data structure
data class Contact(
    val id: Long,
    val name: String,
    val photoUri: Uri?,
    val number: String?
)

// Enum to manage the active tab state
enum class SearchTab {
    APPS, CONTACTS
}

data class BatteryState(val level: Int, val isCharging: Boolean)

class MainActivity : ComponentActivity() {
    private val appInfoViewModel: AppInfoViewModel by viewModels()
    private val settingInfoViewModel:SettingsViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing because this is a launcher screen
            }
        })

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            ComferTheme { LauncherScreen(appInfoViewModel,settingInfoViewModel,mainViewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        val logger = LoggerManager(applicationContext)
        logger.setLog("MainActivity","Resumed")
        lifecycleScope.launch {
            appInfoViewModel.loadAppLists()
            settingInfoViewModel.loadSettings()
        }
    }

    override fun onPause(){
        super.onPause()
        val logger = LoggerManager(applicationContext)
        logger.setLog("MainActivity","Paused")
        lifecycleScope.launch {
            //delay(1000) // Delay, does not stop main thread
            mainViewModel.loadImageData()
        }
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
fun BatteryStatus(themeColor: Color) {
    val batteryState by rememberBatteryState()
    val batteryLevel = batteryState.level
    val isCharging = batteryState.isCharging
    val isLow = batteryLevel < 10
    val batteryLevelColor = if (isLow) Color.Red else themeColor

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp, 12.dp)
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
                    val path = androidx.compose.ui.graphics.Path().apply {
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
        if (batteryLevel > 0) {
            Text(
                text = "$batteryLevel%",
                color = themeColor,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
fun QuickListOverlay(apps: List<AppInfo>,
                     imageData: ImageData?,
                     onSwipeUp: () -> Unit,
                     onShowSearch:() -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape)}
    var isDefault by remember { mutableStateOf(false) }
    var guideShown by remember { mutableStateOf(true) }
    var feedbackShown by remember { mutableStateOf(true)}
    val guideKeyword = "quick_guide_1"
    var canShowGuide by remember { mutableStateOf(false) }

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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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
        title = "Navigation",
        steps = listOf(
            "Swipe up to see apps.",
            "Swipe down to see notifications.",
            "Double tap screen to open recents.",
            "Long press the screen to open settings.",
            "Tap on Date-Time to show alarms.",
            "Long press on Date-Time to open Calendar"
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
        } catch (e: Exception) {
            // If Play Store is not installed, open in a web browser
            val webIntent = Intent(Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri())
            context.startActivity(webIntent)
        }
    }
    if (guideShown && canShowGuide && !feedbackShown && isDefault)FeedbackDialog(
        {onFeedbackDismiss()},
        {onFeedbackRateIt()}
    )

    val dateTimeAlignment = imageData?.position?.let { position ->
        alignmentFromString(position)
    } ?: Alignment.TopCenter
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(dateTimeAlignment)
                //.border(1.dp,color = Color.Red)
                .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 40.dp)
                .pointerInput(Unit) {
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
                            val calendarIntent = Intent(
                                Intent.ACTION_VIEW,
                                CalendarContract.CONTENT_URI.buildUpon()
                                    .appendPath("time")
                                    .build()
                            )
                            if (calendarIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(calendarIntent)
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var time by remember { mutableStateOf("") }
            var date by remember { mutableStateOf("") }

            val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
            val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

            LaunchedEffect(Unit) {
                while (true) {
                    val now = System.currentTimeMillis()
                    time = timeFormat.format(Date(now))
                    date = dateFormat.format(Date(now))
                    delay(1000)
                }
            }
            val textColor = imageData?.color?.let { colorName ->
                stringToColor(colorName)
            } ?: Color.White
            Text(
                text = time,
                color = textColor,
                fontSize = 60.sp,
                fontWeight = FontWeight.Light
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date,
                    color = textColor,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                BatteryStatus(textColor)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                //.border(1.dp,color=Color.Green)
                .fillMaxWidth()
                .height(LocalConfiguration.current.screenHeightDp.dp / 2 - 40.dp)
                .pointerInput(Unit) {
                    val packageManager = context.packageManager
                    var totalDragOffset = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            totalDragOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragOffset += dragAmount
                        },
                        onDragEnd = {
                            val swipeThreshold = 50f
                            val (x, y) = totalDragOffset
                            if (x.absoluteValue > y.absoluteValue) {
                                if (x.absoluteValue > swipeThreshold) {
                                    if (x > 0) {
                                        val swipeRightPackage =
                                            PreferenceManager.getSwipeApp(context, "right")
                                        if (swipeRightPackage != null) {
                                            val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(swipeRightPackage)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                    } else {
                                        val swipeLeftPackage =
                                            PreferenceManager.getSwipeApp(context, "left")
                                        if (swipeLeftPackage != null) {
                                            val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(swipeLeftPackage)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (y.absoluteValue > swipeThreshold) {
                                    if (y > 0) {
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
                                    } else {
                                        onSwipeUp()
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 64.dp)
            ) {

                if (!isDefault) {
                    OutlinedButton (onClick = { openDefaultLauncherSettings()},
                        border = null,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f) // Text color
                        )
                    ) {
                        Text("Set as default launcher",
                            fontSize = 18.sp,
                            color = Color.White)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val searchPosition = when (apps.size) {
                        0 -> 0 // Insert at the start if empty
                        1 -> 1 // Insert at the end if one item
                        2 -> 1 // Insert in the middle if two items
                        else -> 2 // Otherwise, insert at index 2
                    }
                    val searchIcon = ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.outline_search_24,
                        null // or context.theme if needed for themed drawables
                    )
                    val searchApp = AppInfo(
                        background = Color.White.toArgb().toDrawable(),
                        foreground = searchIcon,
                        scale = 0.8f,
                        label = "Search",
                        packageName = "search")
                    val appsList = apps.toMutableList()
                    appsList.add(searchPosition,searchApp)
                    appsList.forEach { app ->
                        val packageManager = context.packageManager
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .clip(getShapeFromShape(iconShape,iconSize))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            view.playSoundEffect(SoundEffectConstants.CLICK)
                                            if (app.packageName == "search"){
                                                onShowSearch()
                                            } else {
                                                val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                }
                                            }
                                        },
                                        onLongPress = {
                                            if(app.packageName != "search") {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                val intent =
                                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                intent.data = "package:${app.packageName}".toUri()
                                                context.startActivity(intent)
                                            }
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
                                    modifier = Modifier.fillMaxSize().scale(app.scale), // Let it fill the clipped Box
                                    contentScale = ContentScale.FillBounds
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
                      contacts: List<Contact>,
                      onRequestContactsPermission: () -> Unit,
                      onSwipeDown: () -> Unit,
                      hasContactPermission: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape) }
    var inputText by remember { mutableStateOf("") }
    var guideShown by remember { mutableStateOf(true) }

    var activeTab: SearchTab by remember { mutableStateOf(SearchTab.APPS) }
    val guideKeyword = "search_guide_1"
    var canShowGuide by remember { mutableStateOf(false) }

    val filteredApps by remember(inputText, apps) {
        derivedStateOf {
            if(activeTab == SearchTab.APPS) {
                searchApps(inputText, apps)
            } else {
                apps
            }
        }
    }
    //val filteredContacts = remember { (1..20).map { Contact(it.toLong(),"Contact $it",null,it.toString()) } }
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
        guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
        delay(500)
        canShowGuide = true
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                iconShape = PreferenceManager.getIconShape(context)
                guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
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
    }

    if(!guideShown && canShowGuide)GuideDialog(
        onDismiss = {onGuideDismiss()},
        title = "Navigation",
        steps = listOf(
            "Swipe down on the keyboard to go back.",
            "Swipe right on the keyboard to search contacts.",
            "Swipe left on the keyboard to search apps.",
            "Scroll sides to scroll contact list.",
            "Tap icon to open app.",
            "Long press icon to view details.",
            "Scroll app list to view all."
        )
    )


    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged() // Only emit when the index actually changes
            .collect { visibleIndex ->
                selectedContactIndex = visibleIndex
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
            .pointerInput(lazyListState, filteredContacts.size) { // Relaunch gesture detection if state or data changes
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val isAtEndOfList = !lazyListState.canScrollForward
                        dragAccumulator += dragAmount
                        // A. Dragging DOWN (positive dragAmount)
                        if (dragAmount > 0) {
                            // If selection is ahead of the list, bring it back without scrolling
                            if (selectedContactIndex > lazyListState.firstVisibleItemIndex) {
                                if (dragAccumulator > scrollThreshold) {
                                    selectedContactIndex = (selectedContactIndex - 1).coerceAtLeast(lazyListState.firstVisibleItemIndex)
                                    dragAccumulator = 0f
                                }
                            } else {
                                // Otherwise, scroll the list normally if possible
                                if (lazyListState.canScrollBackward) {
                                    coroutineScope.launch { lazyListState.scrollBy(-3*dragAmount) }
                                }
                            }
                        }
                        // B. Dragging UP (negative dragAmount)
                        else if (dragAmount < 0) {
                            // If at the end of the list, change selection instead of scrolling
                            if (isAtEndOfList) {
                                if (dragAccumulator < -scrollThreshold) {
                                    selectedContactIndex = (selectedContactIndex + 1).coerceAtMost(filteredContacts.lastIndex)
                                    dragAccumulator = 0f
                                }
                            } else {
                                // Otherwise, scroll the list normally
                                coroutineScope.launch { lazyListState.scrollBy(-3*dragAmount) }
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
                modifier = Modifier.padding(bottom = 40.dp, top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)), // Softens the container edges
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
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                .fillMaxSize()
                            ) {
                                items(filteredContacts) { contact ->
                                    ContactListItem(contact,
                                        isSelected = (contact.id == selectedContact?.id))
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            // Add spacing between the items
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredApps) { app ->
                                Box(
                                    modifier = Modifier
                                        .size(iconSize)
                                        .clip(getShapeFromShape(iconShape, iconSize))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                                    val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                    if (launchIntent != null) {
                                                        context.startActivity(launchIntent)
                                                    }
                                                },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    val intent =
                                                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
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
                                            modifier = Modifier.fillMaxSize().scale(app.scale), // Let it fill the clipped Box
                                            contentScale = ContentScale.FillBounds
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


            }
        }
}
@Composable
fun ContactListItem(contact: Contact,isSelected:Boolean) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)), // Rounded corners for each item
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        headlineContent = {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                            text = contact.name.firstOrNull()?.toString() ?: "",
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
    Box(modifier = Modifier
        .fillMaxWidth()
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
                textAlign = TextAlign.Center
                // Add a clickable modifier for the privacy policy link
            )
        }
    }
}

@Composable
fun AppListOverlay(apps: List<AppInfo>, onSwipeDown: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    var iconSize by remember { mutableStateOf(48.dp) }
    var iconShape: Shape by remember { mutableStateOf(CircleShape) }
    var guideShown by remember { mutableStateOf(true) }
    val guideKeyword = "primary_guide_1"
    var canShowGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
        delay(500)
        canShowGuide = true
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                iconShape = PreferenceManager.getIconShape(context)
                guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
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
    }

    if(!guideShown && canShowGuide)GuideDialog(
        onDismiss = {onGuideDismiss()},
        title = "Navigation",
        steps = listOf(
            "Double tap to open app in the middle.",
            "Swipe down to go back.",
            "Swipe left/right to scroll.",
            "Tap on icon to open app.",
            "Long press on icon to open app info"
        )
    )

    val scrollAnimatable = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }
    var lastYPosition by remember { mutableFloatStateOf(0f) }
    var centerAppIndex by remember { mutableIntStateOf(0) }
    var lastCenterAppIndex by remember { mutableIntStateOf(0) }
    var centerIconX by remember { mutableFloatStateOf(0f) }
    var centerIconY by remember { mutableFloatStateOf(0f) }
    var centerIconSize by remember { mutableFloatStateOf(0f) }
    var dragAxis by remember { mutableStateOf<DragAxis?>(null) }
    var verticalDragAmount by remember { mutableFloatStateOf(0f) }

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
                            if(centerAppIndex < apps.size) {
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
                detectDragGestures(
                    onDragStart = {
                        dragAxis = null
                        verticalDragAmount = 0f
                        velocityTracker.resetTracking()
                        scope.launch {
                            scrollAnimatable.stop()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
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
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                                lastYPosition = change.position.y
                                if (change.position.y > size.height / 2) {
                                    val increment = dragAmount.x * 0.3f

                                    scope.launch {
                                        val currentValue = scrollAnimatable.value
                                        var newValue =
                                            currentValue + increment
                                        if (apps.isNotEmpty()) {
                                            val totalScrollWidth = apps.size * 20f
                                            if (totalScrollWidth > 0) {
                                                newValue =
                                                    newValue.rem(totalScrollWidth)
                                                if (newValue < 0) {
                                                    newValue += totalScrollWidth
                                                }
                                            }
                                        }
                                        scrollAnimatable.snapTo(newValue)
                                    }
                                }
                            }

                            DragAxis.VERTICAL -> {
                                verticalDragAmount += dragAmount.y
                                if (verticalDragAmount > 80f) {
                                    onSwipeDown()
                                }
                            }

                            null -> { /* Wait for axis detection */
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragAxis == DragAxis.HORIZONTAL) {
                            val velocity = velocityTracker.calculateVelocity()
                            scope.launch {
                                val initialVelocity = velocity.x * 0.3f
                                val result = scrollAnimatable.animateDecay(
                                    initialVelocity,
                                    exponentialDecay()
                                )

                                if (result.endReason == AnimationEndReason.Finished && apps.isNotEmpty()) {
                                    val totalScrollWidth = apps.size * 20f
                                    if (totalScrollWidth > 0) {
                                        var wrappedValue =
                                            scrollAnimatable.value.rem(totalScrollWidth)
                                        if (wrappedValue < 0) {
                                            wrappedValue += totalScrollWidth
                                        }
                                        scrollAnimatable.snapTo(wrappedValue)
                                    }
                                }
                            }
                        }
                    }/*,
                    onDragCancel = {
                        velocityTracker.resetTracking()
                    }*/
                )
            }
    ) {
        if (apps.isNotEmpty()) {
            UshapedAppList(
                apps = apps,
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
                   mainViewModel: MainViewModel) {
    val context = LocalContext.current
    var isAppListVisible by remember { mutableStateOf(false) }
    var isSearchListVisible by remember { mutableStateOf(false) }
    var backgroundImage by remember { mutableStateOf<String?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }

    val appInfoUiState by appInfoViewModel.uiState.collectAsState()
    val settingInfoUiState by settingsViewModel.uiState.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()

    val quickApps = appInfoUiState.quickApps
    val primaryApps = appInfoUiState.primaryApps

    val wallpaperMotionEnabled = settingInfoUiState.wallpaperMotionEnabled

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
    if (hasContactsPermission) {
        // This LaunchedEffect will run the contact fetching logic
        LaunchedEffect(Unit) {
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
                        contactsList.add(Contact(id, name, photoUri, number))
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

    val haptic = LocalHapticFeedback.current

    if (cachedImagePath != null && File(cachedImagePath).exists()) {
        backgroundImage = cachedImagePath
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                    onDoubleTap = {
                        if (isAccessibilityServiceEnabled(context, RecentsAccessibilityService::class.java)) {
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

        // Quick-list layer
        AnimatedVisibility(
            visible = !isAppListVisible && !isSearchListVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            QuickListOverlay(apps = quickApps,
                imageData = imageData,
                onSwipeUp = { isAppListVisible = true },
                onShowSearch = { isSearchListVisible = true})
        }

        // app list - second layer
        AnimatedVisibility(
            visible = isAppListVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            AppListOverlay(apps = primaryApps,
                onSwipeDown = { isAppListVisible = false })
        }

        // search list
        AnimatedVisibility(
            visible = isSearchListVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SearchListOverlay (apps = primaryApps,
                contacts,
                onSwipeDown = { isSearchListVisible = false },
                onRequestContactsPermission = { onRequestContactsPermission() },
                hasContactPermission = hasContactsPermission
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
                    shape = iconShape,
                    x = x.toDp(),
                    y = y.toDp(),
                    size = size
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo,shape: Shape, x: Dp, y: Dp, size: Dp) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val iconShape = getShapeFromShape(shape,size)
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(iconShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)
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
                modifier = Modifier.fillMaxSize().scale(app.scale),
                contentScale = ContentScale.FillBounds
            )
        }
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
    } catch (e: Settings.SettingNotFoundException) {
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
    // 1. InteractionSource to track press state
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 2. Animate scale based on press state for visual feedback
    val scale by animateFloatAsState(targetValue = if (isPressed) 1.2f else 1f)

    // 3. Get the current view to trigger the sound effect
    val view = LocalView.current

    Button(
        onClick = {
            // Play default tap sound
            view.playSoundEffect(SoundEffectConstants.CLICK)
            // Execute the original onClick action
            onClick()
        },
        modifier = modifier
            .size(size)
            .graphicsLayer { // Apply the scaling animation
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.5f)
        ),
        // Pass the interactionSource to the button
        interactionSource = interactionSource,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = (size.value / 2.0).sp
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Backspace",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(size * 0.5f)
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
                var totalDragOffset = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        totalDragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragOffset += dragAmount
                    },
                    onDragEnd = {
                        val swipeThreshold = 50f
                        val (x, y) = totalDragOffset
                        if (y.absoluteValue > x.absoluteValue) {
                            if (y.absoluteValue > swipeThreshold) {
                                if (y > 0) {
                                    onSwipeDown()
                                }
                            }
                        } else {
                            // Horizontal swipe detection
                            if (x.absoluteValue > swipeThreshold) {
                                if (x > 0) {
                                    // Positive x means a swipe from left to right
                                    onSwipeRight()
                                } else {
                                    // Negative x means a swipe from right to left
                                    onSwipeLeft()
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {

        val maxWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp }
        val maxHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp }
        // FIX: Explicitly use the 'this' scope to resolve the warning.
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
        contact.name.contains(text, ignoreCase = true)
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


