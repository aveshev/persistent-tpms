package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.OnboardingVehicleViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.OnboardingVehicleViewModel

/**
 * The introduction's last step: the "Add a new vehicle" dialog, whose vehicle replaces the one the
 * app was installed with. Cancelled, that one stays. [onDone] right away when that one was
 * already used or changed, the introduction being shown again from the settings.
 */
@Composable
public fun OnboardingVehicle(onDone: () -> Unit): Unit =
    OnboardingVehicle(onDone, viewModel { OnboardingVehicleViewModel() })

@Composable
internal fun OnboardingVehicle(onDone: () -> Unit, viewModel: OnboardingVehicleViewModel) {
    val currentOnDone by rememberUpdatedState(onDone)
    when (viewModel.hasDefault.collectAsState().value) {
        null -> Unit
        false -> LaunchedEffect(Unit) { currentOnDone() }
        true -> AddVehicle(
            onDismissRequest = onDone,
            onVehicleAdd = { name, kind -> viewModel.replace(name.trim(), kind) { currentOnDone() } },
        )
    }
}
