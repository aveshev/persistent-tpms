package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.model.CarColor.createCustom
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Row
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.IconCompat.createWithResource
import com.masselis.tpmsadvanced.core.ui.DarkColors
import com.masselis.tpmsadvanced.core.ui.LightColors
import com.masselis.tpmsadvanced.feature.androidauto.R
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.Speech
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized

/**
 * Silences the alerts' speech for a while, as the main screen's button does. Always there with the
 * same title, only its text and icon change: the host takes it for a refresh of the same screen
 * rather than a new one, which it only allows a few times in a row.
 */
context(screen: Screen)
@Suppress("FunctionName", "FunctionNaming", "MaxLineLength")
internal fun SpeechRow(
    speech: Speech,
    onSilence: (isCritical: Boolean) -> Unit,
    onUnmute: () -> Unit,
) = Row
    .Builder()
    .setTitle(localized(CommonR.string.alert_speech))
    .setImage(
        CarIcon
            .Builder(createWithResource(screen.carContext, if (speech is Speech.Silenced) R.drawable.volume_off else R.drawable.volume_up))
            .setTint(
                if (speech is Speech.Offer) createCustom(LightColors.error.toArgb(), DarkColors.error.toArgb())
                else createCustom(LightColors.onSurfaceVariant.toArgb(), DarkColors.onSurfaceVariant.toArgb())
            )
            .build(),
        Row.IMAGE_TYPE_SMALL
    )
    .addText(
        when (speech) {
            Speech.On -> localized(CommonR.string.text_on)
            is Speech.Offer ->
                if (speech.isCritical) localized(CommonR.string.aa_silence_all, SilenceAlertsUseCase.DURATION.inWholeMinutes)
                else localized(CommonR.string.aa_silence, SilenceAlertsUseCase.DURATION.inWholeMinutes)

            is Speech.Silenced -> localized(CommonR.string.aa_off, speech.minutesLeft)
        }
    )
    .apply {
        when (speech) {
            Speech.On -> Unit
            is Speech.Offer -> setOnClickListener { onSilence(speech.isCritical) }
            is Speech.Silenced -> setOnClickListener(onUnmute)
        }
    }
    .build()
