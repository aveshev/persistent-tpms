package com.masselis.tpmsadvanced.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.masselis.tpmsadvanced.core.common.isUnderInstrumentation
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.Preconditions
import com.masselis.tpmsadvanced.ioc.Bindings
import java.util.UUID

@Composable
internal fun Main(expectedVehicle: UUID?) {
    val onboardingDone by Bindings.appPreferences.onboardingDone.collectAsState()
    // Lite only for now. The instrumented tests start from the main screen.
    val isOnboarding = onboardingDone.not() && AppTier.current == LITE && isUnderInstrumentation.not()
    // From the start each time it's shown, again from the settings too
    var step by rememberSaveable(isOnboarding) { mutableStateOf(Onboarding.Step.WELCOME) }
    val onboarding = Onboarding(
        step = step,
        onStep = { step = it },
        onFinish = { Bindings.appPreferences.onboardingDone.value = true },
    ).takeIf { isOnboarding }
    if (onboarding?.step?.isPage == true) OnboardingPages(onboarding)
    else Preconditions {
        Home(expectedVehicle, onboarding)
    }
}
