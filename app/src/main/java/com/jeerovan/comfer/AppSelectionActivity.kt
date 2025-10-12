package com.jeerovan.comfer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import kotlin.math.min

class AppSelectionActivity : ComponentActivity() {

    private val appInfoViewModel: AppInfoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val swipeDirection = intent.getStringExtra("swipe_direction")
        val gesturePattern = intent.getStringExtra("gesture_pattern")
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    AppSelectionScreen(appInfoViewModel = appInfoViewModel,
                        swipeDirection = swipeDirection,
                        gesturePattern = gesturePattern)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(appInfoViewModel: AppInfoViewModel, swipeDirection: String?, gesturePattern: String?) {
    val appListState by appInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val allApps = (appListState.quickApps + appListState.primaryApps + appListState.restApps).sortedBy { it.label.toString() }
    val iconSize = PreferenceManager.getIconSize(context)
    val iconShape = PreferenceManager.getIconShape(context)
    Scaffold(topBar = {
                        TopAppBar(
                            title = {
                                Text("Select an app")
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                      },
        containerColor = Color.Transparent
        ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 70.dp),
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = paddingValues
        ) {
            items(allApps) { app ->
                SelectAppIcon(app,iconSize,iconShape) {
                    (context as? Activity)?.let { activity ->
                        val resultIntent = Intent()
                        resultIntent.putExtra("swipe_direction", swipeDirection)
                        resultIntent.putExtra("gesture_pattern",gesturePattern)
                        resultIntent.putExtra("package_name", app.packageName)
                        activity.setResult(Activity.RESULT_OK, resultIntent)
                        activity.finish()
                    }
                }
            }
        }
    }
}

@Composable
fun SelectAppIcon(app: AppInfo, iconSize: Int, iconShape: Shape, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(iconSize.dp)
                .clip(getShapeFromShape(iconShape, iconSize.dp)),
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
                    modifier = Modifier.fillMaxSize()
                        .scale(app.scale), // Let it fill the clipped Box
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}
