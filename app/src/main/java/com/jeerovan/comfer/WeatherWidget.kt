package com.jeerovan.comfer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1_000L

@Composable
fun WeatherWidget(
    settings: SettingsUiState,
    foregroundColor: Color,
    editMode: Boolean,
    backgroundColor: Color = Color.Black,
    onLocationChanged: (WeatherCoordinates) -> Unit,
    onTemperatureChanged: (Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationTracker = remember(context) { WeatherLocationTracker(context) }
    LaunchedEffect(lifecycleOwner, locationTracker) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locationTracker.locationUpdates().collectLatest(onLocationChanged)
        }
    }

    LaunchedEffect(settings.weatherLatitude, settings.weatherLongitude) {
        val latitude = settings.weatherLatitude ?: return@LaunchedEffect
        val longitude = settings.weatherLongitude ?: return@LaunchedEffect
        while (true) {
            try {
                val currentWeather = WeatherRepository.getCurrentWeather(latitude, longitude)
                onTemperatureChanged(currentWeather.temperatureC)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last successful temperature until the next refresh.
            }
            delay(WEATHER_REFRESH_INTERVAL_MS)
        }
    }

    val customColor = !settings.autoWallpapers && !settings.monochrome
    val weatherColor = if (customColor) {
        settings.weatherColor.copy(alpha = settings.weatherAlpha / 100f)
    } else {
        foregroundColor
    }
    val shadowColor = if (customColor) {
        Color.Transparent.toArgb()
    } else {
        backgroundColor.toArgb()
    }
    val borderColor = if (editMode) weatherColor else Color.Transparent

    Box(
        modifier = Modifier
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        EffectTextBlock(
            text = settings.weatherTemperatureC?.let { "${it.roundToInt()}°" } ?: "—°",
            color = weatherColor,
            fontSize = settings.weatherFontSize.sp,
            fontWeight = FontWeight.Light,
            shadowColor = shadowColor,
        )
    }
}
