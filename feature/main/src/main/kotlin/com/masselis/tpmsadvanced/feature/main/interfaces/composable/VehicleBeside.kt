package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.isWideWindow
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlin.math.roundToInt

/**
 * In a wide window, see [isWideWindow], the vehicle needs all the height: what the top bar would
 * show goes to its side. [side] then the [vehicle], the room left split evenly: as much from the
 * window's left edge to [side], from [side] to the vehicle's [outlines], and from those to the
 * window's right edge, at least [MIN_SIDE_MARGIN]. [side] is as wide as it asks up to that, and
 * centered on the whole height like the vehicle. Until the outlines are placed, both are at the
 * left edge.
 */
@Composable
internal fun VehicleBeside(
    /** Where each location's outline is in the window, see [TyreTaps.onOutlinePositioned] */
    outlines: Map<Location, Rect>,
    side: @Composable () -> Unit,
    vehicle: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where the vehicle's box is in the window: its outlines' span is told from it, so it doesn't
    // change as the box moves
    var vehicleX by remember { mutableFloatStateOf(0f) }
    Layout(
        contents = listOf(
            side,
            {
                vehicle(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { vehicleX = it.positionInWindow().x }
                )
            },
        ),
        modifier = modifier,
    ) { (sides, vehicles), constraints ->
        val span = outlines.spanFrom(vehicleX)
        val minMargin = MIN_SIDE_MARGIN.toPx()
        val sidePlaceables = sides.map { measurable ->
            measurable.measure(
                Constraints(
                    maxWidth = span
                        ?.let { (left, right) -> constraints.maxWidth - (right - left) - minMargin * 3 }
                        ?.roundToInt()
                        ?.coerceAtLeast(0)
                        ?: constraints.maxWidth,
                    maxHeight = constraints.maxHeight,
                )
            )
        }
        val sideWidth = sidePlaceables.maxOfOrNull { it.width } ?: 0
        val vehiclePlaceables = vehicles.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val (sideX, vehicleShift) = span
                ?.let { (left, right) ->
                    ((constraints.maxWidth - sideWidth - (right - left)) / 3)
                        .coerceAtLeast(0f)
                        .let { margin -> margin to margin * 2 + sideWidth - left }
                }
                ?: (0f to 0f)
            vehiclePlaceables.forEach { it.place(vehicleShift.roundToInt(), 0) }
            sidePlaceables.forEach { it.place(sideX.roundToInt(), (constraints.maxHeight - it.height) / 2) }
        }
    }
}

/**
 * In a wide window, see [isWideWindow], the vehicle needs all the height: the top bar's
 * [navigationIcon] and [actions] stay in the top corners, its [title] at the top too, centered in
 * the room between the [navigationIcon] and the vehicle's [outlines]. The vehicle is centered in
 * the window, moved left only as much as the [actions] need room beside it. The [title] is as wide
 * as it asks up to that room, [MIN_SIDE_MARGIN] off the vehicle, and shown once the outlines are
 * placed.
 */
@Composable
internal fun VehicleBetweenCorners(
    /** Where each location's outline is in the window, see [TyreTaps.onOutlinePositioned] */
    outlines: Map<Location, Rect>,
    navigationIcon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    vehicle: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where the vehicle's box is in the window, see VehicleBeside
    var vehicleX by remember { mutableFloatStateOf(0f) }
    Layout(
        contents = listOf(
            navigationIcon,
            title,
            actions,
            {
                vehicle(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { vehicleX = it.positionInWindow().x }
                )
            },
        ),
        modifier = modifier,
    ) { (navigationIcons, titles, actionses, vehicles), constraints ->
        val corner = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val margin = MIN_SIDE_MARGIN.toPx()
        val navigationIconPlaceables = navigationIcons.map { it.measure(corner) }
        val navigationIconWidth = navigationIconPlaceables.maxOfOrNull { it.width } ?: 0
        val actionPlaceables = actionses.map { it.measure(corner) }
        val actionsWidth = actionPlaceables.maxOfOrNull { it.width } ?: 0
        val vehiclePlaceables = vehicles.map { it.measure(constraints) }
        // The vehicle draws itself centered in its box, which only moves for the actions: its
        // outlines don't move with what they tell
        val span = outlines.spanFrom(vehicleX)
        val vehicleShift = span
            ?.let { (_, right) -> (constraints.maxWidth - actionsWidth - margin - right).coerceAtMost(0f) }
            ?: 0f
        // Between the navigation icon and the vehicle
        val titleRoom = span?.let { (left, _) -> navigationIconWidth.toFloat() to vehicleShift + left - margin }
        val titlePlaceables = titleRoom
            ?.let { (from, to) -> corner.copy(maxWidth = (to - from).roundToInt().coerceAtLeast(0)) }
            ?.let { titleConstraints -> titles.map { it.measure(titleConstraints) } }
            .orEmpty()
        layout(constraints.maxWidth, constraints.maxHeight) {
            vehiclePlaceables.forEach { it.place(vehicleShift.roundToInt(), 0) }
            navigationIconPlaceables.forEach { it.place(0, 0) }
            titleRoom?.also { (from, to) ->
                titlePlaceables.forEach { it.place((from + (to - from - it.width) / 2).roundToInt(), 0) }
            }
            actionPlaceables.forEach { it.place(constraints.maxWidth - it.width, 0) }
        }
    }
}

/** From the left of the outlines to their right, relative to [x], none until they're placed */
private fun Map<Location, Rect>.spanFrom(x: Float): Pair<Float, Float>? = values
    .takeIf { it.isNotEmpty() }
    ?.let { all -> all.minOf { it.left } - x to all.maxOf { it.right } - x }

private val MIN_SIDE_MARGIN = 8.dp

/** Material's small top app bar, where a page beside its vehicle keeps its navigation icon */
internal val TOP_BAR_HEIGHT = 64.dp
