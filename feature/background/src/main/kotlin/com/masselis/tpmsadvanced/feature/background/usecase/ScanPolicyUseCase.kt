package com.masselis.tpmsadvanced.feature.background.usecase

import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier
import com.masselis.tpmsadvanced.data.app.interfaces.AppTier.LITE
import com.masselis.tpmsadvanced.feature.background.usecase.BeaconPresenceUseCase.Companion.asBeaconScanMode
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ALWAYS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.ANDROID_AUTO
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.CABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.MANUAL
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.STAY_ACTIVE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.WIRELESS
import com.masselis.tpmsadvanced.feature.background.usecase.ScanSuspensionUseCase.Reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList")
internal class ScanPolicyUseCase(
    private val appPreferences: AppPreferences,
    scanSuspensionUseCase: ScanSuspensionUseCase,
    private val chargingStateUseCase: ChargingStateUseCase,
    private val androidAutoUseCase: AndroidAutoUseCase,
    private val bluetoothDevicesUseCase: BluetoothDevicesUseCase,
    private val beaconPresenceUseCase: BeaconPresenceUseCase,
    scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val tier: AppTier = AppTier.current,
) {
    private val logger = Logger.withTag("ScanPolicy")

    /**
     * Read by both the decision and the stay active timer: shared, so that the system listeners
     * behind it (WiFi, idle, Bluetooth devices) are registered once rather than twice. A stale
     * value isn't replayed to a new subscriber.
     */
    private val suspensionReasons: SharedFlow<Set<Reason>> = scanSuspensionUseCase
        .suspensionReasons
        .shareIn(scope, WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /**
     * Each decision along with the mode (persistent scanning or not) it was made for. Shared, so
     * the service and the UI observe the same decision and a single set of system listeners is
     * registered.
     */
    private val decisionByMode: SharedFlow<Pair<Boolean, ScanDecision>> = appPreferences
        .persistentScanning
        .flatMapLatest { persistent ->
            if (persistent) automatic().map { persistent to it }
            // Without persistent scanning, monitoring is the manual start/stop button: scanning
            // for as long as the service runs
            else flowOf(persistent to ScanDecision.Active(setOf(MANUAL)))
        }
        // Several inputs can change without changing the outcome: nobody needs to hear it again
        .distinctUntilChanged()
        .shareIn(scope, WhileSubscribed(), replay = 1)

    /**
     * What background scanning should do right now. Right after the mode changed, the shared
     * replay still holds the previous mode's decision until the new mode's inputs are read: it is
     * never handed out, so that nobody scans (nor shows "Active") for a stale reason.
     */
    val decision: Flow<ScanDecision> = combine(
        appPreferences.persistentScanning,
        decisionByMode,
    ) { persistent, (mode, decision) -> decision.takeIf { mode == persistent } }
        .filterNotNull()
        .distinctUntilChanged()

    /**
     * The addresses of the beacons nearby, from a single scan shared by the decision and the names
     * it tells. Renaming a beacon doesn't restart it, and a stale value isn't replayed to a new
     * subscriber. Starts a scan of its own when collected while not deciding on beacons.
     */
    val nearbyBeacons: SharedFlow<Map<String, Int>> = combine(
        appPreferences
            .beacons
            .map { beacons -> beacons.map { it.copy(label = null) } }
            .distinctUntilChanged(),
        appPreferences
            .beaconScanMode
            .map { it.asBeaconScanMode() }
            .distinctUntilChanged(),
        ::Pair,
    )
        .flatMapLatest { (beacons, mode) -> beaconPresenceUseCase.nearby(beacons, mode) }
        .shareIn(scope, WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    /** The latest [decision] if one is known for the current mode, without waiting for it */
    val currentDecision: ScanDecision?
        get() = decisionByMode
            .replayCache
            .lastOrNull()
            ?.takeIf { (mode) -> mode == appPreferences.persistentScanning.value }
            ?.second

    private fun automatic(): Flow<ScanDecision> = combine(
        enabledCauses(),
        appPreferences.stayActiveMinutes,
        appPreferences.beaconOverridesSuspend,
        ::Triple,
    ).flatMapLatest { (enabled, stayActiveMinutes, beaconOverridesSuspend) ->
        combine(
            fulfilledCauses(enabled, stayActiveMinutes.minutes, beaconOverridesSuspend),
            suspensionReasons,
        ) { fulfilled, reasons ->
            decide(enabled, fulfilled, reasons, beaconOverridesSuspend).let { decision ->
                // What the decision was made from, to tell afterwards why it changed
                decision to "enabled=$enabled, fulfilled=$fulfilled, suspend reasons=$reasons, " +
                    "beacon overrides suspend=$beaconOverridesSuspend"
            }
        }
            .distinctUntilChangedBy { (decision) -> decision }
            .onEach { (decision, inputs) -> logger.i { "Decision: $decision, from $inputs" } }
            .map { (decision) -> decision }
            .flatMapLatest { decision ->
            // Names the devices and beacons a condition holds for, the user reads them in the
            // rationale
            when {
                decision is ScanDecision.Active -> combine(
                    if (BLUETOOTH in decision.causes) appPreferences.activateBluetoothDevices.connectedNames()
                    else flowOf(emptyList()),
                    if (BEACON in decision.causes) nearbyBeaconNames()
                    else flowOf(emptyList()),
                ) { devices, beacons -> decision.copy(bluetoothDevices = devices, beacons = beacons) }

                decision is ScanDecision.Suspended && Reason.BLUETOOTH in decision.reasons ->
                    appPreferences
                        .suspendBluetoothDevices
                        .connectedNames()
                        .map { decision.copy(bluetoothDevices = it) }

                else -> flowOf(decision)
            }
        }
    }

    /** The names of the connected devices among the selected ones */
    private fun Flow<Set<String>>.connectedNames(): Flow<List<String>> =
        combine(this, bluetoothDevicesUseCase.connected) { selected, connected ->
            connected
                .filter { it.address in selected }
                .map { it.name }
                .sortedBy { it.lowercase() }
        }
            // Empty only for the moment between a disconnection and the decision changing because
            // of it: that stale decision isn't worth telling
            .filter { it.isNotEmpty() }

    /** The names of the beacons nearby, like [connectedNames] */
    private fun nearbyBeaconNames(): Flow<List<String>> =
        combine(appPreferences.beacons, nearbyBeacons) { beacons, nearby ->
            beacons
                .filter { it.address in nearby }
                .map { it.displayName }
                .sortedBy { it.lowercase() }
        }
            .filter { it.isNotEmpty() }

    /**
     * Lite only has the Bluetooth and the beacon conditions, without a switch for the conditions
     * as a whole: the others are ignored whatever they're set to, and none of the two selected is
     * the same as the conditions being off.
     */
    private fun enabledCauses(): Flow<Set<ActivateCause>> = combine(
        appPreferences.activateConditions.map { it || tier == LITE },
        combine(
            appPreferences.activateOnCableCharging,
            appPreferences.activateOnWirelessCharging,
            appPreferences.activateOnAndroidAuto,
            appPreferences.activateOnBluetooth,
            appPreferences.activateBluetoothDevices,
        ) { cable, wireless, androidAuto, bluetooth, bluetoothDevices ->
            buildSet {
                if (tier != LITE) {
                    if (cable) add(CABLE)
                    if (wireless) add(WIRELESS)
                    if (androidAuto) add(ANDROID_AUTO)
                }
                // Selected devices unpaired since then count too: the paired ones are unknown
                // while Bluetooth is off, which must not turn this into "always" active. Leaving
                // the device picker with none of them paired turns the condition off instead.
                if (bluetooth && bluetoothDevices.isNotEmpty()) add(BLUETOOTH)
            }
        }.combine(
            // Without any beacon to look for, the condition is the same as off: leaving the beacons
            // page like this turns it off
            combine(appPreferences.activateOnBeacon, appPreferences.beacons) { beacon, beacons ->
                beacon && beacons.isNotEmpty()
            }
        ) { selected, beacon -> if (beacon) selected + BEACON else selected },
        appPreferences.stayActive,
    ) { conditions, selected, stayActive ->
        buildSet {
            // Nothing selected is the same as the conditions being off, which they are turned into
            // when leaving their page: the decision doesn't flicker through "idle" meanwhile.
            // "Stay active" only extends the others, it doesn't count.
            if (conditions.not() || selected.isEmpty()) add(ALWAYS)
            addAll(selected)
            if (stayActive) add(STAY_ACTIVE)
        }
    }

    private fun fulfilledCauses(
        enabled: Set<ActivateCause>,
        stayActiveFor: Duration,
        beaconOverridesSuspend: Boolean,
    ): Flow<Set<ActivateCause>> = when {
        // No need to listen to anything when always scanning overrides every other condition
        ALWAYS in enabled -> flowOf(emptySet())
        STAY_ACTIVE in enabled ->
            directCauses(enabled).stayingActive(enabled, stayActiveFor, beaconOverridesSuspend)

        else -> directCauses(enabled)
    }

    private fun directCauses(enabled: Set<ActivateCause>): Flow<Set<ActivateCause>> {
        val sources = buildList {
            if (CABLE in enabled || WIRELESS in enabled) {
                add(
                    chargingStateUseCase.state.map { charging ->
                        buildSet {
                            if (charging.cable) add(CABLE)
                            if (charging.wireless) add(WIRELESS)
                        }
                    }
                )
            }
            if (ANDROID_AUTO in enabled) {
                add(androidAutoUseCase.connected.map { if (it) setOf(ANDROID_AUTO) else emptySet() })
            }
            if (BEACON in enabled) {
                add(
                    nearbyBeacons
                        .map { if (it.isEmpty()) emptySet() else setOf(BEACON) }
                        // The signal of each packet changes the map, not the cause
                        .distinctUntilChanged()
                )
            }
            if (BLUETOOTH in enabled) {
                add(
                    combine(
                        appPreferences.activateBluetoothDevices,
                        bluetoothDevicesUseCase.connected,
                    ) { selected, connected ->
                        if (connected.any { it.address in selected }) setOf(BLUETOOTH) else emptySet()
                    }
                )
            }
        }
        return if (sources.isEmpty()) flowOf(emptySet())
        else combine(sources) { it.flatMap(Set<ActivateCause>::toList).toSet() }
    }

    /**
     * Once none of the [enabled] conditions is fulfilled any more, reports [STAY_ACTIVE] for
     * [duration], if scanning was active at that very moment (a nearby beacon may have kept it
     * active despite a suspend reason): otherwise there is nothing to stay active for. The stay
     * itself is suspended like any other cause, the beacon's override doesn't extend to it. Starts
     * over each time the conditions end, and nothing is held for conditions that were never
     * fulfilled. A suspension that begins or ends during the stay does not move its end, it is a
     * fixed point in time.
     */
    private fun Flow<Set<ActivateCause>>.stayingActive(
        enabled: Set<ActivateCause>,
        duration: Duration,
        beaconOverridesSuspend: Boolean,
    ): Flow<Set<ActivateCause>> = flow {
        var wasFulfilled = false
        // Whether scanning was active the last time a condition was fulfilled
        var wasActive = false
        var stayEnd: TimeMark? = null
        emitAll(
            combine(this@stayingActive, suspensionReasons) { causes, reasons ->
                causes to (decide(enabled, causes, reasons, beaconOverridesSuspend) is ScanDecision.Active)
            }.transformLatest { (causes, active) ->
                if ((causes intersect enabled).isNotEmpty()) {
                    wasFulfilled = true
                    wasActive = active
                    stayEnd = null
                    emit(causes)
                    return@transformLatest
                }
                if (wasFulfilled) {
                    wasFulfilled = false
                    stayEnd = if (wasActive) timeSource.markNow() + duration else null
                }
                val remaining = stayEnd?.let { -it.elapsedNow() }?.takeIf { it.isPositive() }
                if (remaining == null) {
                    emit(causes)
                } else {
                    emit(causes + STAY_ACTIVE)
                    delay(remaining)
                    stayEnd = null
                    emit(causes)
                }
            }
        )
    }
}
