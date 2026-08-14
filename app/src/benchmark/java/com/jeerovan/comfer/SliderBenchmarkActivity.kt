package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.theme.ComferTheme

/** Benchmark-only fixture. Keeps production launcher settings and widgets unchanged. */
class SliderBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        var value by remember { mutableIntStateOf(50) }
                        SettingSlider(
                            label = "Settings slider benchmark",
                            value = value,
                            range = 0f..100f,
                            modifier = Modifier.semantics {
                                contentDescription = SLIDER_DESCRIPTION
                            },
                            onValueChange = { value = it },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SLIDER_DESCRIPTION = "Settings slider benchmark"
    }
}
