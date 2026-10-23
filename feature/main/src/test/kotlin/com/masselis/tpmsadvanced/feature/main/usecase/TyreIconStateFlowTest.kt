package com.masselis.tpmsadvanced.feature.main.usecase

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.common.now
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.model.AlertThresholds
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.bar
import com.masselis.tpmsadvanced.data.vehicle.model.SensorLocation.FRONT_LEFT
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature
import com.masselis.tpmsadvanced.data.vehicle.model.Temperature.CREATOR.celsius
import com.masselis.tpmsadvanced.data.vehicle.model.TyreAtmosphere
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import com.masselis.tpmsadvanced.data.vehicle.model.Voltage.CREATOR.volts
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow.State
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
internal class TyreIconStateFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var tyreAtmosphereUseCase: TyreAtmosphereUseCase
    private lateinit var vehicleRangesUseCase: VehicleRangesUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        tyreAtmosphereUseCase = mockk {
            every { listen() } returns emptyFlow()
        }
        vehicleRangesUseCase = mockk {
            every { lowTemp } returns MutableStateFlow(20f.celsius)
            every { highTemp } returns MutableStateFlow(90f.celsius)
            every { alertThresholds(Wheel(FRONT_LEFT)) } returns
                flowOf(AlertThresholds(1f.bar, 3f.bar, 90f.celsius, 2.6f.volts))
        }
        savedStateHandle = SavedStateHandle()
    }

    context(scope: TestScope)
    private fun test() = TyreIconStateFlow(
        TyreAlertsUseCase(tyreAtmosphereUseCase, vehicleRangesUseCase, flowOf(null), Wheel(FRONT_LEFT)),
        vehicleRangesUseCase,
        Wheel(FRONT_LEFT),
        SensorBindingUseCase.NoOp,
        scope.backgroundScope,
    )

    private fun setAtmosphere(pressure: Pressure, temperature: Temperature) =
        every { tyreAtmosphereUseCase.listen() }.returns(
            flowOf(TyreAtmosphere(now(), 0x562D00, pressure, temperature))
        )

    @Test
    fun notDetected(): Unit = runTest {
        assertIs<State.NotDetected>(test().value)
    }

    @Test
    fun belowRangeTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, (-10f).celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal.BlueToGreen>(awaitItem())
        }
    }

    @Test
    fun normalTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, 25f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal.BlueToGreen>(awaitItem())
        }
    }

    @Test
    fun `the tyre turns green over the 10 degrees above cold`(): Unit = runTest {
        setAtmosphere(2f.bar, 25f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertEquals(0.5f, assertIs<State.Normal.BlueToGreen>(awaitItem()).fraction.value, DELTA)
        }
    }

    @Test
    fun `the tyre stays green until the reading turns amber`(): Unit = runTest {
        setAtmosphere(2f.bar, 79f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertEquals(1f, assertIs<State.Normal.BlueToGreen>(awaitItem()).fraction.value, DELTA)
        }
    }

    @Test
    fun highTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, 85f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertEquals(0.5f, assertIs<State.Normal.GreenToRed>(awaitItem()).fraction.value, DELTA)
        }
    }

    @Test
    fun `a narrow range splits between both fades`(): Unit = runTest {
        every { vehicleRangesUseCase.lowTemp } returns MutableStateFlow(20f.celsius)
        every { vehicleRangesUseCase.highTemp } returns MutableStateFlow(30f.celsius)
        every { vehicleRangesUseCase.alertThresholds(Wheel(FRONT_LEFT)) } returns
            flowOf(AlertThresholds(1f.bar, 3f.bar, 30f.celsius, 2.6f.volts))
        setAtmosphere(2f.bar, 22.5f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertEquals(0.5f, assertIs<State.Normal.BlueToGreen>(awaitItem()).fraction.value, DELTA)
        }
    }

    @Test
    fun `cold equal to hot doesn't divide by zero`(): Unit = runTest {
        every { vehicleRangesUseCase.lowTemp } returns MutableStateFlow(20f.celsius)
        every { vehicleRangesUseCase.highTemp } returns MutableStateFlow(20f.celsius)
        setAtmosphere(2f.bar, 20f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertEquals(0f, assertIs<State.Normal.BlueToGreen>(awaitItem()).fraction.value, DELTA)
        }
    }

    @Test
    fun hotTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, 95f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertFalse(assertIs<State.Alerting>(awaitItem()).isCritical)
        }
    }

    @Test
    fun ultraHighTemperature(): Unit = runTest {
        setAtmosphere(2f.bar, 115f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertTrue(assertIs<State.Alerting>(awaitItem()).isCritical)
        }
    }

    @Test
    fun `amber doesn't change the tyre`(): Unit = runTest {
        setAtmosphere(1.02f.bar, 30f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal.BlueToGreen>(awaitItem())
        }
    }

    @Test
    fun `a low battery doesn't make the tyre alert`(): Unit = runTest {
        every { tyreAtmosphereUseCase.listen() }.returns(
            flowOf(TyreAtmosphere(now(), 0x562D00, 2f.bar, 30f.celsius, 2.6f.volts))
        )
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal>(awaitItem())
        }
    }

    @Test
    fun noPressure(): Unit = runTest {
        setAtmosphere(0f.bar, 45f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertTrue(assertIs<State.Alerting>(awaitItem()).isCritical)
        }
    }

    @Test
    fun lowPressure(): Unit = runTest {
        setAtmosphere(0.8f.bar, 45f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Alerting>(awaitItem())
        }
    }

    @Test
    fun highPressure(): Unit = runTest {
        setAtmosphere(4f.bar, 45f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Alerting>(awaitItem())
        }
    }

    @Test
    fun normalPressure(): Unit = runTest {
        setAtmosphere(2f.bar, 45f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal>(awaitItem())
        }
    }

    @Test
    fun obsolete(): Unit = runTest {
        setAtmosphere(2f.bar, 35f.celsius)
        test().test {
            assertIs<State.NotDetected>(awaitItem())
            assertIs<State.Normal.BlueToGreen>(awaitItem())
            advanceTimeBy(1.hours)
            assertIs<State.NotDetected>(awaitItem())
        }
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
