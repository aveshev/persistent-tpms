package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreActionsViewModel.State
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreActionsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import com.masselis.tpmsadvanced.core.ui.viewModel as keyedViewModel

/** Where taps magnify, away from the edges of the vehicle's area */
private val TAPS_PADDING = 16.dp

/**
 * Lays [content] out, a vehicle, over taps magnifying the reading of the location they're near,
 * see [MagnifierZones], for as long as the settings say. [content] tells where its tyres and
 * outlines are through the callbacks it's given, see [TyreTaps]. What the vehicle can be tapped
 * for itself, like assigning a sensor or its center's buttons, keeps its taps.
 */
@Composable
internal fun Magnifiable(
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    settings: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
    content: @Composable (
        onTyrePositioned: (Location, Offset) -> Unit,
        onOutlinePositioned: (Location, Rect) -> Unit,
    ) -> Unit,
) {
    val seconds by settings.magnifierSeconds.collectAsState()
    // Only read once tapped: written on every layout, they don't recompose anything
    val centers = remember(vehicleComponent) { mutableMapOf<Location, Offset>() }
    val outlines = remember(vehicleComponent) { mutableMapOf<Location, Rect>() }
    var area by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var magnified by remember(vehicleComponent) { mutableStateOf<Location?>(null) }
    // A location without a sensor has nothing to magnify, tapping its outline assigns it one
    val magnifiable = vehicleComponent.vehicle.kind.locations.filter { location ->
        vehicleComponent
            .TyreComponent(location)
            .let { keyedViewModel(it.keyed()) { it.TyreActionsViewModel() } }
            .stateFlow
            .collectAsState()
            .value !is State.Unassigned
    }
    Box(modifier) {
        if (seconds > 0) Box(
            Modifier
                .matchParentSize()
                .padding(TAPS_PADDING)
                .onGloballyPositioned { area = it }
                .pointerInput(magnifiable) {
                    detectTapGestures { tap ->
                        area
                            ?.let { area ->
                                MagnifierZones(centers.toMap(), outlines.toMap())
                                    .locationAt(area.localToWindow(tap), area.boundsInWindow())
                            }
                            ?.takeIf { it in magnifiable }
                            ?.also { magnified = it }
                    }
                }
                .testTag(MagnifierTags.taps)
        )
        content(
            remember(centers) { { location, center -> centers[location] = center } },
            remember(outlines) { { location, outline -> outlines[location] = outline } },
        )
        magnified?.also { location ->
            LaunchedEffect(location) {
                delay(seconds.seconds)
                magnified = null
            }
            Magnifier(location, seconds.seconds, onDismissRequest = { magnified = null })
        }
    }
}

/**
 * The reading of [location] in a card over the dimmed screen, as large as it fits, closing after
 * [duration]. Tapping anywhere, the card too, or back closes it: there's no aiming on a shaking
 * phone. A dialog, which dims the top bar and the system bars too.
 */
@Suppress("LongMethod")
@Composable
private fun Magnifier(
    location: Location,
    duration: Duration,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
) {
    val pressureUnit by vehicleComponent
        .keyedViewModel(vehicleComponent.key()) { it.VehicleSettingsViewModel() }
        .pressureUnit
        .collectAsState()
    // As wide as the widest reading, its size doesn't change with what it reads
    val width = rememberWidestReadoutWidth(pressureUnit, isOutlined = false)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // As light as a Material 3 dialog's scrim, the platform's is darker
        LocalView.current.also { view ->
            SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(SCRIM_ALPHA) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier.testTag(MagnifierTags.root(location)),
            ) {
                // Its widest position sizes the title, which keeps its size from one location to another
                val measurer = rememberTextMeasurer()
                val titleStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
                val widestTitle = remember(measurer, titleStyle, vehicleComponent) {
                    vehicleComponent.vehicle.kind.locations.maxOf { other ->
                        measurer
                            .measure(buildString { appendLoc(other, withType = false, capitalized = true) }, titleStyle)
                            .size
                            .width
                    }
                }
                // Everything scaled from its size at the pressure's font size, see the steps below
                Layout(
                    content = {
                        Text(
                            buildString { appendLoc(location, withType = false, capitalized = true) },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                        CloseButton(duration, onDismissRequest)
                        TyreStat(location = location, isCentered = true)
                    },
                    // The same on both sides, a centered title is centered on the screen
                    modifier = Modifier.padding(16.dp),
                ) { (titleMeasurable, closeMeasurable, readingMeasurable), constraints ->
                    val title = titleMeasurable.measure(Constraints())
                    val close = closeMeasurable.measure(Constraints())
                    // As wide as the widest reading, its size doesn't change with what it reads
                    val reading = readingMeasurable.measure(Constraints.fixedWidth(width.roundToPx()))
                    val maxWidth = constraints.maxWidth.toFloat()
                    val maxHeight = constraints.maxHeight
                        .takeIf { it != Constraints.Infinity }
                        ?.toFloat()
                        ?: Float.MAX_VALUE
                    // 1. The title over the reading, both at the same size, as large as they fit
                    val shared = minOf(
                        maxWidth / maxOf(widestTitle, reading.width, 1),
                        maxHeight / (title.height + reading.height).coerceAtLeast(1),
                    )
                    // 2. Smaller if the widest title doesn't fit beside the close button at that size
                    val titleScale = minOf(
                        shared,
                        (maxWidth - close.width - CLOSE_GAP.toPx()) / widestTitle.coerceAtLeast(1),
                    )
                    val rowHeight = maxOf(title.height * titleScale, close.height.toFloat())
                    // 3. The reading larger, as large as it fits under them
                    val readingScale = minOf(
                        maxWidth / reading.width.coerceAtLeast(1),
                        (maxHeight - rowHeight) / reading.height.coerceAtLeast(1),
                    )
                    layout(constraints.maxWidth, (rowHeight + reading.height * readingScale).roundToInt()) {
                        // Centered, unless that reaches the close button: then against it, as far as
                        // the gap
                        (title.width * titleScale).also { titleWidth ->
                            placeScaled(
                                title,
                                minOf(
                                    (maxWidth - titleWidth) / 2,
                                    maxWidth - close.width - CLOSE_GAP.toPx() - titleWidth,
                                ),
                                (rowHeight - title.height * titleScale) / 2,
                                titleScale,
                            )
                        }
                        close.place(constraints.maxWidth - close.width, ((rowHeight - close.height) / 2).roundToInt())
                        placeScaled(reading, (maxWidth - reading.width * readingScale) / 2, rowHeight, readingScale)
                    }
                }
            }
        }
    }
}

/** Closes the magnifier, circled by what's left of its [duration] */
@Composable
private fun CloseButton(duration: Duration, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Counted every frame rather than animated: the system's animation scale would speed an
    // animation up, or skip it, while the delay closing the magnifier stays as long
    val left by produceState(1f, duration) {
        val start = TimeSource.Monotonic.markNow()
        while (value > 0f) withFrameNanos { value = (1 - start.elapsedNow() / duration).toFloat().coerceAtLeast(0f) }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(CLOSE_SIZE)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(MagnifierTags.close),
    ) {
        CircularProgressIndicator(
            progress = { left },
            strokeWidth = 4.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.close_24px),
            contentDescription = "Close",
            modifier = Modifier.size(36.dp),
        )
    }
}

/** Places it at [x], [y], scaled by [scale] from its top left corner */
private fun Placeable.PlacementScope.placeScaled(placeable: Placeable, x: Float, y: Float, scale: Float) =
    placeable.placeWithLayer(x.roundToInt(), y.roundToInt()) {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
    }

/** A dialog's, see Material 3 */
private const val SCRIM_ALPHA = .32f
private val CLOSE_SIZE = 64.dp

/** Between the title and the close button */
private val CLOSE_GAP = 16.dp

internal object MagnifierTags {
    const val taps = "MagnifierTags_taps"
    const val close = "MagnifierTags_close"
    fun root(location: Location) = "MagnifierTags_root_$location"
}
