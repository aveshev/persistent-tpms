package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlin.math.roundToInt

/** Between what [ClearOfReadouts] places and what it keeps clear of, and the screen's edges */
private val CLEARANCE = 8.dp

/** The text sizes [ClearOfReadouts] tries in a room, largest first, as shares of the usual one */
@Suppress("MagicNumber")
private val TEXT_SCALES = listOf(1f, .95f, .9f, .85f, .8f, .75f, .7f, .65f, .6f)

/**
 * Where the current vehicle's readouts, image and middle are in the window, and its top bar in the
 * coordinates of the [ClearOfReadouts] laid out over it, see [VehicleWithTopBar]. Written as they're
 * laid out, read by [ClearOfReadouts]'s layout, which they lay out again.
 */
internal class VehicleBounds {
    val readouts = mutableStateMapOf<Location, Rect>()

    /** The outlines and frames drawn around locations, their title included, see [TyreTapArea] */
    val frames = mutableStateMapOf<Location, Rect>()
    var image by mutableStateOf<Rect?>(null)

    /** Between its axles, or in the middle of a single axle, where [CurrentVehicle]'s center is */
    var middle by mutableStateOf<Offset?>(null)
    var bar by mutableStateOf<List<Rect>>(emptyList())
}

/** The [VehicleBounds] the current vehicle reports to, none outside of [CurrentVehicle] */
internal val LocalVehicleBounds = staticCompositionLocalOf<VehicleBounds?> { null }

/** Reports where the readout of [location] is to the [VehicleBounds] around, if any */
@Composable
internal fun Modifier.reportsReadout(location: Location): Modifier = LocalVehicleBounds.current
    ?.let { bounds -> onGloballyPositioned { bounds.readouts[location] = it.boundsInWindow() } }
    ?: this

/**
 * Reports where the frame of [location] is, while [isDrawn], to the [VehicleBounds] around, if any,
 * with its title reaching [titleInset] pixels above it
 */
@Composable
internal fun Modifier.reportsFrame(location: Location, isDrawn: Boolean, titleInset: Int): Modifier =
    LocalVehicleBounds.current
        ?.let { bounds ->
            onGloballyPositioned {
                if (isDrawn) {
                    bounds.frames[location] = it
                        .boundsInWindow()
                        .let { frame -> frame.copy(top = frame.top - titleInset) }
                } else {
                    bounds.frames.remove(location)
                }
            }
        }
        ?: this

/** Reports where the vehicle's image is to the [VehicleBounds] around, if any */
@Composable
internal fun Modifier.reportsImage(): Modifier = LocalVehicleBounds.current
    ?.let { bounds -> onGloballyPositioned { bounds.image = it.boundsInWindow() } }
    ?: this

/**
 * Lays [content] out over the vehicle [bounds] tells of, as wide as its readings and image, or
 * as the room beside them, and as tall as its content wants at that width. Covering no reading,
 * whichever fits first:
 * - between the axles, centered on its middle
 * - in the room between the top bar and the vehicle, while the bar is over it (taller screens)
 * - in the room past the readings' end, under the bar's end corner, while the bar is beside it
 *   (wider screens), or the widest part of it a spare's readout leaves, as close to the window's
 *   vertical middle as it gets there. Its text is made smaller, down to [TEXT_SCALES]' smallest,
 *   rather than going to a narrower part, where it would wrap on more lines.
 * - between the axles still, raised to cover the front's lower lines rather than the rear's
 *   pressure, which matters the most while alerting
 *
 * Every text size is tried within the same layout: it shows at the size it fits, never shrinking.
 * The content's sizes in sp shrink with its text, see [Density.fontScale].
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition")
@Composable
internal fun ClearOfReadouts(
    bounds: VehicleBounds,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    SubcomposeLayout(modifier.onGloballyPositioned { origin = it.positionInWindow() }) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val clearance = CLEARANCE.toPx()
        // The content with its text this much smaller, composed once for each size tried
        val sizes = mutableMapOf<Float, List<Measurable>>()
        val scaled = { scale: Float ->
            sizes
                .getOrPut(scale) {
                    subcompose(scale) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(density.density, density.fontScale * scale),
                            content = content,
                        )
                    }
                }
                .firstOrNull()
        }
        val readouts = bounds.readouts.mapValues { (_, it) -> it.translate(-origin) }
        val frames = bounds.frames.values.map { it.translate(-origin) }
        val image = bounds.image?.translate(-origin)
        val middle = bounds.middle?.minus(origin)
        val full = scaled(1f)
        if (full == null || image == null || middle == null || readouts.isEmpty())
            return@SubcomposeLayout layout(width, height) {}
        // The spare's readout beside the rear right one isn't part of the readings' width
        val vehicle = readouts
            .filterKeys { it != Location.Spare }
            .values
            .fold(image, Rect::union)
        // The image isn't one: between the axles is over it, the rooms around it are out of it
        val obstacles = readouts.values + frames + bounds.bar
        val heightAt = { measurable: Measurable, w: Float -> measurable.minIntrinsicHeight(w.roundToInt()).toFloat() }
        val isClear = { rect: Rect -> obstacles.none { it.overlaps(rect) } }
        // Centered between the axles, as wide as the readings
        val between = heightAt(full, vehicle.width).let { h ->
            Rect(vehicle.left, middle.y - h / 2, vehicle.right, middle.y + h / 2)
        }
        val isBarOver = bounds.bar.any { it.width >= width - 1 }
        // Past the readings' end, under the bar's end corner
        val beside = Rect(
            left = vehicle.right + clearance,
            top = bounds.bar.maxByOrNull { it.right }?.bottom ?: 0f,
            right = width - clearance,
            bottom = height - clearance,
        )
        // What's there, like a spare's readout beside the rear right one
        val besideObstacles = obstacles.filter { it.overlaps(beside) }
        // Alone beside the vehicle, it's as high as the window's middle, as far as its room lets
        // it: the vehicle is centered on the window's height too, not on what the bar leaves under
        // it. Beside a spare's readout, it's centered in what that leaves.
        val aimY = (windowHeight / 2f - origin.y).takeIf { isBarOver.not() && besideObstacles.isEmpty() }
        val inRoom = if (isBarOver) {
            // Between the bar and the vehicle, as wide as the readings
            Rect(
                left = vehicle.left,
                // The bar's height leaves room around what's in it already
                top = bounds.bar.maxOfOrNull { it.bottom } ?: 0f,
                right = vehicle.right,
                bottom = readouts.values.fold(image, Rect::union).top - clearance,
            ).let(::listOf)
        } else {
            // Beside, or the parts what's there leaves
            besideObstacles
                .flatMap { obstacle ->
                    listOf(
                        beside.copy(bottom = obstacle.top - clearance),
                        beside.copy(top = obstacle.bottom + clearance),
                        beside.copy(left = obstacle.right + clearance),
                    )
                }
                .ifEmpty { listOf(beside) }
        }
            .filter { it.width > 0 && it.height > 0 && isClear(it) }
            .sortedByDescending { it.width }
            // The widest room it fits in, at the largest size it fits there
            .firstNotNullOfOrNull { rect ->
                TEXT_SCALES.firstNotNullOfOrNull { scale ->
                    scaled(scale)?.let { measurable ->
                        heightAt(measurable, rect.width)
                            .takeIf { it <= rect.height }
                            ?.let { h ->
                                (aimY?.coerceIn(rect.top + h / 2, rect.bottom - h / 2) ?: rect.center.y)
                                    .let { y -> measurable to Rect(rect.left, y - h / 2, rect.right, y + h / 2) }
                            }
                    }
                }
            }
        val (measurable, placed) = between.takeIf(isClear)?.let { full to it }
            ?: inRoom
            // Raised over the front's lower lines, its bottom off the rear's readouts
            ?: readouts.values
                .filter { it.top > middle.y }
                .minOfOrNull { it.top }
                ?.let { rearTop ->
                    full to between.translate(0f, (rearTop - clearance - between.bottom).coerceAtMost(0f))
                }
            ?: (full to between)
        val placeable = measurable.measure(
            Constraints.fixed(placed.width.roundToInt(), placed.height.roundToInt())
        )
        layout(width, height) {
            placeable.place(placed.left.roundToInt(), placed.top.roundToInt())
        }
    }
}

/** The smallest [Rect] around both */
private fun Rect.union(other: Rect): Rect =
    Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
