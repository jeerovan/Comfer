package com.jeerovan.comfer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live API smoke test: run with -e liveWeatherApi true in notificationTest. */
@RunWith(AndroidJUnit4::class)
class WeatherApiIntegrationTest {
    @Test
    fun liveForecastReturnsAllDetails() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveWeatherApi") == "true")
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".notificationtest"))

        // Synthetic ocean coordinates: never read or transmit the device's location.
        val weather = WeatherRepository.getCurrentWeather(0.0, 0.0)
        assertTrue(weather.temperatureC.isFinite())
        assertTrue(weather.apparentTemperatureC.isFinite())
        assertTrue(weather.relativeHumidity in 0..100)
        assertTrue(weather.windSpeedKmh.isFinite() && weather.windSpeedKmh >= 0.0)
        assertTrue(weather.weatherCode in 0..99)
        assertTrue(weather.isDay in 0..1)
    }
}
