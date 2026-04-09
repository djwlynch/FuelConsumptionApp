package com.example.fuelconsumptionapp.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fuelconsumptionapp.fuel.FuelBlend
import com.example.fuelconsumptionapp.fuel.FuelConsumptionCalculator
import com.example.fuelconsumptionapp.fuel.SpeedDensityMaf
import com.example.fuelconsumptionapp.obd.ObdLiveData
import com.example.fuelconsumptionapp.obd.ObdRepository
import com.example.fuelconsumptionapp.trip.TripAccumulator
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MafSource {
    SENSOR,
    SPEED_DENSITY,
    NONE,
}

data class UiState(
    val status: String = "Disconnected",
    val connectedDeviceName: String? = null,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val selectedFuelBlend: FuelBlend = FuelBlend.GASOLINE,
    val litersPer100Km: Double? = null,
    val litersPerHour: Double? = null,
    val speedKmh: Double? = null,
    /** MAF used for fuel math (sensor or speed-density estimate). */
    val mafGramsPerSecond: Double? = null,
    val mafGramsPerSecondSensor: Double? = null,
    val mafSource: MafSource = MafSource.NONE,
    val lambda: Double? = null,
    val stftPercent: Double? = null,
    val ltftPercent: Double? = null,
    val mapKpa: Double? = null,
    val intakeTempCelsius: Double? = null,
    val rpm: Double? = null,
    val engineDisplacementLiters: Double = DEFAULT_ENGINE_DISPLACEMENT_L,
    val engineDisplacementInputText: String = formatDisplacement(DEFAULT_ENGINE_DISPLACEMENT_L),
    val tripDistanceKm: Double = 0.0,
    val tripLitersConsumed: Double = 0.0,
    val tripAverageLitersPer100Km: Double? = null,
)

private data class CoreSnapshot(
    val status: String,
    val connectedDeviceName: String?,
    val bondedDevices: List<BluetoothDevice>,
    val selectedFuelBlend: FuelBlend,
    val litersPer100Km: Double?,
    val litersPerHour: Double?,
    val speedKmh: Double?,
    val mafGramsPerSecond: Double?,
    val mafGramsPerSecondSensor: Double?,
    val mafSource: MafSource,
    val lambda: Double?,
    val stftPercent: Double?,
    val ltftPercent: Double?,
    val mapKpa: Double?,
    val intakeTempCelsius: Double?,
    val rpm: Double?,
    val engineDisplacementLiters: Double,
    val engineDisplacementInputText: String,
)

private const val PREFS_NAME = "fuel_dashboard"
private const val KEY_ENGINE_DISPLACEMENT_L = "engine_displacement_l"
private const val KEY_FUEL_BLEND = "fuel_blend"
private const val KEY_TRIP_DISTANCE_KM = "trip_distance_km"
private const val KEY_TRIP_LITERS = "trip_liters_consumed"
private const val DEFAULT_ENGINE_DISPLACEMENT_L = 2.0
private const val MIN_DISPLACEMENT_L = 0.1
private const val MAX_DISPLACEMENT_L = 20.0

private fun formatDisplacement(liters: Double): String =
    String.format(Locale.US, "%.2f", liters)

private fun parseDisplacementLiters(text: String, fallback: Double): Double {
    val n = text.trim().replace(',', '.').toDoubleOrNull() ?: return fallback
    return n.coerceIn(MIN_DISPLACEMENT_L, MAX_DISPLACEMENT_L)
}

private fun SharedPreferences.loadFuelBlend(): FuelBlend {
    val name = getString(KEY_FUEL_BLEND, null) ?: return FuelBlend.GASOLINE
    return FuelBlend.entries.find { it.name == name } ?: FuelBlend.GASOLINE
}

private fun computeCoreSnapshot(
    live: ObdLiveData,
    blend: FuelBlend,
    devices: List<BluetoothDevice>,
    dispText: String,
    prefs: SharedPreferences,
): CoreSnapshot {
    val savedDisplacement =
        prefs.getFloat(KEY_ENGINE_DISPLACEMENT_L, DEFAULT_ENGINE_DISPLACEMENT_L.toFloat())
            .toDouble()
            .coerceIn(MIN_DISPLACEMENT_L, MAX_DISPLACEMENT_L)
    val displacementLiters = parseDisplacementLiters(dispText, savedDisplacement)
    val sensorMaf = live.mafGramsPerSecond
    val sdMaf =
        if (sensorMaf == null) {
            val map = live.mapKpa
            val iat = live.intakeTempCelsius
            val rpm = live.rpm
            if (map != null && iat != null && rpm != null) {
                SpeedDensityMaf.estimateGramsPerSecond(
                    mapKPa = map,
                    intakeTempCelsius = iat,
                    rpm = rpm,
                    displacementLiters = displacementLiters,
                )
            } else {
                null
            }
        } else {
            null
        }
    val effectiveMaf = sensorMaf ?: sdMaf
    val mafSource =
        when {
            effectiveMaf == null -> MafSource.NONE
            sensorMaf != null -> MafSource.SENSOR
            else -> MafSource.SPEED_DENSITY
        }

    val out =
        if (effectiveMaf != null) {
            FuelConsumptionCalculator.compute(
                FuelConsumptionCalculator.Inputs(
                    mafGramsPerSecond = effectiveMaf,
                    speedKmh = live.speedKmh ?: 0.0,
                    lambda = live.lambda,
                    stftPercent = live.stftPercent,
                    ltftPercent = live.ltftPercent,
                    fuelBlend = blend,
                )
            )
        } else {
            null
        }

    return CoreSnapshot(
        status = live.status,
        connectedDeviceName = live.connectedDeviceName,
        bondedDevices = devices,
        selectedFuelBlend = blend,
        litersPer100Km = out?.litersPer100Km,
        litersPerHour = out?.litersPerHour,
        speedKmh = live.speedKmh,
        mafGramsPerSecond = effectiveMaf,
        mafGramsPerSecondSensor = sensorMaf,
        mafSource = mafSource,
        lambda = live.lambda,
        stftPercent = live.stftPercent,
        ltftPercent = live.ltftPercent,
        mapKpa = live.mapKpa,
        intakeTempCelsius = live.intakeTempCelsius,
        rpm = live.rpm,
        engineDisplacementLiters = displacementLiters,
        engineDisplacementInputText = dispText,
    )
}

private fun CoreSnapshot.toUiState(trip: TripAccumulator): UiState =
    UiState(
        status = status,
        connectedDeviceName = connectedDeviceName,
        bondedDevices = bondedDevices,
        selectedFuelBlend = selectedFuelBlend,
        litersPer100Km = litersPer100Km,
        litersPerHour = litersPerHour,
        speedKmh = speedKmh,
        mafGramsPerSecond = mafGramsPerSecond,
        mafGramsPerSecondSensor = mafGramsPerSecondSensor,
        mafSource = mafSource,
        lambda = lambda,
        stftPercent = stftPercent,
        ltftPercent = ltftPercent,
        mapKpa = mapKpa,
        intakeTempCelsius = intakeTempCelsius,
        rpm = rpm,
        engineDisplacementLiters = engineDisplacementLiters,
        engineDisplacementInputText = engineDisplacementInputText,
        tripDistanceKm = trip.tripDistanceKm,
        tripLitersConsumed = trip.tripLitersConsumed,
        tripAverageLitersPer100Km = trip.averageLitersPer100Km(),
    )

class FuelDashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ObdRepository(app.applicationContext)
    private val prefs = app.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val tripAccumulator = TripAccumulator(
        prefs = prefs,
        keyDistanceKm = KEY_TRIP_DISTANCE_KM,
        keyLiters = KEY_TRIP_LITERS,
    )

    private val initialDisplacement =
        prefs.getFloat(KEY_ENGINE_DISPLACEMENT_L, DEFAULT_ENGINE_DISPLACEMENT_L.toFloat())
            .toDouble()
            .coerceIn(MIN_DISPLACEMENT_L, MAX_DISPLACEMENT_L)
    private val initialFuelBlend = prefs.loadFuelBlend()

    private val fuelBlend = MutableStateFlow(initialFuelBlend)
    private val bonded = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    private val engineDisplacementInputText = MutableStateFlow(formatDisplacement(initialDisplacement))

    private val coreFlow =
        combine(repo.live, fuelBlend, bonded, engineDisplacementInputText) { live, blend, devices, dispText ->
            computeCoreSnapshot(live, blend, devices, dispText, prefs)
        }

    /** Bumps on trip reset so [state] recomputes without waiting for the next OBD sample. */
    private val tripResetEpoch = MutableStateFlow(0L)

    private val coreWithTripIntegration =
        coreFlow.onEach { core ->
            tripAccumulator.onSample(
                connected = core.status == "Connected",
                speedKmh = core.speedKmh,
                litersPerHour = core.litersPerHour,
            )
        }

    val state: StateFlow<UiState> =
        combine(coreWithTripIntegration, tripResetEpoch) { core, _ ->
            core.toUiState(tripAccumulator)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                selectedFuelBlend = initialFuelBlend,
                engineDisplacementLiters = initialDisplacement,
                engineDisplacementInputText = formatDisplacement(initialDisplacement),
                tripDistanceKm = tripAccumulator.tripDistanceKm,
                tripLitersConsumed = tripAccumulator.tripLitersConsumed,
                tripAverageLitersPer100Km = tripAccumulator.averageLitersPer100Km(),
            ),
        )

    override fun onCleared() {
        tripAccumulator.persistImmediate()
        super.onCleared()
    }

    fun refreshBondedDevices() {
        bonded.value = repo.getBondedDevices()
    }

    fun setFuelBlend(blend: FuelBlend) {
        fuelBlend.value = blend
        prefs.edit().putString(KEY_FUEL_BLEND, blend.name).apply()
    }

    fun setEngineDisplacementInput(text: String) {
        engineDisplacementInputText.value = text
        val parsed = text.trim().replace(',', '.').toDoubleOrNull()
        if (parsed != null && parsed in MIN_DISPLACEMENT_L..MAX_DISPLACEMENT_L) {
            prefs.edit().putFloat(KEY_ENGINE_DISPLACEMENT_L, parsed.toFloat()).apply()
        }
    }

    fun resetTrip() {
        tripAccumulator.reset()
        tripResetEpoch.value = System.currentTimeMillis()
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            repo.connectAndPoll(device)
        }
    }

    fun disconnect() {
        repo.disconnect()
    }
}
