package com.masselis.tpmsadvanced.feature.qrcode.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
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
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class QrCodeSensor(
    val id: Int,
    val wheel: Vehicle.Kind.Location.Wheel
) : Parcelable

@Suppress("MagicNumber", "MaxLineLength")
internal sealed interface QrCodeSensors : Set<QrCodeSensor>, Parcelable {

    @Parcelize
    @JvmInline
    value class FourWheel private constructor(
        private val set: Set<QrCodeSensor>
    ) : QrCodeSensors, Set<QrCodeSensor> by set {
        constructor(
            first: QrCodeSensor,
            second: QrCodeSensor,
            third: QrCodeSensor,
            fourth: QrCodeSensor
        ) : this(setOf(first, second, third, fourth)) {
            // From the arguments: the set keeps one of 2 identical sensors, hiding that one is repeated
            listOf(first, second, third, fourth).also { sensors ->
                require(sensors.distinctBy { it.wheel }.size == 4) { throw DuplicateWheelLocation(sensors.map { it.wheel }) }
                require(sensors.distinctBy { it.id }.size == 4) { throw DuplicateId(sensors.map { it.id }) }
            }
        }
    }

    @Parcelize
    @JvmInline
    value class TwoWheel private constructor(
        private val set: Set<QrCodeSensor>
    ) : QrCodeSensors, Set<QrCodeSensor> by set {
        constructor(first: QrCodeSensor, second: QrCodeSensor) : this(setOf(first, second)) {
            // From the arguments: the set keeps one of 2 identical sensors, hiding that one is repeated
            listOf(first, second).also { sensors ->
                require(sensors.distinctBy { it.wheel }.size == 2) { throw DuplicateWheelLocation(sensors.map { it.wheel }) }
                require(sensors.distinctBy { it.id }.size == 2) { throw DuplicateId(sensors.map { it.id }) }
            }
        }
    }

    data class DuplicateWheelLocation(
        val wheels: Collection<Vehicle.Kind.Location.Wheel>
    ) : IllegalArgumentException()

    data class DuplicateId(
        val ids: Collection<Int>
    ) : IllegalArgumentException()
}

/**
 * The code's sensors on a vehicle of this kind, each at the wheel its own kit puts it, or null when
 * the vehicle has fewer wheels than the code has sensors: the user assigns them via Bluetooth then.
 * A car with a spare keeps the spare's, the kits don't have one.
 */
internal fun QrCodeSensors.sensorsFor(kind: Vehicle.Kind): List<Sensor>? = this
    .takeIf { kind.locations.size >= size }
    ?.map {
        Sensor(
            it.id,
            when (kind) {
                CAR, CAR_WITH_SPARE -> it.wheel

                SINGLE_AXLE_TRAILER -> it.wheel.toSide()

                MOTORCYCLE -> it.wheel.toAxle()

                TADPOLE_THREE_WHEELER -> when (it.wheel.location) {
                    FRONT_LEFT, FRONT_RIGHT -> it.wheel
                    REAR_LEFT, REAR_RIGHT -> it.wheel.toAxle()
                }

                DELTA_THREE_WHEELER -> when (it.wheel.location) {
                    FRONT_LEFT, FRONT_RIGHT -> it.wheel.toAxle()
                    REAR_LEFT, REAR_RIGHT -> it.wheel
                }

                // A code has 2 sensors at least, more than its single wheel
                MONOWHEEL -> Location.Single
            },
            // Only Sysgration sensors come with a QR code
            SYSGRATION,
        )
    }
