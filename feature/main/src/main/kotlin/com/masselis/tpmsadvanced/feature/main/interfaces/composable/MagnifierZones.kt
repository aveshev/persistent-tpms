package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlin.math.abs

/**
 * Splits the area around a vehicle between its locations, so that tapping anywhere near a tyre
 * magnifies its reading, see [Magnifier]. Made of where each location's tyre is ([tyres]), where
 * its outline is ([outlines]), and what isn't the vehicle's to tap ([excluded], its top bar), in
 * the same coordinates as the area.
 */
internal data class MagnifierZones(
    val tyres: Map<Location, Rect>,
    val outlines: Map<Location, Rect>,
    val excluded: List<Rect> = emptyList(),
) {

    /**
     * The location a tap at [point] is about, null out of [area] or in what's [excluded]. The area
     * is split midway between the axles, then midway between the tyres of an axle. A spare has
     * what's under a line, see [spareLine], and with its readout beside the rear right one, what's
     * past the middle between them too.
     */
    fun locationAt(point: Offset, area: Rect): Location? = when {
        area.contains(point).not() || excluded.any { it.contains(point) } -> null
        spareBesideFrom()?.let { point.x >= it } == true -> Location.Spare
        spareLine(area)?.let { point.y > it.yAt(point.x) } == true -> Location.Spare
        else -> tyres
            .minus(Location.Spare)
            .entries
            // The tyres of an axle are centered at the same height, a vehicle without axles has
            // them all on one
            .groupBy { it.key.toAxleOrNull() }
            .values
            .minByOrNull { axle -> abs(axle.map { it.value.center.y }.average().toFloat() - point.y) }
            ?.minByOrNull { abs(it.value.center.x - point.x) }
            ?.key
    }

    /**
     * Where the spare's readout beside the rear right one starts having its taps, midway between
     * their outlines, null while it's under the spare
     */
    private fun spareBesideFrom(): Float? = outlines[Location.Spare]?.let { spare ->
        outlines[Location.Wheel(REAR_RIGHT)]
            ?.takeIf { it.isBeside(spare) }
            ?.let { (it.right + spare.left) / 2 }
    }

    /**
     * From left to right. Its middle goes through the middles of the closest corners of the
     * spare's box and the rear left outline, then the same with the rear right outline: the box is
     * its outline when its readout is under it, its tyre when the readout is beside the rear right
     * one. Under, its ends go to the area's bottom corners. Beside, the left one goes down at 45°
     * to the area's edge, the right one goes on level, the readout beside having what's past it.
     */
    @Suppress("NestedBlockDepth")
    private fun spareLine(area: Rect): List<Offset>? = outlines[Location.Spare]?.let { spareOutline ->
        val isBeside = outlines[Location.Wheel(REAR_RIGHT)]?.isBeside(spareOutline) == true
        (if (isBeside) tyres[Location.Spare] else spareOutline)?.let { spare ->
            listOf(REAR_LEFT, REAR_RIGHT)
                .mapNotNull { outlines[Location.Wheel(it)]?.let { outline -> spare.closestCornersMiddle(outline) } }
                .takeIf { it.size == 2 }
                ?.let { (left, right) ->
                    if (isBeside) listOf(Offset(area.left, left.y + left.x - area.left), left, right)
                    else listOf(area.bottomLeft, left, right, area.bottomRight)
                }
        }
    }

    private companion object {
        val Rect.corners get() = listOf(topLeft, topRight, bottomLeft, bottomRight)

        /** Side by side with [other], on its left, rather than over it */
        fun Rect.isBeside(other: Rect) = other.top < bottom && other.left >= right

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
