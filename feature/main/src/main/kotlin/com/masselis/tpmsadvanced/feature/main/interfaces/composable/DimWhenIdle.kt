package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass.Initial
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.TyreDisplaySettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Lowers the screen's brightness over [content] once nothing happened for as long as the settings
 * say, see [DimDisplayDelay]: no touch, no new warning or alert of the shown vehicle's tyres. While
 * [isAlerting], a red or critical alert of any vehicle going on, it stays bright.
 *
 * Dimmed, a touch only brightens the screen: [content], and the dialogs over it, get nothing until
 * every finger is lifted.
 */
@Composable
public fun DimWhenIdle(
    isAlerting: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Unit = DimWhenIdle(
    isAlerting,
    modifier,
    LocalVehicleComponent.current,
    viewModel { TyreDisplaySettingsViewModel() },
    content,
)

@Suppress("LongMethod")
@Composable
internal fun DimWhenIdle(
    isAlerting: Boolean,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    settings: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
    content: @Composable () -> Unit,
) {
    val isEnabled by settings.dimDisplay.collectAsState()
    val seconds by settings.dimDisplaySeconds.collectAsState()
    // Back from another app, or with the screen turned back on, it starts bright
    val isResumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(RESUMED)
    val warnings = vehicleComponent.vehicle.kind.locations
        .flatMap { location ->
            (vehicleComponent.TyreComponent(location).tyreStatsStateFlow.collectAsState().value as? State.Detected)
                ?.levels
                .orEmpty()
                .map { (alertClass, level) -> (location to alertClass) to level }
        }
        .toMap()
    // Each one starts the wait over: a touch, a new warning
    var wakes by remember { mutableIntStateOf(0) }
    // Only read by the effect below, it doesn't recompose anything
    var warned by remember(vehicleComponent) { mutableStateOf(emptyMap<Pair<Location, AlertClass>, AlertLevel>()) }
    LaunchedEffect(warnings) {
        if (warnings.raisesFrom(warned)) wakes++
        warned = warnings
    }
    var isDimmed by remember { mutableStateOf(false) }
    LaunchedEffect(isEnabled, seconds, isAlerting, isResumed, wakes) {
        isDimmed = false
        if (isEnabled && isAlerting.not() && isResumed) {
            delay(seconds.seconds)
            isDimmed = true
        }
    }
    // The touch which brightened the screen, until its fingers are lifted
    var isWaking by remember { mutableStateOf(false) }
    Box(
        modifier.pointerInput(Unit) {
            // Before anything inside can consume it, without consuming it either
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = Initial)
                wakes++
            }
        }
    ) {
        content()
        // A window of its own, shown after the dialogs over the main screen so it's over them too:
        // they're windows of their own, which the content's touches never reach. It holds the low
        // brightness, the screen's brightness comes back with it gone.
        if (isDimmed || isWaking) Dialog(
            // Not focusable, it doesn't get the back key, the window under it does
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            LocalView.current.also { view ->
                SideEffect {
                    (view.parent as? DialogWindowProvider)?.window?.apply {
                        setDimAmount(0f)
                        // Leaves the focus, and the keyboard, to the window under it
                        addFlags(FLAG_NOT_FOCUSABLE)
                        attributes = attributes.apply {
                            screenBrightness = if (isDimmed) DIMMED_BRIGHTNESS else BRIGHTNESS_OVERRIDE_NONE
                            // Over the cutout too, in landscape, whose touches would go through
                            if (SDK_INT >= P) layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(DimWhenIdleTags.overlay)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            wakes++
                            isWaking = true
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                isWaking = false
                            }
                        }
                    }
            )
        }
    }
}

/** Whether a tyre warns or alerts of something it didn't in [previous], or at a higher level */
internal fun Map<Pair<Location, AlertClass>, AlertLevel>.raisesFrom(
    previous: Map<Pair<Location, AlertClass>, AlertLevel>
): Boolean = any { (key, level) -> previous[key]?.let { level > it } ?: true }

/**
 * The lowest brightness the window can ask for, short of 0, which turns the backlight off on some
 * phones
 */
private const val DIMMED_BRIGHTNESS = 0.01f

@Suppress("ConstPropertyName")
internal object DimWhenIdleTags {
    const val overlay = "DimWhenIdleTags_overlay"
}
