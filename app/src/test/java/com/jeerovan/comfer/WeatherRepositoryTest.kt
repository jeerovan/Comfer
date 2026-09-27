package com.jeerovan.comfer

import io.ktor.client.request.HttpRequestBuilder
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WeatherRepositoryTest {
    @Test
    fun requestUsesApiTimezoneLiteralInEnglishAndArabic() {
        val originalLocale = Locale.getDefault()
        try {
            for (language in listOf("en", "ar")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                val request = HttpRequestBuilder().apply {
                    configureCurrentWeatherRequest(0.0, 0.0)
                }
                assertEquals("API timezone must not be a translated resource ($language)",
                    listOf("auto"), request.url.build().parameters.getAll("timezone"))
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun requestPreservesCoordinatesAndRequestsEveryWeatherDetail() {
        for ((latitude, longitude) in listOf(0.0 to 0.0, -90.0 to -180.0, 90.0 to 180.0)) {
            val parameters = HttpRequestBuilder().apply {
                configureCurrentWeatherRequest(latitude, longitude)
            }.url.build().parameters
            assertEquals(latitude.toString(), parameters["latitude"])
            assertEquals(longitude.toString(), parameters["longitude"])
            assertEquals("1", parameters["forecast_days"])
            assertEquals(
                setOf("temperature_2m", "apparent_temperature", "relative_humidity_2m",
                    "wind_speed_10m", "weather_code", "is_day"),
                parameters["current"]!!.split(',').toSet(),
            )
        }
    }

    @Test
    fun currentWeatherResponseDecodesOpenMeteoFieldNames() {
        val response = weatherJson.decodeFromString<ForecastResponse>(
            """
            {
              "current": {
                "temperature_2m": 31.4,
                "apparent_temperature": 34.1,
                "relative_humidity_2m": 62,
                "wind_speed_10m": 14.7,
                "weather_code": 2,
                "is_day": 1
              }
            }
            """.trimIndent(),
        )

        assertEquals(31.4, response.current.temperatureC, 0.0)
        assertEquals(2, response.current.weatherCode)
        assertEquals(62, response.current.relativeHumidity)
        assertEquals(14.7, response.current.windSpeedKmh, 0.0)
    }

    @Test
    fun weatherCodesMapToUsefulPresentations() {
        assertEquals(R.string.weather_condition_clear_sky, weatherPresentation(0, isDay = true).descriptionRes)
        assertEquals("🌙", weatherPresentation(0, isDay = false).icon)
        assertEquals(R.string.weather_condition_rain, weatherPresentation(63, isDay = true).descriptionRes)
        assertEquals(R.string.weather_condition_thunderstorm_with_hail, weatherPresentation(99, isDay = true).descriptionRes)
        assertEquals(R.string.weather_condition_current_weather, weatherPresentation(500, isDay = true).descriptionRes)
    }

    @Test
    fun temperaturesConvertBetweenCelsiusAndFahrenheit() {
        assertEquals(32.0, convertTemperature(0.0, useFahrenheit = true), 0.0)
        assertEquals(212.0, convertTemperature(100.0, useFahrenheit = true), 0.0)
        assertEquals(25.0, convertTemperature(25.0, useFahrenheit = false), 0.0)
        assertEquals("77°F", formatTemperature(25.0, useFahrenheit = true))
        assertEquals("25°C", formatTemperature(25.0, useFahrenheit = false))
    }
}
