package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jeerovan.comfer.ui.theme.ComferTheme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

class CrashViewActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                CrashLogScreen()
            }
        }
    }
}

@Composable
fun CrashLogScreen() {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(getCrashLogs(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash Logs") },
                actions = {
                    Button(onClick = {
                        clearCrashLogs(context)
                        logs = "" // Clear the state to update UI
                    }) {
                        Text("Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No crash logs found.")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = logs,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}

private fun getCrashLogs(context: Context): String {
    val prefs = context.getSharedPreferences("CrashLogs", Context.MODE_PRIVATE)
    return prefs.getString("logs", "") ?: ""
}

private fun clearCrashLogs(context: Context) {
    val prefs = context.getSharedPreferences("CrashLogs", Context.MODE_PRIVATE)
    prefs.edit { remove("logs") }
}
