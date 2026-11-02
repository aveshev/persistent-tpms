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
import com.masselis.tpmsadvanced.core.common.R as CommonR
import androidx.compose.ui.res.stringResource

/**
 * What [result], a scanned QR code, leads to on the vehicle [vehicleUuid] it was scanned for, at
 * [location] for a code with a single sensor, shown
 * on the screen the scan started from once the camera is closed. [scan] tells one scan from any
 * other, on any vehicle, so a code scanned again is worked out again. [onDismiss] once answered, [scanBluetooth] for
 * a code the app can't use.
 */
@Suppress("MaxLineLength", "LongMethod", "CyclomaticComplexMethod")
@Composable
public fun QrCodeResultDialog(
    vehicleUuid: UUID,
    location: Location,
    result: QrCodeResult,
    scan: UUID,
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
                                append(stringResource(CommonR.string.qr_assign_wicarlink))
                                appendBold(sensor.id.asSensorId(sensor.brand))
                                append(stringResource(CommonR.string.qr_to_the))
                                if (sensor.location == Location.Single) append(stringResource(CommonR.string.loc_wheel))
                                else appendBold(buildString { appendLoc(sensor.location) })
                                append(stringResource(CommonR.string.qr_of))
                            }
                            ?: append(stringResource(CommonR.string.qr_assign_sysgration, state.sensors.size))
                        appendBold(state.vehicle.name)
                        append("?")
                        state.reassigns?.also { (vehicle, location) ->
                            append("\n\n⚠️ ")
                            append(if (state.sensors.size == 1) stringResource(CommonR.string.qr_this_sensor) else stringResource(CommonR.string.qr_one_of))
                            append(stringResource(CommonR.string.qr_already_assigned))
                            if (location == Location.Single) append(stringResource(CommonR.string.loc_wheel))
                            else appendBold(buildString { appendLoc(location) })
                            append(stringResource(CommonR.string.qr_of))
                            appendBold(vehicle.name)
                            append(stringResource(CommonR.string.qr_reassigned))
                        }
                        if (state.overwrites) append(stringResource(CommonR.string.qr_overwrite))
                    }
                )
            },
            // Answering yes is confirmation enough, the assigned sensors show on the vehicle
            confirmButton = {
                TextButton(onClick = { viewModel.bind(); onDismiss() }) {
                    Text(text = if (state.reassigns != null) stringResource(CommonR.string.reassign) else stringResource(CommonR.string.text_yes))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(CommonR.string.cancel)) } },
        )

        is State.TooManySensors -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(CommonR.string.too_many_sensors)) },
            text = {
                Text(
                    buildAnnotatedString {
                        append(stringResource(CommonR.string.qr_lists, state.count))
                        appendBold(state.vehicle.name)
                        append(stringResource(CommonR.string.qr_only_needs, state.vehicle.kind.locations.size))
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.Unreadable -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(CommonR.string.error_reading_qr_please_assign_via_bluetooth)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        is State.Unusable -> UnusableAlert(state.result, scanBluetooth, onDismiss)

        is State.AskShared -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(CommonR.string.shared_sensors)) },
            text = {
                Text(
                    buildAnnotatedString {
                        append(stringResource(CommonR.string.qr_assign_sensors_of))
                        appendBold(state.from)
                        append(stringResource(CommonR.string.qr_to))
                        appendBold(state.vehicle.name)
                        append("?\n")
                        state.sensors.forEach { shared ->
                            append("\n· ")
                            append(buildString { appendLoc(shared.sensor.location, withType = false, capitalized = true) })
                            append(": ")
                            appendBold(shared.sensor.id.asSensorId(shared.sensor.brand))
                            append(" ${shared.sensor.brand.label}")
                            if (shared.calibration.adjusts) append(stringResource(CommonR.string.calibrated))
                        }
                        state.reassigns.forEach { (id, assigned) ->
                            append("\n\n⚠️ ")
                            appendBold(id.asSensorId(state.sensors.first { it.sensor.id == id }.sensor.brand))
                            append(stringResource(CommonR.string.qr_is_assigned))
                            if (assigned.location == Location.Single) append(stringResource(CommonR.string.loc_wheel))
                            else appendBold(buildString { appendLoc(assigned.location) })
                            append(stringResource(CommonR.string.qr_of))
                            appendBold(assigned.vehicle.name)
                            append(stringResource(CommonR.string.qr_reassigned))
                        }
                        if (state.overwrites) append(stringResource(CommonR.string.qr_overwrite))
                        if (state.skipped.isNotEmpty()) {
                            append(stringResource(CommonR.string.qr_not_assigned))
                            appendBold(state.vehicle.name)
                            append(stringResource(CommonR.string.qr_has_no))
                            append(state.skipped.joinToString(stringResource(CommonR.string.qr_or)) { buildString { appendLoc(it.sensor.location) } })
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.bind(); onDismiss() }) {
                    Text(text = if (state.reassigns.isNotEmpty()) stringResource(CommonR.string.reassign) else stringResource(CommonR.string.assign))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(CommonR.string.cancel)) } },
        )

        is State.NoSharedSensorFits -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(CommonR.string.shared_sensors)) },
            text = {
                Text(
                    buildAnnotatedString {
                        append(stringResource(CommonR.string.qr_none_of))
                        appendBold(state.shared.name)
                        append(stringResource(CommonR.string.qr_on_a_wheel))
                        appendBold(state.vehicle.name)
                        append(stringResource(CommonR.string.qr_has))
                    }
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.SharedByNewerApp -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(CommonR.string.these_sensors_were_shared_by_a_newer)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
        )

        State.SharedUnreadable -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(CommonR.string.error_reading_the_shared_sensors_please_scan)) },
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
                    append(stringResource(CommonR.string.detected_inconsistency_with_the_qr_code))
                    when (result) {
                        is QrCodeResult.DuplicateWheelLocation -> {
                            append(stringResource(CommonR.string.qr_dup_wheel))
                            append(if (result.wheels.size == 1) stringResource(CommonR.string.qr_duplication) else stringResource(CommonR.string.qr_duplications))
                            result.wheels.forEach { location ->
                                append("\n   · ")
                                appendLoc(location)
                            }
                        }

                        is QrCodeResult.DuplicateId ->
                            append(stringResource(CommonR.string.qr_dup_id))

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
        dismissButton = { TextButton(onClick = { onDismiss(); scanBluetooth() }) { Text(text = stringResource(CommonR.string.scan_via_bluetooth)) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = "OK") } },
    )
}
