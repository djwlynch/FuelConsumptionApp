package com.example.fuelconsumptionapp.fuel

import kotlin.math.abs

object FuelConsumptionCalculator {
    data class Inputs(
        val mafGramsPerSecond: Double,
        val speedKmh: Double,
        val lambda: Double?, // lambda (λ). If null, treat as 1.0
        val stftPercent: Double?, // + adds fuel, - removes fuel
        val ltftPercent: Double?, // + adds fuel, - removes fuel
        val fuelBlend: FuelBlend,
    )

    data class Output(
        val litersPerHour: Double,
        val litersPer100Km: Double?, // null when speed too low
    )

    fun compute(i: Inputs): Output {
        val lambda = i.lambda?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

        val stft = (i.stftPercent ?: 0.0).takeIf { it.isFinite() } ?: 0.0
        val ltft = (i.ltftPercent ?: 0.0).takeIf { it.isFinite() } ?: 0.0

        // Combined trims: +10% STFT means ECU adds ~10% more fuel than base.
        // More fuel -> lower effective AFR; model as AFR / (1 + trim).
        val trimFrac = ((stft + ltft) / 100.0).coerceIn(-0.5, 0.5)

        val stoich = i.fuelBlend.stoichAfr
        val commandedAfr = stoich * lambda
        val effectiveAfr = commandedAfr / (1.0 + trimFrac)

        // Fuel mass flow (g/s) = air mass flow (g/s) / (air/fuel)
        val fuelGramsPerSecond = i.mafGramsPerSecond / effectiveAfr
        val litersPerHour =
            (fuelGramsPerSecond * 3600.0) / i.fuelBlend.densityGramsPerLiter

        val speed = i.speedKmh
        val litersPer100Km =
            if (speed.isFinite() && speed > 1.0 && abs(speed) > 1e-9) (litersPerHour / speed) * 100.0 else null

        return Output(
            litersPerHour = litersPerHour,
            litersPer100Km = litersPer100Km,
        )
    }
}

