package com.jeerovan.comfer

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRepositoryTest {
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
