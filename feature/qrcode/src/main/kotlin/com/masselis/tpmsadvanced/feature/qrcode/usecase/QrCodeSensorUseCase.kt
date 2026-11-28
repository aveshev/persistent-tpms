package com.masselis.tpmsadvanced.feature.qrcode.usecase

import android.net.Uri
import androidx.camera.view.CameraController
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.CameraAnalyser
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensor
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.FourWheel
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeSensors.TwoWheel
import com.masselis.tpmsadvanced.feature.qrcode.model.SharedSensors
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class QrCodeSensorUseCase(
    private val cameraAnalyser: CameraAnalyser,
) {

    /** What the camera sees */
    sealed interface Sight {
        /** What a QR code holds, null for a code that isn't a TPMS one */
        @JvmInline
        value class Code(val result: QrCodeResult?) : Sight

        /** A sensor ID printed next to its code, see [printedId] */
        @JvmInline
        value class PrintedId(val id: String) : Sight
    }

    /** What each frame of the camera shows, its codes first */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun analyse(
        controller: CameraController
    ): Flow<Sight> = cameraAnalyser
        .analyse(controller)
        .flatMapConcat { seen ->
            seen.codes
                .map { Sight.Code(decode(it)) }
                .plus(seen.texts.printedIds().map(Sight::PrintedId))
                .asFlow()
        }
        .flowOn(Default)

    /**
     * The TPMS codes the pictures [uris] hold, each once, in their order. A picture without one gives
     * the sensor IDs printed on it instead, as if typed in.
     */
    suspend fun read(uris: List<Uri>): List<QrCodeResult> = uris
        .flatMap { uri ->
            cameraAnalyser
                .read(uri)
                .let { seen ->
                    seen.codes
                        .mapNotNull { decode(it) }
                        .ifEmpty { seen.texts.printedIds().mapNotNull { decode(it) } }
                }
        }
        .distinct()

    private fun List<String>.printedIds() = mapNotNull { printedId(it) }.distinct()

    /**
     * The sensor ID [text] reads, null when it isn't one: 6 hexadecimal characters, as a Wicarlink
     * cap prints them below its code. Spaces are left out and the letters a hexadecimal ID can't hold
     * read as the digits they're mistaken for, O for 0, I and L for 1.
     */
    fun printedId(text: String): String? = text
        .filterNot { it.isWhitespace() }
        .uppercase()
        .map {
            when (it) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> it
            }
        }
        .joinToString("")
        .takeIf { printedIdRegex.matches(it) }

    /** What [code] holds, read off the camera, a picture or typed in, null when it isn't a TPMS one */
    @Suppress("MagicNumber", "MaxLineLength")
    fun decode(code: String): QrCodeResult? = try {
        logger.d { "QR code read: $code" }
        // First, the kit formats look for their ids anywhere in a code
        try {
            SharedSensors.decode(code)?.let(QrCodeResult::Shared)
        } catch (_: SharedSensors.UnsupportedVersion) {
            QrCodeResult.SharedByNewerApp
        } catch (_: IllegalArgumentException) {
            QrCodeResult.SharedUnreadable
        }
            ?: fourSensorRegex.find(code)?.groupValues?.subList(1, 5)?.asSysgration()
            ?: twoSensorRegex.find(code)?.groupValues?.subList(1, 3)?.asSysgration()
            // Printed the way the cap reads, the id's most significant byte first
            ?: wicarlinkSensorRegex.find(code.trim())?.groupValues?.get(1)?.toInt(16)?.let(QrCodeResult::Wicarlink)
    } catch (exc: QrCodeSensors.DuplicateWheelLocation) {
        QrCodeResult.DuplicateWheelLocation(exc.wheels.duplicates())
    } catch (exc: QrCodeSensors.DuplicateId) {
        QrCodeResult.DuplicateId(exc.ids.duplicates())
    }

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

    private fun <T> Iterable<T>.duplicates() = groupingBy { it }
        .eachCount()
        .mapNotNull { (value, count) -> if (count > 1) value else null }

    companion object {
        private val logger = Logger.withTag("QrCodeSensorUseCase")

        // Test available here: https://regex101.com/r/aLJ0o6/1
        private val fourSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        private val twoSensorRegex =
            "([0-9a-fA-F]{6})&([0-9a-fA-F]{6})".toRegex()

        // A Wicarlink/LYTPMS QR code only contains a single sensor's 6 hex character id, e.g. 2221C5
        private val wicarlinkSensorRegex =
            "^([0-9a-fA-F]{6})$".toRegex()

        private val printedIdRegex = "[0-9A-F]{6}".toRegex()
    }
}
