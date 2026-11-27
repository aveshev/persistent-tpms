package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("MaxLineLength")
internal class PressureLossTest {

    // A warning from a 5 kPa drop, and two steps of the sensor
    private val rule = PressureLoss.Rule()

    /** A reading [minute] minutes in, at 30 °C unless told otherwise */
    private fun reading(minute: Double, kpa: Float, celsius: Float = 30f, sensorId: Int = 1) =
        TyreAtmosphere(minute * SECONDS_PER_MINUTE, sensorId, kpa.kpa, celsius.celsius)

    private fun track(vararg readings: TyreAtmosphere, rule: PressureLoss.Rule = this.rule) =
        readings.fold(PressureLoss.Tracker()) { tracker, reading -> tracker.next(reading, rule) }.loss

    @Test
    fun `a tyre warming up loses nothing`() {
        assertNull(track(*(0..20).map { reading(it.toDouble(), 200f + it, 30f + it / 2f) }.toTypedArray()))
    }

    @Test
    fun `a puncture while riding is warned about`() {
        val loss = track(
            reading(0.0, 200f),
            reading(5.0, 210f, 35f),
            reading(10.0, 214f, 38f),
            reading(12.0, 210f, 38f),
            reading(14.0, 206f, 38f),
            reading(16.0, 202f, 38f),
        )
        assertNotNull(loss)
        // From the peak, 12 kPa in 6 minutes
        assertEquals(10.0 * SECONDS_PER_MINUTE, loss.since)
        assertEquals(12f, loss.drop.kpa, 0.01f)
        assertEquals(120f, loss.perHour.kpa, 0.01f)
    }

    @Test
    fun `a single reading under the minimum drop isn't enough`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 206f)))
    }

    @Test
    fun `a flickering reading isn't a loss`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 206f), reading(3.0, 210f), reading(4.0, 206f)))
    }

    @Test
    fun `a drop smaller than the minimum isn't a loss`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 210f), reading(4.0, 210f)))
    }

    @Test
    fun `the minimum drop is a setting`() {
        // Warming up in steps of 2 kPa
        val readings = arrayOf(reading(-1.0, 212f), reading(0.0, 214f), reading(2.0, 210f), reading(4.0, 210f))
        assertNotNull(track(*readings, rule = PressureLoss.Rule(4f.kpa)))
    }

    @Test
    fun `a tyre cooling down at a stop isn't a loss`() {
        assertNull(track(reading(0.0, 214f, 36f), reading(2.0, 206f, 33f), reading(4.0, 202f, 29f)))
    }

    @Test
    fun `the peak is the latest reading at the highest pressure`() {
        val loss = track(reading(-1.0, 212f), reading(0.0, 214f), reading(6.0, 214f), reading(8.0, 206f), reading(10.0, 206f))
        assertEquals(6.0 * SECONDS_PER_MINUTE, loss?.since)
    }

    @Test
    fun `a loss stays until the pressure is back to its highest`() {
        val lossy = arrayOf(reading(-1.0, 212f), reading(0.0, 214f), reading(2.0, 206f), reading(4.0, 206f), reading(5.0, 210f))
        assertNotNull(track(*lossy))
        assertNull(track(*lossy, reading(6.0, 214f)))
    }

    @Test
    fun `a new ride starts over`() {
        val lossy = arrayOf(reading(-1.0, 212f), reading(0.0, 214f), reading(2.0, 206f), reading(4.0, 206f))
        assertNotNull(track(*lossy))
        assertNull(track(*lossy, reading(14.0, 190f)))
    }

    @Test
    fun `the rides are split by a gap of ten minutes`() {
        // The pressure dropped while parked, 20 kPa between the rides
        assertNull(track(reading(0.0, 214f), reading(9.0 * 60, 194f), reading(9.0 * 60 + 1, 194f)))
    }

    @Test
    fun `a sensor off the valve starts over`() {
        assertNull(track(reading(0.0, 214f), reading(1.0, 0.7f), reading(2.0, 200f), reading(3.0, 200f)))
    }

    @Test
    fun `another sensor starts over`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 200f, sensorId = 2), reading(3.0, 200f, sensorId = 2)))
    }

    @Test
    fun `a drop the sensor read in one go is a single step`() {
        // Nothing tells 8 kPa is two steps rather than one: the drop is the smallest change yet
        assertNull(track(reading(0.0, 214f), reading(2.0, 206f), reading(4.0, 206f)))
    }

    @Test
    fun `a single step of a coarse sensor isn't a loss`() {
        // An older Wicarlink sensor, in steps of 10 kPa
        assertNull(track(reading(0.0, 220f), reading(2.0, 230f), reading(4.0, 240f), reading(6.0, 230f), reading(8.0, 230f)))
    }

    @Test
    fun `two steps of a coarse sensor are a loss`() {
        val loss = track(
            reading(0.0, 220f),
            reading(2.0, 230f),
            reading(4.0, 240f),
            reading(6.0, 230f),
            reading(8.0, 220f),
            reading(10.0, 220f),
        )
        assertEquals(20f, assertNotNull(loss).drop.kpa, 0.01f)
    }

    @Test
    fun `an older Wicarlink sensor flickering by a step isn't a loss at the smallest minimum drop`() {
        // Only the unit of 3.144 kPa nearest to each 0.1 bar: 73, 76 and 80 units for 230, 240 and
        // 250 kPa, 9.4 or 12.6 kPa apart
        val kpa = { units: Int -> units * 3.144f }
        val warmingUp = arrayOf(reading(0.0, kpa(73)), reading(2.0, kpa(76)), reading(4.0, kpa(80)))
        assertNull(track(*warmingUp, reading(6.0, kpa(76)), reading(8.0, kpa(76)), rule = PressureLoss.Rule(5f.kpa)))
        assertNotNull(track(*warmingUp, reading(6.0, kpa(76)), reading(8.0, kpa(73)), reading(10.0, kpa(73)), rule = PressureLoss.Rule(5f.kpa)))
    }

    @Test
    fun `a sensor's step carries over from its earlier rides`() {
        val earlier = arrayOf(reading(0.0, 220f), reading(2.0, 230f))
        val later = arrayOf(reading(60.0, 240f), reading(62.0, 220f), reading(64.0, 220f))
        assertNull(track(*later))
        assertNotNull(track(*earlier, *later))
    }

    @Test
    fun `another sensor's step is learned again`() {
        val earlier = arrayOf(reading(0.0, 220f), reading(2.0, 230f))
        val later = arrayOf(reading(60.0, 240f, sensorId = 2), reading(62.0, 220f, sensorId = 2), reading(64.0, 220f, sensorId = 2))
        assertNull(track(*earlier, *later))
    }

    @Test
    fun `a newer Wicarlink sensor's ride isn't a loss`() {
        // Gold's front left on 2026-11-27, in units of 3.144 kPa which 0.1 bar steps turned into a
        // 10 kPa drop at 09:39
        val kpa = { units: Int -> units * 3.144f }
        assertNull(
            track(
                reading(0.0, kpa(55), 32f),
                reading(0.6, kpa(55), 33f),
                reading(2.6, kpa(55), 33f),
                reading(4.6, kpa(55), 34f),
                reading(8.7, kpa(55), 36f),
                reading(10.75, kpa(57), 34f),
                reading(22.9, kpa(56), 33f),
                reading(24.9, kpa(56), 32f),
                reading(26.9, kpa(56), 32f),
                reading(28.9, kpa(56), 31f),
                reading(30.0, kpa(56), 30f),
                reading(32.0, kpa(55), 30f),
                reading(34.0, kpa(55), 31f),
                reading(36.0, kpa(56), 31f),
            ),
        )
    }

    @Test
    fun `the other newer Wicarlink sensor's ride isn't a loss either`() {
        // Gold's rear left, the same ride
        val kpa = { units: Int -> units * 3.144f }
        assertNull(
            track(
                reading(0.0, kpa(76), 34f),
                reading(0.5, kpa(76), 34f),
                reading(4.4, kpa(75), 40f),
                reading(9.8, kpa(75), 38f),
                reading(11.8, kpa(75), 39f),
                reading(23.2, kpa(75), 41f),
                reading(25.2, kpa(75), 40f),
                reading(27.2, kpa(75), 39f),
                reading(29.15, kpa(77), 39f),
                reading(31.1, kpa(77), 38f),
                reading(35.1, kpa(76), 40f),
                reading(37.1, kpa(77), 39f),
                reading(39.05, kpa(76), 40f),
            ),
        )
    }

    @Test
    fun `older readings are ignored`() {
        assertNull(track(reading(0.0, 214f), reading(2.0, 214f), reading(1.0, 200f), reading(1.5, 200f)))
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
