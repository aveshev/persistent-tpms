package com.masselis.tpmsadvanced.data.vehicle

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.REAR_RIGHT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.Side.LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
internal class SensorDatabaseTest {

    @ContributesTo(AppScope::class)
    internal interface Extractor {
        val database: Database
    }

    private lateinit var database: Database
    private lateinit var currentVehicleUuid: UUID
    private lateinit var vehicleQueries: VehicleQueries
    private lateinit var sensorQueries: SensorQueries
    private lateinit var sensorDatabase: SensorDatabase

    @Before
    fun setup() {
        appContext.getDatabasePath("car.db").delete()
        database = (appGraph as Extractor).database
        vehicleQueries = database.vehicleQueries
        sensorQueries = database.sensorQueries
        currentVehicleUuid = vehicleQueries.currentFavourite().executeAsOne().uuid
        sensorDatabase = SensorDatabase(database)
    }

    private fun assertSensorId(id: Int, vehicleUuid: UUID, location: Location) =
        assertEquals(
            id,
            sensorQueries
                .selectByVehicleAndLocation(vehicleUuid, location)
                .executeAsOne()
                .id
        )

    private fun assertSensorCount(count: Long, vehicleUuid: UUID) =
        assertEquals(count, sensorQueries.countByVehicle(vehicleUuid).executeAsOne())

    @Test
    fun simpleInsert() = runTest {
        assertSensorCount(0, currentVehicleUuid)
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        assertSensorId(1, currentVehicleUuid, Location.Wheel(FRONT_LEFT))
        assertSensorCount(1, currentVehicleUuid)
    }

    @Test
    fun insertToACarThanUpsertToAnOtherCar() = runTest {
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        assertSensorCount(1, currentVehicleUuid)

        val uuid = UUID.randomUUID()
        vehicleQueries.insert(uuid, Kind.CAR, "MOCK", false)
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), uuid)
        assertSensorCount(0, currentVehicleUuid)
        assertSensorId(1, uuid, Location.Wheel(FRONT_LEFT))
        assertSensorCount(1, uuid)
    }

    @Test
    fun insertSensor1ThenInsertAtTheSamePlaceSensor2() = runTest {
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        assertSensorCount(1, currentVehicleUuid)

        sensorDatabase.upsert(Sensor(2, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        assertSensorId(2, currentVehicleUuid, Location.Wheel(FRONT_LEFT))
        assertSensorCount(1, currentVehicleUuid)
    }

    @Test
    fun insertSensor1ThenUpsertWithANewLocation() = runTest {
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        assertSensorCount(1, currentVehicleUuid)

        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_RIGHT), PECHAM), currentVehicleUuid)
        assertSensorId(1, currentVehicleUuid, Location.Wheel(FRONT_RIGHT))
        assertSensorCount(1, currentVehicleUuid)
    }

    @Test
    fun upsertSensorToAWrongLocationForTheKind() = runTest {
        assertSensorCount(0, currentVehicleUuid)
        assertFailsWith<IllegalArgumentException> {
            sensorDatabase.upsert(Sensor(1, Location.Side(LEFT), PECHAM), currentVehicleUuid)
        }
        assertSensorCount(0, currentVehicleUuid)
    }

    private fun reading(location: Location, timestamp: Double, sensorId: Int) = database.readingQueries
        .insert(currentVehicleUuid, location, timestamp, sensorId, -60, "0303a5270308425208ff801b1b02a584ea")

    /** Each reading of the tyre at [location], as its sensor and time */
    private fun readings(location: Location) = database.readingQueries
        .allByLocation(currentVehicleUuid, location)
        .executeAsList()
        .map { it.sensorId to it.timestamp }

    @Test
    fun readingsFollowTheirSensorsAroundARotationEvenAtTheSameTime() = runTest {
        val wheels = listOf(FRONT_LEFT, FRONT_RIGHT, REAR_RIGHT, REAR_LEFT).map { Location.Wheel(it) }
        wheels.forEachIndexed { index, wheel ->
            sensorDatabase.upsert(Sensor(index + 1, wheel, PECHAM), currentVehicleUuid)
            // Every tyre read at once, and a reading of its own
            reading(wheel, 1.0, index + 1)
            reading(wheel, 10.0 + index, index + 1)
        }

        sensorDatabase.move(currentVehicleUuid, wheels.zipWithNext() + (wheels.last() to wheels.first()))

        wheels.forEachIndexed { index, wheel ->
            // Each wheel now has the sensor of the one before it
            val sensorId = (index + wheels.size - 1) % wheels.size + 1
            assertSensorId(sensorId, currentVehicleUuid, wheel)
            assertEquals(listOf(sensorId to 1.0, sensorId to 10.0 + sensorId - 1), readings(wheel))
        }
    }

    @Test
    fun aReadingOfAnotherSensorAtTheSameTimeStaysAndTheMovedOneIsDropped() = runTest {
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        reading(Location.Wheel(FRONT_LEFT), 1.0, 1)
        reading(Location.Wheel(FRONT_LEFT), 2.0, 1)
        // A sensor unbound since, its readings stay with the tyre
        reading(Location.Wheel(FRONT_RIGHT), 1.0, 9)

        sensorDatabase.move(currentVehicleUuid, listOf(Location.Wheel(FRONT_LEFT) to Location.Wheel(FRONT_RIGHT)))

        assertEquals(listOf(9 to 1.0, 1 to 2.0), readings(Location.Wheel(FRONT_RIGHT)))
        assertEquals(emptyList(), readings(Location.Wheel(FRONT_LEFT)))
    }

    @Test
    fun swappedSensorsKeepTheirCalibration() = runTest {
        val calibration = SensorCalibration(true, PressureCalibration((-93f).kpa, 1.05f))
        sensorDatabase.upsert(Sensor(1, Location.Wheel(FRONT_LEFT), PECHAM), currentVehicleUuid)
        sensorDatabase.upsert(Sensor(2, Location.Wheel(FRONT_RIGHT), PECHAM), currentVehicleUuid)
        sensorDatabase.updateCalibration(1, calibration)

        sensorDatabase.move(
            currentVehicleUuid,
            listOf(
                Location.Wheel(FRONT_LEFT) to Location.Wheel(FRONT_RIGHT),
                Location.Wheel(FRONT_RIGHT) to Location.Wheel(FRONT_LEFT),
            )
        )

        assertSensorId(1, currentVehicleUuid, Location.Wheel(FRONT_RIGHT))
        assertEquals(calibration, sensorDatabase.selectCalibration(1).execute())
        assertEquals(SensorCalibration.None, sensorDatabase.selectCalibration(2).execute())
    }
}
