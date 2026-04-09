package com.example.fuelconsumptionapp.trip

import android.content.SharedPreferences
import android.os.SystemClock

/**
 * Integrates speed and fuel flow into trip distance and fuel used.
 * Persists at most about once per [persistIntervalMs] to limit flash wear.
 */
class TripAccumulator(
    private val prefs: SharedPreferences,
    private val keyDistanceKm: String,
    private val keyLiters: String,
    private val persistIntervalMs: Long = 30_000L,
) {
    var tripDistanceKm: Double = prefs.getFloat(keyDistanceKm, 0f).toDouble().coerceAtLeast(0.0)
        private set

    var tripLitersConsumed: Double = prefs.getFloat(keyLiters, 0f).toDouble().coerceAtLeast(0.0)
        private set

    private var lastSampleElapsedMs: Long? = null
    private var lastPersistElapsedMs = 0L

    fun averageLitersPer100Km(): Double? {
        if (tripDistanceKm < MIN_DISTANCE_KM_FOR_AVG) return null
        val avg = (tripLitersConsumed / tripDistanceKm) * 100.0
        return if (avg.isFinite()) avg else null
    }

    /**
     * Call whenever new telemetry arrives. When not [connected], the next interval after reconnect
     * does not include time spent offline.
     */
    fun onSample(
        connected: Boolean,
        speedKmh: Double?,
        litersPerHour: Double?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        if (!connected || speedKmh == null || litersPerHour == null) {
            lastSampleElapsedMs = null
            return
        }
        val last = lastSampleElapsedMs
        lastSampleElapsedMs = nowElapsedMs
        if (last == null) return

        var dtMs = nowElapsedMs - last
        if (dtMs < MIN_DT_MS) return
        dtMs = dtMs.coerceAtMost(MAX_DT_MS)

        val dtHr = dtMs / 3_600_000.0
        tripDistanceKm += speedKmh.coerceAtLeast(0.0) * dtHr
        tripLitersConsumed += litersPerHour.coerceAtLeast(0.0) * dtHr

        if (nowElapsedMs - lastPersistElapsedMs >= persistIntervalMs) {
            lastPersistElapsedMs = nowElapsedMs
            persist()
        }
    }

    fun reset() {
        tripDistanceKm = 0.0
        tripLitersConsumed = 0.0
        lastSampleElapsedMs = null
        lastPersistElapsedMs = SystemClock.elapsedRealtime()
        persist()
    }

    fun persistImmediate() {
        lastPersistElapsedMs = SystemClock.elapsedRealtime()
        persist()
    }

    private fun persist() {
        prefs.edit()
            .putFloat(keyDistanceKm, tripDistanceKm.toFloat())
            .putFloat(keyLiters, tripLitersConsumed.toFloat())
            .apply()
    }

    companion object {
        private const val MIN_DISTANCE_KM_FOR_AVG = 0.05
        private const val MIN_DT_MS = 50L
        private const val MAX_DT_MS = 60_000L
    }
}
