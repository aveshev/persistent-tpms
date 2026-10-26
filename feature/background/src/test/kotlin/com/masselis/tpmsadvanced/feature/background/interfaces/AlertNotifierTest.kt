package com.masselis.tpmsadvanced.feature.background.interfaces

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE_LOSS
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.TEMPERATURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.CRIMSON
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureLoss
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAlerts
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Action
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Companion.action
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Companion.raises
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Companion.reevaluation
import com.masselis.tpmsadvanced.feature.background.interfaces.AlertNotifier.Shown
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
internal class AlertNotifierTest {

    private val thresholds = AlertThresholds(200f.kpa, 300f.kpa, 90f.celsius, 2.6f.volts)

    private fun alerts(vararg kpa: Float, sensorId: Int = 1, loss: PressureLoss? = null) = kpa
        .withIndex()
        .fold(TyreAlerts()) { alerts, (index, value) ->
            alerts.next(TyreAtmosphere(index * 60.0, sensorId, value.kpa, 30f.celsius), thresholds, loss)
        }

    private val loss = PressureLoss(10f.kpa, 7f.kpa, 0.0, 60.0)

    private val notSnoozed: (AlertLevel) -> Boolean = { false }

    private fun action(alerts: TyreAlerts, shown: Shown?, isHeld: Boolean = false, isSnoozed: (AlertLevel) -> Boolean = notSnoozed) =
        action(alerts, PRESSURE, shown, isHeld, isSnoozed)

    @Test
    fun `a new alert sounds`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), null))
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f), null))
    }

    @Test
    fun `a higher level sounds`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `red sounds again at each reading, restarting the hold`() {
        assertEquals(Action.Post(RED, sound = true), action(alerts(190f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `amber updates silently`() {
        assertEquals(Action.Post(AMBER, sound = false), action(alerts(205f), Shown(1, AMBER), isHeld = true))
    }

    @Test
    fun `a lower level waits for the hold to end`() {
        assertEquals(Action.Hold(RED), action(alerts(190f), Shown(1, CRIMSON), isHeld = true))
        assertEquals(Action.Hold(null), action(alerts(250f), Shown(1, RED), isHeld = true))
    }

    @Test
    fun `a lower level after the hold updates silently`() {
        assertEquals(Action.Post(RED, sound = false), action(alerts(190f), Shown(1, CRIMSON)))
    }

    @Test
    fun `a cleared class after the hold cancels its notification`() {
        assertEquals(Action.Cancel, action(alerts(250f), Shown(1, RED)))
        assertEquals(Action.None, action(alerts(250f), null))
    }

    @Test
    fun `another sensor's alert sounds`() {
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f), Shown(2, AMBER), isHeld = true))
    }

    @Test
    fun `another sensor without alert clears the previous one's`() {
        assertEquals(Action.Cancel, action(alerts(250f), Shown(2, RED), isHeld = true))
    }

    @Test
    fun `a snoozed level doesn't post`() {
        assertEquals(Action.None, action(alerts(190f), null) { it <= RED })
    }

    @Test
    fun `a higher level than snoozed posts`() {
        assertEquals(Action.Post(CRIMSON, sound = true), action(alerts(90f), null) { it <= RED })
    }

    @Test
    fun `a leak is notified on its own while the pressure is fine or amber`() {
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(250f, loss = loss), PRESSURE_LOSS, null, false, notSnoozed))
        assertEquals(Action.Post(AMBER, sound = true), action(alerts(205f, loss = loss), PRESSURE_LOSS, null, false, notSnoozed))
    }

    @Test
    fun `a leak isn't notified on its own while the pressure is red or crimson`() {
        assertEquals(Action.None, action(alerts(190f, loss = loss), PRESSURE_LOSS, null, false, notSnoozed))
        assertEquals(Action.None, action(alerts(90f, loss = loss), PRESSURE_LOSS, null, false, notSnoozed))
    }

    @Test
    fun `a leak's notification goes at once when the pressure turns red, its hold notwithstanding`() {
        assertEquals(Action.Cancel, action(alerts(190f, loss = loss), PRESSURE_LOSS, Shown(1, AMBER), true, notSnoozed))
    }

    @Test
    fun `classes are apart`() {
        assertEquals(Action.None, action(alerts(190f), TEMPERATURE, null, false, notSnoozed))
    }

    /** [kpa] read under [thresholds], then re-evaluated under [low] as the minimum pressure */
    private fun reevaluated(kpa: Float, low: Float, loss: PressureLoss? = null) = alerts(kpa, loss = loss)
        .let { it.next(requireNotNull(it.latest), AlertThresholds(low.kpa, 300f.kpa, 90f.celsius, 2.6f.volts), loss) }

    @Test
    fun `a lower threshold lowers the notification silently, its hold notwithstanding`() {
        assertEquals(Action.Post(AMBER, sound = false), reevaluation(reevaluated(130f, 127f), PRESSURE, Shown(1, CRIMSON), notSnoozed))
    }

    @Test
    fun `a lower threshold clears the notification`() {
        assertEquals(Action.Cancel, reevaluation(reevaluated(190f, 150f), PRESSURE, Shown(1, RED), notSnoozed))
    }

    @Test
    fun `a lower level dismissed for a while clears the notification`() {
        assertEquals(Action.Cancel, reevaluation(reevaluated(130f, 127f), PRESSURE, Shown(1, CRIMSON)) { it == AMBER })
    }

    @Test
    fun `the same level updates silently`() {
        assertEquals(Action.Post(RED, sound = false), reevaluation(reevaluated(190f, 195f), PRESSURE, Shown(1, RED), notSnoozed))
    }

    @Test
    fun `a higher threshold doesn't raise the notification`() {
        assertEquals(Action.None, reevaluation(reevaluated(205f, 260f), PRESSURE, Shown(1, AMBER), notSnoozed))
    }

    @Test
    fun `a leak folded into a pressure turned red goes`() {
        assertEquals(Action.Cancel, reevaluation(reevaluated(205f, 210f, loss), PRESSURE_LOSS, Shown(1, AMBER), notSnoozed))
    }

    @Test
    fun `another sensor's notification is left to the next reading`() {
        assertEquals(Action.None, reevaluation(reevaluated(250f, 200f), PRESSURE, Shown(2, RED), notSnoozed))
    }

    @Test
    fun `a new notification or a higher level waits before it's raised`() {
        assertTrue(action(alerts(190f), null).raises(null, 1))
        assertTrue(action(alerts(90f), Shown(1, RED), isHeld = true).raises(Shown(1, RED), 1))
        assertTrue(action(alerts(190f), Shown(2, RED)).raises(Shown(2, RED), 1))
    }

    @Test
    fun `the same level, a lower one or a hold isn't raised`() {
        assertFalse(action(alerts(190f), Shown(1, RED), isHeld = true).raises(Shown(1, RED), 1))
        assertFalse(action(alerts(190f), Shown(1, CRIMSON)).raises(Shown(1, CRIMSON), 1))
        assertFalse(action(alerts(250f), Shown(1, RED), isHeld = true).raises(Shown(1, RED), 1))
        assertFalse(action(alerts(250f), null).raises(null, 1))
    }
}
