package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.AlertSilenceViewModel
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase.State
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * Silences the alerts' speech for a while, shown over the vehicle, clear of its readings, while a
 * red or critical alert is notified, see docs/alerts.md. Big enough to hit while riding, and as wide
 * as it's given. Its icon tells whether the alerts are audible now, its text what a tap does.
 */
@Composable
public fun SilenceAlertsButton(modifier: Modifier = Modifier): Unit = SilenceAlertsButton(
    modifier,
    viewModel { Bindings.featureBackgroundInternal.alertSilenceViewModel() },
)

@Composable
internal fun SilenceAlertsButton(
    modifier: Modifier = Modifier,
    viewModel: AlertSilenceViewModel,
) {
    val state by viewModel.stateFlow.collectAsState()
    SilenceAlertsButton(state, viewModel::silence, viewModel::unmute, modifier)
}

@Composable
private fun SilenceAlertsButton(
    state: State,
    onSilence: (isCritical: Boolean) -> Unit,
    onUnmute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In sp, it's smaller with its text where it's shown smaller, see ClearOfReadouts. 72 dp at the
    // usual 110 % font size.
    val buttonModifier = modifier.heightIn(min = with(LocalDensity.current) { 65.sp.toDp() })
    // Little vertically, its text is large and the room for it over the vehicle may be short
    val padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    when (state) {
        State.Disabled, State.Idle -> Unit
        is State.Offer -> Button(
            onClick = { onSilence(state.isCritical) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isCritical) CRIMSON else MaterialTheme.colorScheme.error,
                contentColor = Color.White,
            ),
            contentPadding = padding,
            modifier = buttonModifier.testTag(SilenceAlertsButtonTags.silence),
        ) {
            Icon(painterResource(R.drawable.volume_up), contentDescription = null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            // Wrapped to the width it's given, see ClearOfReadouts, "for 10 min" kept together
            Text(
                text = "Silence ${if (state.isCritical) "critical alerts" else "alerts"} " +
                    "for\u00A0${SilenceAlertsUseCase.DURATION.inWholeMinutes}\u00A0min",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }

        is State.Silenced -> FilledTonalButton(
            onClick = onUnmute,
            contentPadding = padding,
            modifier = buttonModifier.testTag(SilenceAlertsButtonTags.unmute),
        ) {
            val minutesLeft by produceState(state.minutesLeft(), state.until) {
                while (true) {
                    value = state.minutesLeft()
                    delay(1.seconds)
                }
            }
            Icon(painterResource(R.drawable.volume_off), contentDescription = null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.isCritical) "All alerts silenced" else "Alerts silenced",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "$minutesLeft min left, tap to unmute",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun State.Silenced.minutesLeft() =
    ceil((until - System.currentTimeMillis()).coerceAtLeast(0) / MILLIS_PER_MINUTE).toInt()

private const val MILLIS_PER_MINUTE = 60_000.0

/** The critical alerts' notification colour */
@Suppress("MagicNumber")
private val CRIMSON = Color(0xFF7A0010)

@Suppress("ConstPropertyName")
internal object SilenceAlertsButtonTags {
    const val silence = "SilenceAlertsButtonTags_silence"
    const val unmute = "SilenceAlertsButtonTags_unmute"
}

@Preview
@Composable
private fun SilenceAlertsPreview() = SilenceAlertsButton(State.Offer(isCritical = false), {}, {})

@Preview
@Composable
private fun SilenceCriticalAlertsPreview() = SilenceAlertsButton(State.Offer(isCritical = true), {}, {})

@Suppress("MagicNumber")
@Preview
@Composable
private fun AlertsSilencedPreview() = SilenceAlertsButton(
    State.Silenced(isCritical = false, until = System.currentTimeMillis() + 8 * MILLIS_PER_MINUTE.toLong()),
    {},
    {},
)
