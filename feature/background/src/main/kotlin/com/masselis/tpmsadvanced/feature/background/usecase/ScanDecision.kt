package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason.IDLE

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

internal fun ScanDecision.explanation(tier: AppTier = AppTier.current): String = when (this) {
    is ScanDecision.Active -> when {
        // Nothing to explain besides the user's own choice, the "due to" wording would sound odd
        ALWAYS in causes && tier == LITE ->
            "Background scanning is always active because no Bluetooth device or beacon is set to activate it"

        ALWAYS in causes -> "Background scanning is active because you chose it to be always active"
        else -> "Background scanning is active due to ${
            causes.joinToString(", ") {
                when (it) {
                    BLUETOOTH -> bluetoothDevices.connectedLabel()
                    BEACON -> beacons.nearbyLabel()
                    else -> it.label
                }
            }
        }"
    }

    is ScanDecision.Suspended ->
        "Background scanning is suspended due to ${
            reasons.joinToString(", ") {
                when (it) {
                    Reason.IDLE -> "the phone being idle"
                    Reason.WIFI -> "WiFi being connected"
                    Reason.BLUETOOTH -> bluetoothDevices.connectedLabel()
                }
            }
        }"

    ScanDecision.Idle -> "Background scanning is idle: no activate condition is currently fulfilled"
}

/** What the user reads when tapping the bell. */
internal fun ScanDecision.rationale(tier: AppTier = AppTier.current): String = when (this) {
    is ScanDecision.Active -> explanation(tier)
    // The foreground UI scans by itself, whatever the decision is
    is ScanDecision.Suspended, ScanDecision.Idle ->
        "${explanation(tier)}.\nNote: Scanning is still active while the app is opened!"
}

private val ScanDecision.ActivateCause.label
    get() = when (this) {
        ScanDecision.ActivateCause.MANUAL -> "monitoring being started manually"
        ScanDecision.ActivateCause.CABLE -> "charging with a cable"
        ScanDecision.ActivateCause.WIRELESS -> "charging wirelessly"
        ScanDecision.ActivateCause.ANDROID_AUTO -> "Android Auto being connected"
        // Told with the devices' names, see connectedLabel()
        BLUETOOTH -> "a Bluetooth device being connected"
        // Told with the beacons' names, see nearbyLabel()
        BEACON -> "a Bluetooth beacon being nearby"
        ScanDecision.ActivateCause.STAY_ACTIVE -> "staying active after the last activate condition ended"
        ALWAYS -> "the activate conditions being turned off"
    }

/** "A being connected", "A and B being connected", "A, B and C being connected" */
private fun List<String>.connectedLabel(): String =
    // The device disconnected in the meantime, its name is gone before the decision changes
    namesLabel(fallback = "a Bluetooth device") + " being connected"

/** "A being nearby", "A and B being nearby", "A, B and C being nearby" */
private fun List<String>.nearbyLabel(): String =
    // The beacon went away in the meantime, its name is gone before the decision changes
    namesLabel(fallback = "a Bluetooth beacon") + " being nearby"

private fun List<String>.namesLabel(fallback: String): String = when (size) {
    0 -> fallback
    1 -> single()
    else -> "${dropLast(1).joinToString(", ")} and ${last()}"
}
