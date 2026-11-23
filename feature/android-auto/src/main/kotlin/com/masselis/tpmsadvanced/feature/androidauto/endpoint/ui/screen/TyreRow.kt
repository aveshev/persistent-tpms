package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import android.animation.ArgbEvaluator
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import androidx.car.app.Screen
import androidx.car.app.model.CarColor.createCustom
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Row
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.IconCompat.createWithResource
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized
import com.masselis.tpmsadvanced.core.common.now as nowSeconds
import com.masselis.tpmsadvanced.core.ui.DarkColors
import com.masselis.tpmsadvanced.core.ui.LightColors
import com.masselis.tpmsadvanced.core.ui.Orange
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.androidauto.R
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.State.Tabs.Readout
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.appendLoc
import com.masselis.tpmsadvanced.feature.main.interfaces.composable.elapsedLabel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State.Alerting
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State.DetectionIssue
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State.Normal
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State.NotDetected
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow

/**
 * A tyre's line after a dot in the tyre's colour: "Front left" over "31.1 psi · 20°C · 3 min",
 * then "Leak? · 2.6 V" when there's one, as the main screen's readout shows them. Each value takes
 * its alert's colour, "LOW!" or "HIGH!" follows a critical one. `now` (seconds) dates the reading.
 */
context(screen: Screen)
@Suppress("FunctionName", "FunctionNaming", "CyclomaticComplexMethod", "NestedBlockDepth", "MaxLineLength")
internal fun TyreRow(
    location: Vehicle.Kind.Location,
    iconState: TyreIconStateFlow.State,
    statsState: TyreStatsStateFlow.State,
    readout: Readout = Readout(),
    now: Double = nowSeconds(),
) = Row
    .Builder()
    .setTitle(buildString { appendLoc(location, false, capitalized = true) })
    .setImage(
        when (iconState) {
            is Alerting -> tyreAlertingIcon()
            is Normal -> tyreNormalIcon(iconState)
            NotDetected -> tyreNotDetectedIcon()
            DetectionIssue -> tyreDetectionIssueIcon()
        },
        Row.IMAGE_TYPE_SMALL
    )
    .apply {
        when (statsState) {
            is TyreStatsStateFlow.State.Detected -> {
                SpannableStringBuilder()
                    .appendIn(
                        statsState.pressure.string(statsState.pressureUnit)
                            // Explained on the vehicle's calibration page
                            .let { if (statsState.isPressureCalibrated) "$it*" else it },
                        statsState.levels[PRESSURE],
                        if (statsState.isHighPressure) localized(CommonR.string.high) else localized(CommonR.string.low),
                    )
                    .append(" · ")
                    // Only a high temperature alerts
                    .appendIn(statsState.temperature.string(statsState.temperatureUnit), statsState.levels[TEMPERATURE], localized(CommonR.string.high))
                    .apply { if (readout.showTimeSinceUpdate) append(" · ").append(elapsedLabel(statsState.timestamp, now)) }
                    .also(::addText)
                // The sensor's own alarm or the pressure falling while riding, then a battery,
                // shown when getting low whatever the setting
                listOfNotNull(
                    if (PRESSURE_LOSS in statsState.levels || SENSOR_ALARM in statsState.levels) localized(CommonR.string.leak) to AMBER else null,
                    (statsState.batteryVoltage?.string() ?: statsState.batteryPercent?.let { "$it%" })
                        ?.takeIf { readout.showBatteryVoltage || BATTERY in statsState.levels }
                        ?.let { it to statsState.levels[BATTERY] },
                )
                    .takeIf { it.isNotEmpty() }
                    ?.foldIndexed(SpannableStringBuilder()) { index, text, (value, level) ->
                        text.apply { if (index > 0) append(" · ") }.appendIn(value, level)
                    }
                    ?.also(::addText)
            }

            TyreStatsStateFlow.State.NotDetected -> addText(localized(CommonR.string.no_reading))
        }
    }
    .build()

context(screen: Screen)
private fun tyreNotDetectedIcon() = CarIcon
    .Builder(createWithResource(screen.carContext, R.drawable.status_dot_outline))
    .setTint(createCustom(LightColors.tertiary.toArgb(), DarkColors.tertiary.toArgb()))
    .build()

private val evaluator = ArgbEvaluator()

context(screen: Screen)
private fun tyreNormalIcon(state: Normal) = CarIcon
    .Builder(createWithResource(screen.carContext, R.drawable.status_dot))
    .setTint(
        createCustom(
            evaluator.evaluate(
                state.fraction.value,
                when (state) {
                    is Normal.BlueToGreen -> LightColors.tertiary.toArgb()
                    is Normal.GreenToRed -> LightColors.primary.toArgb()
                },
                when (state) {
                    is Normal.BlueToGreen -> LightColors.primary.toArgb()
                    is Normal.GreenToRed -> LightColors.error.toArgb()
                },
            ) as Int,
            evaluator.evaluate(
                state.fraction.value,
                when (state) {
                    is Normal.BlueToGreen -> DarkColors.tertiary.toArgb()
                    is Normal.GreenToRed -> DarkColors.primary.toArgb()
                },
                when (state) {
                    is Normal.BlueToGreen -> DarkColors.primary.toArgb()
                    is Normal.GreenToRed -> DarkColors.error.toArgb()
                },
            ) as Int,
        )
    )
    .build()

context(screen: Screen)
private fun tyreAlertingIcon() = CarIcon
    .Builder(createWithResource(screen.carContext, R.drawable.status_dot))
    .setTint(createCustom(LightColors.error.toArgb(), DarkColors.error.toArgb()))
    .build()

context(screen: Screen)
private fun tyreDetectionIssueIcon() = CarIcon
    .Builder(createWithResource(screen.carContext, R.drawable.status_dot_outline))
    .setTint(createCustom(LightColors.error.toArgb(), DarkColors.error.toArgb()))
    .build()

/** Appends [text] in the colour of its alert [level], followed by [criticalLabel] while crimson */
private fun SpannableStringBuilder.appendIn(
    text: String,
    level: AlertLevel?,
    criticalLabel: String = "",
): SpannableStringBuilder = apply {
    val start = length
    append(text)
    if (level == CRIMSON && criticalLabel.isNotEmpty()) append(" ").append(criticalLabel)
    when (level) {
        null -> null
        AMBER -> createCustom(Orange.toArgb(), Orange.toArgb())
        RED, CRIMSON -> createCustom(LightColors.error.toArgb(), DarkColors.error.toArgb())
    }?.also { setSpan(ForegroundCarColorSpan.create(it), start, length, SPAN_EXCLUSIVE_EXCLUSIVE) }
}
