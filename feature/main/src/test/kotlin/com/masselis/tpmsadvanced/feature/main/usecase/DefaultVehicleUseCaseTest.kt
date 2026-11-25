package com.masselis.tpmsadvanced.feature.main.usecase

import com.masselis.tpmsadvanced.core.test.mockkQueryList
import com.masselis.tpmsadvanced.core.test.mockkQueryOne
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.interfaces.VehicleDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DefaultVehicleUseCaseTest {

    private lateinit var vehicles: List<Vehicle>
    private var sensorCount: Long = 0
    private lateinit var vehicleDatabase: VehicleDatabase
    private lateinit var sensorDatabase: SensorDatabase
    private lateinit var currentVehicleUseCase: CurrentVehicleUseCase
    private lateinit var deleted: MutableList<Vehicle>
    private var inserted = false
    private var insertedBeforeDelete = false

    private fun vehicle(name: String = "My car", kind: Vehicle.Kind = CAR): Vehicle = mockk {
        val uuid = UUID.randomUUID()
        every { this@mockk.uuid } returns uuid
        every { this@mockk.name } returns name
        every { this@mockk.kind } returns kind
    }

    @Before
    fun setup() {
        vehicles = listOf(vehicle())
        sensorCount = 0
        deleted = mutableListOf()
        inserted = false
        insertedBeforeDelete = false
        vehicleDatabase = mockk { every { selectAll() } answers { mockkQueryList(vehicles) } }
        sensorDatabase = mockk { every { countByVehicle(any()) } answers { mockkQueryOne(sensorCount) } }
        currentVehicleUseCase = mockk {
            coEvery { insertAsCurrent(any(), any()) } answers {
                inserted = true
                UUID.randomUUID()
            }
        }
    }

    private fun test() = DefaultVehicleUseCase(vehicleDatabase, sensorDatabase, currentVehicleUseCase) {
        insertedBeforeDelete = inserted
        deleted += it
    }

    @Test
    fun `the vehicle the app is installed with is untouched`() = runTest {
        assertEquals(vehicles.single(), test().untouched())
    }

    @Test
    fun `a renamed vehicle isn't the default one anymore`() = runTest {
        vehicles = listOf(vehicle(name = "Van"))
        assertNull(test().untouched())
    }

    @Test
    fun `a vehicle of another kind isn't the default one`() = runTest {
        vehicles = listOf(vehicle(kind = MOTORCYCLE))
        assertNull(test().untouched())
    }

    @Test
    fun `a vehicle with a sensor isn't the default one anymore`() = runTest {
        sensorCount = 1
        assertNull(test().untouched())
    }

    @Test
    fun `with another vehicle, none is the default one`() = runTest {
        vehicles = listOf(vehicle(), vehicle(name = "Van"))
        assertNull(test().untouched())
    }

    @Test
    fun `the vehicle added replaces the default one once added`() = runTest {
        val default = vehicles.single()
        test().replace("NMax", MOTORCYCLE)
        coVerify { currentVehicleUseCase.insertAsCurrent("NMax", MOTORCYCLE) }
        assertEquals(listOf(default), deleted)
        // The app always has a vehicle to show
        assertTrue(insertedBeforeDelete)
    }

    @Test
    fun `a vehicle in use is kept, the new one only added`() = runTest {
        sensorCount = 2
        test().replace("NMax", MOTORCYCLE)
        coVerify { currentVehicleUseCase.insertAsCurrent("NMax", MOTORCYCLE) }
        assertEquals(emptyList(), deleted)
    }
}
