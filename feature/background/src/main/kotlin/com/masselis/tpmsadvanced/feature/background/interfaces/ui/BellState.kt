package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE

internal enum class BellState {
    /** Red, crossed out: notifications or the battery exemption are missing */
    NeedsPermission,

    /** Green */
    Active,

    /**
     * Green: Lite scanning without any Bluetooth device or beacon to activate it, so all the time, as
     * the iOS app always does. Pro's "always" is a condition of its own that can be suspended like
     * the others, it is [Active].
     */
    Always,

    /**
     * Blue, like Bluetooth: scanning because a Bluetooth device is connected or a beacon is near, or
     * on Lite also during "Stay active" after one, as its only conditions are those. Pro's "always"
     * overrides it, as it doesn't depend on them. The manual monitoring button stays [Active].
     */
    Bluetooth,

    /** Orange: an activate condition holds but a suspend condition overrides it */
    Suspended,

    /** Neutral: nothing to activate */
    Idle;

    companion object {
        fun of(permissionsOk: Boolean, decision: ScanDecision, tier: AppTier = AppTier.current): BellState = when {
            permissionsOk.not() -> NeedsPermission
            decision is ScanDecision.Active && tier == LITE && ALWAYS in decision.causes -> Always
            decision is ScanDecision.Active && ALWAYS !in decision.causes && decision.causes.any {
                it == BLUETOOTH || it == BEACON || (tier == LITE && it == STAY_ACTIVE)
            } -> Bluetooth
            decision is ScanDecision.Active -> Active
            decision is ScanDecision.Suspended -> Suspended
            else -> Idle
        }
    }
}
