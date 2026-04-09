package com.example.fuelconsumptionapp.fuel

import kotlin.math.max

/**
 * Estimates mass air flow (g/s) from manifold pressure, intake temperature, RPM, and displacement
 * using the ideal-gas speed-density relation for a four-stroke engine:
 *
 *   ṁ = ρ · VE · (V · RPM / 120)  with  ρ = MAP / (R · T)
 *
 * MAP in Pa, V in m³, T in K, R = 287 J/(kg·K). Result in kg/s, then ×1000 for g/s.
 */
object SpeedDensityMaf {
    /** Air specific gas constant (J/(kg·K)). */
    private const val R_AIR = 287.05

    /** Default volumetric efficiency when not measured (typical cruise-ish assumption). */
    private const val DEFAULT_VE = 0.85

    /**
     * @param mapKPa manifold absolute pressure (kPa)
     * @param intakeTempCelsius intake air temperature (°C)
     * @param rpm engine speed (min⁻¹)
     * @param displacementLiters total engine displacement (L)
     * @param volumetricEfficiency VE in (0, 1.5]; default 0.85
     */
    fun estimateGramsPerSecond(
        mapKPa: Double,
        intakeTempCelsius: Double,
        rpm: Double,
        displacementLiters: Double,
        volumetricEfficiency: Double = DEFAULT_VE,
    ): Double? {
        if (!mapKPa.isFinite() || !intakeTempCelsius.isFinite() || !rpm.isFinite() || !displacementLiters.isFinite()) {
            return null
        }
        if (mapKPa <= 0.0 || displacementLiters <= 0.0 || rpm < 0.0) return null
        val ve = volumetricEfficiency.coerceIn(0.05, 1.5)
        val tK = intakeTempCelsius + 273.15
        if (tK <= 0.0) return null
        val mapPa = mapKPa * 1000.0
        val vM3 = displacementLiters / 1000.0
        val massKgS = (mapPa * ve * vM3 * rpm) / (120.0 * R_AIR * tK)
        val gS = massKgS * 1000.0
        if (!gS.isFinite() || gS < 0.0) return null
        return max(gS, 0.0)
    }
}
