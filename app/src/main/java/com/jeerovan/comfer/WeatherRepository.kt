package com.jeerovan.comfer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
private const val WEATHER_TIMEOUT_MS = 15_000L
internal val weatherJson = Json { ignoreUnknownKeys = true }

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperatureC: Double,
    @SerialName("apparent_temperature") val apparentTemperatureC: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
    @SerialName("wind_speed_10m") val windSpeedKmh: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)

@Serializable
internal data class ForecastResponse(
    val current: CurrentWeather,
)

object WeatherRepository {
    private val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(weatherJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = WEATHER_TIMEOUT_MS
            connectTimeoutMillis = WEATHER_TIMEOUT_MS
            socketTimeoutMillis = WEATHER_TIMEOUT_MS
        }
    }

    suspend fun getCurrentWeather(latitude: Double, longitude: Double): CurrentWeather =
        client.get(FORECAST_ENDPOINT) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter(
                "current",
                "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day",
            )
            parameter("timezone", R.string.weather_condition_auto)
            parameter("forecast_days", 1)
        }.body<ForecastResponse>().current
}

data class WeatherPresentation(
    val icon: String,
    @androidx.annotation.StringRes val descriptionRes: Int,
)

fun weatherPresentation(weatherCode: Int, isDay: Boolean): WeatherPresentation = when (weatherCode) {
    0 -> WeatherPresentation(if (isDay) "☀️" else "🌙", R.string.weather_condition_clear_sky)
    1 -> WeatherPresentation(if (isDay) "🌤️" else "🌙", R.string.weather_condition_mainly_clear)
    2 -> WeatherPresentation("⛅", R.string.weather_condition_partly_cloudy)
    3 -> WeatherPresentation("☁️", R.string.weather_condition_overcast)
    45, 48 -> WeatherPresentation("🌫️", R.string.weather_condition_fog)
    51, 53, 55 -> WeatherPresentation("🌦️", R.string.weather_condition_drizzle)
    56, 57 -> WeatherPresentation("🌨️", R.string.weather_condition_freezing_drizzle)
    61, 63, 65 -> WeatherPresentation("🌧️", R.string.weather_condition_rain)
    66, 67 -> WeatherPresentation("🌧️", R.string.weather_condition_freezing_rain)
    71, 73, 75, 77 -> WeatherPresentation("❄️", R.string.weather_condition_snow)
    80, 81, 82 -> WeatherPresentation("🌦️", R.string.weather_condition_rain_showers)
    85, 86 -> WeatherPresentation("🌨️", R.string.weather_condition_snow_showers)
    95 -> WeatherPresentation("⛈️", R.string.weather_condition_thunderstorm)
    96, 99 -> WeatherPresentation("⛈️", R.string.weather_condition_thunderstorm_with_hail)
    else -> WeatherPresentation("🌡️", R.string.weather_condition_current_weather)
}
