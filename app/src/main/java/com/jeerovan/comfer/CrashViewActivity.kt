package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jeerovan.comfer.ui.theme.ComferTheme

import android.content.Context
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
import androidx.core.content.edit

class CrashViewActivity : AppCompatActivity(){
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
                    color = MaterialTheme.colorScheme.onSurface
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
