package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.vehicle.model.Pressure.CREATOR.kpa
import com.masselis.tpmsadvanced.data.vehicle.model.PressureCalibration
import com.masselis.tpmsadvanced.data.vehicle.model.SensorCalibration
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel.Dialog
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel.Event
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.SensorCalibrationViewModel.State
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleCalibrationUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class SensorCalibrationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var saved: MutableStateFlow<SensorCalibration>
    private lateinit var others: MutableStateFlow<List<SensorCalibration>>
    private lateinit var calibrationUseCase: VehicleCalibrationUseCase

    @Before
    fun setup() {
        saved = MutableStateFlow(calibrated)
        others = MutableStateFlow(emptyList())
        calibrationUseCase = mockk(relaxUnitFun = true) {
            every { of(SENSOR) } returns saved
            every { othersThan(SENSOR) } returns others
            every { locationOf(SENSOR) } returns flowOf(null)
            every { latestRead(SENSOR) } returns flowOf(null)
        }
    }

    private fun test() = SensorCalibrationViewModel(calibrationUseCase, SENSOR)

    /** The page shows the calibration, like the composable collecting it does */
    private fun TestScope.shown(viewModel: SensorCalibrationViewModel) = viewModel.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.stateFlow.collect {} }
    }

    private fun state(draft: SensorCalibration, vararg others: SensorCalibration) =
        State(saved = calibrated, draft = draft, others = others.toList())

    @Test
    fun `applying to all changes something while edited or another sensor reads differently`() {
        assertTrue(state(edited, calibrated).canApplyToAll)
        assertTrue(state(calibrated, SensorCalibration.None).canApplyToAll)
        assertFalse(state(calibrated, calibrated).canApplyToAll)
        // Nothing to apply it to
        assertFalse(state(edited).canApplyToAll)
    }

    @Test
    fun `an edit undone or adjusting nothing writes nothing`() {
        assertFalse(state(calibrated).isChanged)
        assertTrue(state(edited).isChanged)
        val off = SensorCalibration.None
        assertFalse(State(saved = off, draft = off.copy(isEnabled = true), others = emptyList()).isChanged)
    }

    @Test
    fun `applying to all is offered by the edits even when the others already read the same`() {
        assertTrue(state(edited, edited).canApplyToAll)
    }

    @Test
    fun `applying erases only another sensor's calibration changing its readings`() {
        assertTrue(state(calibrated, other).applyingErases)
        assertFalse(state(calibrated, SensorCalibration.None).applyingErases)
        assertFalse(state(calibrated, other.copy(isEnabled = false)).applyingErases)
    }

    @Test
    fun `a calibration on adjusting nothing is applied off`() {
        val noAdjustment = SensorCalibration(true, PressureCalibration(0f.kpa, 1f))
        assertEquals(calibrated.copy(isEnabled = false), state(noAdjustment).toApply)
    }

    @Test
    fun `values edited behind a calibration turned back off change nothing`() {
        val off = calibrated.copy(isEnabled = false)
        val draft = edited.copy(isEnabled = false)
        State(saved = off, draft = draft, others = emptyList()).also {
            assertEquals(off, it.toApply)
            assertFalse(it.isChanged)
        }
    }

    @Test
    fun `turning a calibration off keeps its saved values`() {
        assertEquals(calibrated.copy(isEnabled = false), state(edited.copy(isEnabled = false)).toApply)
    }

    @Test
    fun `edits stay a draft until applied to the sensor`() = runTest {
        val viewModel = shown(test())

        viewModel.setOffset(20f.kpa)

        assertTrue(viewModel.stateFlow.value!!.isChanged)
        coVerify(exactly = 0) { calibrationUseCase.set(any(), any()) }

        viewModel.applyToSensor()

        coVerify { calibrationUseCase.set(SENSOR, SensorCalibration(true, PressureCalibration(20f.kpa, 1f))) }
        assertEquals(Event.Leave, viewModel.eventChannel.receive())
    }

    @Test
    fun `applies to all right away when it erases nothing`() = runTest {
        others.value = listOf(SensorCalibration.None)
        val viewModel = shown(test())

        viewModel.applyToAll()

        assertNull(viewModel.stateFlow.value!!.dialog)
        coVerify { calibrationUseCase.applyToAll(calibrated) }
        assertEquals(Event.Leave, viewModel.eventChannel.receive())
    }

    @Test
    fun `asks before erasing another sensor's calibration`() = runTest {
        others.value = listOf(other)
        val viewModel = shown(test())

        viewModel.applyToAll()

        assertEquals(Dialog.EraseOthers, viewModel.stateFlow.value!!.dialog)
        coVerify(exactly = 0) { calibrationUseCase.applyToAll(any()) }

        viewModel.eraseOthers()

        coVerify { calibrationUseCase.applyToAll(calibrated) }
        assertEquals(Event.Leave, viewModel.eventChannel.receive())
    }

    @Test
    fun `back leaves right away without edits`() = runTest {
        val viewModel = shown(test())

        viewModel.back()

        assertEquals(Event.Leave, viewModel.eventChannel.receive())
    }

    @Test
    fun `back asks before discarding the edits, which are never saved`() = runTest {
        val viewModel = shown(test())
        viewModel.setEnabled(false)

        viewModel.back()

        assertEquals(Dialog.Discard, viewModel.stateFlow.value!!.dialog)
        viewModel.dismissDialog()
        assertNull(viewModel.stateFlow.value!!.dialog)

        viewModel.back()
        viewModel.discard()

        assertEquals(Event.Leave, viewModel.eventChannel.receive())
        coVerify(exactly = 0) { calibrationUseCase.set(any(), any()) }
    }

    private companion object {
        const val SENSOR = 1
        val calibrated = SensorCalibration(true, PressureCalibration(10f.kpa, 1f))
        val edited = SensorCalibration(true, PressureCalibration(20f.kpa, 1f))
        val other = SensorCalibration(true, PressureCalibration((-5f).kpa, 1.02f))
    }
}
