package com.jeerovan.comfer

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LogcatViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                LogcatLogScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatLogScreen() {
    val context = LocalContext.current
    // State to hold the raw string logs. Load asynchronously to avoid blocking the main thread.
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        logLines = withContext(Dispatchers.IO) { getLogcatLogLines(context) }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Error Logs") },
                actions = {
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { clearLogcatLogs(context) }
                            logLines = emptyList()
                        }
                    }) {
                        Text("Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (logLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No system error logs found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                itemsIndexed(logLines, key = { index, _ -> index }) { _, line ->
                    Text(
                        text = line,
                        color = logLineColor(line),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Parses log content line by line and applies colors based on keywords.
 * Error -> Red
 * Debug -> Blue
 * Info -> Black
 */
private fun logLineColor(line: String): Color {
    val lowerLine = line.lowercase()
    return when {
            // Check for "Error" or standard Logcat " E/" tag
            lowerLine.contains("error") || line.contains(" E/") -> Color.Red

            // Check for "Debug" or standard Logcat " D/" tag
            lowerLine.contains("debug") || line.contains(" D/") -> Color.Blue

            // Check for "Info" or standard Logcat " I/" tag (Defaulting others to black as requested)
            lowerLine.contains("info") || line.contains(" I/") -> Color.Black

            // Default color for lines that don't match specific tags (e.g., stack trace continuation)
            // You might want to use Color.Red here if you want stack traces to follow the error color
        else -> Color.Black
    }
}

private fun getLogcatLogLines(context: Context): List<String> {
    val file = File(context.filesDir, "app_error_logs.txt")
    return runCatching { BoundedLogFile.readTailLines(file) }
        .getOrElse { listOf("Error reading log file: ${it.localizedMessage}") }
}

private fun clearLogcatLogs(context: Context) {
    val file = File(context.filesDir, "app_error_logs.txt")
    if (file.exists()) {
        file.delete()
    }
}
