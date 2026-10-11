package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.isWideWindow
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.ManageSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.ManageSensorsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.model.MoveChain
import com.masselis.tpmsadvanced.feature.main.model.MoveChain.Step

/**
 * The vehicle's sensors, laid out like the main screen: every location is outlined, tapping one
 * manages its sensor, or assigns it one by [scanQrCode], [scanBluetooth] or the sensor detected
 * there. Moving a sensor picks where it goes on the vehicle, see [MoveChain].
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
public fun ManageSensors(
    snackbarHostState: SnackbarHostState,
    scanQrCode: (Location) -> Unit,
    scanBluetooth: (Location) -> Unit,
    /** Opens the calibration of the sensor with this id */
    calibrate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** In a wide window, see [isWideWindow], the top bar makes way: the page shows its own */
    navigationIcon: @Composable () -> Unit = {},
    component: VehicleComponent = LocalVehicleComponent.current,
) {
    val viewModel: ManageSensorsViewModel = component.viewModel(component.key()) { it.ManageSensorsViewModel() }
    val vehicle by component.vehicleStateFlow.collectAsState()
    val occupied by viewModel.occupied.collectAsState()
    val all = vehicle.kind.locations
    // Waiting for the user to pick where the last sensor of the chain goes
    var moving by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    // Its last location has a sensor too: just swap them, or continue the chain
    var asking by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    // Ready, its arrows shown until the user applies or cancels it
    var confirming by rememberSaveable { mutableStateOf<MoveChain?>(null) }
    // Where each tyre is in the window, for the arrows of the moves
    val tyreCenters = remember { mutableStateMapOf<Location, Offset>() }
    val outlines = remember { mutableStateMapOf<Location, Rect>() }
    fun stop() {
        moving = null
        asking = null
        confirming = null
    }
    BackHandler(enabled = moving != null || confirming != null, onBack = ::stop)
    val name: @Composable () -> Unit = {
        Text(
            text = vehicle.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // At the bottom like the calibration's and the Bluetooth assignment's: what to tap, then what
    // the move needs. Always as tall, so the vehicle doesn't resize from one to the other.
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        val prompt: @Composable (String) -> Unit = { text ->
            Text(
                text = text,
                style = promptStyle,
                textAlign = TextAlign.Center,
            )
        }
        val cancel: @Composable () -> Unit = {
            OutlinedButton(
                onClick = ::stop,
                modifier = Modifier.fillMaxWidth().testTag(ManageSensorsTags.cancelMove),
            ) { Text("Cancel") }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = modifier.height(ACTIONS_HEIGHT),
        ) {
            val chain = confirming
            when {
                chain != null -> {
                    cancel()
                    Button(
                        onClick = { viewModel.apply(chain); stop() },
                        modifier = Modifier.fillMaxWidth().testTag(ManageSensorsTags.confirmMove),
                    ) { Text("Move as shown") }
                }

                moving != null -> {
                    prompt(MOVE_PROMPT)
                    cancel()
                }

                else -> prompt(MANAGE_PROMPT)
            }
        }
    }
    val vehicleWithArrows: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            Vehicle(
                component = component,
                snackbarHostState = snackbarHostState,
                taps = TyreTaps(
                    isManaging = true,
                    scanQrCode = scanQrCode,
                    scanBluetooth = scanBluetooth,
                    calibrate = calibrate,
                    // Once complete, the tyres show as usual: the arrows tell what's going to happen
                    move = moving?.takeIf { confirming == null }?.let { chain ->
                        TyreMove(chain, all) { target ->
                            when (val step = chain.tap(target, occupied, all)) {
                                is Step.Done -> confirming = step.chain
                                is Step.AskSwapOrChain -> asking = step.chain
                                is Step.Continue -> moving = step.chain
                            }
                        }
                    },
                    startMove = { location ->
                        if (all.size <= 2)
                        // Nowhere to pick, straight to the confirmation
                            all.first { it != location }.let { confirming = MoveChain.from(location).plus(it) }
                        else
                            moving = MoveChain.from(location)
                    },
                    onTyrePositioned = { location, center -> tyreCenters[location] = center },
                    onOutlinePositioned = { location, outline -> outlines[location] = outline },
                    canMove = all.size > 1,
                ),
                modifier = Modifier.fillMaxSize(),
            )
            MoveArrows(
                // Each move picked so far, all of them once the chain is complete
                moves = confirming?.moves(occupied)
                    ?: (asking ?: moving)?.locations?.zipWithNext()
                    ?: emptyList(),
                tyreCenters = tyreCenters,
                outlines = outlines,
                modifier = Modifier.fillMaxSize(),
            )
            // Nothing to tap until it's applied or cancelled
            if (confirming != null) Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            )
        }
    }
    Box(modifier.testTag(ManageSensorsTags.root)) {
        // Wider than tall, the vehicle needs all the height: the top bar, the name and the prompt
        // go to its side
        if (isWideWindow()) BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            // Where the vehicle's box is in the window: its outlines' span is told from it, so it
            // doesn't change as the box moves
            var vehicleX by remember { mutableFloatStateOf(0f) }
            val span = outlines.values
                .takeIf { it.isNotEmpty() }
                ?.let { all ->
                    with(density) {
                        (all.minOf { it.left } - vehicleX).toDp() to (all.maxOf { it.right } - vehicleX).toDp()
                    }
                }
            // As wide as its widest text, whichever prompt it shows, so nothing moves with it
            val measurer = rememberTextMeasurer()
            val nameStyle = MaterialTheme.typography.titleLarge
            val sidePromptStyle = promptStyle
            val textWidth = remember(measurer, vehicle.name, nameStyle, sidePromptStyle, density) {
                listOf(
                    measurer.measure(vehicle.name, nameStyle),
                    measurer.measure(MANAGE_PROMPT, sidePromptStyle),
                    measurer.measure(MOVE_PROMPT, sidePromptStyle),
                ).maxOf { it.size.width }.let { with(density) { it.toDp() } }
            }
            // The text then the vehicle, the room left split evenly: as much from the screen's left
            // edge to the text, from the text to the vehicle's outlines, and from those to the
            // screen's right edge
            val (sideWidth, sideStart, shift) = span
                ?.let { (left, right) ->
                    val sideWidth = textWidth
                        .coerceAtMost(maxWidth - (right - left) - MIN_SIDE_MARGIN * 3)
                        .coerceAtLeast(0.dp)
                    val margin = ((maxWidth - sideWidth - (right - left)) / 3).coerceAtLeast(0.dp)
                    Triple(sideWidth, margin, margin * 2 + sideWidth - left)
                }
                // Until the outlines are placed
                ?: Triple(SIDE_WIDTH, 0.dp, 0.dp)
            vehicleWithArrows(
                Modifier
                    .fillMaxSize()
                    .offset(x = shift)
                    .onGloballyPositioned { vehicleX = it.positionInWindow().x }
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(TOP_BAR_HEIGHT)) {
                Box(Modifier.padding(start = 4.dp)) { navigationIcon() }
                Text(
                    text = "Manage sensors",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Centered on the whole height like the vehicle, rather than under the title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = sideStart)
                    .width(sideWidth),
            ) {
                name()
                actions(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { name() }
            vehicleWithArrows(Modifier.weight(1f))
            HorizontalDivider()
            actions(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
    asking?.also { chain ->
        SwapOrChainDialog(
            from = chain.start,
            to = chain.last,
            swap = { asking = null; confirming = chain },
            chain = {
                asking = null
                when (val step = chain.continuing(all)) {
                    is Step.Done -> confirming = step.chain
                    is Step.Continue -> moving = step.chain
                    is Step.AskSwapOrChain -> error("Continuing never asks")
                }
            },
            onDismissRequest = { asking = null },
        )
    }
}

/**
 * An arrow from each move's location to the next over the vehicle, [tyreCenters] being in the
 * window. A swap's two arrows are drawn side by side.
 */
@Composable
private fun MoveArrows(
    moves: List<Pair<Location, Location>>,
    tyreCenters: Map<Location, Offset>,
    outlines: Map<Location, Rect>,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
        moves.forEach { (from, to) ->
            val start = tyreCenters[from]?.minus(origin) ?: return@forEach
            val end = tyreCenters[to]?.minus(origin) ?: return@forEach
            val length = (end - start).getDistance().takeIf { it > 0f } ?: return@forEach
            val direction = (end - start) / length
            val normal = Offset(-direction.y, direction.x)
            // Side by side with the swap's other arrow, rather than over it
            val shift = if ((to to from) in moves) normal * ARROW_SPACING.toPx() else Offset.Zero
            val tail = start + shift
            val tip = end + shift
            // Out of the outlines at both ends, or off the tyres when they're too close for it
            val gap = ARROW_GAP.toPx()
            val (fromInset, toInset) = outlines[from]
                ?.translate(-origin)
                ?.exitDistance(tail, direction)
                ?.let { fromInset ->
                    outlines[to]
                        ?.translate(-origin)
                        ?.exitDistance(tip, -direction)
                        ?.let { toInset -> fromInset + gap to toInset + gap }
                }
                ?.takeIf { (fromInset, toInset) -> length - fromInset - toInset > ARROW_HEAD.toPx() * 2 }
                ?: ARROW_INSET.toPx().coerceAtMost(length / 3).let { it to it }
            val arrowTail = tail + direction * fromInset
            val arrowTip = tip - direction * toInset
            val head = ARROW_HEAD.toPx()
            drawLine(color, arrowTail, arrowTip - direction * (head / 2f), ARROW_WIDTH.toPx(), StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(arrowTip.x, arrowTip.y)
                    (arrowTip - direction * head + normal * (head / 2f)).also { lineTo(it.x, it.y) }
                    (arrowTip - direction * head - normal * (head / 2f)).also { lineTo(it.x, it.y) }
                    close()
                },
                color,
            )
        }
    }
}

private val ARROW_WIDTH = 3.dp
private val ARROW_HEAD = 14.dp
private val ARROW_INSET = 28.dp
private val ARROW_SPACING = 6.dp
/** Between an arrow's ends and the outlines it leaves and reaches */
private val ARROW_GAP = 4.dp

/** How far from [point], inside this rectangle, its edge is in that [direction] */
private fun Rect.exitDistance(point: Offset, direction: Offset): Float = listOfNotNull(
    direction.x.takeIf { it > 0f }?.let { (right - point.x) / it },
    direction.x.takeIf { it < 0f }?.let { (left - point.x) / it },
    direction.y.takeIf { it > 0f }?.let { (bottom - point.y) / it },
    direction.y.takeIf { it < 0f }?.let { (top - point.y) / it },
)
    .minOrNull()
    ?.coerceAtLeast(0f)
    ?: 0f

/** Sending [from]'s sensor to [to], which has one: just swap them, or move more sensors around */
@Composable
private fun SwapOrChainDialog(
    from: Location,
    to: Location,
    swap: () -> Unit,
    chain: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionsDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(buildString { appendLoc(to, withType = false, capitalized = true); append(" already has a sensor") })
        },
        text = {
            Column {
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(swapIcon(from, to)), null) },
                    title = "Just swap them",
                    subtitle = buildString {
                        appendLoc(from, withType = false, capitalized = true)
                        append(" ⇄ ")
                        appendLoc(to, withType = false, capitalized = true)
                    },
                    onClick = swap,
                    modifier = Modifier.testTag(ManageSensorsTags.justSwap),
                )
                DialogOption(
                    icon = { Icon(ImageVector.vectorResource(R.drawable.rotate_wheels_24px), null) },
                    title = "Multi-wheel change",
                    subtitle = "Tyre rotation, etc",
                    onClick = chain,
                    modifier = Modifier.testTag(ManageSensorsTags.multiWheel),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
        modifier = modifier.testTag(ManageSensorsTags.askDialog),
    )
}

/**
 * The arrows between two locations seen from above, the front at the top: left-right on the same
 * axle, up-down on the same side, the matching diagonal otherwise
 */
@DrawableRes
private fun swapIcon(from: Location, to: Location): Int {
    val (fromX, fromY) = from.position
    val (toX, toY) = to.position
    return when {
        fromY == toY -> R.drawable.swap_horizontal_24px
        fromX == toX -> R.drawable.swap_vertical_24px
        (toX - fromX) * (toY - fromY) > 0 -> R.drawable.swap_diagonal_down_24px
        else -> R.drawable.swap_diagonal_up_24px
    }
}

/** Where a location sits seen from above: left -1 to right 1, front -1 to rear 1 */
private val Location.position: Pair<Int, Int>
    get() = when (this) {
        is Location.Wheel -> location.side.x to location.axle.y
        is Location.Axle -> 0 to axle.y
        is Location.Side -> side.x to 0
        // Behind the rear wheels
        Location.Spare -> 0 to 2
        Location.Single -> 0 to 0
    }

private val SensorLocation.Side.x get() = if (this == SensorLocation.Side.LEFT) -1 else 1
private val SensorLocation.Axle.y get() = if (this == SensorLocation.Axle.FRONT) -1 else 1

/**
 * The most the actions show: two buttons with their touch targets, or a prompt of two lines and a
 * button. Always this tall, so nothing moves when they change.
 */
private val ACTIONS_HEIGHT = 104.dp

/** Larger than a hint: it's what the page asks the user to do */
private val promptStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium

/**
 * The name and prompt's column, next to the vehicle while the screen is wider than tall, until the
 * outlines are placed. Then it's as wide as its text, its margins and the vehicle's equal and at
 * least [MIN_SIDE_MARGIN], its text wrapping otherwise.
 */
private val SIDE_WIDTH = 200.dp
private val MIN_SIDE_MARGIN = 8.dp

private const val MANAGE_PROMPT = "Tap the wheel/sensor to manage"
private const val MOVE_PROMPT = "Tap the wheel to move to"

/** Material's small top app bar */
private val TOP_BAR_HEIGHT = 64.dp

@Suppress("ConstPropertyName")
internal object ManageSensorsTags {
    const val root = "ManageSensorsTags_root"
    const val cancelMove = "ManageSensorsTags_cancelMove"
    const val askDialog = "ManageSensorsTags_askDialog"
    const val justSwap = "ManageSensorsTags_justSwap"
    const val multiWheel = "ManageSensorsTags_multiWheel"
    const val confirmMove = "ManageSensorsTags_confirmMove"
}
