package com.jeerovan.comfer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

private const val LOCATION_UPDATE_INTERVAL_MS = 10 * 60 * 1_000L
private const val LOCATION_UPDATE_DISTANCE_METERS = 1_000f
private const val FUSED_PROVIDER = "fused"

data class WeatherCoordinates(
    val latitude: Double,
    val longitude: Double,
)

class WeatherLocationTracker(context: Context) {
    private val applicationContext = context.applicationContext
    private val locationManager =
        applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<WeatherCoordinates> = callbackFlow {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            close()
            return@callbackFlow
        }

        @Suppress("OVERRIDE_DEPRECATION")
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toWeatherCoordinates())
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val providers = listOf(
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            FUSED_PROVIDER,
        ).filter { it in locationManager.allProviders }

        val lastKnownLocations = providers.mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        lastKnownLocations
            .maxByOrNull(Location::getTime)
            ?.let { trySend(it.toWeatherCoordinates()) }

        providers.forEach { provider ->
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_UPDATE_INTERVAL_MS,
                    LOCATION_UPDATE_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: SecurityException) {
                // Permission can be revoked while providers are being registered.
            } catch (_: IllegalArgumentException) {
                // Some devices report a provider but reject registration for it.
            }
        }

        awaitClose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: SecurityException) {
                // Permission can be revoked while the flow is active.
            }
        }
    }
        // Provider discovery and registration can cross Binder. Keep those calls
        // away from the main thread; the explicit Looper still delivers updates on it.
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
}

private fun Location.toWeatherCoordinates() = WeatherCoordinates(
    latitude = latitude,
    longitude = longitude,
)
