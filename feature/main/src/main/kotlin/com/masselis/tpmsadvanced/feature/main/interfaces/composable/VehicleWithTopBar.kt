package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.SpareReadout.BESIDE_CENTERED
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.SpareReadout.UNDER
import kotlin.math.roundToInt

/**
 * The [vehicle] and the top bar's [navigationIcon], [title] and [actions], laid out as whichever
 * [Placement] leaves the vehicle's image the tallest in this size, see [VehicleSizing]. Drawn over
 * the vehicle, the top bar is laid out as Material's centered one, the bar this screen had before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun VehicleWithTopBar(
    sizing: VehicleSizing,
    navigationIcon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    vehicle: @Composable (SpareReadout) -> Unit,
    modifier: Modifier = Modifier,
    /** Where the top bar is, in this layout's coordinates: its whole strip over the vehicle, its corners beside it */
    onBarPositioned: (List<Rect>) -> Unit = {},
) {
    // The top bar's, wherever they're drawn
    val colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
    val titleStyle = MaterialTheme.typography.titleLarge
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val barHeight = TOP_BAR_HEIGHT.roundToPx()
        val barPadding = BAR_PADDING.roundToPx()
        val bar = Constraints(maxWidth = width, maxHeight = barHeight)
        val navigationIcons = subcompose(Slot.NAVIGATION_ICON) {
            CompositionLocalProvider(LocalContentColor provides colors.navigationIconContentColor, navigationIcon)
        }.map { it.measure(bar) }
        val actionRows = subcompose(Slot.ACTIONS) {
            CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }.map { it.measure(bar) }
        val titles = subcompose(Slot.TITLE) {
            CompositionLocalProvider(LocalContentColor provides colors.titleContentColor) {
                ProvideTextStyle(titleStyle, title)
            }
        }
        val navigationIconWidth = navigationIcons.maxOfOrNull { it.width } ?: 0
        val actionsWidth = actionRows.maxOfOrNull { it.width } ?: 0
        // Beside, the title then the actions in the top right corner, with up to MAX_END_SHARE of
        // the width
        val besideTitleGaps = (BESIDE_TITLE_START + BESIDE_TITLE_END + BAR_PADDING).roundToPx()
        val besideTitleMaxWidth = ((width * MAX_END_SHARE).roundToInt() - besideTitleGaps - actionsWidth)
            .coerceAtLeast(0)
        val besideEndWidth = (titles.maxOfOrNull { it.maxIntrinsicWidth(barHeight) } ?: 0)
            .coerceAtMost(besideTitleMaxWidth)
            .plus(besideTitleGaps + actionsWidth)
        // The vehicle's box for each placement
        fun Placement.box() = when (this) {
            Placement.ON_TOP, Placement.ON_TOP_SPARE_BESIDE -> width to height - barHeight
            Placement.BESIDE -> width - navigationIconWidth - besideEndWidth to height
        }
        val placement = Placement.entries.maxBy { placement ->
            placement
                .box()
                .takeIf { (boxWidth, boxHeight) -> boxWidth > 0 && boxHeight > 0 }
                // Without the minimum height, which only lets the readouts go past the box
                ?.let { (boxWidth, boxHeight) ->
                    boxHeight * sizing.imageHeight(boxWidth.toDp(), boxHeight.toDp(), placement.spareReadout, 0f)
                }
                ?: 0f
        }
        val (boxWidth, boxHeight) = placement.box()
        val vehicles = subcompose(Slot.VEHICLE) { vehicle(placement.spareReadout) }
            .map { it.measure(Constraints.fixed(boxWidth.coerceAtLeast(0), boxHeight.coerceAtLeast(0))) }
        when (placement) {
            Placement.ON_TOP, Placement.ON_TOP_SPARE_BESIDE -> {
                // As Material's centered top bar: its title is padded on both sides and centered on
                // the whole width, unless it would cover the navigation icon or the actions
                val titlePadding = BAR_PADDING.roundToPx()
                val startWidth = navigationIconWidth + barPadding
                val endWidth = actionsWidth + barPadding
                val titlePlaceables = titles.map {
                    it.measure(bar.copy(maxWidth = (width - startWidth - endWidth - titlePadding * 2).coerceAtLeast(0)))
                }
                layout(width, height) {
                    onBarPositioned(listOf(Rect(0f, 0f, width.toFloat(), barHeight.toFloat())))
                    vehicles.forEach { it.place(0, barHeight) }
                    navigationIcons.forEach { it.place(barPadding, (barHeight - it.height) / 2) }
                    actionRows.forEach { it.place(width - endWidth, (barHeight - it.height) / 2) }
                    titlePlaceables.forEach {
                        val boxWidth = it.width + titlePadding * 2
                        val start = maxOf(TITLE_INSET.roundToPx(), startWidth)
                        val x = ((width - boxWidth) / 2)
                            .coerceAtMost(width - endWidth - boxWidth)
                            .coerceAtLeast(start)
                        it.place(x + titlePadding, (barHeight - it.height) / 2)
                    }
                }
            }

            Placement.BESIDE -> {
                val titlePlaceables = titles.map { it.measure(bar.copy(maxWidth = besideTitleMaxWidth)) }
                layout(width, height) {
                    onBarPositioned(
                        listOfNotNull(
                            Rect(0f, 0f, navigationIconWidth.toFloat(), barHeight.toFloat())
                                .takeIf { navigationIconWidth > 0 },
                            Rect((width - besideEndWidth).toFloat(), 0f, width.toFloat(), barHeight.toFloat()),
                        )
                    )
                    vehicles.forEach { it.place(navigationIconWidth, 0) }
                    navigationIcons.forEach { it.place(0, (barHeight - it.height) / 2) }
                    actionRows.forEach {
                        it.place(width - barPadding - actionsWidth, (barHeight - it.height) / 2)
                    }
                    titlePlaceables.forEach {
                        it.place(
                            width - barPadding - actionsWidth - BESIDE_TITLE_END.roundToPx() - it.width,
                            (barHeight - it.height) / 2,
                        )
                    }
                }
            }
        }
    }
}

/** Where the top bar goes, and the spare's readout with it */
private enum class Placement(val spareReadout: SpareReadout) {
    /** Over the vehicle, the spare's readout under the spare */
    ON_TOP(UNDER),

    /** Over the vehicle, the spare's readout beside the vehicle */
    ON_TOP_SPARE_BESIDE(SpareReadout.BESIDE),

    /**
     * The navigation icon in the top left corner, the title then the actions in the top right one,
     * the vehicle between them taking the whole height, the spare's readout beside it
     */
    BESIDE(BESIDE_CENTERED),
}

private enum class Slot { NAVIGATION_ICON, TITLE, ACTIONS, VEHICLE }

/** Material's top bar's padding at its ends, and around its title */
private val BAR_PADDING = 4.dp

/** Material's top bar's title's least distance from its start */
private val TITLE_INSET = 16.dp

/** Around the title beside the vehicle */
private val BESIDE_TITLE_START = 16.dp
private val BESIDE_TITLE_END = 8.dp

private const val MAX_END_SHARE = 1f / 2
