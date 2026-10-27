package com.masselis.tpmsadvanced.feature.qrcode.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR_WITH_SPARE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.SINGLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.Base64

/**
 * A vehicle's sensors, shown by one phone as a QR code for another one to assign them all at once,
 * see [uri] and [decode]. The code holds the [kind] and [name] of the vehicle they're on, and each
 * sensor's id, wheel, brand and calibration. The id is the one the sensor advertises, in clear: the
 * receiving phone needs it as is to recognise the sensor, a hash of it would be of no use.
 */
@Parcelize
internal data class SharedSensors(
    val kind: Vehicle.Kind,
    val name: String,
    val sensors: List<SharedSensor>,
) : Parcelable {

    init {
        require(sensors.isNotEmpty()) { "Nothing to share" }
        require(sensors.distinctBy { it.sensor.location }.size == sensors.size) { "Two sensors on a wheel: $sensors" }
        require(sensors.distinctBy { it.sensor.id }.size == sensors.size) { "A sensor shared twice: $sensors" }
    }

    /** The code's content, a URI so a QR code reader shows what it is, ~150 characters for 4 sensors */
    @IgnoredOnParcel
    val uri: String by lazy {
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).run {
                    writeByte(kinds.codeOf(kind))
                    name
                        .take(MAX_NAME_LENGTH)
                        .encodeToByteArray()
                        .also { writeByte(it.size) }
                        .also { write(it) }
                    writeByte(sensors.size)
                    sensors.forEach { shared ->
                        writeInt(shared.sensor.id)
                        writeByte(locations.codeOf(shared.sensor.location))
                        writeByte(brands.codeOf(shared.sensor.brand))
                        shared.calibration.also { (isEnabled, calibration) ->
                            // Most sensors aren't calibrated: their values are left out
                            writeByte((if (isEnabled) ENABLED else 0) or (if (calibration.adjusts) VALUES else 0))
                            if (calibration.adjusts) {
                                writeFloat(calibration.offset.kpa)
                                writeFloat(calibration.multiplier)
                            }
                        }
                    }
                }
            }
            .toByteArray()
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            .let { "$PREFIX$VERSION_KEY=$VERSION&$DATA_KEY=$it" }
    }

    /** A code shared by a newer version of the app, in a format this one doesn't know */
    class UnsupportedVersion(val version: String) : IllegalArgumentException("Unsupported version $version")

    companion object {
        private const val PREFIX = "tpmsadvanced://sensors?"
        private const val VERSION_KEY = "v"
        private const val DATA_KEY = "d"
        private const val VERSION = "1"

        // Its bytes fit the length's single byte
        private const val MAX_NAME_LENGTH = 40

        private const val ENABLED = 0b01
        private const val VALUES = 0b10

        // A code is its index in these: only ever append to them, a shared code must read the same
        // in every version of the app
        private val kinds = listOf(
            CAR,
            SINGLE_AXLE_TRAILER,
            MOTORCYCLE,
            TADPOLE_THREE_WHEELER,
            DELTA_THREE_WHEELER,
            CAR_WITH_SPARE,
            MONOWHEEL,
        )
        private val brands = listOf(PECHAM, BEKUBEE_KY, WICARLINK, BEKUBEE_TPMS, SYSGRATION)
        private val locations = listOf(
            Location.Wheel(FRONT_LEFT),
            Location.Wheel(FRONT_RIGHT),
            Location.Wheel(REAR_LEFT),
            Location.Wheel(REAR_RIGHT),
            Location.Axle(SensorLocation.Axle.FRONT),
            Location.Axle(SensorLocation.Axle.REAR),
            Location.Side(SensorLocation.Side.LEFT),
            Location.Side(SensorLocation.Side.RIGHT),
            Location.Spare,
            Location.Single,
        )

        /**
         * The sensors shared by [code], null for a code that isn't a shared one. Throws
         * [UnsupportedVersion] for a code from a newer app, [IllegalArgumentException] for a
         * damaged one.
         */
        fun decode(code: String): SharedSensors? = code
            .takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.split('&')
            ?.associate { it.substringBefore('=') to it.substringAfter('=', "") }
            ?.also { parameters ->
                requireNotNull(parameters[VERSION_KEY]) { "No version in $code" }
                    .takeIf { it == VERSION }
                    ?: throw UnsupportedVersion(parameters.getValue(VERSION_KEY))
            }
            ?.let { parameters -> requireNotNull(parameters[DATA_KEY]) { "No data in $code" } }
            ?.let { Base64.getUrlDecoder().decode(it) }
            ?.let { ByteBuffer.wrap(it) }
            ?.let { buffer ->
                try {
                    SharedSensors(
                        kind = kinds.of(buffer.get()),
                        name = ByteArray(buffer.get().toUByte().toInt())
                            .also { buffer.get(it) }
                            .decodeToString(),
                        sensors = List(buffer.get().toUByte().toInt()) {
                            SharedSensor(
                                Sensor(buffer.int, locations.of(buffer.get()), brands.of(buffer.get())),
                                buffer.get().toInt().let { flags ->
                                    SensorCalibration(
                                        isEnabled = flags and ENABLED != 0,
                                        calibration = if (flags and VALUES != 0)
                                            PressureCalibration(Pressure(buffer.float), buffer.float)
                                        else
                                            SensorCalibration.None.calibration,
                                    )
                                },
                            )
                        },
                    ).also { require(buffer.hasRemaining().not()) { "Trailing bytes in $code" } }
                } catch (exc: BufferUnderflowException) {
                    throw IllegalArgumentException("Truncated code $code", exc)
                }
            }

        private fun <T> List<T>.codeOf(value: T): Int = indexOf(value).also { check(it >= 0) { "No code for $value" } }

        private fun <T> List<T>.of(code: Byte): T =
            requireNotNull(getOrNull(code.toUByte().toInt())) { "Unknown code $code" }
    }
}

/** A shared sensor, with its [calibration] since it's the sensor which reads too high or too low */
@Parcelize
internal data class SharedSensor(
    val sensor: Sensor,
    private val isCalibrated: Boolean,
    private val offsetKpa: Float,
    private val multiplier: Float,
) : Parcelable {

    constructor(sensor: Sensor, calibration: SensorCalibration) : this(
        sensor,
        calibration.isEnabled,
        calibration.calibration.offset.kpa,
        calibration.calibration.multiplier,
    )

    val calibration: SensorCalibration
        get() = SensorCalibration(isCalibrated, PressureCalibration(Pressure(offsetKpa), multiplier))
}
