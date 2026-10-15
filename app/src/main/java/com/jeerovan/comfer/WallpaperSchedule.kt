package com.jeerovan.comfer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

internal data class WallpaperSchedule(
    val periodStart: Long,
    val nextChange: Long,
    val isDay: Boolean,
    val periodKey: String,
) {
    // The existing cloud API selects light images for hours 7..18. Send a
    // representative hour so its fixed clock thresholds follow our solar phase.
    val cloudRequestHour: Int get() = if (isDay) 12 else 0
}

internal data class SolarTimes(val sunrise: Long?, val sunset: Long?, val polarDay: Boolean? = null)

/** NOAA's apparent sunrise/sunset equations (90.833 degree zenith), offline.
 * https://gml.noaa.gov/grad/solcalc/solareqns.PDF
 */
internal fun solarTimes(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): SolarTimes {
    val gamma = 2 * PI / date.lengthOfYear() * (date.dayOfYear - 1)
    val equation = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
        0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
        0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
        0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    val lat = Math.toRadians(latitude)
    val cosine = cos(Math.toRadians(90.833)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
    if (cosine > 1) return SolarTimes(null, null, false)
    if (cosine < -1) return SolarTimes(null, null, true)
    val angle = Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
    fun instant(minutes: Double): Long {
        var result = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + (minutes * 60_000).roundToLong()
        // Civil time zones can cross the date line relative to longitude.
        val civilDate = Instant.ofEpochMilli(result).atZone(zone).toLocalDate()
        result += java.time.temporal.ChronoUnit.DAYS.between(civilDate, date) * 86_400_000L
        return result
    }
    return SolarTimes(instant(720 - 4 * (longitude + angle) - equation),
        instant(720 - 4 * (longitude - angle) - equation))
}

internal fun wallpaperSchedule(
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    latitude: Double? = null,
    longitude: Double? = null,
    ownWallpapers: Boolean = false,
): WallpaperSchedule {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val coordinatesValid = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    data class Boundary(val at: Long, val day: Boolean, val date: LocalDate)
    val boundaries = (-2L..2L).flatMap { offset ->
        val date = today.plusDays(offset)
        val solar = if (coordinatesValid) solarTimes(date, zone, latitude!!, longitude!!) else null
        fun clock(hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        if (solar?.polarDay != null) {
            // No sunrise/sunset during polar day/night: rotate once per civil
            // day, keeping the cloud image appropriate to the actual solar state.
            listOf(Boundary(clock(6), solar.polarDay, date))
        } else {
            // No saved location yet: use local 06:00/18:00 until one arrives.
            listOfNotNull(Boundary(solar?.sunrise ?: clock(6), true, date),
                if (ownWallpapers) null else Boundary(solar?.sunset ?: clock(18), false, date))
        }
    }.sortedBy { it.at }
    val current = boundaries.last { it.at <= now }
    val next = boundaries.first { it.at > now }
    return WallpaperSchedule(current.at, next.at, current.day,
        "${current.date}:${if (ownWallpapers) "sunrise" else if (current.day) "day" else "night"}")
}
