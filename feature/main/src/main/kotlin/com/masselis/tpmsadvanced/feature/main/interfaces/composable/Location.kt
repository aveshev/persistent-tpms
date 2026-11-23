package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import java.util.Locale
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized

/** Every location is named as a wheel, whether it's one of an axle's two, an axle's only one or a side's */
public fun StringBuilder.appendLoc(
    location: Vehicle.Kind.Location,
    withType: Boolean = true,
    capitalized: Boolean = false,
): StringBuilder = when (location) {
    is Vehicle.Kind.Location.Wheel -> {
        append(
            when (location.location) {
                SensorLocation.FRONT_LEFT -> localized(CommonR.string.front_left).capitalizeIf(capitalized)
                SensorLocation.FRONT_RIGHT -> localized(CommonR.string.front_right).capitalizeIf(capitalized)
                SensorLocation.REAR_LEFT -> localized(CommonR.string.rear_left).capitalizeIf(capitalized)
                SensorLocation.REAR_RIGHT -> localized(CommonR.string.rear_right).capitalizeIf(capitalized)
            }
        )
        appendIf(withType, localized(CommonR.string.loc_wheel_suffix))
    }

    is Vehicle.Kind.Location.Axle -> {
        append(
            when (location.axle) {
                SensorLocation.Axle.FRONT -> localized(CommonR.string.loc_front).capitalizeIf(capitalized)
                SensorLocation.Axle.REAR -> localized(CommonR.string.loc_rear).capitalizeIf(capitalized)
            }
        )
        appendIf(withType, localized(CommonR.string.loc_wheel_suffix))
    }

    is Vehicle.Kind.Location.Side -> {
        append(
            when (location.side) {
                SensorLocation.Side.LEFT -> localized(CommonR.string.loc_left).capitalizeIf(capitalized)
                SensorLocation.Side.RIGHT -> localized(CommonR.string.loc_right).capitalizeIf(capitalized)
            }
        )
        appendIf(withType, localized(CommonR.string.loc_wheel_suffix))
    }

    Vehicle.Kind.Location.Spare -> {
        append(localized(CommonR.string.loc_spare).capitalizeIf(capitalized))
        appendIf(withType, localized(CommonR.string.loc_wheel_suffix))
    }

    // Named after what it is, there's nothing to tell it apart from
    Vehicle.Kind.Location.Single -> append(localized(CommonR.string.loc_wheel).capitalizeIf(capitalized))
}

private fun StringBuilder.appendIf(condition: Boolean, string: String) =
    if (condition) append(string) else this

private fun String.capitalizeIf(condition: Boolean) =
    if (condition) replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault())
        else it.toString()
    }
    else
        this
