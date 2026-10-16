package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Text is never shrunk below this share of the size it's given, see [FitsText]'s textScale */
private const val MIN_TEXT_SCALE = .5f

/** The search for the text's scale stops once it's known to this much */
private const val SEARCH_PRECISION = .02f

/** Taken off each value predicted, so it's likelier to fit than to need another try */
private const val PREDICTION_MARGIN = .01f

/** A gap changing less than this between two layouts didn't change with them, it's rounding */
private const val MIN_GAP_CHANGE_PX = 2f

/** Layouts tried at most, past the first, if the predictions don't settle */
private const val MAX_TRIES = 6

/** A readout still loading doesn't keep its vehicle hidden longer than this, see [HoldTextFit] */
private val MAX_HOLD = 1.seconds

/** Readouts closer than this many of their pressure lines get a line between them, see [TextFit.separators] */
private const val SEPARATED_LINES = 2

private val SEPARATOR_WIDTH = 1.dp

/** The readouts' font's capitals' height, as a share of its size: Roboto's 1456 units per 2048 */
private const val CAP_HEIGHT = 1456f / 2048

/** Rounding to pixels makes neighbors touch by a pixel, which isn't an overlap */
private const val OVERLAP_TOLERANCE_PX = 1f

/**
 * Shrinks the text of [content]'s readouts and tap areas, see [FittedText], until those it reports,
 * see [fitsText], neither overlap another location's nor go past this box or the window, from
 * [textScale] times the system's size, the readouts' text size setting, down to [MIN_TEXT_SCALE] of
 * that. What's sized from that text, like the image taking the
 * room its readouts leave, is sized from the text tried, see [rememberFittedTextDensity].
 *
 * [content] is laid out but not drawn until that size is found, so it's first seen as it ends up:
 * each layout tried predicts the next from how far its parts overlap or keep apart, see
 * [TextFit.search].
 * Nothing's tried while a readout still loads, see [HoldTextFit]. Only that text shrinks: what's
 * sized in dp keeps its size, the readouts' slots, measured from their text, shrink with it, and the
 * rest, like dialogs, keeps the system's size.
 *
 * Once shown, the text only shrinks, it isn't grown back until one of [restartOn], [textScale] or
 * the density changes, so it never goes back and forth between two sizes. Whatever's added to a readout then,
 * like an alert's line, can shrink it more.
 */
@Composable
internal fun FitsText(
    vararg restartOn: Any?,
    modifier: Modifier = Modifier,
    textScale: Float = 1f,
    /** Draws a line between a readout and the one under it when they're close, see [TextFit.separators] */
    separates: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // A readout's pressure line, in the text's style, see TyreStat
    val textStyle = LocalTextStyle.current
    val separatorColor = MaterialTheme.colorScheme.outlineVariant
    val windowSize = LocalWindowInfo.current.containerSize
    val fit = remember(density, textScale, *restartOn) { TextFit(textScale) }
    LaunchedEffect(fit, windowSize) {
        val window = Rect(Offset.Zero, windowSize.toSize())
        snapshotFlow { fit.box }.first { it != null }
        withTimeoutOrNull(MAX_HOLD) { snapshotFlow { fit.holds.isEmpty() }.first { it } }
        // What's no longer held is laid out again in the frame after, maybe not yet
        repeat(2) { withFrameNanos { } }
        fit.search(window)
        snapshotFlow { fit.reports }
            .collect { if (fit.isOverflowing(window)) fit.search(window) }
    }
    CompositionLocalProvider(LocalTextFit provides fit) {
        Box(
            modifier
                .onGloballyPositioned {
                    fit.box = it
                    fit.reports++
                }
                // Measured again once what's in it is composed with what's tried, so it's told once
                // that's placed: a layout can run before what it lays out is composed again
                .layout { measurable, constraints ->
                    val composed = fit.composed
                    measurable.measure(constraints).run {
                        layout(width, height) {
                            place(0, 0)
                            fit.laidOut = composed
                        }
                    }
                }
                .graphicsLayer { alpha = if (fit.isShown) 1f else 0f }
                .run {
                    if (separates.not()) this
                    else drawWithContent {
                        drawContent()
                        // Read to draw them again as the readouts move
                        fit.reports
                        Density(density.density, density.fontScale * fit.scale)
                            .run {
                                textStyle.lineHeight.takeIf { it.isSp }?.toPx()?.let { lineHeight ->
                                    textStyle.fontSize.takeIf { it.isSp }?.toPx()?.let { fontSize ->
                                        fit.separators(lineHeight * SEPARATED_LINES, fontSize * CAP_HEIGHT)
                                    }
                                }
                            }
                            ?.forEach { (start, end) -> drawLine(separatorColor, start, end, SEPARATOR_WIDTH.toPx()) }
                    }
                }
        ) {
            val composing = fit.scale
            SideEffect { fit.composed = composing }
            content()
        }
    }
}

/** What [FitsText] finds out from the layout, and what it settles on, from its [initial] scale */
private class TextFit(initial: Float) {
    /** The text's, see [FittedText] */
    var scale by mutableFloatStateOf(initial)
        private set

    /** Below which [scale] never goes, see [MIN_TEXT_SCALE] */
    private val floor = initial * MIN_TEXT_SCALE

    /** The [scale] [FitsText]'s content was last composed with */
    var composed by mutableStateOf<Float?>(null)

    /** The [scale] [FitsText]'s content was last composed, then placed, with */
    var laidOut by mutableStateOf<Float?>(null)

    /** [FitsText]'s box */
    var box by mutableStateOf<LayoutCoordinates?>(null)

    /**
     * Each location's readout and tap area, see [fitsText]. Where they are is read when it's
     * needed, from the layout itself rather than as last told, which a layout can skip telling.
     */
    val parts = mutableStateMapOf<Pair<Location, TextFitPart>, LayoutCoordinates>()

    /** Counts the times the box or a part was placed, which may have changed what fits */
    var reports by mutableIntStateOf(0)

    /** What's loading, see [HoldTextFit] */
    val holds = mutableStateListOf<Any>()

    /** Once the first [search] is done: until then, what's tried isn't drawn */
    var isShown by mutableStateOf(false)
        private set

    /**
     * How far apart each part is from the others and from the box, or past the [window]'s size from
     * it, negative when they overlap or it's past, along with the size along that axis of what's
     * measured, by what it's between. From the box, not where it is in the window, which moves
     * while it slides in.
     */
    private fun gaps(window: Rect): Map<Any, Gap> {
        val box = box?.takeIf { it.isAttached }?.unclippedBoundsInWindow() ?: return emptyMap()
        val limits = Size(box.width.coerceAtMost(window.width), box.height.coerceAtMost(window.height))
            .let { Rect(Offset.Zero, it) }
        val parts = parts.entries
            .filter { (_, coordinates) -> coordinates.isAttached }
            .map { (key, coordinates) ->
                key to coordinates.unclippedBoundsInWindow().translate(-box.topLeft).deflate(OVERLAP_TOLERANCE_PX)
            }
        return parts.flatMap { (key, rect) ->
            listOf(
                (key to Edge.LEFT) to Gap(rect.left - limits.left, rect.width),
                (key to Edge.RIGHT) to Gap(limits.right - rect.right, rect.width),
                (key to Edge.TOP) to Gap(rect.top - limits.top, rect.height),
                (key to Edge.BOTTOM) to Gap(limits.bottom - rect.bottom, rect.height),
            )
        }
            .plus(
                parts.flatMapIndexed { index, (key, rect) ->
                    parts.drop(index + 1)
                        .filter { (other, _) -> other.first != key.first }
                        .map { (other, otherRect) ->
                            // Apart along the axis they're furthest apart, or the least deep in each other
                            val apartX = maxOf(otherRect.left - rect.right, rect.left - otherRect.right)
                            val apartY = maxOf(otherRect.top - rect.bottom, rect.top - otherRect.bottom)
                            (key to other) to
                                if (apartX >= apartY) Gap(apartX, rect.width + otherRect.width)
                                else Gap(apartY, rect.height + otherRect.height)
                        }
                }
            )
            .toMap()
    }

    /**
     * A line between each readout and the next one under it, across both, when they share some
     * width and are less than [maxGap] apart: close enough to be read as one, otherwise. Midway
     * between what's seen of them, the upper one's last baseline and the top of the lower one's
     * capitals, [capHeight] above its first baseline: their lines' leading differs.
     */
    fun separators(maxGap: Float, capHeight: Float): List<Pair<Offset, Offset>> {
        val box = box?.takeIf { it.isAttached } ?: return emptyList()
        val origin = box.positionInWindow()
        val readouts = parts
            .filterKeys { (_, part) -> part == TextFitPart.READOUT }
            .values
            .filter { it.isAttached }
            .map { coordinates ->
                val bounds = coordinates.unclippedBoundsInWindow().translate(-origin)
                Readout(
                    bounds,
                    inkTop = coordinates[FirstBaseline]
                        .takeIf { it != AlignmentLine.Unspecified }
                        ?.let { bounds.top + it - capHeight }
                        ?: bounds.top,
                    inkBottom = coordinates[LastBaseline]
                        .takeIf { it != AlignmentLine.Unspecified }
                        ?.let { bounds.top + it }
                        ?: bounds.bottom,
                )
            }
        return readouts.mapNotNull { (upper, _, upperInk) ->
            readouts
                // Touching it counts, as fitted readouts can, see OVERLAP_TOLERANCE_PX
                .filter { it.bounds.top >= upper.bottom - OVERLAP_TOLERANCE_PX * 2 }
                .filter { minOf(it.bounds.right, upper.right) > maxOf(it.bounds.left, upper.left) }
                .minByOrNull { it.bounds.top }
                ?.takeIf { it.bounds.top - upper.bottom < maxGap }
                ?.let { lower ->
                    val y = (upperInk + lower.inkTop) / 2
                    Offset(minOf(upper.left, lower.bounds.left), y) to Offset(maxOf(upper.right, lower.bounds.right), y)
                }
        }
    }

    fun isOverflowing(window: Rect): Boolean = gaps(window).values.any { it.distance < 0 }

    /**
     * The largest [scale] up to the current one which fits, laid out before it's shown. Tries the
     * one predicted from the layouts tried, see [Tried.predicted], when it's between those known to
     * fit and to overflow, and halfway between them otherwise.
     */
    suspend fun search(window: Rect) {
        awaitLayout()
        var tried = Tried(scale, gaps(window))
        var previous: Tried? = null
        // The largest scale known to fit, or the floor, which isn't known to until it's tried
        var fits = scale.takeIf { tried.fits } ?: floor
        var isFloorTried = false
        // The smallest known not to
        var overflows = scale
        var tries = 0
        while (overflows - fits > SEARCH_PRECISION && tries++ < MAX_TRIES) {
            scale = tried.predicted(previous)
                .coerceAtLeast(floor)
                .takeIf { it < overflows && (it > fits || isFloorTried.not()) }
                ?: ((fits + overflows) / 2)
            isFloorTried = isFloorTried || scale == floor
            awaitLayout()
            previous = tried
            tried = Tried(scale, gaps(window))
            when {
                tried.fits.not() -> overflows = scale
                else -> {
                    fits = scale
                    // Close enough to where it would overflow
                    if (tried.predicted(previous) - scale < SEARCH_PRECISION) break
                }
            }
        }
        scale = fits
        awaitLayout()
        isShown = true
    }

    /** Until what's in [FitsText] is composed, then placed, with the [scale] just set */
    private suspend fun awaitLayout() = snapshotFlow { laidOut }.first { it == scale }
}

/** A layout [TextFit.search] tried: the text's scale it was tried with, [value], and its [gaps] */
private class Tried(val value: Float, val gaps: Map<Any, Gap>) {
    val fits = gaps.values.none { it.distance < 0 }

    /**
     * The value at which the first gap would close, less [PREDICTION_MARGIN] to be likelier to fit
     * than to need another try. From how each gap changed since the [previous] layout, as if it
     * kept changing so: a gap which didn't change with the value doesn't close by changing it.
     * Without one, from how deep the parts are in each other, as if what's measured shrank in
     * proportion to the value, though only part of it does.
     */
    fun predicted(previous: Tried?): Float = gaps
        .mapNotNull { (key, gap) ->
            previous
                ?.gaps
                ?.get(key)
                ?.let { before ->
                    (gap.distance - before.distance)
                        .takeIf { it.absoluteValue >= MIN_GAP_CHANGE_PX }
                        ?.div(value - previous.value)
                        ?.takeIf { it < 0 }
                        ?.let { slope -> value - gap.distance / slope }
                }
                ?: (value * (1 + gap.distance / gap.size.coerceAtLeast(1f)))
                    .takeIf { previous == null && gap.distance < 0 }
        }
        .minOrNull()
        ?.minus(PREDICTION_MARGIN)
        ?: Float.POSITIVE_INFINITY
}

/** A readout's [bounds], and where its text is seen from and to, see [TextFit.separators] */
private data class Readout(val bounds: Rect, val inkTop: Float, val inkBottom: Float)

/** How far apart two parts are, or a part from an edge, and the size along it of what's measured */
private data class Gap(val distance: Float, val size: Float)

private enum class Edge { LEFT, RIGHT, TOP, BOTTOM }

/** What [fitsText] reports of a location */
internal enum class TextFitPart {
    READOUT,
    TAP_AREA,

    /** A line drawn from it, which another location's readout mustn't cross */
    LINE,
}

private val LocalTextFit = staticCompositionLocalOf<TextFit?> { null }

/** Tells the [FitsText] around it, if any, where this [part] of [location] is */
@Composable
internal fun Modifier.fitsText(location: Location, part: TextFitPart): Modifier = LocalTextFit.current
    ?.let { fit ->
        DisposableEffect(fit, location, part) { onDispose { fit.parts.remove(location to part) } }
        onGloballyPositioned {
            fit.parts[location to part] = it
            fit.reports++
        }
    }
    ?: this

/**
 * Keeps the [FitsText] around it, if any, from fitting the text while [isHolding], up to
 * [MAX_HOLD]: what's shown is about to change, like a readout's loading reading
 */
@Composable
internal fun HoldTextFit(isHolding: Boolean) {
    val fit = LocalTextFit.current ?: return
    val hold = remember { Any() }
    DisposableEffect(fit, isHolding) {
        if (isHolding) fit.holds += hold
        onDispose { fit.holds -= hold }
    }
}

/** The density [content]'s text is drawn with, shrunk by the [FitsText] around it, if any */
@Composable
internal fun FittedText(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalDensity provides rememberFittedTextDensity(), content = content)

/** The density text is measured with, shrunk by the [FitsText] around it, if any, see [FittedText] */
@Composable
internal fun rememberFittedTextDensity(): Density {
    val density = LocalDensity.current
    val scale = LocalTextFit.current?.scale ?: 1f
    // Only as a font scale: text is laid out from its node's density and font scale, not from
    // this object, so converting sp any other way here wouldn't match what's drawn
    return remember(density, scale) {
        density.takeIf { scale == 1f } ?: Density(density.density, density.fontScale * scale)
    }
}

/** Unlike `boundsInWindow()`, not cut by its parents or the window: what's past them is what's looked for */
private fun LayoutCoordinates.unclippedBoundsInWindow(): Rect = Rect(positionInWindow(), size.toSize())
