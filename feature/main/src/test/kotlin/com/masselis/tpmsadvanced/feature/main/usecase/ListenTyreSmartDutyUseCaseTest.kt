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

    private val first = mockk<Tyre.Located>()
    private val second = mockk<Tyre.Located>()
    private val normal = mockk<Tyre.Located>()

    @Before
    fun setup() {
        scanner = mockk {
            every { highDutyScan() } returns flowOf(first, second)
            every { normalScan() } returns flowOf(normal)
        }
        balanced = MutableStateFlow(false)
        isAppVisible = MutableStateFlow(true)
        keepsLowLatencyInBackground = false
    }

    private fun test() = ListenTyreSmartDutyUseCase(scanner, balanced, isAppVisible, keepsLowLatencyInBackground)

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
