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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    /** Given the corners beside it, see [Vehicle]'s */
    vehicle: @Composable (corners: Pair<Dp, Dp>?) -> Unit,
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
        // The vehicle's box for each placement, the whole width, and the corners beside it
        fun Placement.boxHeight() = when (this) {
            Placement.ON_TOP -> height - barHeight
            Placement.BESIDE -> height
        }
        fun Placement.corners() = when (this) {
            Placement.ON_TOP -> null
            Placement.BESIDE -> navigationIconWidth.toDp() to besideEndWidth.toDp()
        }
        val placement = Placement.entries.maxBy { placement ->
            placement
                .boxHeight()
                .takeIf { width > 0 && it > 0 }
                // Without the minimum height, which only lets the readouts go past the box
                ?.let { boxHeight ->
                    boxHeight * sizing.imageHeight(width.toDp(), boxHeight.toDp(), placement.corners(), 0f)
                }
                ?: 0f
        }
        val vehicles = subcompose(Slot.VEHICLE) { vehicle(placement.corners()) }
            .map { it.measure(Constraints.fixed(width, placement.boxHeight().coerceAtLeast(0))) }
        when (placement) {
            Placement.ON_TOP -> {
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
                    vehicles.forEach { it.place(0, 0) }
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

/** Where the top bar goes */
private enum class Placement {
    /** Over the vehicle, a spare's readout under the spare */
    ON_TOP,

    /**
     * The navigation icon in the top left corner, the title then the actions in the top right one,
     * the vehicle between them taking the whole height, a spare's readout beside it
     */
    BESIDE,
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
