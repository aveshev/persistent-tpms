package com.masselis.tpmsadvanced.data.vehicle.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Axle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Side
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
public data class Vehicle(
    public val uuid: UUID,
    public val kind: Kind,
    public val name: String,
    public val lowPressure: Pressure,
    public val highPressure: Pressure,
    public val lowTemp: Temperature,
    public val highTemp: Temperature,
    // Kept while separateRearPressure is off, so turning it back on restores the previous values
    public val rearLowPressure: Pressure? = null,
    public val rearHighPressure: Pressure? = null,
    public val separateRearPressure: Boolean = false,
) : Parcelable {

    /**
     * Defines the layout of a vehicle.
     *
     * For each [Kind], the property [locations] represent the current layout with a [Set] of
     * [Location].
     *
     * A new vehicle starts with [defaultPressures], from its minimum to its maximum, for every tyre,
     * or for the front only when [defaultRearPressures] gives the rear its own. The minimum is a
     * typical recommended cold pressure and the maximum a typical sidewall maximum, each on the
     * strict side: an alert the user didn't need is better than one that never came.
     */
    public enum class Kind(
        public val locations: Set<Location>,
        public val defaultPressures: ClosedRange<Pressure>,
        public val defaultRearPressures: ClosedRange<Pressure>? = null,
    ) {
        /**
         * ```
         * N-N
         *  |
         * N-N
         * ```
         */
        CAR(
            setOf(
                Wheel(FRONT_LEFT),
                Wheel(FRONT_RIGHT),
                Wheel(REAR_LEFT),
                Wheel(REAR_RIGHT)
            ),
            2.3f.bar..3.2f.bar,
        ),

        /**
         * ```
         * N-N
         * ```
         */
        // Trailer tyres run near their sidewall maximum, from car tyres on a caravan to load range D
        SINGLE_AXLE_TRAILER(setOf(Side(LEFT), Side(RIGHT)), 2.8f.bar..4.5f.bar),

        /**
         * ```
         *  N
         *  |
         *  N
         * ```
         */
        MOTORCYCLE(
            setOf(Axle(FRONT), Axle(REAR)),
            2.5f.bar..3f.bar,
            2.9f.bar..3.4f.bar,
        ),

        /**
         * ```
         * N-N
         *  N
         * ```
         */
        TADPOLE_THREE_WHEELER(
            setOf(Wheel(FRONT_LEFT), Wheel(FRONT_RIGHT), Axle(REAR)),
            // Piaggio MP3's, from 1.6 / 2.0 bar for a 125 ridden alone to 2.0 / 2.4 for a 500, the
            // minimums at the 125's with a passenger
            1.8f.bar..2.5f.bar,
            2.4f.bar..3f.bar,
        ),

        /**
         * ```
         *  N
         * N-N
         * ```
         */
        DELTA_THREE_WHEELER(
            setOf(Axle(FRONT), Wheel(REAR_LEFT), Wheel(REAR_RIGHT)),
            // A motorcycle tyre ahead of two car tyres, like Harley-Davidson's Tri Glide's 36 psi
            // and 26 psi
            2.5f.bar..3f.bar,
            1.9f.bar..2.8f.bar,
        ),

        /**
         * ```
         * N-N
         *  |
         * N-N
         *  S
         * ```
         */
        CAR_WITH_SPARE(
            setOf(
                Wheel(FRONT_LEFT),
                Wheel(FRONT_RIGHT),
                Wheel(REAR_LEFT),
                Wheel(REAR_RIGHT),
                Location.Spare,
            ),
            2.3f.bar..3.2f.bar,
        ),

        /**
         * ```
         *  N
         * ```
         */
        MONOWHEEL(setOf(Location.Single), 2.8f.bar..3.5f.bar);

        public val defaultsToSeparateFrontRearPressure: Boolean
            get() = defaultRearPressures != null

        /**
         * `true` when this [Kind]'s [locations] distinguish a front axle from a rear one, i.e.
         * every kind except [SINGLE_AXLE_TRAILER] which only has [Location.Side] left/right
         * locations. Used to decide whether a separate front/rear pressure target makes sense.
         */
        public val hasFrontRearAxles: Boolean
            get() = locations.any { it.toAxleOrNull() != null }

        /**
         * Unlike [SensorLocation] which represents a location from the sensor standpoint, a
         * [Location] represents a location from a [Vehicle] standpoint.
         */
        public sealed interface Location : Parcelable {
            public fun toAxleOrNull(): Axle?

            @JvmInline
            @Parcelize
            public value class Wheel(public val location: SensorLocation) : Location {
                public fun toAxle(): Axle = Axle(location.axle)
                public fun toSide(): Side = Side(location.side)
                override fun toAxleOrNull(): Axle = toAxle()
            }

            @JvmInline
            @Parcelize
            public value class Axle(public val axle: SensorLocation.Axle) : Location {
                override fun toAxleOrNull(): Axle = this
            }

            @JvmInline
            @Parcelize
            public value class Side(public val side: SensorLocation.Side) : Location {
                override fun toAxleOrNull(): Axle? = null
            }

            /** A car's spare wheel, kept at the rear's pressure like a full-size spare */
            @Parcelize
            public data object Spare : Location {
                override fun toAxleOrNull(): Axle = Axle(SensorLocation.Axle.REAR)
            }

            /** The only wheel of a monowheel */
            @Parcelize
            public data object Single : Location {
                override fun toAxleOrNull(): Axle? = null
            }
        }
    }
}
