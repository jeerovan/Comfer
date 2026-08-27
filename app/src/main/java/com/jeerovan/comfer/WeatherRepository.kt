package com.jeerovan.comfer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
internal val weatherJson = Json { ignoreUnknownKeys = true }

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperatureC: Double,
    @SerialName("apparent_temperature") val apparentTemperatureC: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
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
    }

    suspend fun getCurrentWeather(latitude: Double, longitude: Double): CurrentWeather =
        client.get(FORECAST_ENDPOINT) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter(
                "current",
                "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day",
            )
            parameter("timezone", "auto")
            parameter("forecast_days", 1)
        }.body<ForecastResponse>().current
}

data class WeatherPresentation(
    val icon: String,
    val description: String,
)

fun weatherPresentation(weatherCode: Int, isDay: Boolean): WeatherPresentation = when (weatherCode) {
    0 -> WeatherPresentation(if (isDay) "☀️" else "🌙", "Clear sky")
    1 -> WeatherPresentation(if (isDay) "🌤️" else "🌙", "Mainly clear")
    2 -> WeatherPresentation("⛅", "Partly cloudy")
    3 -> WeatherPresentation("☁️", "Overcast")
    45, 48 -> WeatherPresentation("🌫️", "Fog")
    51, 53, 55 -> WeatherPresentation("🌦️", "Drizzle")
    56, 57 -> WeatherPresentation("🌨️", "Freezing drizzle")
    61, 63, 65 -> WeatherPresentation("🌧️", "Rain")
    66, 67 -> WeatherPresentation("🌧️", "Freezing rain")
    71, 73, 75, 77 -> WeatherPresentation("❄️", "Snow")
    80, 81, 82 -> WeatherPresentation("🌦️", "Rain showers")
    85, 86 -> WeatherPresentation("🌨️", "Snow showers")
    95 -> WeatherPresentation("⛈️", "Thunderstorm")
    96, 99 -> WeatherPresentation("⛈️", "Thunderstorm with hail")
    else -> WeatherPresentation("🌡️", "Current weather")
}
