package com.masselis.tpmsadvanced.data.app.interfaces

import com.masselis.tpmsadvanced.data.app.BuildConfig

/**
 * Which version of the app this build is, chosen at build time with `-Papp.tier=lite|pro`. Lite
 * only activates persistent scanning on a connected Bluetooth device or a nearby beacon, and only
 * suspends it while the phone is idle. Pro is the full app.
 */
public enum class AppTier {
    LITE, PRO;

    public companion object {
        public val current: AppTier = if (BuildConfig.APP_TIER == "lite") LITE else PRO
    }
}
