package com.masselis.tpmsadvanced.interfaces.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.R
import com.masselis.tpmsadvanced.core.ui.OnboardingPage
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.OnboardingPermissions
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.BELL
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.MODES
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.MONITOR_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.PERMISSIONS
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.PERSISTENT_MODE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.SCREEN_ON
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.THRESHOLDS
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.VEHICLE
import com.masselis.tpmsadvanced.interfaces.composable.Onboarding.Step.WELCOME
import com.masselis.tpmsadvanced.core.common.R as CommonR

/**
 * Where the introduction shown at the first launch is: full screen pages up to [Step.PERMISSIONS],
 * then the tour of the main screen, ending on the pressure thresholds, then the vehicle to add.
 * The app starts in manual mode, the tour only shows persistent mode, without turning it on.
 */
internal class Onboarding(
    val step: Step,
    private val onStep: (Step) -> Unit,
    private val onFinish: () -> Unit,
) {
    /** What the tour points at on the main screen, told by their bounds in the window's root */
    enum class Anchor { MONITORING, OVERFLOW }

    enum class Step {
        WELCOME,
        MODES,
        PERMISSIONS,
        SCREEN_ON,
        MONITOR_MODE,
        PERSISTENT_MODE,
        BELL,

        /** That a new vehicle's pressure thresholds are guesses, at the vehicle settings' item */
        THRESHOLDS,

        /** The vehicle that replaces the default one, see OnboardingVehicle */
        VEHICLE;

        /** The steps told by the progress dots, the vehicle's dialog being the end */
        val dotIndex: Int get() = ordinal

        val isPage: Boolean get() = this in listOf(WELCOME, MODES, PERMISSIONS)

        val isTour: Boolean get() = this in listOf(SCREEN_ON, MONITOR_MODE, PERSISTENT_MODE, BELL, THRESHOLDS)

        companion object {
            val dotCount: Int = VEHICLE.ordinal
        }
    }

    fun next() = Step.entries
        .getOrNull(step.ordinal + 1)
        ?.let(onStep)
        ?: onFinish()

    fun back() = Step.entries
        .getOrNull(step.ordinal - 1)
        ?.let(onStep)

    /** Straight to the vehicle, which the app can't do without: it would keep the default one */
    fun skip() = onStep(VEHICLE)

    fun finish() = onFinish()
}

/** The introduction's full screen pages, before the main screen, see [Onboarding.Step.isPage] */
@Composable
internal fun OnboardingPages(onboarding: Onboarding, modifier: Modifier = Modifier) {
    // Back from the first page leaves the app, as from the main screen
    BackHandler(enabled = onboarding.step != WELCOME) { onboarding.back() }
    when (onboarding.step) {
        WELCOME -> WelcomePage(onNext = onboarding::next, modifier = modifier)
        MODES -> ModesPage(onBack = onboarding::back, onNext = onboarding::next, onSkip = onboarding::skip, modifier)
        PERMISSIONS -> OnboardingPermissions(
            step = PERMISSIONS.dotIndex,
            stepCount = Onboarding.Step.dotCount,
            onBack = onboarding::back,
            onNext = onboarding::next,
            onSkip = onboarding::skip,
            modifier = modifier,
        )

        SCREEN_ON, MONITOR_MODE, PERSISTENT_MODE, BELL, THRESHOLDS, VEHICLE -> error("Not a page: ${onboarding.step}")
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit, modifier: Modifier = Modifier) = OnboardingPage(
    step = WELCOME.dotIndex,
    stepCount = Onboarding.Step.dotCount,
    primary = stringResource(CommonR.string.onboarding_get_started),
    onPrimary = onNext,
    modifier = modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // The launcher icon, whose adaptive icon can't be drawn as a resource: its layers instead
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
        )
        Text(
            text = stringResource(CommonR.string.onboarding_welcome_title, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )
        Text(
            text = stringResource(CommonR.string.onboarding_welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ModesPage(
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) = OnboardingPage(
    step = MODES.dotIndex,
    stepCount = Onboarding.Step.dotCount,
    primary = stringResource(CommonR.string.onboarding_next),
    onPrimary = onNext,
    secondary = stringResource(CommonR.string.back),
    onSecondary = onBack,
    onSkip = onSkip,
    modifier = modifier,
) {
    Text(
        text = stringResource(CommonR.string.onboarding_modes_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    listOf(
        CommonR.string.onboarding_manual_mode_title to CommonR.string.onboarding_manual_mode_text,
        CommonR.string.onboarding_persistent_mode_title to CommonR.string.onboarding_persistent_mode_text,
    ).forEach { (title, text) ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Text(stringResource(CommonR.string.onboarding_modes_footer))
}

@Preview
@Composable
private fun WelcomePagePreview() = TpmsAdvancedTheme { WelcomePage(onNext = {}) }

@Preview
@Composable
private fun ModesPagePreview() = TpmsAdvancedTheme { ModesPage(onBack = {}, onNext = {}, onSkip = {}) }
