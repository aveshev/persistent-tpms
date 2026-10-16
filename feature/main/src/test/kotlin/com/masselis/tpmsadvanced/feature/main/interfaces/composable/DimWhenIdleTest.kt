package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.BATTERY
import com.masselis.tpmsadvanced.data.vehicle.model.AlertClass.PRESSURE
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.AMBER
import com.masselis.tpmsadvanced.data.vehicle.model.AlertLevel.RED
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DimWhenIdleTest {

    @Test
    fun `a first warning raises`() {
        assertTrue(warnings((Wheel(FRONT_LEFT) to BATTERY) to AMBER).raisesFrom(warnings()))
    }

    @Test
    fun `the same warnings don't raise`() {
        warnings((Wheel(FRONT_LEFT) to BATTERY) to AMBER)
            .also { assertFalse(it.raisesFrom(it)) }
    }

    @Test
    fun `a warning turning into an alert raises`() {
        assertTrue(
            warnings((Wheel(FRONT_LEFT) to PRESSURE) to RED)
                .raisesFrom(warnings((Wheel(FRONT_LEFT) to PRESSURE) to AMBER))
        )
    }

    @Test
    fun `a warning going away or lowering doesn't raise`() {
        assertFalse(
            warnings((Wheel(FRONT_LEFT) to PRESSURE) to AMBER)
                .raisesFrom(
                    warnings(
                        (Wheel(FRONT_LEFT) to PRESSURE) to RED,
                        (Wheel(REAR_RIGHT) to BATTERY) to AMBER,
                    )
                )
        )
    }

    @Test
    fun `the same warning on another tyre raises`() {
        assertTrue(
            warnings(
                (Wheel(FRONT_LEFT) to BATTERY) to AMBER,
                (Wheel(REAR_RIGHT) to BATTERY) to AMBER,
            ).raisesFrom(warnings((Wheel(FRONT_LEFT) to BATTERY) to AMBER))
        )
    }

    private fun warnings(vararg warnings: Pair<Pair<Location, AlertClass>, AlertLevel>) = mapOf(*warnings)
}
