package com.jeerovan.comfer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    var currentWeather by remember { mutableStateOf<CurrentWeather?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, locationTracker) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            locationTracker.locationUpdates().collectLatest(onLocationChanged)
        }
    }

    LaunchedEffect(settings.weatherLatitude, settings.weatherLongitude) {
        currentWeather = null
        val latitude = settings.weatherLatitude ?: return@LaunchedEffect
        val longitude = settings.weatherLongitude ?: return@LaunchedEffect
        while (true) {
            try {
                val fetchedWeather = WeatherRepository.getCurrentWeather(latitude, longitude)
                currentWeather = fetchedWeather
                onTemperatureChanged(fetchedWeather.temperatureC)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last successful temperature until the next refresh.
            }
            delay(WEATHER_REFRESH_INTERVAL_MS)
        }
    }

    LaunchedEffect(editMode) {
        if (editMode) showDetails = false
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
            .clickable(enabled = !editMode) { showDetails = true }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        EffectTextBlock(
            text = settings.weatherTemperatureC?.let {
                "${convertTemperature(it, settings.weatherUseFahrenheit).roundToInt()}°"
            } ?: "—°",
            color = weatherColor,
            fontSize = settings.weatherFontSize.sp,
            fontWeight = FontWeight.Light,
            shadowColor = shadowColor,
        )
    }

    if (showDetails) {
        WeatherDetailsDialog(
            weather = currentWeather,
            cachedTemperatureC = settings.weatherTemperatureC,
            useFahrenheit = settings.weatherUseFahrenheit,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun WeatherDetailsDialog(
    weather: CurrentWeather?,
    cachedTemperatureC: Double?,
    useFahrenheit: Boolean,
    onDismiss: () -> Unit,
) {
    val presentation = weather?.let {
        weatherPresentation(it.weatherCode, it.isDay == 1)
    }
    val temperatureC = weather?.temperatureC ?: cachedTemperatureC

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.weather_details_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (presentation != null) {
                            Text(
                                text = "${presentation.icon} ${presentation.description}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.weather_close_details),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = temperatureC?.let {
                        formatTemperature(it, useFahrenheit)
                    } ?: "—°",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                if (weather == null) {
                    Text(
                        text = stringResource(
                            if (cachedTemperatureC == null) {
                                R.string.weather_unavailable
                            } else {
                                R.string.weather_more_details_loading
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WeatherDetailRow(
                        label = stringResource(R.string.weather_feels_like),
                        value = formatTemperature(weather.apparentTemperatureC, useFahrenheit),
                    )
                    WeatherDetailRow(
                        label = stringResource(R.string.weather_humidity),
                        value = "${weather.relativeHumidity}%",
                    )
                    WeatherDetailRow(
                        label = stringResource(R.string.weather_wind),
                        value = "${weather.windSpeedKmh.roundToInt()} km/h",
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.weather_widget_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeatherDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal fun convertTemperature(temperatureC: Double, useFahrenheit: Boolean): Double =
    if (useFahrenheit) temperatureC * 9.0 / 5.0 + 32.0 else temperatureC

internal fun formatTemperature(temperatureC: Double, useFahrenheit: Boolean): String =
    "${convertTemperature(temperatureC, useFahrenheit).roundToInt()}°${if (useFahrenheit) "F" else "C"}"
