package com.masselis.tpmsadvanced.core.common

public val isRunningInstrumentedTest: Boolean by lazy {
    try {
        Class.forName("androidx.test.espresso.Espresso")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

/**
 * Whether instrumented tests are running the app right now. Unlike [isRunningInstrumentedTest],
 * false in a debug build used by hand, which ships Espresso too: the test runner is asked for its
 * instrumentation, which it only has while running.
 */
public val isUnderInstrumentation: Boolean by lazy {
    runCatching {
        Class.forName("androidx.test.platform.app.InstrumentationRegistry")
            .getMethod("getInstrumentation")
            .invoke(null)
    }.isSuccess
}
