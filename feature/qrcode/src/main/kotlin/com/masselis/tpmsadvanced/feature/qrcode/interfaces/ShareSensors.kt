package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.common.BitMatrix
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.asSensorId
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.ShareSensorsViewModel.State
import com.masselis.tpmsadvanced.feature.qrcode.ioc.Bindings.Companion.ShareSensorsViewModel
import java.util.UUID

/**
 * A QR code holding the sensors of the vehicle [vehicleUuid], for another phone to assign them all
 * at once by scanning it, see [QrCodeResultDialog]
 */
@Composable
public fun ShareSensors(
    vehicleUuid: UUID,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShareSensorsViewModel = viewModel(key = "ShareSensors_$vehicleUuid") {
        ShareSensorsViewModel(vehicleUuid)
    }
    val state by viewModel.stateFlow.collectAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (val state = state) {
            State.Loading -> {}

            is State.NoSensor -> Text(
                text = "${state.vehicle.name} has no sensor assigned yet, there's nothing to share",
                textAlign = TextAlign.Center,
            )

            is State.Ready -> {
                Text(
                    text = "On the other phone, open this app's vehicle settings, tap “Scan shared sensors” " +
                        "and point the camera at this code",
                    textAlign = TextAlign.Center,
                )
                QrCode(
                    code = state.code,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Column(Modifier.fillMaxWidth()) {
                    Text(text = state.shared.name, style = MaterialTheme.typography.titleMedium)
                    state.shared.sensors.forEach { shared ->
                        Text(
                            text = buildString {
                                appendLoc(shared.sensor.location, withType = false, capitalized = true)
                                append(": ")
                                append(shared.sensor.id.asSensorId(shared.sensor.brand))
                                if (shared.calibration.adjusts) append(", calibrated")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Dark on light whatever the theme, with the quiet zone around it scanners need */
@Composable
private fun QrCode(
    code: BitMatrix,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(Color.White)
        val module = size.minDimension / (code.width + QUIET_ZONE * 2)
        for (x in 0 until code.width) for (y in 0 until code.height) if (code[x, y]) drawRect(
            color = Color.Black,
            topLeft = Offset((x + QUIET_ZONE) * module, (y + QUIET_ZONE) * module),
            // A hair larger, so the modules don't show seams between them
            size = Size(module + .5f, module + .5f),
        )
    }
}

/** In modules, what the QR code specification asks for */
private const val QUIET_ZONE = 4
