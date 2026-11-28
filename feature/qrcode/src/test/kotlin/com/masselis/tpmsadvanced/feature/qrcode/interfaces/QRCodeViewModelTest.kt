package com.masselis.tpmsadvanced.feature.qrcode.interfaces

import androidx.camera.view.CameraController
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.feature.qrcode.interfaces.QRCodeViewModel.Event
import com.masselis.tpmsadvanced.feature.qrcode.model.QrCodeResult
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase
import com.masselis.tpmsadvanced.feature.qrcode.usecase.QrCodeSensorUseCase.Sight
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QRCodeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val code = QrCodeResult.Wicarlink(1)

    private lateinit var results: MutableSharedFlow<Sight>
    private lateinit var useCase: QrCodeSensorUseCase

    @Before
    fun setup() {
        results = MutableSharedFlow(extraBufferCapacity = 64)
        useCase = mockk()
    }

    private fun test(
        results: Flow<Sight> = this.results,
        controller: CameraController? = mockk(),
    ): QRCodeViewModel {
        every { useCase.analyse(any()) } returns results
        return QRCodeViewModel(useCase, controller)
    }

    private fun failing(exception: Exception): Flow<Sight> = flow { throw exception }

    /** What the camera sees, once the view model looks at it */
    private suspend fun seen(result: QrCodeResult?) {
        results.subscriptionCount.first { it > 0 }
        results.emit(Sight.Code(result))
    }

    /** An ID printed next to a code, read [times] in a row */
    private suspend fun printed(id: String, times: Int = 1) {
        results.subscriptionCount.first { it > 0 }
        repeat(times) { results.emit(Sight.PrintedId(id)) }
    }

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `nothing happens until a code is seen`() = runTest {
        test().apply {
            runCurrent()
            assertFalse(isUnknownCode.value)
            assertTrue(eventChannel.isEmpty)
        }
    }

    @Test
    fun `a TPMS code found closes the scan`() = runTest {
        test().apply {
            seen(code)
            assertEquals(Event.Found(code), eventChannel.receive())
            results.subscriptionCount.first { it == 0 }
        }
    }

    @Test
    fun `only the first code found is told`() = runTest {
        test().apply {
            seen(code)
            results.emit(Sight.Code(QrCodeResult.Wicarlink(2)))
            assertEquals(Event.Found(code), eventChannel.receive())
            runCurrent()
            assertTrue(eventChannel.isEmpty)
        }
    }

    @Test
    fun `a code which isn't a TPMS one is told and the scan goes on`() = runTest {
        test().apply {
            seen(null)
            assertTrue(isUnknownCode.value)
            assertTrue(eventChannel.isEmpty)
            assertEquals(1, results.subscriptionCount.value)
        }
    }

    @Test
    fun `the unknown code's flag lowers 2 seconds after it's out of view`() = runTest {
        test().apply {
            seen(null)
            advanceBy(2.seconds - 1.milliseconds)
            assertTrue(isUnknownCode.value)
            advanceBy(1.milliseconds)
            assertFalse(isUnknownCode.value)
        }
    }

    @Test
    fun `the unknown code's flag stays up while it's in view`() = runTest {
        test().apply {
            seen(null)
            advanceBy(1500.milliseconds)
            seen(null)
            advanceBy(1500.milliseconds)
            assertTrue(isUnknownCode.value)
            advanceBy(500.milliseconds)
            assertFalse(isUnknownCode.value)
        }
    }

    @Test
    fun `a TPMS code seen after an unknown one lowers its flag`() = runTest {
        test().apply {
            seen(null)
            seen(code)
            assertFalse(isUnknownCode.value)
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }

    @Test
    fun `an unavailable camera leaves`() = runTest {
        test(failing(CameraAnalyser.CameraUnavailable(IllegalArgumentException()))).apply {
            assertEquals(Event.LeaveBecauseCameraUnavailable, eventChannel.receive())
        }
    }

    @Test
    fun `a typed ID closes the scan`() = runTest {
        every { useCase.decode("2221C5") } returns code
        test().apply {
            enter("2221C5")
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }

    @Test
    fun `a typed ID is read without the camera`() = runTest {
        every { useCase.decode("2221C5") } returns code
        test(controller = null).apply {
            enter("2221C5")
            assertEquals(Event.Found(code), eventChannel.receive())
            verify(exactly = 0) { useCase.analyse(any()) }
        }
    }

    @Test
    fun `a typed ID after a code found isn't told`() = runTest {
        every { useCase.decode("2221C5") } returns QrCodeResult.Wicarlink(2)
        test().apply {
            seen(code)
            enter("2221C5")
            assertEquals(Event.Found(code), eventChannel.receive())
            runCurrent()
            assertTrue(eventChannel.isEmpty)
        }
    }

    @Test
    fun `the only code of the pictures closes the scan`() = runTest {
        coEvery { useCase.read(any()) } returns listOf(code)
        test().apply {
            read(listOf(mockk()))
            assertEquals(Event.Found(code), eventChannel.receive())
            assertFalse(isReading.value)
        }
    }

    @Test
    fun `pictures without a code tell so and the scan goes on`() = runTest {
        coEvery { useCase.read(any()) } returns emptyList()
        test().apply {
            read(listOf(mockk()))
            assertEquals(Event.NoCodeInPictures, eventChannel.receive())
            assertEquals(1, results.subscriptionCount.value)
        }
    }

    @Test
    fun `no picture picked reads nothing`() = runTest {
        test().apply {
            read(emptyList())
            runCurrent()
            assertTrue(eventChannel.isEmpty)
            coVerify(exactly = 0) { useCase.read(any()) }
        }
    }

    @Test
    fun `several codes in the pictures are a choice`() = runTest {
        val other = QrCodeResult.Wicarlink(2)
        coEvery { useCase.read(any()) } returns listOf(code, other)
        test().apply {
            read(listOf(mockk(), mockk()))
            runCurrent()
            assertEquals(listOf(code, other), choices.value)
            assertTrue(eventChannel.isEmpty)
            choose(other)
            assertEquals(Event.Found(other), eventChannel.receive())
            assertEquals(emptyList(), choices.value)
        }
    }

    @Test
    fun `a code which can't be used isn't a choice next to one which can`() = runTest {
        coEvery { useCase.read(any()) } returns listOf(QrCodeResult.SharedUnreadable, code)
        test().apply {
            read(listOf(mockk(), mockk()))
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }

    @Test
    fun `a choice dismissed keeps scanning`() = runTest {
        coEvery { useCase.read(any()) } returns listOf(code, QrCodeResult.Wicarlink(2))
        test().apply {
            read(listOf(mockk(), mockk()))
            runCurrent()
            choose(null)
            runCurrent()
            assertEquals(emptyList(), choices.value)
            assertTrue(eventChannel.isEmpty)
            seen(code)
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }

    @Test
    fun `a printed ID is only suggested after 10 seconds`() = runTest {
        test().apply {
            printed("224822", times = 3)
            advanceBy(10.seconds - 1.milliseconds)
            assertNull(suggestedId.value)
            advanceBy(1.milliseconds)
            assertEquals("224822", suggestedId.value)
        }
    }

    @Test
    fun `a printed ID read twice isn't suggested`() = runTest {
        test().apply {
            printed("224822", times = 2)
            advanceBy(20.seconds)
            assertNull(suggestedId.value)
            printed("224822")
            runCurrent()
            assertEquals("224822", suggestedId.value)
        }
    }

    @Test
    fun `the printed ID read the most is suggested`() = runTest {
        test().apply {
            printed("224B22", times = 3)
            printed("224822", times = 5)
            advanceBy(10.seconds)
            assertEquals("224822", suggestedId.value)
        }
    }

    @Test
    fun `a code read before 10 seconds closes the scan without a suggestion`() = runTest {
        test().apply {
            printed("224822", times = 3)
            seen(code)
            assertEquals(Event.Found(code), eventChannel.receive())
            advanceBy(10.seconds)
            assertNull(suggestedId.value)
            results.subscriptionCount.first { it == 0 }
        }
    }

    @Test
    fun `the suggested ID is used like a typed one`() = runTest {
        every { useCase.decode("224822") } returns code
        test().apply {
            printed("224822", times = 3)
            advanceBy(10.seconds)
            enter(checkNotNull(suggestedId.value))
            assertEquals(Event.Found(code), eventChannel.receive())
        }
    }
}
