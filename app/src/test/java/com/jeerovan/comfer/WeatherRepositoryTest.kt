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
                "weather_code": 2,
                "is_day": 1
              }
            }
            """.trimIndent(),
        )

        assertEquals(31.4, response.current.temperatureC, 0.0)
        assertEquals(2, response.current.weatherCode)
        assertEquals(62, response.current.relativeHumidity)
    }

    @Test
    fun weatherCodesMapToUsefulPresentations() {
        assertEquals("Clear sky", weatherPresentation(0, isDay = true).description)
        assertEquals("🌙", weatherPresentation(0, isDay = false).icon)
        assertEquals("Rain", weatherPresentation(63, isDay = true).description)
        assertEquals("Thunderstorm with hail", weatherPresentation(99, isDay = true).description)
        assertEquals("Current weather", weatherPresentation(500, isDay = true).description)
    }
}
