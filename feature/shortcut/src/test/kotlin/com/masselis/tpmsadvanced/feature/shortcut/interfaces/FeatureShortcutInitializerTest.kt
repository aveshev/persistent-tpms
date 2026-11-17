package com.masselis.tpmsadvanced.feature.shortcut.interfaces

import com.masselis.tpmsadvanced.core.common.appGraph
import com.masselis.tpmsadvanced.feature.shortcut.ioc.Bindings
import com.masselis.tpmsadvanced.feature.shortcut.usecase.ShortcutUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

internal class FeatureShortcutInitializerTest {

    private var built = 0

    @Before
    fun setup() {
        built = 0
        // Making any Bindings initializes its companion first, which reads appGraph at once. So the
        // fake graph is made when appGraph is read, the JVM lets the companion's initialization
        // reenter there.
        mockkStatic("com.masselis.tpmsadvanced.core.common.CoreCommonInitializerKt")
        every { appGraph } answers {
            object : Bindings {
                override val featureShortcutInternal = Bindings.Internal {
                    mockk<ShortcutUseCase>().also { built++ }
                }
            }
        }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `starting the app builds the shortcut use case`() {
        FeatureShortcutInitializer().create(mockk())
        // The use case publishes the shortcuts once built
        assertEquals(1, built)
    }
}
