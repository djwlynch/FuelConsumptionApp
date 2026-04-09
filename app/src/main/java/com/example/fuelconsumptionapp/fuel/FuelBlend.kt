package com.example.fuelconsumptionapp.fuel

enum class FuelBlend(
    val displayName: String,
    val stoichAfr: Double,        // mass air / mass fuel
    val densityGramsPerLiter: Double,
) {
    GASOLINE(
        displayName = "Gasoline (E0)",
        stoichAfr = 14.7,
        densityGramsPerLiter = 745.0,
    ),
    E10(
        displayName = "E10",
        stoichAfr = 14.1,
        densityGramsPerLiter = 755.0,
    ),
    E85(
        displayName = "E85",
        stoichAfr = 9.8,
        densityGramsPerLiter = 785.0,
    ),
}

