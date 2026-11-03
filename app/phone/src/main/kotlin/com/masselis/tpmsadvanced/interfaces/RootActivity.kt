package com.masselis.tpmsadvanced.interfaces

import android.content.Context
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
import com.masselis.tpmsadvanced.core.common.AppLanguage
import com.masselis.tpmsadvanced.core.common.applyAppLanguage
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

public class RootActivity : AppCompatActivity() {

    private val counter = ScreenOnCounter.Activity()

    private var hasConsumedIntent by saved { false }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyAppLanguage()
    }

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
        // Every text is read again in the chosen language
        AppLanguage
            .current
            .drop(1)
            .onEach { recreate() }
            .launchIn(lifecycleScope)
        // The language is a debug option: with them off, the app follows the phone. After the
        // above, which would miss a change from the first value
        Bindings
            .appPreferences
            .debugOptionsInUse
            .filter { it.not() }
            .onEach { AppLanguage.current.value = AppLanguage.SYSTEM }
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
