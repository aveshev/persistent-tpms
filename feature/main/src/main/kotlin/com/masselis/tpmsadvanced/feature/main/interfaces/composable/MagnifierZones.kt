package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlin.math.abs

/**
 * Splits the area around a vehicle between its locations, so that tapping anywhere near a tyre
 * magnifies its reading, see [Magnifier]. Made of where each location's tyre is centered
 * ([centers]) and where its outline is ([outlines]), in the same coordinates as the area.
 */
internal data class MagnifierZones(
    val centers: Map<Location, Offset>,
    val outlines: Map<Location, Rect>,
) {

    /**
     * The location a tap at [point] is about, null out of [area]. The area is split midway between
     * the axles, then midway between the tyres of an axle. The spare has what's under the lines
     * going from the area's bottom left corner, to the middle of the closest corners of its outline
     * and the rear left's, to the same between its outline and the rear right's, to the area's
     * bottom right corner.
     */
    fun locationAt(point: Offset, area: Rect): Location? = when {
        area.contains(point).not() -> null
        spareLine(area)?.let { point.y > it.yAt(point.x) } == true -> Location.Spare
        else -> centers
            .minus(Location.Spare)
            .entries
            // The tyres of an axle are centered at the same height, a vehicle without axles has
            // them all on one
            .groupBy { it.key.toAxleOrNull() }
            .values
            .minByOrNull { axle -> abs(axle.map { it.value.y }.average().toFloat() - point.y) }
            ?.minByOrNull { abs(it.value.x - point.x) }
            ?.key
    }

    /** From left to right, see [locationAt] */
    private fun spareLine(area: Rect): List<Offset>? = outlines[Location.Spare]?.let { spare ->
        listOf(REAR_LEFT, REAR_RIGHT)
            .mapNotNull { outlines[Location.Wheel(it)]?.let { outline -> spare.closestCornersMiddle(outline) } }
            .takeIf { it.size == 2 }
            ?.let { listOf(area.bottomLeft) + it + area.bottomRight }
    }

    private companion object {
        val Rect.corners get() = listOf(topLeft, topRight, bottomLeft, bottomRight)

        fun Rect.closestCornersMiddle(other: Rect): Offset = corners
            .flatMap { corner -> other.corners.map { corner to it } }
            .minBy { (a, b) -> (a - b).getDistanceSquared() }
            .let { (a, b) -> (a + b) / 2f }

        /** The height of this line, its points from left to right, at [x], level past its ends */
        fun List<Offset>.yAt(x: Float): Float = zipWithNext()
            .firstOrNull { (_, end) -> x <= end.x }
            ?.let { (start, end) ->
                if (end.x > start.x) start.y + (end.y - start.y) * ((x - start.x) / (end.x - start.x)).coerceIn(0f, 1f)
                else end.y
            }
            ?: last().y
    }
}
