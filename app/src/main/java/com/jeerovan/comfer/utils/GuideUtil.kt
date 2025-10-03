package com.jeerovan.comfer.utils

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jeerovan.comfer.R
import kotlinx.coroutines.delay

object GuideUtil {

    @Composable
    fun GuideDialog(
        title: String,
        steps: List<String>,
        onDismiss: () -> Unit
    ) {
        var isClosable by remember { mutableStateOf(false) }
        var remainingTime by remember { mutableIntStateOf(5) }

        LaunchedEffect(Unit) {
            while (remainingTime > 0) {
                delay(1000)
                remainingTime--
            }
            isClosable = true
        }

        Dialog(onDismissRequest = { if (isClosable) onDismiss() }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        steps.forEach { step ->
                            Text(
                                text = "• $step",
                                modifier = Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        enabled = isClosable,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (!isClosable) {
                            Icon(
                                painter = painterResource(R.drawable.outline_timer_24),
                                contentDescription = "Timer",
                                modifier = Modifier
                                    .size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "OK ($remainingTime)")
                        } else {
                            Text(text = "OK")
                        }
                    }
                }
            }
        }
    }
}
