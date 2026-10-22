package com.masselis.tpmsadvanced.feature.background.interfaces.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.background.R
import com.masselis.tpmsadvanced.feature.background.interfaces.ui.rememberPermissionJourney
import com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel.PhoneIdleMechanismViewModel
import com.masselis.tpmsadvanced.feature.background.ioc.Bindings
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.DEEP_DOZE
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.NO_SIGNIFICANT_MOTION
import com.masselis.tpmsadvanced.feature.background.usecase.PhoneIdleUseCase.Mechanism.STANDING_STILL

/**
 * What tells the phone is idle, to suspend scanning. The mechanisms not selected are tracked too
 * and only logged, to compare them on the same ride.
 */
@Composable
public fun PhoneIdleMechanismSettingsItem(modifier: Modifier = Modifier): Unit =
    PhoneIdleMechanismSettingsItem(
        modifier,
        viewModel { Bindings.featureBackgroundInternal.phoneIdleMechanismViewModel() },
    )

@Composable
internal fun PhoneIdleMechanismSettingsItem(
    modifier: Modifier = Modifier,
    viewModel: PhoneIdleMechanismViewModel = viewModel {
        Bindings.featureBackgroundInternal.phoneIdleMechanismViewModel()
    },
) {
    val mechanism by viewModel.mechanism.collectAsState(initial = PhoneIdleUseCase.DEFAULT_MECHANISM)
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Only selected once the permission is held, so nothing is left to turn off on a refusal
    val journey = rememberPermissionJourney(
        permissions = viewModel.requiredActivityPermissions(),
        systemPopupFirst = true,
        rationale = "Telling the phone stands still needs the physical activity permission." +
                " In the app's settings, open Permissions, then allow Physical activity.",
        disabledInfo = "The phone idle mechanism was not changed, because the physical activity" +
                " permission was not granted.",
        isSettingsOnScreen = { true },
        onDenied = {},
    )
    Box(modifier) {
        TextSettingsItem(
            headline = "Phone idle mechanism",
            supporting = "${mechanism.label}, suspends scanning. The others are only logged",
            onClick = { expanded = true },
            modifier = Modifier.testTag(PhoneIdleMechanismSettingsItemTags.mechanism),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PhoneIdleUseCase.mechanisms().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        if (option == STANDING_STILL) journey.request { viewModel.setMechanism(option) }
                        else viewModel.setMechanism(option)
                    },
                    trailingIcon = {
                        if (option == mechanism) Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.check_24px),
                            contentDescription = "Selected",
                        )
                    },
                    modifier = Modifier.testTag("${PhoneIdleMechanismSettingsItemTags.option}_${option.name}"),
                )
            }
        }
    }
}

private val Mechanism.label
    get() = when (this) {
        DEEP_DOZE -> "Deep Doze"
        NO_SIGNIFICANT_MOTION -> "No significant motion for ${PhoneIdleUseCase.QUIET_PERIOD}"
        STANDING_STILL -> "Still (${PhoneIdleUseCase.STILL_MIN_CONFIDENCE}%+) for ${PhoneIdleUseCase.QUIET_PERIOD}"
    }

@Suppress("ConstPropertyName")
internal object PhoneIdleMechanismSettingsItemTags {
    const val mechanism = "PhoneIdleMechanismSettingsItemTags_mechanism"
    const val option = "PhoneIdleMechanismSettingsItemTags_option"
}
