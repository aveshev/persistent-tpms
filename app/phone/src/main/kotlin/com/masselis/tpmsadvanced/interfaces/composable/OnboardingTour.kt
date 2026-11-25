package com.masselis.tpmsadvanced.interfaces.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.core.ui.OnboardingStepDots
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.BELL
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.MONITOR_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.PERSISTENT_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.SCREEN_ON
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.THRESHOLDS
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * The tour of the main screen, drawn over it, which it dims and keeps from being touched: a card
 * telling the step, with an arrow to what the step is about. Persistent mode's and the thresholds'
 * steps draw the menu themselves, under the overflow button, with [menuItems]: a menu's popup would
 * be drawn over the tour. The step's item takes the modifier given, the arrow points at it.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun OnboardingTour(
    onboarding: Onboarding,
    anchors: Map<Onboarding.Anchor, Rect>,
    menuItems: @Composable ColumnScope.(targetModifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = onboarding.step
    BackHandler { onboarding.back() }
    // Every bound is read in the root, then drawn in this one's
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Measured again for each step's item
    var menu by remember(step) { mutableStateOf<Rect?>(null) }
    var menuItem by remember(step) { mutableStateOf<Rect?>(null) }
    val isMenu = step in listOf(PERSISTENT_MODE, THRESHOLDS)
    var card by remember { mutableStateOf<Rect?>(null) }
    val overflow = anchors[Onboarding.Anchor.OVERFLOW]?.translate(-origin)
    val target = when (step) {
        MONITOR_MODE, BELL -> anchors[Onboarding.Anchor.MONITORING]
        PERSISTENT_MODE, THRESHOLDS -> menuItem
        else -> null
    }?.translate(-origin)
    // The card goes under what it's about, the menu as a whole for its item
    val above = if (isMenu) menu?.translate(-origin) else target
    val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Nothing under it is touched, the card's buttons above it are
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // The menu is drawn over the dimmed screen, a hole only for a button of the top bar
            val hole = target
                ?.takeIf { isMenu.not() }
                ?.inflate(HIGHLIGHT_MARGIN.toPx())
            clipPath(
                path = Path().apply { hole?.also { addRoundRect(RoundRect(it, CornerRadius(it.minDimension / 2))) } },
                clipOp = ClipOp.Difference,
            ) { drawRect(scrim) }
        }
        if (isMenu && overflow != null) {
            // Under its button and against its end, as the menu opens
            Layout(
                content = {
                    Surface(
                        shape = MenuDefaults.shape,
                        color = MenuDefaults.containerColor,
                        tonalElevation = MenuDefaults.TonalElevation,
                        shadowElevation = MenuDefaults.ShadowElevation,
                        modifier = Modifier.onGloballyPositioned { menu = it.boundsInRoot() },
                    ) {
                        Column(
                            Modifier
                                .width(IntrinsicSize.Max)
                                .widthIn(min = 112.dp, max = 280.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            menuItems(Modifier.onGloballyPositioned { menuItem = it.boundsInRoot() })
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (overflow.right.roundToInt() - placeable.width).coerceAtLeast(0),
                        y = overflow.bottom.roundToInt(),
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            target?.inflate(HIGHLIGHT_MARGIN.toPx())?.also {
                drawRoundRect(
                    color = Color.White,
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(it.minDimension / 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            val currentCard = card?.translate(-origin)
            if (target != null && currentCard != null) {
                drawArrow(target, currentCard, fromStart = isMenu)
            }
        }
        Layout(
            content = {
                TourCard(
                    onboarding = onboarding,
                    text = stringResource(
                        when (step) {
                            SCREEN_ON -> CommonR.string.onboarding_tour_screen_on
                            MONITOR_MODE -> CommonR.string.onboarding_tour_monitor_mode
                            PERSISTENT_MODE -> CommonR.string.onboarding_tour_persistent_mode
                            THRESHOLDS -> CommonR.string.onboarding_thresholds_text
                            else -> CommonR.string.onboarding_tour_bell
                        }
                    ),
                    modifier = Modifier.onGloballyPositioned { card = it.boundsInRoot() },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = CARD_MARGIN.roundToPx()
            val placeable = measurables.single().measure(
                Constraints(
                    maxWidth = (constraints.maxWidth - 2 * margin).coerceIn(0, CARD_MAX_WIDTH.roundToPx()),
                    maxHeight = (constraints.maxHeight - 2 * margin).coerceAtLeast(0),
                )
            )
            val lowest = constraints.maxHeight - placeable.height - margin
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(
                    x = (constraints.maxWidth - placeable.width) / 2,
                    // Room left above it for the arrow, or in the middle with nothing to point at
                    y = above
                        ?.let { (it.bottom + ARROW_ROOM.toPx()).roundToInt().coerceAtMost(lowest) }
                        ?: (constraints.maxHeight - placeable.height) / 2,
                )
            }
        }
    }
}

@Composable
private fun TourCard(onboarding: Onboarding, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        // Scrolls when the largest font sizes make it taller than the screen
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            OnboardingStepDots(step = onboarding.step.dotIndex, stepCount = Onboarding.Step.dotCount)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onboarding::skip) { Text(stringResource(CommonR.string.onboarding_skip)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onboarding::back) { Text(stringResource(CommonR.string.back)) }
                Button(onClick = onboarding::next) { Text(stringResource(CommonR.string.onboarding_next)) }
            }
        }
    }
}

/**
 * From the [card]'s top edge to the [target]: to its bottom, or with [fromStart] to its start
 * side, curving up then across: a menu's item has others under it. Not drawn when the card
 * covers the target, the screen being too short for both.
 */
private fun DrawScope.drawArrow(target: Rect, card: Rect, fromStart: Boolean) {
    val gap = ARROW_GAP.toPx()
    val end = if (fromStart) Offset(target.left - gap, target.center.y)
    else Offset(target.center.x, target.bottom + gap)
    val start = Offset(
        x = if (fromStart) (card.left + ARROW_START_INSET.toPx()).coerceAtMost(end.x)
        else end.x.coerceIn(card.left + ARROW_START_INSET.toPx(), card.right - ARROW_START_INSET.toPx()),
        y = card.top - gap,
    )
    if (start.y - end.y < ARROW_MIN_LENGTH.toPx()) return
    val control = if (fromStart) Offset(start.x, end.y) else (start + end) / 2f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    drawPath(
        path = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(control.x, control.y, end.x, end.y)
        },
        color = Color.White,
        style = stroke,
    )
    // The head, along the curve's direction at its end
    val angle = atan2(end.y - control.y, end.x - control.x)
    val headLength = ARROW_HEAD.toPx()
    listOf(angle + ARROW_HEAD_ANGLE, angle - ARROW_HEAD_ANGLE).forEach {
        drawLine(
            color = Color.White,
            start = end,
            end = Offset(end.x - headLength * cos(it), end.y - headLength * sin(it)),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private const val SCRIM_ALPHA = .7f
private val HIGHLIGHT_MARGIN = 4.dp
private val CARD_MARGIN = 16.dp
private val CARD_MAX_WIDTH = 420.dp
private val ARROW_ROOM = 72.dp
private val ARROW_GAP = 6.dp
private val ARROW_START_INSET = 40.dp
private val ARROW_MIN_LENGTH = 24.dp
private val ARROW_HEAD = 12.dp
private const val ARROW_HEAD_ANGLE = .5f
