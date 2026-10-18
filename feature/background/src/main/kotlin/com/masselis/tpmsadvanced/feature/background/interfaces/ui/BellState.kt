package com.masselis.tpmsadvanced.feature.background.interfaces.ui

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS

internal enum class BellState {
    /** Red, crossed out: notifications or the battery exemption are missing */
    NeedsPermission,

    /** Green */
    Active,

    /**
     * Blue: Lite scanning without any Bluetooth device or beacon to activate it, so all the time.
     * Pro's "always" is a condition of its own that can be suspended like the others, it stays green.
     */
    Always,

    /** Orange: an activate condition holds but a suspend condition overrides it */
    Suspended,

    /** Neutral: nothing to activate */
    Idle;

    companion object {
        fun of(permissionsOk: Boolean, decision: ScanDecision, tier: AppTier = AppTier.current): BellState = when {
            permissionsOk.not() -> NeedsPermission
            tier == LITE && decision is ScanDecision.Active && ALWAYS in decision.causes -> Always
            decision is ScanDecision.Active -> Active
            decision is ScanDecision.Suspended -> Suspended
            else -> Idle
        }
    }
}
