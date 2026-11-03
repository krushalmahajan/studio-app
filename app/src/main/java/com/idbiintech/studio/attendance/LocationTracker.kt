package com.idbiintech.studio.attendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Listens to the phone's location providers (fused, GPS, network) while the punch screen is open
 * and keeps the best recent fix.
 */
class LocationTracker(context: Context, private val onUpdate: (Location) -> Unit) {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    var best: Location? = null
        private set

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            consider(location)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasCoarseOnly(): Boolean =
        !hasPermission() &&
                appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationOn(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return lm.isLocationEnabled
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission()) return
        val providers = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            try {
                if (!lm.allProviders.contains(p) || !lm.isProviderEnabled(p)) continue
                lm.requestLocationUpdates(p, 2_000L, 0f, listener, Looper.getMainLooper())
                running = true
                lm.getLastKnownLocation(p)?.let { consider(it) }
            } catch (e: SecurityException) {
                // permission removed while running
            } catch (e: IllegalArgumentException) {
                // provider not present on this phone
            }
        }
    }

    fun stop() {
        if (!running) return
        try {
            lm.removeUpdates(listener)
        } catch (e: Exception) {
        }
        running = false
    }

    /** Keep a new fix when it is more accurate, or the current one is getting old. */
    private fun consider(loc: Location) {
        val current = best
        val take = current == null ||
                ageSeconds(loc) < ageSeconds(current) && (loc.accuracy <= current.accuracy + 5f || ageSeconds(current) > 10) ||
                loc.accuracy < current.accuracy && ageSeconds(loc) <= ageSeconds(current) + 5
        if (take) {
            best = loc
            onUpdate(loc)
        }
    }

    companion object {
        fun ageSeconds(loc: Location): Long {
            val nanos = SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos
            return if (nanos < 0) 0 else nanos / 1_000_000_000L
        }

        @Suppress("DEPRECATION")
        fun isMock(loc: Location): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.isMock else loc.isFromMockProvider

        /** Distance in metres between two points (same formula the server uses). */
        fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6371000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = Math.toRadians(lat2 - lat1)
            val dl = Math.toRadians(lng2 - lng1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        fun formatDistance(m: Double): String =
            if (m >= 1000) String.format(java.util.Locale.US, "%.1f km", m / 1000) else "${Math.round(m)} m"
    }
}
