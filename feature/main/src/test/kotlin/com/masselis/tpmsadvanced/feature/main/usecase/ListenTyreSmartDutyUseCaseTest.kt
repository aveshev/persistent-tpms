package com.masselis.tpmsadvanced.feature.main.usecase

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class ListenTyreSmartDutyUseCaseTest {

    private lateinit var scanner: LocatedTyreScannerUseCase
    private lateinit var balanced: MutableStateFlow<Boolean>
    private lateinit var isAppVisible: MutableStateFlow<Boolean>
    private var keepsLowLatencyInBackground = false
    private lateinit var lowPowerWatch: MutableStateFlow<Boolean>

    private val first = mockk<Tyre.Located>()
    private val second = mockk<Tyre.Located>()
    private val normal = mockk<Tyre.Located>()
    private val lowPower = mockk<Tyre.Located>()

    @Before
    fun setup() {
        scanner = mockk {
            every { highDutyScan() } returns flowOf(first, second)
            every { normalScan() } returns flowOf(normal)
            every { lowPowerScan() } returns flowOf(lowPower)
        }
        balanced = MutableStateFlow(false)
        isAppVisible = MutableStateFlow(true)
        keepsLowLatencyInBackground = false
        lowPowerWatch = MutableStateFlow(false)
    }

    private fun test() =
        ListenTyreSmartDutyUseCase(scanner, balanced, isAppVisible, keepsLowLatencyInBackground, lowPowerWatch)

    @Test
    fun `uses the low power scan while watching in the background`() = runTest {
        isAppVisible.value = false
        keepsLowLatencyInBackground = true
        balanced.value = true
        lowPowerWatch.value = true
        test().listen().test {
            assertEquals(lowPower, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 0) { scanner.highDutyScan() }
        verify(exactly = 0) { scanner.normalScan() }
    }

    @Test
    fun `ignores the watch while the app is visible`() = runTest {
        lowPowerWatch.value = true
        test().listen().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 0) { scanner.lowPowerScan() }
    }

    @Test
    fun `the watch ending switches to the normal scan`() = runTest {
        isAppVisible.value = false
        lowPowerWatch.value = true
        every { scanner.lowPowerScan() } returns flow {
            emit(lowPower)
            awaitCancellation()
        }
        test().listen().test {
            assertEquals(lowPower, awaitItem())
            lowPowerWatch.value = false
            assertEquals(normal, awaitItem())
        }
    }

    @Test
    fun `switches to the normal scan after the first reading while balanced`() = runTest {
        balanced.value = true
        test().listen().test {
            assertEquals(first, awaitItem())
            assertEquals(normal, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `stays on the high duty scan while the app is visible`() = runTest {
        test().listen().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 0) { scanner.normalScan() }
    }

    @Test
    fun `turning balanced on starts over with the high duty scan`() = runTest {
        every { scanner.highDutyScan() } returns flow {
            emit(first)
            awaitCancellation()
        }
        every { scanner.normalScan() } returns flow {
            emit(normal)
            awaitCancellation()
        }
        test().listen().test {
            assertEquals(first, awaitItem())
            balanced.value = true
            assertEquals(first, awaitItem())
            assertEquals(normal, awaitItem())
        }
    }

    @Test
    fun `uses the normal scan while the app isn't visible`() = runTest {
        isAppVisible.value = false
        test().listen().test {
            assertEquals(normal, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 0) { scanner.highDutyScan() }
    }

    @Test
    fun `follows the app being shown and left`() = runTest {
        every { scanner.highDutyScan() } returns flow {
            emit(first)
            awaitCancellation()
        }
        every { scanner.normalScan() } returns flow {
            emit(normal)
            awaitCancellation()
        }
        test().listen().test {
            assertEquals(first, awaitItem())
            isAppVisible.value = false
            assertEquals(normal, awaitItem())
            isAppVisible.value = true
            assertEquals(first, awaitItem())
        }
    }

    @Test
    fun `a phone keeping low latency in the background stays on the high duty scan`() = runTest {
        keepsLowLatencyInBackground = true
        isAppVisible.value = false
        test().listen().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 0) { scanner.normalScan() }
    }

    @Test
    fun `balanced after the first reading ignores the app being visible`() = runTest {
        balanced.value = true
        isAppVisible.value = false
        test().listen().test {
            assertEquals(first, awaitItem())
            assertEquals(normal, awaitItem())
            isAppVisible.value = true
            expectNoEvents()
        }
    }
}
