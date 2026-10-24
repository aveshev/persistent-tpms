package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.compose.ui.text.buildAnnotatedString
import com.masselis.tpmsadvanced.core.ui.appendBold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.asSensorId
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QrCodeResultViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.QrCodeResultViewModel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import java.util.UUID

/**
 * What [result], a scanned QR code, leads to on the vehicle [vehicleUuid] it was scanned for, at
 * [location] for a code with a single sensor, shown
 * on the screen the scan started from once the camera is closed. [scan] tells one scan from the
 * next, so a code scanned again is worked out again. [onDismiss] once answered, [scanBluetooth] for
 * a code the app can't use.
 */
@Suppress("MaxLineLength", "LongMethod", "CyclomaticComplexMethod")
@Composable
public fun QrCodeResultDialog(
    vehicleUuid: UUID,
    location: Location,
    result: QrCodeResult,
    scan: Int,
    scanBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: QrCodeResultViewModel = viewModel(key = "QrCodeResult_$scan") {
        QrCodeResultViewModel(vehicleUuid, location, result)
    }
    val state by viewModel.stateFlow.collectAsState()
    when (val state = state) {
        State.Loading -> {}

        is State.Ask -> AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                Text(
                    buildAnnotatedString {
                        state
                            .sensors
                            .singleOrNull()
                            ?.takeIf { it.brand == WICARLINK }
                            ?.also { sensor ->
                                append("Assign the Wicarlink sensor ")
                                appendBold(sensor.id.asSensorId(sensor.brand))
                                append(" to the ")
                                if (sensor.location == Location.Single) append("wheel")
                                else appendBold(buildString { appendLoc(sensor.location) })
                                append(" of ")
                            }
                            ?: append("Assign the ${state.sensors.size} Sysgration sensors from this QR to ")
                        appendBold(state.vehicle.name)
                        append("?")
                        state.reassigns?.also { (vehicle, location) ->
                            append("\n\n⚠️ ")
                            append(if (state.sensors.size == 1) "This sensor is" else "One of these sensors is")
                            append(" already assigned to the ")
                            if (location == Location.Single) append("wheel")
                            else appendBold(buildString { appendLoc(location) })
                            append(" of ")
                            appendBold(vehicle.name)
                            append(", it will be reassigned")
                        }
                        if (state.overwrites) append("\n\n⚠️ Warning: this will overwrite already assigned sensors")
                    }
                )
            },
            // Answering yes is confirmation enough, the assigned sensors show on the vehicle
            confirmButton = {
                TextButton(onClick = { viewModel.bind(); onDismiss() }) {
                    Text(text = if (state.reassigns != null) "Reassign" else "Yes")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
        )

        is State.TooManySensors -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Too many sensors") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("This QR lists ${state.count} sensors, but ")
                        appendBold(state.vehicle.name)
                        append(" only needs ${state.vehicle.kind.locations.size}.\nPlease assign via Bluetooth instead.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.Unreadable -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("Error reading QR, please assign via Bluetooth instead") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        is State.Unusable -> UnusableAlert(state.result, scanBluetooth, onDismiss)

        is State.AskShared -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Shared sensors") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("Assign the sensors of ")
                        appendBold(state.from)
                        append(" to ")
                        appendBold(state.vehicle.name)
                        append("?\n")
                        state.sensors.forEach { shared ->
                            append("\n· ")
                            append(buildString { appendLoc(shared.sensor.location, withType = false, capitalized = true) })
                            append(": ")
                            appendBold(shared.sensor.id.asSensorId(shared.sensor.brand))
                            append(" ${shared.sensor.brand.label}")
                            if (shared.calibration.adjusts) append(", calibrated")
                        }
                        state.reassigns.forEach { (id, assigned) ->
                            append("\n\n⚠️ ")
                            appendBold(id.asSensorId(state.sensors.first { it.sensor.id == id }.sensor.brand))
                            append(" is assigned to the ")
                            if (assigned.location == Location.Single) append("wheel")
                            else appendBold(buildString { appendLoc(assigned.location) })
                            append(" of ")
                            appendBold(assigned.vehicle.name)
                            append(", it will be reassigned")
                        }
                        if (state.overwrites) append("\n\n⚠️ Warning: this will overwrite already assigned sensors")
                        if (state.skipped.isNotEmpty()) {
                            append("\n\nNot assigned, ")
                            appendBold(state.vehicle.name)
                            append(" has no ")
                            append(state.skipped.joinToString(" or ") { buildString { appendLoc(it.sensor.location) } })
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.bind(); onDismiss() }) {
                    Text(text = if (state.reassigns.isNotEmpty()) "Reassign" else "Assign")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
        )

        is State.NoSharedSensorFits -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Shared sensors") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("None of the sensors of ")
                        appendBold(state.shared.name)
                        append(" is on a wheel ")
                        appendBold(state.vehicle.name)
                        append(" has.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.SharedByNewerApp -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("These sensors were shared by a newer version of the app, please update it to read them") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.SharedUnreadable -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text("Error reading the shared sensors, please scan the code again") },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )
    }
}

/** The brand the way it's sold */
private val SensorBrand.label
    get() = when (this) {
        PECHAM -> "Pecham"
        BEKUBEE_KY -> "Bekubee KY"
        WICARLINK -> "Wicarlink"
        BEKUBEE_TPMS -> "Bekubee"
        SYSGRATION -> "Sysgration"
    }

@Suppress("MaxLineLength")
@Composable
private fun UnusableAlert(
    result: QrCodeResult,
    scanBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                buildString {
                    append("Detected inconsistency with the QR Code")
                    when (result) {
                        is QrCodeResult.DuplicateWheelLocation -> {
                            append("\n\n⚠️ Filled QR Code contains different sensors associated to the same wheel, ")
                            append(if (result.wheels.size == 1) "duplication:" else "duplications:")
                            result.wheels.forEach { location ->
                                append("\n   · ")
                                appendLoc(location)
                            }
                        }

                        is QrCodeResult.DuplicateId ->
                            append("\n\n⚠️ Filled QR Code contains the same sensor id multiple time")

                        is QrCodeResult.Sensors,
                        is QrCodeResult.Wicarlink,
                        is QrCodeResult.Shared,
                        QrCodeResult.SharedByNewerApp,
                        QrCodeResult.SharedUnreadable -> {}
                    }
                }
            )
        },
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = { onDismiss(); scanBluetooth() }) { Text(text = "Scan via Bluetooth") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
    )
}
