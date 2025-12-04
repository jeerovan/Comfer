package com.jeerovan.comfer

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.theme.ComferTheme
import java.io.File

class CrashViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                CrashLogScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen() {
    val context = LocalContext.current
    // Load logs into state. Note: For very large files, consider loading this inside a LaunchedEffect(Dispatchers.IO)
    var logs by remember { mutableStateOf(getCrashLogs(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash Logs") },
                actions = {
                    Button(onClick = {
                        clearCrashLogs(context)
                        logs = "" // Clear the state to update UI immediately
                    }) {
                        Text("Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                        .verticalScroll(rememberScrollState()), // Enable scrolling
                    fontFamily = FontFamily.Monospace, // Monospace is better for stack traces
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun getCrashLogs(context: Context): String {
    val file = File(context.filesDir, "crash_logs.txt")
    return if (file.exists()) {
        try {
            file.readText()
        } catch (e: Exception) {
            "Error reading log file: ${e.localizedMessage}"
        }
    } else {
        ""
    }
}

private fun clearCrashLogs(context: Context) {
    val file = File(context.filesDir, "crash_logs.txt")
    if (file.exists()) {
        file.delete()
    }
}
