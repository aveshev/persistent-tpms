package com.masselis.tpmsadvanced.feature.unlocated.usecase

import com.masselis.tpmsadvanced.data.vehicle.interfaces.ReadingDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class BindSensorToVehicleUseCaseTest {

    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var readingDatabase: ReadingDatabase

    @Before
    fun setup() {
        sensorDatabase = mockk(relaxUnitFun = true)
        readingDatabase = mockk(relaxUnitFun = true)
    }

    private fun test() = BindSensorToVehicleUseCase.Impl(sensorDatabase, readingDatabase)

    @Test
    fun `the sensor is bound, then the reading it was found with stored at its wheel`() = runTest {
        val vehicle = UUID.randomUUID()
        val location = Wheel(FRONT_LEFT)
        val sensor = Sensor(1, location, PECHAM)
        val tyre = Tyre.Unlocated(10.0, -50, 1, Pressure(230f), Temperature(20f), PECHAM, false)

        test().bind(vehicle, sensor, tyre)

        coVerifyOrder {
            sensorDatabase.upsert(sensor, vehicle)
            readingDatabase.insert(Tyre.Located(tyre, location), vehicle)
        }
    }
}
