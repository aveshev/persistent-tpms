package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.PowerManager
import android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
import android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass.Initial
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Lowers the screen's brightness over [content] once nothing happened for as long as the settings
 * say, see [DimDisplayDelay]: no touch, no new warning or alert of the shown vehicle's tyres. While
 * [isAlerting], a red or critical alert of any vehicle going on, it stays bright.
 *
 * Dimmed, a touch only brightens the screen: [content], and the dialogs over it, get nothing until
 * every finger is lifted. [content] is told whether it's dimmed, to say so, see [DimmedNotice].
 * Dimmed, and while the pocket guard below watches, the screen's orientation is locked: turning
 * the phone into a pocket would otherwise recreate the activity, which forgets it was dimmed.
 *
 * With the pocket guard on, while dimmed and for [POCKET_GRACE] after, the proximity sensor is
 * watched: covered (the phone put in a pocket, the screen looking off), the screen goes black and
 * dimmed, saying touches are blocked somewhere else every [BLOCKED_SHIFT] so it isn't burnt in,
 * and touches do nothing, not even brighten it, until uncovered. Meanwhile the screen is turned
 * off as during a phone call, the system ignoring its touches, and back on once uncovered, still
 * unlocked: the app stays open and scanning in the pocket. A toast then says why it went off, the
 * screen being off for as long as there would be something to say on it. On a phone which can't
 * turn its screen off this way, the black screen says it instead, and the system bars are hidden:
 * a first swipe from an edge only brings them back for a moment rather than opening the shade or
 * going home. Not otherwise, the main screen being laid out again for them.
 */
@Composable
public fun DimWhenIdle(
    isAlerting: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (isDimmed: Boolean) -> Unit,
): Unit = DimWhenIdle(
    isAlerting,
    modifier,
    LocalVehicleComponent.current,
    viewModel { TyreDisplaySettingsViewModel() },
    content,
)

@Suppress("LongMethod", "CyclomaticComplexMethod", "MaxLineLength")
@Composable
internal fun DimWhenIdle(
    isAlerting: Boolean,
    modifier: Modifier = Modifier,
    vehicleComponent: VehicleComponent = LocalVehicleComponent.current,
    settings: TyreDisplaySettingsViewModel = viewModel { TyreDisplaySettingsViewModel() },
    content: @Composable (isDimmed: Boolean) -> Unit,
) {
    val isEnabled by settings.dimDisplay.collectAsState()
    val seconds by settings.dimDisplaySeconds.collectAsState()
    val isPocketGuard by settings.pocketGuard.collectAsState()
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
    // Brightened again a moment ago: the phone may be on its way into a pocket all the same
    var isJustWoken by remember { mutableStateOf(false) }
    LaunchedEffect(isDimmed || isWaking) {
        if (isDimmed || isWaking) isJustWoken = true
        else {
            delay(POCKET_GRACE)
            isJustWoken = false
        }
    }
    val isDimmedOrJustWoken = isDimmed || isWaking || isJustWoken
    val proximity = LocalContext.current.let { remember(it) { Proximity(it) } }
    // Once covered, watched until uncovered whatever the screen does meanwhile
    val isPocketWatched = isPocketGuard && isResumed && (isDimmedOrJustWoken || proximity.isCovered)
    DisposableEffect(proximity, isPocketWatched) {
        if (isPocketWatched) proximity.start()
        onDispose { proximity.stop() }
    }
    val isBlocked = proximity.isCovered
    if (isDimmedOrJustWoken || isBlocked) LocalActivity.current?.also { activity ->
        DisposableEffect(activity) {
            val requested = activity.requestedOrientation
            activity.requestedOrientation = SCREEN_ORIENTATION_LOCKED
            onDispose { activity.requestedOrientation = requested }
        }
    }
    val powerManager = LocalContext.current.let { remember(it) { it.getSystemService(PowerManager::class.java) } }
    val canTurnScreenOff = remember(powerManager) {
        powerManager?.isWakeLockLevelSupported(PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (isBlocked && canTurnScreenOff) DisposableEffect(powerManager) {
        // Released once uncovered, or with the app no longer resumed, which stops the sensor
        @SuppressLint("WakelockTimeout")
        val screenOff = powerManager
            ?.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, "TPMS:pocket")
            ?.apply { acquire() }
        onDispose {
            screenOff?.release(RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            // Uncovered, rather than the sensor stopped with the app leaving the screen
            if (lifecycle.currentState.isAtLeast(RESUMED))
                Toast.makeText(context, "Screen was turned off by the proximity sensor", LENGTH_LONG).show()
        }
    }
    if (isBlocked && canTurnScreenOff.not()) LocalActivity.current?.window?.also { window ->
        DisposableEffect(window) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(systemBars())
            }
            onDispose { WindowCompat.getInsetsController(window, window.decorView).show(systemBars()) }
        }
    }
    Box(
        modifier.pointerInput(Unit) {
            // Before anything inside can consume it, without consuming it either
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = Initial)
                wakes++
            }
        }
    ) {
        content(isDimmed)
        // A window of its own, shown after the dialogs over the main screen so it's over them too:
        // they're windows of their own, which the content's touches never reach. It holds the low
        // brightness, the screen's brightness comes back with it gone.
        if (isDimmed || isWaking || isBlocked) Dialog(
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
                            screenBrightness = if (isDimmed || isBlocked) DIMMED_BRIGHTNESS else BRIGHTNESS_OVERRIDE_NONE
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
                    .then(if (isBlocked && canTurnScreenOff.not()) Modifier.background(Color.Black) else Modifier)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            // Blocked, the whole gesture is swallowed without waking anything
                            if (proximity.isCovered) {
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                return@awaitEachGesture
                            }
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
            ) {
                if (isBlocked && canTurnScreenOff.not()) BlockedTouches()
            }
        }
    }
}

/** Says touches are blocked, somewhere else every [BLOCKED_SHIFT] so it isn't burnt in */
@Composable
private fun BlockedTouches() {
    // Where the text is, in fractions of the room left around it
    var at by remember { mutableStateOf(0.5f to 0.5f) }
    LaunchedEffect(Unit) {
        while (true) {
            at = Random.nextFloat() to Random.nextFloat()
            delay(BLOCKED_SHIFT)
        }
    }
    Text(
        text = "Proximity sensor active\nTouches are blocked",
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .testTag(DimWhenIdleTags.blocked)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        ((constraints.maxWidth - placeable.width).coerceAtLeast(0) * at.first).roundToInt(),
                        ((constraints.maxHeight - placeable.height).coerceAtLeast(0) * at.second).roundToInt(),
                    )
                }
            },
    )
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

/** How long after brightening again the proximity sensor is still watched */
private val POCKET_GRACE = 3.seconds

/** How often the blocked touches' text moves */
private val BLOCKED_SHIFT = 5.seconds

@Suppress("ConstPropertyName")
internal object DimWhenIdleTags {
    const val overlay = "DimWhenIdleTags_overlay"
    const val blocked = "DimWhenIdleTags_blocked"
    const val dimmed = "DimWhenIdleTags_dimmed"
}
