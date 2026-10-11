@file:Suppress("NAME_SHADOWING", "TooManyFunctions")

package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.animation.ArgbEvaluator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.Fraction
import com.masselis.tpmsadvanced.core.ui.restartApp
import com.masselis.tpmsadvanced.core.ui.viewModel
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.feature.main.R
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreBindings.Companion.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.TyreComponent
import com.masselis.tpmsadvanced.feature.main.ioc.tyre.TyreComponent.Companion.keyed
import com.masselis.tpmsadvanced.feature.main.ioc.vehicle.VehicleComponent
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val evaluator = ArgbEvaluator()

private val logger = Logger.withTag("Tyre")

/** A tyre's width over its height, seen from above */
internal const val TYRE_ASPECT_RATIO = 15f / 40f

@Suppress("LongMethod")
@Composable
internal fun Tyre(
    location: Location,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    /** An alerting tyre blinks, but on the sensors' page */
    blinks: Boolean = true,
    /** Its width over its height, a monowheel's is narrower */
    aspectRatio: Float = TYRE_ASPECT_RATIO,
    vehicleComponent: Lazy<VehicleComponent> = lazyOf(LocalVehicleComponent.current),
    viewModel: TyreIconViewModel = vehicleComponent.value
        .TyreComponent(location)
        .let { viewModel(it.keyed()) { it.TyreIconViewModel() } },
) {
    val state by viewModel.stateFlow.collectAsState()
    Tyre(state, snackbarHostState, modifier, logName = "$location", blinks = blinks, aspectRatio = aspectRatio)
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "MaxLineLength")
@Composable
internal fun Tyre(
    state: State,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    // Names the tyre in the logs, null for the demo tyres which aren't logged
    logName: String? = null,
    blinks: Boolean = true,
    aspectRatio: Float = TYRE_ASPECT_RATIO,
) {
    // Under 3 flashes a second either way, see docs/alerts.md
    val isHidden = (state as? State.Alerting)
        ?.takeIf { blinks }
        ?.let { isFirstBlinkPhase(if (it.isCritical) CRITICAL_BLINK else BLINK).not() }
        ?: false
    // Traces #35, a tyre not drawn at all, whose cause is unknown. The drawn state, to compare with
    // TyreIconStateFlow's logs, and a warning when the tyre stays hidden longer than a blink phase.
    if (logName != null) {
        LaunchedEffect(state) { logger.d { "$logName drawn as $state" } }
        LaunchedEffect(isHidden) {
            if (isHidden) {
                delay(1.seconds)
                logger.w { "$logName hidden for over 1 s, blinking is stuck. State: $state" }
            }
        }
    }
    Box(
        modifier
            .alpha(if (isHidden) 0f else 1f)
            .clip(RoundedCornerShape(percent = 20))
            .aspectRatio(aspectRatio)
            .run {
                when (val state = state) {
                    State.NotDetected -> this
                        .background(Color.Transparent)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onBackground,
                            RoundedCornerShape(percent = 20)
                        )

                    is State.Normal ->
                        background(
                            Color(
                                evaluator.evaluate(
                                    state.fraction.value,
                                    when (state) {
                                        is State.Normal.BlueToGreen -> MaterialTheme.colorScheme.tertiary.toArgb()
                                        is State.Normal.GreenToRed -> MaterialTheme.colorScheme.primary.toArgb()
                                    },
                                    when (state) {
                                        is State.Normal.BlueToGreen -> MaterialTheme.colorScheme.primary.toArgb()
                                        is State.Normal.GreenToRed -> MaterialTheme.colorScheme.error.toArgb()
                                    },
                                ) as Int
                            )
                        )

                    is State.Alerting ->
                        background(MaterialTheme.colorScheme.error)

                    State.DetectionIssue -> this
                        .background(Color.Transparent)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.error,
                            RoundedCornerShape(percent = 20)
                        )
                }
            }
    ) {
        if (state is State.DetectionIssue) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            AlertButton(
                disableBluetooth = {
                    try {
                        context.startActivity(Intent().apply { action = ACTION_BLUETOOTH_SETTINGS })
                    } catch (_: ActivityNotFoundException) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No bluetooth settings screen were found on your device, please disable bluetooth by yourself")
                        }
                    }
                },
                restartApp = context::restartApp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .matchParentSize()
            )
        }
    }
}

@Composable
private fun AlertButton(
    disableBluetooth: () -> Unit,
    restartApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { showDialog = true },
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.alert_octagon),
            contentDescription = "There is an issue to find the sensor",
            tint = MaterialTheme.colorScheme.error,
        )
    }
    if (showDialog)
        AlertDialog(
            text = {
                Text("Persistent TPMS is unable to find your sensor, try to disable bluetooth or restart the app")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { disableBluetooth(); showDialog = false }) {
                        Text("Disable Bluetooth")
                    }
                    TextButton(onClick = restartApp) {
                        Text("Restart the app")
                    }
                }
            },
            onDismissRequest = { showDialog = false },
        )
}

@Preview
@Composable
internal fun NotDetectedPreview() {
    Tyre(State.NotDetected, SnackbarHostState())
}

@Preview
@Composable
internal fun BlueToGreenPreview() {
    Tyre(State.Normal.BlueToGreen(Fraction(0f)), SnackbarHostState())
}

@Preview
@Composable
internal fun BlueToGreen2Preview() {
    Tyre(State.Normal.BlueToGreen(Fraction(0.5f)), SnackbarHostState())
}

@Preview
@Composable
internal fun BlueToGreen3Preview() {
    Tyre(State.Normal.BlueToGreen(Fraction(1f)), SnackbarHostState())
}

@Preview
@Composable
internal fun GreenToRedPreview() {
    Tyre(State.Normal.GreenToRed(Fraction(0f)), SnackbarHostState())
}

@Preview
@Composable
internal fun GreenToRed2Preview() {
    Tyre(State.Normal.GreenToRed(Fraction(0.5f)), SnackbarHostState())
}

@Preview
@Composable
internal fun GreenToRed3Preview() {
    Tyre(State.Normal.GreenToRed(Fraction(1f)), SnackbarHostState())
}

@Preview
@Composable
internal fun AlertingPreview() {
    Tyre(State.Alerting(), SnackbarHostState())
}

@Preview
@Composable
internal fun CriticalPreview() {
    Tyre(State.Alerting(isCritical = true), SnackbarHostState())
}

@Preview
@Composable
internal fun DetectionIssuePreview() {
    Tyre(State.DetectionIssue, SnackbarHostState())
}
