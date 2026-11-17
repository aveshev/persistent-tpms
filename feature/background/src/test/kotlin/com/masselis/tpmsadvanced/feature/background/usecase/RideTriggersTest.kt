package com.masselis.tpmsadvanced.feature.background.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.BluetoothLeScanner.Advertisement.Companion.RSSI_UNAVAILABLE
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BEACON
import com.masselis.tpmsadvanced.feature.background.usecase.ScanDecision.ActivateCause.BLUETOOTH
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Ported from the iOS app's `RideTriggersTests`, without what Android doesn't need */
internal class RideTriggersTest {

    private val source = "source"
    private val absent = 30.minutes
    private val stale = 60.minutes
    private val start = 1_000_000L

    @Test
    fun `first report is back near the vehicle`() {
        assertTrue(RideTriggers().isBack(source, start, absent))
    }

    @Test
    fun `staying nearby is not coming back`() {
        // Each report starts the absence over: reports closer than it never add up to one
        (1..10)
            .map { start + (it * 20).minutes.inWholeMilliseconds }
            .fold(RideTriggers().heard(source, start)) { triggers, at ->
                assertFalse(triggers.isBack(source, at, absent))
                triggers.heard(source, at)
            }
    }

    @Test
    fun `heard again after the absence is coming back`() {
        RideTriggers()
            .heard(source, start)
            .also { assertFalse(it.isBack(source, start + absent.inWholeMilliseconds - 1, absent)) }
            .also { assertTrue(it.isBack(source, start + absent.inWholeMilliseconds, absent)) }
    }

    @Test
    fun `a connection ending counts as heard until then`() {
        // Connected for 2 hours, then gone for less than the absence
        RideTriggers()
            .heard(source, start)
            .heard(source, start + 120.minutes.inWholeMilliseconds)
            .also { assertFalse(it.isBack(source, start + 121.minutes.inWholeMilliseconds, absent)) }
    }

    @Test
    fun `each trigger has its own absence`() {
        val bike = RideTriggers.sensors(UUID.randomUUID())
        val car = RideTriggers.sensors(UUID.randomUUID())
        // The car's sensors heard all along don't hide being back near the bike
        RideTriggers()
            .heard(bike, start)
            .heard(car, start + 45.minutes.inWholeMilliseconds)
            .let { triggers ->
                val at = start + 50.minutes.inWholeMilliseconds
                assertTrue(triggers.isBack(bike, at, absent))
                assertFalse(triggers.isBack(car, at, absent))
                assertTrue(triggers.isBack(RideTriggers.condition(BEACON), at, absent))
            }
    }

    @Test
    fun `weak or unmeasured signal is not heard`() {
        // From the house: keeps the bike absent rather than present
        assertFalse(RideTriggers.isHeard(-86))
        assertTrue(RideTriggers.isHeard(-85))
        assertTrue(RideTriggers.isHeard(-40))
        assertFalse(RideTriggers.isHeard(RSSI_UNAVAILABLE))
    }

    @Test
    fun `nothing showing always posts`() {
        assertTrue(RideTriggers().posts(start, isShowing = false, stale))
        // Dismissed, or the app opened
        assertTrue(RideTriggers().fired(start).posts(start + 1, isShowing = false, stale))
    }

    @Test
    fun `a current notification stays as it is`() {
        assertFalse(
            RideTriggers()
                .fired(start)
                .posts(start + stale.inWholeMilliseconds - 1, isShowing = true, stale)
        )
    }

    @Test
    fun `each firing keeps the notification current`() {
        val second = start + stale.inWholeMilliseconds - 1
        assertFalse(
            RideTriggers()
                .fired(start)
                .fired(second)
                .posts(second + stale.inWholeMilliseconds - 1, isShowing = true, stale)
        )
    }

    @Test
    fun `a stale notification is replaced`() {
        assertTrue(
            RideTriggers()
                .fired(start)
                .posts(start + stale.inWholeMilliseconds, isShowing = true, stale)
        )
    }

    @Test
    fun `a notification of unknown age is replaced`() {
        assertTrue(RideTriggers().posts(start, isShowing = true, stale))
    }

    @Test
    fun `each condition has its own absence`() {
        val beacon = RideTriggers.condition(BEACON)
        val bluetooth = RideTriggers.condition(BLUETOOTH)
        // A beacon at home heard all day doesn't hide the Bluetooth device coming back
        RideTriggers()
            .heard(bluetooth, start)
            .heard(beacon, start + 45.minutes.inWholeMilliseconds)
            .let { triggers ->
                val at = start + 50.minutes.inWholeMilliseconds
                assertTrue(triggers.isBack(bluetooth, at, absent))
                assertFalse(triggers.isBack(beacon, at, absent))
            }
    }
}
