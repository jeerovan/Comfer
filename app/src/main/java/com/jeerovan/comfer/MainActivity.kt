package com.jeerovan.comfer

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherScreen()
        }
    }
}

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val apps = packageManager.queryIntentActivities(intent, 0)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data("https://images.unsplash.com/photo-1611656993299-9f9d73232b9c?w=2000&q=99")
                    .crossfade(true)
                    .build()
            ),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                items(apps) { app ->
                    AppIcon(app)
                }
            }
        }

    }
}

@Composable
fun AppIcon(app: ResolveInfo) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)

    Image(
        painter = rememberAsyncImagePainter(app.loadIcon(packageManager)),
        contentDescription = app.loadLabel(packageManager).toString(),
        modifier = Modifier
            .size(64.dp)
            .padding(8.dp)
            .clickable {
                context.startActivity(launchIntent)
            }
    )
}

@Preview
@Composable
fun PreviewLauncher(){
    LauncherScreen()
}