package com.masselis.tpmsadvanced.feature.qrcode.usecase

import androidx.camera.view.CameraController
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.FourWheel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.TwoWheel
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class QrCodeSensorUseCase(
    private val cameraAnalyser: CameraAnalyser,
) {

    @Suppress("MagicNumber", "MaxLineLength")
    /** What each QR code the camera sees holds, null for a code that isn't a TPMS one */
    fun analyse(
        controller: CameraController
    ): Flow<QrCodeResult?> = cameraAnalyser
        .findQrCode(controller)
        .map { code ->
            fourSensorRegex.find(code)?.groupValues?.subList(1, 5)?.asSysgration()
                ?: twoSensorRegex.find(code)?.groupValues?.subList(1, 3)?.asSysgration()
                // Printed the way the cap reads, the id's most significant byte first
                ?: wicarlinkSensorRegex.find(code.trim())?.groupValues?.get(1)?.toInt(16)?.let(QrCodeResult::Wicarlink)
        }
        .flowOn(Default)

    /** The sensors of a Sysgration code, each at the wheel its id starts with or its rank */
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("MagicNumber", "CyclomaticComplexMethod", "MaxLineLength")
    private fun List<String>.asSysgration(): QrCodeResult = this
        .map { stringHex ->
            Pair(
                // Trying to recognize the location with the id of the sensor
                when (stringHex.first()) {
                    '1' -> Location.Wheel(FRONT_LEFT)
                    '2' -> Location.Wheel(FRONT_RIGHT)
                    '3' -> Location.Wheel(REAR_LEFT)
                    '4' -> Location.Wheel(REAR_RIGHT)
                    else -> null
                },
                // Converts the hexadecimal id to an int
                stringHex
                    .hexToByteArray()
                    .let {
                        ByteBuffer
                            .wrap(byteArrayOf(0x00) + it)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .int
                    },
            )
        }
        .mapIndexed { index, (location, id) ->
            QrCodeSensor(
                id,
                // The sensor id didn't provided the location, let's determine it with the
                // list index
                location ?: when (index) {
                    0 -> Location.Wheel(FRONT_LEFT)
                    1 -> Location.Wheel(FRONT_RIGHT)
                    2 -> Location.Wheel(REAR_LEFT)
                    3 -> Location.Wheel(REAR_RIGHT)
                    else -> error("Filled list cannot have more than 4 entries")
                }
            )
        }
        .let {
            when (it.size) {
                2 -> TwoWheel(it[0], it[1])
                4 -> FourWheel(it[0], it[1], it[2], it[3])
                else -> error("Unreachable state, previous regex should only contains 2 or 4 sensors, current sensors: $it")
            }
        }
        .let(QrCodeResult::Sensors)

    companion object {
        // Test available here: https://regex101.com/r/aLJ0o6/1
        private val fourSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        private val twoSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        // A Wicarlink/LYTPMS QR code only contains a single sensor's 6 hex character id, e.g. 2221C5
        private val wicarlinkSensorRegex =
            "^([0-9a-fA-F]{6})$".toRegex()
    }
}
