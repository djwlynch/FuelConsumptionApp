package com.example.fuelconsumptionapp.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

data class ObdLiveData(
    val connectedDeviceName: String? = null,
    val status: String = "Disconnected",
    val mafGramsPerSecond: Double? = null,
    val speedKmh: Double? = null,
    val mapKpa: Double? = null,
    val intakeTempCelsius: Double? = null,
    val rpm: Double? = null,
    val lambda: Double? = null,
    val stftPercent: Double? = null,
    val ltftPercent: Double? = null,
)

class ObdRepository(private val appContext: Context) {
    private val _live = MutableStateFlow(ObdLiveData())
    val live: StateFlow<ObdLiveData> = _live

    private var transport: Elm327Transport? = null

    fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter() ?: return emptyList()
        @SuppressLint("MissingPermission")
        return adapter.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndPoll(device: BluetoothDevice) {
        disconnect()
        _live.value = _live.value.copy(
            connectedDeviceName = device.name ?: device.address,
            status = "Connecting…",
        )

        val t = Elm327Transport(device)
        transport = t

        try {
            t.connect()
            _live.value = _live.value.copy(status = "Initializing ELM327…")
            t.initializeElm()
            _live.value = _live.value.copy(status = "Connected")

            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val speedRaw = t.sendCommand(ObdPids.SPEED)
                val rpmRaw = t.sendCommand(ObdPids.RPM)
                val mapRaw = t.sendCommand(ObdPids.MAP)
                val iatRaw = t.sendCommand(ObdPids.IAT)
                val mafRaw = t.sendCommand(ObdPids.MAF)
                val stftRaw = t.sendCommand(ObdPids.STFT_B1)
                val ltftRaw = t.sendCommand(ObdPids.LTFT_B1)

                // Try lambda: commanded equivalence ratio (preferred), else O2S1 wideband.
                val lambdaRaw1 = t.sendCommand(ObdPids.COMMANDED_EQ_RATIO)
                val lambda1 = ObdParsers.parseLambdaFromEquivalenceRatio(lambdaRaw1, ObdPids.COMMANDED_EQ_RATIO)
                val lambda =
                    lambda1 ?: run {
                        val lambdaRaw2 = t.sendCommand(ObdPids.O2S1_EQ_RATIO_VOLT)
                        ObdParsers.parseLambdaFromEquivalenceRatio(lambdaRaw2, ObdPids.O2S1_EQ_RATIO_VOLT)
                    }

                _live.value = _live.value.copy(
                    status = "Connected",
                    speedKmh = ObdParsers.parseSpeedKmh(speedRaw),
                    rpm = ObdParsers.parseRpm(rpmRaw),
                    mapKpa = ObdParsers.parseMapKpa(mapRaw),
                    intakeTempCelsius = ObdParsers.parseIntakeTempCelsius(iatRaw),
                    mafGramsPerSecond = ObdParsers.parseMafGramsPerSecond(mafRaw),
                    stftPercent = ObdParsers.parseFuelTrimPercent(stftRaw, ObdPids.STFT_B1),
                    ltftPercent = ObdParsers.parseFuelTrimPercent(ltftRaw, ObdPids.LTFT_B1),
                    lambda = lambda,
                )

                delay(350L)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _live.value = _live.value.copy(status = "Error: ${e.message ?: e::class.java.simpleName}")
        } finally {
            disconnect()
        }
    }

    fun disconnect() {
        transport?.close()
        transport = null
        _live.value = ObdLiveData(status = "Disconnected")
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }
}

