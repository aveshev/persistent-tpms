package com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DebugSettingsViewModelTest {

    private lateinit var developer: MutableStateFlow<Boolean>
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setup() {
        developer = MutableStateFlow(false)
        appPreferences = mockk(relaxed = true) {
            every { developer } returns this@DebugSettingsViewModelTest.developer
        }
    }

    private fun test() = DebugSettingsViewModel(appPreferences, mockk())

    private fun DebugSettingsViewModel.tap(times: Int) = List(times) { onVersionTap() }

    @Test
    fun `the first 6 taps show nothing`() {
        assertEquals(List(6) { null }, test().tap(6))
        assertFalse(developer.value)
    }

    @Test
    fun `the last 3 taps count down to becoming a developer`() {
        test()
            .tap(10)
            .drop(6)
            .also {
                assertEquals(
                    listOf(
                        "Tap 3 more times to become a developer",
                        "Tap 2 more times to become a developer",
                        "Tap 1 more time to become a developer",
                        "You are now a developer",
                    ),
                    it,
                )
            }
        assertTrue(developer.value)
    }

    @Test
    fun `10 more taps hide the debug options again`() {
        test()
            .also { it.tap(10) }
            .also { assertNull(it.tap(6).last()) }
            .tap(4)
            .also {
                assertEquals(
                    listOf(
                        "Tap 3 more times to stop being a developer",
                        "Tap 2 more times to stop being a developer",
                        "Tap 1 more time to stop being a developer",
                        "You are no longer a developer",
                    ),
                    it,
                )
            }
        assertFalse(developer.value)
    }
}
