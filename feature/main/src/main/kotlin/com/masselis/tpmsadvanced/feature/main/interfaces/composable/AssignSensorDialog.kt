package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R


/**
 * The ways to assign a sensor to [location], each acting when tapped: the [detected] sensor first
 * when one advertises this location, then by its QR code or by scanning the sensors around
 */
@Suppress("LongMethod")
@Composable
internal fun AssignSensorDialog(
    location: Location,
    detected: Sensor?,
    scanQrCode: () -> Unit,
    scanBluetooth: () -> Unit,
    assignDetected: (Sensor) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionsDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(buildString { append("Assign a sensor to the "); appendLoc(location) }) },
        text = {
            Column {
                detected?.also { sensor ->
                    // The recommended way, under a light tint of the primary color
                    DialogOption(
                        icon = { SysgrationBadge() },
                        title = "Assign detected sensor",
                        subtitle = buildString {
                            append("Sysgration, ")
                            appendLoc(location, withType = false)
                            append(", ID: ")
                            append(sensor.id.asSensorId(sensor.brand))
                        },
                        onClick = { assignDetected(sensor) },
                        isHighlighted = true,
                        modifier = Modifier.testTag(AssignSensorDialogTags.detected),
                    )
                }
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.qr_code_24px), null) },
                    title = "Scan QR code",
                    subtitle = "Printed on the sensors' box, card or cap",
                    onClick = scanQrCode,
                    modifier = Modifier.testTag(AssignSensorDialogTags.qrCode),
                )
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.bluetooth_24px), null) },
                    title = "Scan via Bluetooth",
                    subtitle = "Use pressure changes to detect the correct sensor",
                    onClick = scanBluetooth,
                    modifier = Modifier.testTag(AssignSensorDialogTags.bluetooth),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag(AssignSensorDialogTags.cancel),
            ) { Text("Cancel") }
        },
        modifier = modifier.testTag(AssignSensorDialogTags.root),
    )
}

@Preview
@Composable
internal fun AssignSensorDialogPreview() {
    AssignSensorDialog(Location.Wheel(FRONT_LEFT), null, {}, {}, {}, {})
}

@Preview
@Composable
internal fun AssignSensorDialogDetectedPreview() {
    AssignSensorDialog(
        Location.Wheel(FRONT_LEFT),
        Sensor(0x1A2B3C00, Location.Wheel(FRONT_LEFT), SYSGRATION),
        {}, {}, {}, {},
    )
}

@Suppress("ConstPropertyName")
internal object AssignSensorDialogTags {
    const val root = "AssignSensorDialogTags_root"
    const val detected = "AssignSensorDialogTags_detected"
    const val qrCode = "AssignSensorDialogTags_qrCode"
    const val bluetooth = "AssignSensorDialogTags_bluetooth"
    const val cancel = "AssignSensorDialogTags_cancel"
}
