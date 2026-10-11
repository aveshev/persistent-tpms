@file:Suppress("LongMethod")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstrainScope
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutBaseScope.HorizontalAnchor
import androidx.constraintlayout.compose.ConstraintLayoutScope
import androidx.constraintlayout.compose.Dimension
import com.masselis.tpmsadvanced.core.ui.KeepScreenOn
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.BAR
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.KILO_PASCAL
import com.masselis.tpmsadvanced.data.unit.model.PressureUnit.PSI
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleBindings.Companion.VehicleSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent.Factory.Companion.key
import com.masselis.tpmsadvanced.feature.main.model.MoveChain
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Every vehicle image is drawn on a 208:462 canvas, centered and taking `imageHeight` (0..1) of the
 * available height. Tyres are placed and sized as fractions of that image so they stay on the
 * drawn wheels whatever the screen size or orientation. Texts and buttons keep a fixed size to
 * remain readable.
 */
private const val IMAGE_RATIO = 208f / 462f

/*
 * The image is as tall as possible, up to [MAX_IMAGE_HEIGHT] of the height, while leaving room on
 * each side for the widest readout text, its [READOUT_GAP] and a [SCREEN_MARGIN], so readouts are
 * never cut by the screen edges. On narrow screens (portrait) the width decides, down to
 * [MIN_IMAGE_HEIGHT].
 */
private const val MIN_IMAGE_HEIGHT = .45f
private const val MAX_IMAGE_HEIGHT = .9f

/*
 * The image never covers more than this share of the available area. Vehicles with readouts on
 * both sides are limited by the width first on phones; this keeps a vehicle whose readouts are all
 * on one side (more width left for the image) from growing much bigger than the others.
 */
private const val MAX_IMAGE_AREA = .5f
private val READOUT_GAP = 8.dp
private val SCREEN_MARGIN = 4.dp

/**
 * Widest pressure line of a readout in this unit, up to the 150 psi settings limit, see [TyreStat]
 * and [com.masselis.tpmsadvanced.data.vehicle.model.Pressure.string]
 */
private val PressureUnit.widestReadout: String
    get() = when (this) {
        KILO_PASCAL -> "1034 kpa*"
        BAR -> "8.88 bar*"
        PSI -> "88.8 psi*"
    }

/** Widest plausible detail lines of a readout, see [TyreStat] */
private val WIDEST_DETAILS = listOf("188°F", "188°C", "88 hours", "99+ days")

/** Height of a tyre as a fraction of the image height, its width follows TYRE_ASPECT_RATIO */
private const val TYRE_HEIGHT = .165f

/** Half the height of a spare's tyre drawn across, as a fraction of the image height */
private const val SPARE_HALF_HEIGHT = TYRE_HEIGHT * TYRE_ASPECT_RATIO / 2f


/** A mono-wheel's tyre is drawn much taller and narrower than the others, its width following [MONOWHEEL_TYRE_RATIO] */
private const val MONOWHEEL_TYRE_HEIGHT = .377f
private const val MONOWHEEL_TYRE_RATIO = 11.2f / 52f

/** Distance between the centers of a vehicle's outermost tyres, as a fraction of the image width */
private const val CAR_TRACK = .74f
private const val CAR_WITH_SPARE_TRACK = .748f
private const val TRAILER_TRACK = .86f
private const val TADPOLE_FRONT_TRACK = .58f

/**
 * How far inside the image's edge the readouts' tyres reach, as a fraction of the image width. The
 * readouts sit against their tyre, not against the image's edge: the image grows by as much, so the
 * outlines reach [SCREEN_MARGIN] from the screen edges. The others' readouts sit against the image.
 */
private val Kind.readoutInset: Float
    get() = when (this) {
        Kind.CAR -> CAR_TRACK
        Kind.CAR_WITH_SPARE -> CAR_WITH_SPARE_TRACK
        Kind.SINGLE_AXLE_TRAILER -> TRAILER_TRACK
        Kind.TADPOLE_THREE_WHEELER -> TADPOLE_FRONT_TRACK
        Kind.MOTORCYCLE, Kind.DELTA_THREE_WHEELER, Kind.MONOWHEEL -> null
    }
        ?.let { track -> (1 - track) / 2 - TYRE_HEIGHT * TYRE_ASPECT_RATIO / 2 / IMAGE_RATIO }
        ?.coerceAtLeast(0f)
        ?: 0f

/**
 * The current vehicle. Tapping a location without a sensor assigns it one by [scanQrCode],
 * [scanBluetooth] or the sensor detected there.
 */
@Composable
public fun CurrentVehicle(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    center: @Composable (Modifier) -> Unit = {},
    scanQrCode: (Location) -> Unit = {},
    scanBluetooth: (Location) -> Unit = {},
) {
    Vehicle(
        component = LocalVehicleComponent.current,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        center = center,
        taps = TyreTaps(isManaging = false, scanQrCode = scanQrCode, scanBluetooth = scanBluetooth),
    )
}

@Suppress("MaxLineLength")
@Composable
public fun Vehicle(
    component: VehicleComponent,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    /** Placed over the vehicle, between its axles or in the middle of a single axle one */
    center: @Composable (Modifier) -> Unit = {},
    taps: TyreTaps = TyreTaps(),
) {
    KeepScreenOn()
    val settings = component.viewModel(component.key()) { it.VehicleSettingsViewModel() }
    val pressureUnit by settings.pressureUnit.collectAsState()
    // Outlines only show around locations to tap, see TyreTapArea: without any, the readouts
    // need no room for them and the image grows by as much
    val isOutlined = taps.isManaging || settings.isFullyAssigned.collectAsState().value.not()
    // Every readout as wide, so the outlines around them are too
    val readoutSlotWidth = rememberWidestReadoutWidth(pressureUnit, isOutlined)
        .plus(OUTLINE_PADDING.takeIf { isOutlined } ?: 0.dp)
    // The outline reaches past the readout, it must stay off the screen's edge too
    val readoutWidth = readoutSlotWidth + READOUT_GAP + (OUTLINE_OUTSET.takeIf { isOutlined } ?: 0.dp)
    val basicReadoutHeight = rememberBasicReadoutHeight()
    // The spare's readout is under the image, not next to it
    val readoutSides = component.vehicle.kind.locations.minus(Location.Spare).map { it.readoutSide }.toSet()
    // As tall as its usual pressure, temperature and time since update, and its outline's edge.
    // Extra lines turned on in the settings go past the bottom, there's room for them while the
    // screen is taller than wide, where the width decides of the image's height.
    val spareRoom = (basicReadoutHeight + OUTLINE_OUTSET * 2)
        .takeIf { Location.Spare in component.vehicle.kind.locations }
        ?: 0.dp
    BoxWithConstraints(modifier) {
        val imageHeight = maxWidth
            .minus(readoutWidth * readoutSides.size)
            .minus(SCREEN_MARGIN * 2)
            .div(1 - component.vehicle.kind.readoutInset * readoutSides.size)
            .div(maxHeight * IMAGE_RATIO)
            // (imageHeight * maxHeight)² * IMAGE_RATIO <= MAX_IMAGE_AREA * maxWidth * maxHeight
            .coerceAtMost(sqrt(MAX_IMAGE_AREA * maxWidth.value / (IMAGE_RATIO * maxHeight.value)))
            .coerceIn(MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT)
            // Room under the image for the spare's readout, see the offset below
            .coerceAtMost((maxHeight - spareRoom) / maxHeight)
        // Centers the image and its readouts together when readouts are only on one side
        val fill = Modifier
            .fillMaxSize()
            .offset(
                x = listOfNotNull(
                    readoutWidth.takeIf { LEFT in readoutSides },
                    readoutWidth.takeIf { RIGHT in readoutSides }?.unaryMinus(),
                ).fold(0.dp, Dp::plus) / 2,
                // Up by half of the spare's readout, it's centered with the image
                y = -spareRoom / 2,
            )
        when (component.vehicle.kind) {
            Kind.CAR ->
                Car(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.SINGLE_AXLE_TRAILER ->
                SingleAxleTrailer(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.MOTORCYCLE ->
                Motorcycle(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.TADPOLE_THREE_WHEELER ->
                TadpoleThreadWheeler(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.DELTA_THREE_WHEELER ->
                DeltaThreeWheeler(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.CAR_WITH_SPARE ->
                CarWithSpare(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
            Kind.MONOWHEEL ->
                Monowheel(imageHeight, snackbarHostState, center, taps, readoutSlotWidth, basicReadoutHeight, fill)
        }
    }
}

/**
 * What tapping the vehicle's locations does. While [isManaging], see [ManageSensors], every
 * location is outlined and its readout keeps to its pressure, temperature and time since update.
 * [startMove] starts moving a location's sensor, then [move] picks where it goes.
 */
@Suppress("LongParameterList")
public class TyreTaps internal constructor(
    internal val isManaging: Boolean = false,
    internal val scanQrCode: (Location) -> Unit = {},
    internal val scanBluetooth: (Location) -> Unit = {},
    internal val move: TyreMove? = null,
    internal val startMove: (Location) -> Unit = {},
    /** Opens the calibration of the sensor with this id */
    internal val calibrate: (Int) -> Unit = {},
    /** A vehicle with a single location has nowhere to move a sensor to */
    internal val canMove: Boolean = true,
    /** Where each location's tyre is centered in the window, to draw over the vehicle */
    internal val onTyrePositioned: ((Location, Offset) -> Unit)? = null,
    /** Where each location's outline is in the window, for what's drawn over the vehicle to avoid it */
    internal val onOutlinePositioned: ((Location, Rect) -> Unit)? = null,
)

private fun Modifier.reportCenter(location: Location, taps: TyreTaps) = taps
    .onTyrePositioned
    ?.let { report -> onGloballyPositioned { report(location, it.boundsInWindow().center) } }
    ?: this

/** A sensor moving along [chain] on a vehicle with [all] these locations, [onTap] picking the next one */
internal class TyreMove(val chain: MoveChain, private val all: Collection<Location>, val onTap: (Location) -> Unit) {
    fun canTap(location: Location): Boolean = chain.canTap(location, all)
}

/**
 * Spans [tyre] and its [readout], whichever side of the tyre the readout of [location] is on, and
 * the readout's height: the whole image, see [outlineCenteredOn]
 */
private fun ConstrainScope.around(
    tyre: ConstrainedLayoutReference,
    readout: ConstrainedLayoutReference,
    location: Location,
) {
    top.linkTo(readout.top)
    bottom.linkTo(readout.bottom)
    when (location.readoutSide) {
        LEFT -> {
            start.linkTo(readout.start)
            end.linkTo(tyre.end)
        }

        RIGHT -> {
            start.linkTo(tyre.start)
            end.linkTo(readout.end)
        }
    }
    width = Dimension.fillToConstraints
    height = Dimension.fillToConstraints
}

/**
 * Given the image's height, as tall as a tyre or [minHeight] for a basic readout, and placed like
 * [verticallyCenteredOn] places the readout: centered on [y], pushed back inside the image when it
 * would overflow, so it stays around the readout wherever it's moved
 */
private fun Modifier.outlineCenteredOn(
    y: Float,
    minHeight: Dp,
    tyreHeight: Float = TYRE_HEIGHT,
    bottomAt: Float? = null,
) = layout { measurable, constraints ->
    val height = (constraints.maxHeight * tyreHeight)
        .roundToInt()
        .coerceAtLeast(minHeight.roundToPx())
        .coerceAtMost(constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(
            x = 0,
            y = (outlineCenter(constraints.maxHeight, y, height, bottomAt) - height / 2f)
                .roundToInt()
                .coerceIn(0, constraints.maxHeight - height)
        )
    }
}

/**
 * Where an outline [height] tall is centered in an image [imageHeight] tall: on [y], or higher if
 * its bottom would go past [bottomAt], both fractions of the image height
 */
private fun outlineCenter(imageHeight: Int, y: Float, height: Int, bottomAt: Float?): Float =
    (imageHeight * y).let { center ->
        bottomAt?.let { center.coerceAtMost(imageHeight * it - height / 2f) } ?: center
    }

/** Side of the image the readout of this location sits on, see the layouts below */
internal val Location.readoutSide: SensorLocation.Side
    get() = when (this) {
        is Location.Axle, Location.Spare, Location.Single -> RIGHT
        is Location.Wheel -> location.side
        is Location.Side -> side
    }

/** Height of a [TyreStat] showing only its pressure, temperature and time since update */
@Composable
private fun rememberBasicReadoutHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val pressureStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(measurer, pressureStyle, density) {
        measurer.measure("0", pressureStyle).size.height
            .plus(measurer.measure("0", pressureStyle.copy(fontSize = 16.sp)).size.height * 2)
            .let { with(density) { it.toDp() } }
    }
}

/**
 * Width of the widest line a [TyreReadout] can show, with the current font and font scale. Only a
 * location without a sensor, outlined, shows what it shows instead ([isOutlined]).
 */
@Composable
private fun rememberWidestReadoutWidth(pressureUnit: PressureUnit, isOutlined: Boolean): Dp {
    val measurer = rememberTextMeasurer()
    val pressureStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(measurer, pressureStyle, density, pressureUnit, isOutlined) {
        listOf(measurer.measure(pressureUnit.widestReadout, pressureStyle))
            .plus(WIDEST_DETAILS.map { measurer.measure(it, pressureStyle.copy(fontSize = 16.sp)) })
            // What a location without a sensor shows instead, see TyreReadout
            .plus(
                listOf(
                    measurer.measure("Tap to", pressureStyle.copy(fontSize = 20.sp)),
                    measurer.measure(
                        "(detected)",
                        pressureStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                    ),
                ).takeIf { isOutlined }.orEmpty()
            )
            .maxOf { it.size.width }
            .let { with(density) { it.toDp() } }
    }
}

@Composable
private fun ConstraintLayoutScope.VehicleImage(
    ref: ConstrainedLayoutReference,
    @DrawableRes id: Int,
    contentDescription: String,
    imageHeight: Float,
) {
    Image(
        bitmap = ImageBitmap.imageResource(id = id),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        contentDescription = contentDescription,
        modifier = Modifier
            .aspectRatio(IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(imageHeight)
            }
    )
}

/**
 * Invisible box centered like the image and spanning [span] of its width, its edges anchor what
 * sits at `(1 - span) / 2` and `(1 + span) / 2` of the image width: wheel centers or the outline.
 */
@Composable
private fun ConstraintLayoutScope.ImageSpan(
    ref: ConstrainedLayoutReference,
    span: Float,
    imageHeight: Float,
) {
    Box(
        Modifier
            .aspectRatio(span * IMAGE_RATIO)
            .constrainAs(ref) {
                centerTo(parent)
                height = Dimension.percent(imageHeight)
            }
    )
}

/** Anchor at [y] (0..1) of the image height */
private fun ConstraintLayoutScope.imageGuideline(y: Float, imageHeight: Float): HorizontalAnchor =
    createGuidelineFromTop((1f - imageHeight) / 2f + imageHeight * y)

/**
 * Takes the whole height it's given and places its content centered on [y] (0..1) of that height,
 * pushed back inside when it would overflow. Given the image's height, it keeps a readout next to
 * its tyre without ever going above or below the image.
 */
private fun Modifier.verticallyCenteredOn(
    y: Float,
    /** The outline's around it, see [outlineCenteredOn]: centered with it, wherever it's raised to */
    outline: Pair<Dp, Float>? = null,
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val center = outline
        ?.let { (minHeight, bottomAt) ->
            (constraints.maxHeight * TYRE_HEIGHT)
                .roundToInt()
                .coerceAtLeast(minHeight.roundToPx())
                .coerceAtMost(constraints.maxHeight)
                .let { outlineCenter(constraints.maxHeight, y, it, bottomAt) }
        }
        ?: (constraints.maxHeight * y)
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(
            x = 0,
            y = (center - placeable.height / 2f)
                .roundToInt()
                .coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
        )
    }
}

/**
 * Moves its content to the vertical middle of the window rather than of the space it's given,
 * which the top bar pushes down
 */
@Composable
private fun Modifier.windowCenteredVertically(): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    var shift by remember { mutableIntStateOf(0) }
    return this
        // Before the offset, where it would be without it
        .onGloballyPositioned {
            shift = (windowHeight / 2f - it.positionInWindow().y - it.size.height / 2f).roundToInt()
        }
        .offset { IntOffset(0, shift) }
}

private fun ConstrainScope.tyreSize(
    imageHeight: Float,
    tyreHeight: Float = TYRE_HEIGHT,
    aspectRatio: Float = TYRE_ASPECT_RATIO,
) {
    height = Dimension.percent(imageHeight * tyreHeight)
    width = Dimension.ratio("$aspectRatio:1")
}

@Composable
private fun Car(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            frontLeft,
            frontLeftStats,
            frontLeftTap,
            frontRight,
            frontRightStats,
            frontRightTap,
            rearLeft,
            rearLeftStats,
            rearLeftTap,
            rearRight,
            rearRightStats,
            rearRightTap
        ) = createRefs()
        VehicleImage(vehicleImage, R.drawable.schema_car_top_view, "Image of your car", imageHeight)
        ImageSpan(track, CAR_TRACK, imageHeight)
        val frontY = .217f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .783f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(track.start)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(frontLeft.start, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontLeftTap) { around(frontLeft, frontLeftStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(track.end)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontRight.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontRightTap) { around(frontRight, frontRightStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(track.start)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(rearLeft.start, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearLeftTap) { around(rearLeft, rearLeftStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(track.end)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(rearRight.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearRightTap) { around(rearRight, rearRightStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun CarWithSpare(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            frontLeft,
            frontLeftStats,
            frontLeftTap,
            frontRight,
            frontRightStats,
            frontRightTap,
            rearLeft,
            rearLeftStats,
            rearLeftTap,
            rearRight,
            rearRightStats,
            rearRightTap
        ) = createRefs()
        VehicleImage(vehicleImage, R.drawable.schema_car_with_spare_top_view, "Image of your car", imageHeight)
        ImageSpan(track, CAR_WITH_SPARE_TRACK, imageHeight)
        val frontY = .241f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .761f
        val rearAxle = imageGuideline(rearY, imageHeight)
        // The rear outlines grow up from their tyres' bottom rather than both ways, they'd reach
        // the spare's otherwise
        val rearBottom = rearY + TYRE_HEIGHT / 2
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(track.start)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(frontLeft.start, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontLeftTap) { around(frontLeft, frontLeftStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(track.end)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontRight.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontRightTap) { around(frontRight, frontRightStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(track.start)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(rearLeft.start, 8.dp)
                }.verticallyCenteredOn(rearY, basicReadoutHeight to rearBottom)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearLeftTap) { around(rearLeft, rearLeftStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight, bottomAt = rearBottom),
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(track.end)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(rearRight.end, 8.dp)
                }.verticallyCenteredOn(rearY, basicReadoutHeight to rearBottom)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearRightTap) { around(rearRight, rearRightStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight, bottomAt = rearBottom),
            )
        }
        // Lying flat behind the car, its tyre is drawn across
        val (spare, spareSpan, spareStats, spareTap) = createRefs()
        ImageSpan(spareSpan, .34f, imageHeight)
        val spareY = .905f
        val spareAxle = imageGuideline(spareY, imageHeight)
        // Drawn across, the spare's tyre is as tall as a tyre is wide
        val spareTop = imageGuideline(spareY - SPARE_HALF_HEIGHT, imageHeight)
        val spareBottom = imageGuideline(spareY + SPARE_HALF_HEIGHT, imageHeight)
        with(Location.Spare) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(spare) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(spareAxle)
                    tyreSize(imageHeight)
                }.rotate(90f).reportCenter(this, taps)
            )
            // Under the spare, past the bottom of the image, see spareRoom
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(spareStats) {
                    top.linkTo(spareBottom, 4.dp)
                    centerHorizontallyTo(vehicleImage)
                    width = Dimension.value(readoutSlotWidth)
                }
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                // Around the spare as drawn across, rather than its tyre's upright layout, and
                // its readings under it
                modifier = Modifier.constrainAs(spareTap) {
                    top.linkTo(spareTop)
                    bottom.linkTo(spareStats.bottom)
                    start.linkTo(spareStats.start)
                    end.linkTo(spareStats.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                },
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun Monowheel(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (vehicleImage, tyre, stats, tap) = createRefs()
        VehicleImage(vehicleImage, R.drawable.schema_monowheel_top_view, "Image of your mono-wheel", imageHeight)
        val wheelY = .505f
        val axle = imageGuideline(wheelY, imageHeight)
        with(Location.Single) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                aspectRatio = MONOWHEEL_TYRE_RATIO,
                modifier = Modifier.constrainAs(tyre) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(axle)
                    tyreSize(imageHeight, MONOWHEEL_TYRE_HEIGHT, MONOWHEEL_TYRE_RATIO)
                }.reportCenter(this, taps)
            )
            // Past the foot rests
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(stats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(wheelY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(tap) { around(tyre, stats, this@with) }
                    .outlineCenteredOn(wheelY, basicReadoutHeight, MONOWHEEL_TYRE_HEIGHT),
            )
        }
        // Last, over everything else, above the wheel rather than over it
        createRef().also { ref ->
            val above = imageGuideline(.2f, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(above)
                }
            )
        }
    }
}

@Composable
private fun SingleAxleTrailer(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            track,
            tyreLeft,
            leftStats,
            leftTap,
            tyreRight,
            rightStats,
            rightTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_single_axle_trailer_top_view,
            "Image of your trailer",
            imageHeight
        )
        ImageSpan(track, TRAILER_TRACK, imageHeight)
        val axleY = .686f
        val axle = imageGuideline(axleY, imageHeight)
        with(Location.Side(LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreLeft) {
                    centerAround(track.start)
                    centerAround(axle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(leftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(tyreLeft.start, 8.dp)
                }.verticallyCenteredOn(axleY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(leftTap) { around(tyreLeft, leftStats, this@with) }
                    .outlineCenteredOn(axleY, basicReadoutHeight),
            )
        }
        with(Location.Side(RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreRight) {
                    centerAround(track.end)
                    centerAround(axle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(tyreRight.end, 8.dp)
                }.verticallyCenteredOn(axleY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rightTap) { around(tyreRight, rightStats, this@with) }
                    .outlineCenteredOn(axleY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            center(
                Modifier
                    .constrainAs(ref) {
                        centerHorizontallyTo(vehicleImage)
                        centerVerticallyTo(vehicleImage)
                    }
                    .windowCenteredVertically()
            )
        }
    }
}

@Composable
private fun Motorcycle(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            tyreFront,
            frontStats,
            frontTap,
            tyreRear,
            rearStats,
            rearTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_motorcycle_top_view,
            "Image of your motorcycle",
            imageHeight
        )
        val frontY = .0865f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .805f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontTap) { around(tyreFront, frontStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearTap) { around(tyreRear, rearStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun TadpoleThreadWheeler(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            frontTrack,
            rearOutline,
            frontLeft,
            frontLeftStats,
            frontLeftTap,
            frontRight,
            frontRightStats,
            frontRightTap,
            tyreRear,
            rearStats,
            rearTap,
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_tadpole_three_wheeler_top_view,
            "Image of your three-wheeler",
            imageHeight
        )
        ImageSpan(frontTrack, TADPOLE_FRONT_TRACK, imageHeight)
        // Outline around the rear wheel (the exhaust on the right), the rear readout sits next to it
        ImageSpan(rearOutline, .65f, imageHeight)
        val frontY = .1005f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .845f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Wheel(FRONT_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontLeft) {
                    centerAround(frontTrack.start)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(frontLeft.start, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontLeftTap) { around(frontLeft, frontLeftStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(FRONT_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(frontRight) {
                    centerAround(frontTrack.end)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontRight.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontRightTap) { around(frontRight, frontRightStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Axle(REAR)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreRear) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(rearOutline.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearTap) { around(tyreRear, rearStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}

@Composable
private fun DeltaThreeWheeler(
    imageHeight: Float,
    snackbarHostState: SnackbarHostState,
    center: @Composable (Modifier) -> Unit,
    taps: TyreTaps,
    readoutSlotWidth: Dp,
    basicReadoutHeight: Dp,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(modifier = modifier) {
        val (
            vehicleImage,
            rearTrack,
            frontOutline,
            tyreFront,
            frontStats,
            frontTap,
            rearLeft,
            rearLeftStats,
            rearLeftTap,
            rearRight,
            rearRightStats,
            rearRightTap
        ) = createRefs()
        VehicleImage(
            vehicleImage,
            R.drawable.schema_delta_three_wheeler_top_view,
            "Image of your three-wheeler",
            imageHeight
        )
        ImageSpan(rearTrack, .713f, imageHeight)
        // Outline around the front wheel, the front readout sits next to it
        ImageSpan(frontOutline, .22f, imageHeight)
        val frontY = .0865f
        val frontAxle = imageGuideline(frontY, imageHeight)
        val rearY = .835f
        val rearAxle = imageGuideline(rearY, imageHeight)
        with(Location.Axle(FRONT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(tyreFront) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(frontAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(frontStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(frontOutline.end, 8.dp)
                }.verticallyCenteredOn(frontY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(frontTap) { around(tyreFront, frontStats, this@with) }
                    .outlineCenteredOn(frontY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_LEFT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearLeft) {
                    centerAround(rearTrack.start)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearLeftStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    end.linkTo(vehicleImage.start, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearLeftTap) { around(rearLeft, rearLeftStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        with(Location.Wheel(REAR_RIGHT)) {
            Tyre(
                location = this,
                snackbarHostState = snackbarHostState,
                blinks = taps.isManaging.not(),
                modifier = Modifier.constrainAs(rearRight) {
                    centerAround(rearTrack.end)
                    centerAround(rearAxle)
                    tyreSize(imageHeight)
                }.reportCenter(this, taps)
            )
            TyreReadout(
                location = this,
                isBasic = taps.isManaging,
                modifier = Modifier.constrainAs(rearRightStats) {
                    top.linkTo(vehicleImage.top)
                    bottom.linkTo(vehicleImage.bottom)
                    height = Dimension.fillToConstraints
                    width = Dimension.value(readoutSlotWidth)
                    start.linkTo(vehicleImage.end, 8.dp)
                }.verticallyCenteredOn(rearY)
            )
            TyreTapArea(
                location = this,
                isManaging = taps.isManaging,
                scanQrCode = taps.scanQrCode,
                scanBluetooth = taps.scanBluetooth,
                move = taps.move,
                startMove = taps.startMove,
                calibrate = taps.calibrate,
                canMove = taps.canMove,
                onOutlinePositioned = taps.onOutlinePositioned,
                modifier = Modifier
                    .constrainAs(rearRightTap) { around(rearRight, rearRightStats, this@with) }
                    .outlineCenteredOn(rearY, basicReadoutHeight),
            )
        }
        // Last, over everything else
        createRef().also { ref ->
            val middle = imageGuideline((frontY + rearY) / 2, imageHeight)
            center(
                Modifier.constrainAs(ref) {
                    centerHorizontallyTo(vehicleImage)
                    centerAround(middle)
                }
            )
        }
    }
}
