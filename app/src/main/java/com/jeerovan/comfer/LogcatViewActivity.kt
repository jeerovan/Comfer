package com.jeerovan.comfer

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.theme.ComferTheme
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
    // State to hold the raw string logs
    var rawLogs by remember { mutableStateOf(getLogcatLogs(context)) }

    // Convert raw string to AnnotatedString with colors.
    // formattedLogs updates automatically whenever rawLogs changes.
    val formattedLogs = remember(rawLogs) {
        parseLogToAnnotatedString(rawLogs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Error Logs") },
                actions = {
                    Button(onClick = {
                        clearLogcatLogs(context)
                        rawLogs = "" // Update state to clear the view immediately
                    }) {
                        Text("Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        if (rawLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No system error logs found.")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = formattedLogs, // Use the annotated string here
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
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
private fun parseLogToAnnotatedString(logContent: String) = buildAnnotatedString {
    logContent.lines().forEach { line ->
        val lowerLine = line.lowercase()

        val lineColor = when {
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

        withStyle(style = SpanStyle(color = lineColor)) {
            append(line + "\n")
        }
    }
}

private fun getLogcatLogs(context: Context): String {
    val file = File(context.filesDir, "app_error_logs.txt")
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

private fun clearLogcatLogs(context: Context) {
    val file = File(context.filesDir, "app_error_logs.txt")
    if (file.exists()) {
        file.delete()
    }
}
