package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized

internal sealed interface ScanDecision {

    enum class ActivateCause { MANUAL, CABLE, WIRELESS, ANDROID_AUTO, BLUETOOTH, BEACON, STAY_ACTIVE, ALWAYS }

    /**
     * Background scanning is running, for at least one of [causes]. [bluetoothDevices] names the
     * connected devices behind [ActivateCause.BLUETOOTH], [beacons] the nearby beacons behind
     * [ActivateCause.BEACON].
     */
    data class Active(
        val causes: Set<ActivateCause>,
        val bluetoothDevices: List<String> = emptyList(),
        val beacons: List<String> = emptyList(),
    ) : ScanDecision

    /**
     * An activate condition holds but a suspend condition overrides it. [bluetoothDevices] names
     * the connected devices behind [Reason.BLUETOOTH].
     */
    data class Suspended(
        val reasons: Set<Reason>,
        val bluetoothDevices: List<String> = emptyList(),
    ) : ScanDecision

    /** No enabled activate condition is fulfilled, there is nothing to scan for. */
    data object Idle : ScanDecision
}

/**
 * Persistent scanning is active if ANY enabled activate condition is fulfilled and NONE of the
 * suspend conditions is. [ALWAYS] (the activate conditions being turned off) is always fulfilled
 * and makes every other one irrelevant. With [beaconOverridesSuspend], a nearby beacon scans
 * whatever the suspend conditions, but the phone being idle.
 */
internal fun decide(
    enabled: Set<ScanDecision.ActivateCause>,
    fulfilled: Set<ScanDecision.ActivateCause>,
    suspendReasons: Set<Reason>,
    beaconOverridesSuspend: Boolean = false,
): ScanDecision {
    val causes = if (ALWAYS in enabled) setOf(ALWAYS) else enabled intersect fulfilled
    return when {
        causes.isEmpty() -> ScanDecision.Idle
        suspendReasons.isEmpty() -> ScanDecision.Active(causes)
        // The beacon tells the vehicle is around. A phone idle for that long was rather forgotten
        // in it, and there is no stopping then since the beacon never leaves.
        beaconOverridesSuspend && BEACON in causes && IDLE !in suspendReasons -> ScanDecision.Active(causes)
        else -> ScanDecision.Suspended(suspendReasons)
    }
}

@Suppress("CyclomaticComplexMethod", "MaxLineLength")
internal fun ScanDecision.explanation(tier: AppTier = AppTier.current): String = when (this) {
    is ScanDecision.Active -> when {
        // Nothing to explain besides the user's own choice, the "due to" wording would sound odd
        ALWAYS in causes && tier == LITE ->
            localized(CommonR.string.always_active_lite)

        ALWAYS in causes -> localized(CommonR.string.always_active)
        else -> causes
            .joinToString(", ") {
                when (it) {
                    BLUETOOTH -> bluetoothDevices.connectedLabel()
                    BEACON -> beacons.nearbyLabel()
                    else -> it.label
                }
            }
            .let { localized(CommonR.string.active_due_to, it) }
    }

    is ScanDecision.Suspended ->
        reasons
            .joinToString(", ") {
                when (it) {
                    Reason.IDLE -> localized(CommonR.string.reason_idle)
                    Reason.WIFI -> localized(CommonR.string.reason_wifi)
                    Reason.BLUETOOTH -> bluetoothDevices.connectedLabel()
                }
            }
            .let { localized(CommonR.string.suspended_due_to, it) }

    // Lite's only conditions, told as such
    ScanDecision.Idle -> if (tier == LITE) {
        localized(CommonR.string.idle_lite)
    } else {
        localized(CommonR.string.idle_pro)
    }
}

/** What the user reads when tapping the bell. */
internal fun ScanDecision.rationale(tier: AppTier = AppTier.current): String = when (this) {
    is ScanDecision.Active -> explanation(tier)
    // The foreground UI scans by itself, whatever the decision is
    is ScanDecision.Suspended, ScanDecision.Idle ->
        localized(CommonR.string.scanning_while_open, explanation(tier))
}

private val ScanDecision.ActivateCause.label
    get() = when (this) {
        ScanDecision.ActivateCause.MANUAL -> localized(CommonR.string.cause_manual)
        ScanDecision.ActivateCause.CABLE -> localized(CommonR.string.cause_cable)
        ScanDecision.ActivateCause.WIRELESS -> localized(CommonR.string.cause_wireless)
        ScanDecision.ActivateCause.ANDROID_AUTO -> localized(CommonR.string.cause_auto)
        // Told with the devices' names, see connectedLabel()
        BLUETOOTH -> localized(CommonR.string.cause_bluetooth)
        // Told with the beacons' names, see nearbyLabel()
        BEACON -> localized(CommonR.string.cause_beacon)
        ScanDecision.ActivateCause.STAY_ACTIVE -> localized(CommonR.string.cause_stay)
        ALWAYS -> localized(CommonR.string.cause_always)
    }

/** "A being connected", "A and B being connected", "A, B and C being connected" */
private fun List<String>.connectedLabel(): String =
    // The device disconnected in the meantime, its name is gone before the decision changes
    localized(CommonR.string.names_connected, namesLabel(fallback = localized(CommonR.string.a_bluetooth_device)))

/** "A being nearby", "A and B being nearby", "A, B and C being nearby" */
private fun List<String>.nearbyLabel(): String =
    // The beacon went away in the meantime, its name is gone before the decision changes
    localized(CommonR.string.names_nearby, namesLabel(fallback = localized(CommonR.string.a_bluetooth_beacon)))

private fun List<String>.namesLabel(fallback: String): String = when (size) {
    0 -> fallback
    1 -> single()
    else -> localized(CommonR.string.names_and, dropLast(1).joinToString(", "), last())
}
