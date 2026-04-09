package com.example.fuelconsumptionapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fuelconsumptionapp.fuel.FuelBlend
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FuelDashboardApp(vm: FuelDashboardViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val permissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        vm.refreshBondedDevices()
    }

    LaunchedEffect(Unit) {
        permLauncher.launch(permissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "OBD2 Fuel Consumption",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = state.status, style = MaterialTheme.typography.bodyMedium)
                if (state.connectedDeviceName != null) {
                    Text(
                        text = "Device: ${state.connectedDeviceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refreshBondedDevices() }) {
                    Text("Devices")
                }
                if (state.connectedDeviceName != null) {
                    Button(onClick = { vm.disconnect() }) { Text("Disconnect") }
                }
            }
        }

        DevicePicker(
            devicesCount = state.bondedDevices.size,
            onRefresh = { vm.refreshBondedDevices() },
            devicesLabel = "Paired devices",
            deviceNames = state.bondedDevices.map { it.name ?: it.address },
            onPickIndex = { idx ->
                state.bondedDevices.getOrNull(idx)?.let(vm::connect)
            }
        )

        FuelBlendPicker(
            selected = state.selectedFuelBlend,
            onSelected = vm::setFuelBlend,
        )

        OutlinedTextField(
            value = state.engineDisplacementInputText,
            onValueChange = vm::setEngineDisplacementInput,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Engine displacement (L)") },
            supportingText = {
                Text(
                    "Used for speed-density MAF if the MAF PID is unavailable (default 2.00).",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        Spacer(Modifier.height(6.dp))

        val lPer100 = state.litersPer100Km
        val shown = lPer100?.let { (it * 10.0).roundToInt() / 10.0 }

        val lpH = state.litersPerHour
        val lpHShown = lpH?.let { String.format(Locale.US, "%.2f", it) }
        val tripAvg = state.tripAverageLitersPer100Km
        val tripShown = tripAvg?.let { (it * 10.0).roundToInt() / 10.0 }
        FuelGauge(
            value = lPer100,
            min = 0.0,
            max = 30.0,
            greenMax = 8.0,
            yellowMax = 15.0,
            label = if (shown == null) "—  L/100km" else "$shown  L/100km",
            secondary = if (lpHShown == null) "—  L/h" else "$lpHShown  L/h",
            litersPerHour = lpH,
            litersPerHourMin = 0.0,
            litersPerHourMax = 40.0,
            tripAverageLitersPer100Km = tripAvg,
            tripAverageLabel = if (tripShown == null) "—  Trip L/100km" else "$tripShown  Trip L/100km",
        )

        OutlinedButton(
            onClick = { vm.resetTrip() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset trip")
        }

        Text(
            text = "Live readings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetricLine("Trip distance", state.tripDistanceKm, "km")
            MetricLine("Trip fuel used", state.tripLitersConsumed, "L")
            MetricLine("Trip avg (since reset)", state.tripAverageLitersPer100Km, "L/100km")
            MetricLine("Engine displacement", state.engineDisplacementLiters, "L")
            MetricLine("MAP", state.mapKpa, "kPa")
            MetricLine("IAT", state.intakeTempCelsius, "°C")
            MetricLine("RPM", state.rpm, "rpm")
            MetricLine("MAF (sensor PID)", state.mafGramsPerSecondSensor, "g/s")
            MetricLine("MAF (used)", state.mafGramsPerSecond, "g/s")
            MetricLine(
                "MAF source",
                when (state.mafSource) {
                    MafSource.SENSOR -> "Sensor"
                    MafSource.SPEED_DENSITY -> "Speed density"
                    MafSource.NONE -> "—"
                },
            )
            MetricLine("Speed", state.speedKmh, "km/h")
            MetricLine("Lambda (λ)", state.lambda, null)
            MetricLine("STFT", state.stftPercent, "%")
            MetricLine("LTFT", state.ltftPercent, "%")
            MetricLine("Fuel consumption", state.litersPerHour, "L/h")
        }

        Text(
            text = "Tip: pair your OBD2 dongle in Android Bluetooth settings first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FuelBlendPicker(selected: FuelBlend, onSelected: (FuelBlend) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Fuel blend", style = MaterialTheme.typography.titleMedium)
        Column(horizontalAlignment = Alignment.End) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected.displayName)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                FuelBlend.entries.forEach { blend ->
                    DropdownMenuItem(
                        text = { Text(blend.displayName) },
                        onClick = {
                            expanded = false
                            onSelected(blend)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devicesCount: Int,
    onRefresh: () -> Unit,
    devicesLabel: String,
    deviceNames: List<String>,
    onPickIndex: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(devicesLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$devicesCount available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            Button(
                onClick = { expanded = true },
                enabled = deviceNames.isNotEmpty(),
            ) { Text("Connect") }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                deviceNames.forEachIndexed { idx, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onPickIndex(idx)
                        }
                    )
                }
            }
        }
    }
}

private fun formatDouble2(value: Double?): String =
    if (value != null && value.isFinite()) String.format(Locale.US, "%.2f", value) else "—"

@Composable
private fun MetricLine(label: String, value: Double?, unit: String?) {
    val number = formatDouble2(value)
    val right =
        when {
            number == "—" && unit != null -> "— $unit"
            number == "—" -> "—"
            unit != null -> "$number $unit"
            else -> number
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = right,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetricLine(label: String, valueText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

