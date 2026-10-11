package com.masselis.tpmsadvanced.feature.background.usecase

import android.media.AudioAttributes
import android.media.AudioAttributes.USAGE_ALARM
import android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
import android.media.AudioAttributes.USAGE_NOTIFICATION
import android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.NONE
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.SPEECH
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.AlertSound.TONES
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.SENSOR_ALARM
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Speaks the alerts, see docs/alerts.md: a red one twice each time its notification sounds, then
 * every alert in one of two loops shared by the whole app. The crimson alerts are said every
 * [CRIMSON_PERIOD] for [CRIMSON_DURATION] after their latest reading, then every [REMINDER_PERIOD]
 * along with the red ones. Everything goes through a single queue, nothing talks over anything. The
 * loops stop with the tyre scans: no reading could clear their alerts any more. The reminders also
 * stop once [isRepeating] doesn't hold, see docs/alerts.md: an alert is then only said once, when
 * its notification sounds. [silenced] mutes the alerts up to its level, see [AlertSilenceUseCase].
 * [ongoing] follows the loops whatever the speech.
 */
internal class AlertSpeaker(
    private val appPreferences: AppPreferences,
    isScanningTyres: Flow<Boolean>,
    isRepeating: Flow<Boolean>,
    silenced: Flow<AlertLevel?>,
    scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val logger = Logger.withTag("AlertSpeaker")
    private val audioManager by lazy { appContext.getSystemService<AudioManager>()!! }

    private data class Queue(
        /** The alerts to say once, as their notification sounds */
        val announcements: List<Announcement> = emptyList(),
        /** The alerts repeated by the loops, by the tag of their notification */
        val repeated: Map<String, Repeated> = emptyMap(),
        /** When the crimson loop speaks next, null to speak right away */
        val nextCrimson: TimeMark? = null,
        /** When the reminder loop speaks next, null while there's nothing to remind of */
        val nextReminder: TimeMark? = null,
        /** When the latest alert sounded, its notification's sound is let out first */
        val sounded: TimeMark? = null,
        /** A phrase is being said: whatever happens meanwhile, it's said to the end */
        val isSpeaking: Boolean = false,
        /** Whether the loops may go on, their alerts are dropped otherwise */
        val isRepeating: Boolean = false,
        /** The alerts up to this level aren't said, nor kept to be said later */
        val silenced: AlertLevel? = null,
    ) {
        val isIdle get() = announcements.isEmpty() && repeated.isEmpty() && isSpeaking.not()

        /** What the crimson loop says */
        val crimson get() = repeated.values.filter(Repeated::isCrimsonLoop)

        /**
         * What the reminder loop says: the crimson alerts done with their own loop, and the red
         * ones of the classes no crimson alert is said for
         */
        val reminders
            get() = repeated
                .values
                .filter(Repeated::isCrimson)
                .map(Repeated::alertClass)
                .toSet()
                .let { crimsonClasses ->
                    repeated.values.filter {
                        if (it.isCrimson) it.isCrimsonLoop.not() else it.alertClass !in crimsonClasses
                    }
                }
    }

    /** [crimsonUntil] is when a crimson alert leaves its loop for the reminders, null for a red one */
    private data class Repeated(val alertClass: AlertClass, val crimsonUntil: TimeMark?) {
        val isCrimson get() = crimsonUntil != null
        val isCrimsonLoop get() = crimsonUntil?.hasNotPassedNow() == true
        val phrase get() = if (isCrimson) "${alertClass.phrase} critical" else alertClass.phrase
    }

    /** A red alert, or a crimson one which the loops can't repeat, by the tag of its notification */
    private data class Announcement(val tag: String, val alertClass: AlertClass, val isCrimson: Boolean) {
        val phrase get() = if (isCrimson) "${alertClass.phrase} critical" else alertClass.phrase
    }

    private val queue = MutableStateFlow(Queue())

    /** A red or crimson alert of [vehicle] which the loops would repeat */
    data class Ongoing(val vehicle: UUID, val level: AlertLevel)

    private val mutableOngoing = MutableStateFlow(emptyMap<String, Ongoing>())

    /**
     * The alerts the loops would repeat whatever the speech, by the tag of their notification: the
     * same ones entering and leaving, but neither silenced nor turned off with the alert sound. What
     * the vehicle picker blinks for, see docs/alerts.md.
     */
    val ongoing: StateFlow<Map<String, Ongoing>> = mutableOngoing.asStateFlow()

    /** Only while the loops can go on, the alert is otherwise said once and forgotten */
    private fun ongoing(tag: String, vehicle: UUID, level: AlertLevel) {
        if (queue.value.isRepeating) mutableOngoing.update { it + (tag to Ongoing(vehicle, level)) }
    }

    /**
     * The highest level the speech has yet to say: of the alerts waiting for their announcement or
     * repeated by the loops, null once there's nothing left. What the silence button silences.
     */
    val level: Flow<AlertLevel?> = queue
        .map { queue ->
            queue.announcements.map(Announcement::isCrimson)
                .plus(queue.repeated.values.map(Repeated::isCrimson))
                .maxOfOrNull { isCrimson -> if (isCrimson) CRIMSON else RED }
        }
        .distinctUntilChanged()

    /**
     * Says [alertClass]'s phrase twice, once, after the notification's sound if it [sounded], then
     * reminds of it. The alerts of a class announced together are said once.
     */
    fun red(tag: String, vehicle: UUID, alertClass: AlertClass, sounded: Boolean = true) {
        ongoing(tag, vehicle, RED)
        if (appPreferences.alertSound.value != NONE && alertClass in SPOKEN) queue.update {
            it.copy(
                announcements = it.announcements + Announcement(tag, alertClass, isCrimson = false),
                repeated = it.repeated + (tag to Repeated(alertClass, null)),
                sounded = if (sounded) timeSource.markNow() else it.sounded,
            ).scheduled()
        }
    }

    /**
     * Says [alertClass]'s critical phrase twice every [CRIMSON_PERIOD] for [CRIMSON_DURATION],
     * starting over that duration if [tag] is already in the crimson loop, then reminds of it. A
     * new one is said right away. Said once when the loops can't go on.
     */
    fun crimson(tag: String, vehicle: UUID, alertClass: AlertClass) {
        ongoing(tag, vehicle, CRIMSON)
        if (appPreferences.alertSound.value != NONE && alertClass in SPOKEN) queue.update { queue ->
            // Its red announcement yet to be said would only come before it
            queue
                .copy(announcements = queue.announcements.filter { it.tag != tag }, sounded = timeSource.markNow())
                .run {
                    if (isRepeating) copy(
                        repeated = repeated + (tag to Repeated(alertClass, timeSource.markNow() + CRIMSON_DURATION)),
                        nextCrimson = nextCrimson.takeIf { repeated[tag]?.isCrimsonLoop == true },
                    )
                    else copy(announcements = announcements + Announcement(tag, alertClass, isCrimson = true))
                }
                .scheduled()
        }
    }

    /**
     * Stops repeating the alert of [tag], and its red announcements yet to be said once
     * [isDismissed]. A phrase being said is always said to the end.
     */
    fun stop(tag: String, isDismissed: Boolean = false) {
        mutableOngoing.update { it - tag }
        queue.update { queue ->
            queue.copy(
                repeated = queue.repeated - tag,
                announcements =
                    if (isDismissed) queue.announcements.filter { it.tag != tag }
                    else queue.announcements,
            ).scheduled()
        }
    }

    /**
     * Drops the repeated alerts while the loops can't go on, and the silenced ones. The reminders
     * start [REMINDER_PERIOD] after something enters them, and over once they empty.
     */
    @Suppress("MaxLineLength")
    private fun Queue.scheduled() = this
        .run { if (isRepeating) this else copy(repeated = emptyMap()) }
        .run {
            when (silenced) {
                null -> this
                RED -> copy(repeated = repeated.filterValues(Repeated::isCrimson), announcements = announcements.filter(Announcement::isCrimson))
                CRIMSON, AMBER -> copy(repeated = emptyMap(), announcements = emptyList())
            }
        }
        .run {
            copy(nextReminder = if (reminders.isEmpty()) null else nextReminder ?: (timeSource.markNow() + REMINDER_PERIOD))
        }

    init {
        isScanningTyres
            .filter { it.not() }
            .onEach { queue.update { it.copy(repeated = emptyMap()).scheduled() } }
            .onEach { mutableOngoing.value = emptyMap() }
            .launchIn(scope)

        // Coming back, they only start again from a new reading
        isRepeating
            .onEach { isRepeating -> queue.update { it.copy(isRepeating = isRepeating).scheduled() } }
            .onEach { isRepeating -> if (isRepeating.not()) mutableOngoing.value = emptyMap() }
            .launchIn(scope)

        // Once it ends, only a new reading is said again
        silenced
            .onEach { silenced -> queue.update { it.copy(silenced = silenced).scheduled() } }
            .launchIn(scope)

        appPreferences
            .alertSound
            .filter { it == NONE }
            .onEach { queue.update { Queue(isRepeating = it.isRepeating, silenced = it.silenced) } }
            .launchIn(scope)

        @Suppress("MaxLineLength")
        scope.launch {
            // The engine is only kept while there's something to say, the queue being spoken for as
            // long as it lives
            combine(appPreferences.alertSound, queue) { sound, queue -> sound.takeIf { queue.isIdle.not() } }
                .distinctUntilChanged()
                .collectLatest { sound ->
                    when (sound) {
                        null, NONE -> Unit
                        SPEECH -> textToSpeech().collect { tts -> Voice { text, usage -> tts.say(text, usage) }.speakQueue() }
                        // The usage tells the crimson phrases apart
                        TONES -> Voice { _, usage -> playTones(usage) }.speakQueue()
                    }
                }
        }
    }

    /** Says a phrase with an audio usage, returning once it's said */
    private fun interface Voice {
        suspend fun say(text: String, usage: Int)
    }

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod", "LongMethod", "MaxLineLength")
    private suspend fun Voice.speakQueue() {
        while (true) {
            // A crimson alert leaving its loop enters the reminders
            queue.update { it.scheduled() }
            val current = queue.value
            when {
                current.announcements.isNotEmpty() -> current.announcements.first().let { announcement ->
                    val phrase = announcement.phrase
                    awaitNotificationSound(current.sounded)
                    // Unless it was dismissed meanwhile
                    if (announcement in queue.value.announcements) sayToTheEnd(
                        "$phrase. $phrase.",
                        if (announcement.isCrimson) USAGE_ALARM else USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                    ) { queue ->
                        queue.copy(
                            // Along with the others waiting which would say the same
                            announcements = queue.announcements.filter { it.phrase != phrase },
                            // Said everything the reminders would, which start over
                            nextReminder = queue
                                .reminders
                                .takeIf { reminders -> reminders.isNotEmpty() && reminders.all { it.phrase == phrase } }
                                ?.let { timeSource.markNow() + REMINDER_PERIOD }
                                ?: queue.nextReminder,
                        )
                    }
                }

                current.crimson.isNotEmpty() && current.nextCrimson?.hasPassedNow() != false -> {
                    awaitNotificationSound(current.sounded)
                    // Every period from the start of the phrases, however long they take
                    queue.update { it.copy(nextCrimson = timeSource.markNow() + CRIMSON_PERIOD) }
                    queue
                        .value
                        .crimson
                        .takeIf { it.isNotEmpty() }
                        ?.let { crimson -> sayToTheEnd(crimson.spoken(), USAGE_ALARM) }
                }

                current.nextReminder?.hasPassedNow() == true -> {
                    awaitNotificationSound(current.sounded)
                    queue.update { it.copy(nextReminder = timeSource.markNow() + REMINDER_PERIOD) }
                    queue
                        .value
                        .reminders
                        .takeIf { it.isNotEmpty() }
                        ?.let { reminders ->
                            sayToTheEnd(
                                reminders.spoken(),
                                if (reminders.any(Repeated::isCrimson)) USAGE_ALARM else USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                            )
                        }
                }

                // Until anything changes, a loop's next phrase or a crimson alert leaving its loop
                else -> withTimeoutOrNull(
                    current
                        .crimson
                        .mapNotNull(Repeated::crimsonUntil)
                        .plus(listOfNotNull(current.nextCrimson.takeIf { current.crimson.isNotEmpty() }))
                        .plus(listOfNotNull(current.nextReminder))
                        .minOfOrNull { it.elapsedNow().unaryMinus() }
                        ?: Duration.INFINITE
                ) { queue.first { it != current } }
            }
        }
    }

    /** Every phrase once, the crimson ones first, the whole said twice */
    private fun List<Repeated>.spoken() = this
        .sortedBy { it.isCrimson.not() }
        .map(Repeated::phrase)
        .distinct()
        .joinToString(", ")
        .let { "$it. $it." }

    /** Says [text], keeping the engine alive until it's said even if the queue empties meanwhile */
    private suspend fun Voice.sayToTheEnd(
        text: String,
        usage: Int,
        // Along with marking it said, the queue never looking idle meanwhile
        dequeue: (Queue) -> Queue = { it },
    ) {
        queue.update { dequeue(it).copy(isSpeaking = true) }
        try {
            say(text, usage)
        } finally {
            queue.update { it.copy(isSpeaking = false) }
        }
    }

    /**
     * Lets a notification's sound play out rather than speaking over it: the one of the alert which
     * [sounded], Android playing it about half a second after it's posted, and any other playing.
     * Doesn't wait for a silent or vibrating notification.
     */
    private suspend fun awaitNotificationSound(sounded: TimeMark?) {
        sounded
            ?.let { -(it + SOUND_START).elapsedNow() }
            ?.takeIf { it.isPositive() }
            ?.let { withTimeoutOrNull(it) { isNotificationSounding().first { sounding -> sounding } } }
        withTimeoutOrNull(MAX_SOUND) { isNotificationSounding().first { it.not() } }
    }

    /** Whether a notification's sound is playing, from any app */
    private fun isNotificationSounding(): Flow<Boolean> = callbackFlow {
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                trySend(configs.any { it.audioAttributes.usage in NOTIFICATION_USAGES })
            }
        }
        audioManager.registerAudioPlaybackCallback(callback, null)
        send(audioManager.activePlaybackConfigurations.any { it.audioAttributes.usage in NOTIFICATION_USAGES })
        awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
    }.distinctUntilChanged()

    /** Speaks [text] with [usage], returning once it's said */
    private suspend fun TextToSpeech.say(text: String, usage: Int) = AudioAttributes
        .Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
        .let { attributes -> withAudioFocus(attributes) { say(text, attributes) } }

    /** Plays the tone pattern of a crimson phrase for the alarm [usage], of a red one otherwise */
    private suspend fun playTones(usage: Int) = AudioAttributes
        .Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
        .let { attributes -> withAudioFocus(attributes) { AlertTones.play(usage == USAGE_ALARM, attributes) } }

    /**
     * The audio focus ducks what's playing while [block] runs, as navigation prompts do: neither
     * the speech engine nor a track ask for it by themselves
     */
    private suspend fun <T> withAudioFocus(attributes: AudioAttributes, block: suspend () -> T): T = AudioFocusRequest
        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
        .let { focus ->
            audioManager.requestAudioFocus(focus)
            try {
                block()
            } finally {
                audioManager.abandonAudioFocusRequest(focus)
            }
        }

    private suspend fun TextToSpeech.say(text: String, attributes: AudioAttributes) =
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
            setAudioAttributes(attributes)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }
            })
            continuation.invokeOnCancellation { stop() }
            if (speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR && continuation.isActive)
                continuation.resume(Unit)
        }

    /** The default engine once it is ready, shut down when the collection ends */
    private fun textToSpeech(): Flow<TextToSpeech> = callbackFlow {
        val status = CompletableDeferred<Int>()
        val tts = TextToSpeech(appContext) { status.complete(it) }
        launch {
            if (status.await() == TextToSpeech.SUCCESS) send(tts)
            else logger.w { "Text-to-speech failed to initialize, alerts won't be spoken" }
        }
        awaitClose { tts.shutdown() }
    }

    private companion object {
        val CRIMSON_PERIOD = 20.seconds
        val CRIMSON_DURATION = 10.minutes
        val REMINDER_PERIOD = 10.minutes

        /** Only the classes reaching red are said */
        val SPOKEN = setOf(PRESSURE, TEMPERATURE, BATTERY)

        /** Short on purpose: they say what to look for, the screen says the rest */
        val AlertClass.phrase
            get() = when (this) {
                PRESSURE -> "Tyre pressure"
                TEMPERATURE -> "Tyre hot"
                BATTERY -> "Sensor battery"
                PRESSURE_LOSS, SENSOR_ALARM -> error("$this is never said")
            }

        /** How long a notification's sound can take to start after it's posted */
        val SOUND_START = 1.5.seconds

        /** Speaks anyway after that, a notification's sound being stuck or very long */
        val MAX_SOUND = 10.seconds

        val NOTIFICATION_USAGES = setOf(USAGE_NOTIFICATION, USAGE_NOTIFICATION_EVENT)
    }
}
