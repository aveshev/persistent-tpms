package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.impl

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.interfaces.SensorDatabase
import com.masselis.tpmsadvanced.data.vehicle.model.Sensor
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class VehicleSettingsViewModelImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sensors: FakeQuery<Sensor>
    private lateinit var sensorDatabase: SensorDatabase

    @Before
    fun setup() {
        sensors = FakeQuery(CAR.locations.mapIndexed { index, location -> Sensor(index, location, PECHAM) })
        sensorDatabase = mockk(relaxed = true) {
            every { selectListByVehicleId(VEHICLE_ID) } returns sensors.asList()
        }
    }

    private fun test() = VehicleSettingsViewModelImpl(
        mockk(relaxed = true),
        mockk(relaxed = true),
        MutableStateFlow(mockk { every { uuid } returns VEHICLE_ID; every { kind } returns CAR }),
        mockk(relaxed = true),
        sensorDatabase,
    )

    @Test
    fun `sensors cleared while nothing collects it are read once collected again`() = runTest {
        val viewModel = test()
        // The main screen, then the vehicle's settings opened over it, which clears its sensors
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isFullyAssigned.collect {} }
            .also { assertTrue(viewModel.isFullyAssigned.value) }
            .cancel()
        sensors.rows = emptyList()
        // Back to the main screen
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isFullyAssigned.collect {} }
        assertFalse(viewModel.isFullyAssigned.value)
    }

    /** A query notifying its listeners as SQLDelight does when its table changes, see [rows] */
    private class FakeQuery<T : Any>(initial: List<T>) : Query<T>({ (it as Cursor<*>).row as T }) {
        private val listeners = mutableListOf<Listener>()

        var rows: List<T> = initial
            set(value) {
                field = value
                listeners.toList().forEach { it.queryResultsChanged() }
            }

        override fun addListener(listener: Listener) {
            listeners += listener
        }

        override fun removeListener(listener: Listener) {
            listeners -= listener
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
            mapper(Cursor(rows.iterator()))

        private class Cursor<T>(private val rows: Iterator<T>) : SqlCursor {
            var row: T? = null
                private set

            override fun next(): QueryResult<Boolean> = QueryResult.Value(
                rows.hasNext().also { if (it) row = rows.next() }
            )

            override fun getString(index: Int): String? = error("Not a column")
            override fun getLong(index: Int): Long? = error("Not a column")
            override fun getBytes(index: Int): ByteArray? = error("Not a column")
            override fun getDouble(index: Int): Double? = error("Not a column")
            override fun getBoolean(index: Int): Boolean? = error("Not a column")
        }
    }

    private companion object {
        val VEHICLE_ID: UUID = UUID.randomUUID()
    }
}
