package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.DebugSettingsViewModel
import com.masselis.tpmsadvanced.feature.main.ioc.Bindings.Companion.DebugSettingsViewModel

/**
 * The app's version, in the "About" group of the app settings. Tapping it 10 times in a row shows
 * the debug options in [MiscSettings], or hides them again, as Android does with its own developer
 * options.
 */
@Composable
public fun VersionSettingsItem(
    versionName: String,
    modifier: Modifier = Modifier,
): Unit = VersionSettingsItem(versionName, modifier, viewModel { DebugSettingsViewModel() })

@Composable
internal fun VersionSettingsItem(
    versionName: String,
    modifier: Modifier = Modifier,
    viewModel: DebugSettingsViewModel = viewModel { DebugSettingsViewModel() },
) {
    val context = LocalContext.current
    // The countdown replaces its previous message rather than queuing after it
    var toast by remember { mutableStateOf<Toast?>(null) }
    TextSettingsItem(
        headline = "Version",
        supporting = versionName,
        onClick = {
            viewModel.onVersionTap()?.also { message ->
                toast?.cancel()
                toast = Toast
                    .makeText(context, message, Toast.LENGTH_SHORT)
                    .also { it.show() }
            }
        },
        modifier = modifier.testTag(VersionSettingsItemTags.version),
    )
}

@Suppress("ConstPropertyName")
internal object VersionSettingsItemTags {
    const val version = "VersionSettingsItemTags_version"
}
