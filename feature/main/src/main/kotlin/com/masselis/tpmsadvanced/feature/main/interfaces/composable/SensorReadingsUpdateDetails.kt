package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.masselis.tpmsadvanced.core.ui.SettingsGroup
import com.masselis.tpmsadvanced.core.ui.SettingsIntro
import com.masselis.tpmsadvanced.core.ui.SettingsSectionHeader
import com.masselis.tpmsadvanced.core.ui.TextSettingsItem

/**
 * The page opened from [TimeSinceUpdateDetails]: how often each sensor brand sends an update, and
 * how to tell a silent sensor from a broken one. Only the schedules measured on real sensors.
 */
@Suppress("MaxLineLength")
@Composable
public fun SensorReadingsUpdateDetails(modifier: Modifier = Modifier): Unit = Column(modifier) {
    SettingsIntro(
        "Sensors from different manufacturers differ in how often they send an update, and in whether they can tell that the wheel is rolling."
    )
    SettingsSectionHeader("Known schedules")
    SettingsGroup {
        TextSettingsItem(
            headline = "WiCarLink",
            supporting = "Every 5.5 minutes, whether the vehicle moves or not, and whenever pressure changes significantly",
        )
        TextSettingsItem(
            headline = "Pecham",
            supporting = "Every minute while the wheel rotates. While stationary, only when the pressure is below ~22 psi, and in either case, whenever pressure changes significantly",
        )
    }
    SettingsSectionHeader("When updates seem to be missing")
    SettingsIntro(
        "If the app does not show the sensor updating as often as you think it should, it could be due to the following reasons:\n\n" +
            "1. The sensor is not sending any updates at all. You can verify that by briefly taking it off the wheel: it should sense 0 pressure, which should force an update, and the app should show an alert. If it does not, either the sensor itself or its battery may be dead.\n\n" +
            "2. The sensor is sending updates, but they are not getting to the phone. Changing the phone's position relative to the wheel may help, and so may replacing the sensor's battery with a fresh one. In the worst case, you may need a different sensor brand.\n\n" +
            "3. The sensor reacts to pressure changes, but fails to detect wheel rotation (if it has rotation detection). Swapping it with a sensor that reports regularly helps tell this apart from #2: if it still doesn't send updates from where the other one does, it's likely faulty and should be replaced. You can use \"Manage sensors\" in the vehicle settings to quickly move them between the wheels."
    )
}

@Preview
@Composable
internal fun SensorReadingsUpdateDetailsPreview() {
    SensorReadingsUpdateDetails()
}
