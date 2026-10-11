package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.FRONT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Axle.REAR
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class MagnifierZonesTest {

    private lateinit var centers: Map<Location, Offset>
    private lateinit var outlines: Map<Location, Rect>
    private lateinit var area: Rect

    private fun test() = MagnifierZones(centers, outlines)

    @Before
    fun setup() {
        // A car on a 400 x 800 area, its axles at 200 and 500, its wheels at 100 and 300
        centers = mapOf(
            Location.Wheel(FRONT_LEFT) to Offset(100f, 200f),
            Location.Wheel(FRONT_RIGHT) to Offset(300f, 200f),
            Location.Wheel(REAR_LEFT) to Offset(100f, 500f),
            Location.Wheel(REAR_RIGHT) to Offset(300f, 500f),
        )
        outlines = emptyMap()
        area = Rect(0f, 0f, 400f, 800f)
    }

    @Test
    fun `a car is split midway between its axles and its wheels`() {
        val zones = test()
        assertEquals(Location.Wheel(FRONT_LEFT), zones.locationAt(Offset(10f, 10f), area))
        assertEquals(Location.Wheel(FRONT_LEFT), zones.locationAt(Offset(199f, 349f), area))
        assertEquals(Location.Wheel(FRONT_RIGHT), zones.locationAt(Offset(201f, 349f), area))
        assertEquals(Location.Wheel(REAR_LEFT), zones.locationAt(Offset(199f, 351f), area))
        assertEquals(Location.Wheel(REAR_RIGHT), zones.locationAt(Offset(390f, 790f), area))
    }

    @Test
    fun `nothing is magnified out of the area`() {
        assertNull(test().locationAt(Offset(10f, 810f), area))
    }

    @Test
    fun `a single wheel axle takes its whole width`() {
        // A tadpole three-wheeler
        centers = mapOf(
            Location.Wheel(FRONT_LEFT) to Offset(100f, 200f),
            Location.Wheel(FRONT_RIGHT) to Offset(300f, 200f),
            Location.Axle(REAR) to Offset(200f, 600f),
        )
        val zones = test()
        assertEquals(Location.Wheel(FRONT_LEFT), zones.locationAt(Offset(10f, 390f), area))
        assertEquals(Location.Axle(REAR), zones.locationAt(Offset(10f, 410f), area))
        assertEquals(Location.Axle(REAR), zones.locationAt(Offset(390f, 410f), area))
    }

    @Test
    fun `a vehicle without axles is split between its sides`() {
        centers = mapOf(
            Location.Side(LEFT) to Offset(100f, 500f),
            Location.Side(RIGHT) to Offset(300f, 500f),
        )
        val zones = test()
        assertEquals(Location.Side(LEFT), zones.locationAt(Offset(10f, 10f), area))
        assertEquals(Location.Side(RIGHT), zones.locationAt(Offset(390f, 790f), area))
    }

    @Test
    fun `a motorcycle is split midway between its wheels`() {
        centers = mapOf(
            Location.Axle(FRONT) to Offset(200f, 100f),
            Location.Axle(REAR) to Offset(200f, 700f),
        )
        val zones = test()
        assertEquals(Location.Axle(FRONT), zones.locationAt(Offset(390f, 399f), area))
        assertEquals(Location.Axle(REAR), zones.locationAt(Offset(10f, 401f), area))
    }

    @Test
    fun `the spare has what's under the lines from its outline's closest corners to the area's bottom corners`() {
        centers = centers + (Location.Spare to Offset(200f, 640f))
        // The rear outlines end at 560, the spare's starts at 600 between 150 and 250: the lines
        // go through (125, 580) and (275, 580)
        outlines = mapOf(
            Location.Wheel(REAR_LEFT) to Rect(0f, 440f, 100f, 560f),
            Location.Wheel(REAR_RIGHT) to Rect(300f, 440f, 400f, 560f),
            Location.Spare to Rect(150f, 600f, 250f, 700f),
        )
        val zones = test()
        // Between the two middles, level
        assertEquals(Location.Spare, zones.locationAt(Offset(200f, 581f), area))
        assertEquals(Location.Wheel(REAR_RIGHT), zones.locationAt(Offset(201f, 579f), area))
        // On the diagonals, from (275, 580) to (400, 800): 690 at 337.5
        assertEquals(Location.Spare, zones.locationAt(Offset(337.5f, 691f), area))
        assertEquals(Location.Wheel(REAR_RIGHT), zones.locationAt(Offset(337.5f, 689f), area))
        // And from (0, 800) to (125, 580)
        assertEquals(Location.Spare, zones.locationAt(Offset(62.5f, 691f), area))
        assertEquals(Location.Wheel(REAR_LEFT), zones.locationAt(Offset(62.5f, 689f), area))
    }
}
