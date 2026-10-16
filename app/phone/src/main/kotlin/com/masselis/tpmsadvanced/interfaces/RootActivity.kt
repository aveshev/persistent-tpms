package com.masselis.tpmsadvanced.interfaces

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.serialization.saved
import com.masselis.tpmsadvanced.core.ui.LocalKeepScreenOnCounter
import com.masselis.tpmsadvanced.core.ui.ScreenOnCounter
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.DARK
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.LIGHT
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences.Theme.SYSTEM
import com.masselis.tpmsadvanced.interfaces.composable.Main
import com.masselis.tpmsadvanced.interfaces.composable.TpmsAdvancedTheme
import com.masselis.tpmsadvanced.ioc.Bindings
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

public class RootActivity : AppCompatActivity() {

    private val counter = ScreenOnCounter.Activity()

    private var hasConsumedIntent by saved { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set before super.onCreate() so that the first frame already has the chosen theme. A change
        // afterwards recreates the activity, the window and system bars following the night resources.
        // Setting the mode already set does nothing, hence the current value collected again below.
        AppCompatDelegate.setDefaultNightMode(Bindings.appPreferences.theme.value.nightMode)
        super.onCreate(savedInstanceState)
        Bindings
            .appPreferences
            .theme
            .onEach { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
            .launchIn(lifecycleScope)

        val expectedVehicle = intent
            ?.data
            ?.pathSegments
            ?.first()
            ?.let { UUID.fromString(it) }
            ?.takeIf { hasConsumedIntent.not() }
            ?.also { hasConsumedIntent = true }

        setContent {
            CompositionLocalProvider(LocalKeepScreenOnCounter provides counter) {
                TpmsAdvancedTheme {
                    Main(expectedVehicle)
                }
            }
        }
    }

    private val Theme.nightMode
        get() = when (this) {
            SYSTEM -> MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> MODE_NIGHT_NO
            DARK -> MODE_NIGHT_YES
        }
}
