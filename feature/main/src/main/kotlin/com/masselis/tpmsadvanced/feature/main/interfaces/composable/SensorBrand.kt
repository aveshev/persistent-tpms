package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SY_4300
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.TC_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.YC_0001
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant.V1
import com.masselis.tpmsadvanced.data.vehicle.model.SensorVariant.V2
import com.masselis.tpmsadvanced.data.vehicle.model.Tyre
import com.masselis.tpmsadvanced.data.vehicle.model.variant

/** The brand as the user knows it, with its type when the brand makes several */
public val SensorBrand.label: String
    get() = when (this) {
        PECHAM -> "Pecham"
        // Several kinds of sensors, named after what they advertise
        BEKUBEE_KY -> "Bekubee KY"
        BEKUBEE_TPMS -> "Bekubee TPMS"
        TC_TPMS -> "TC.TPMS"
        WICARLINK -> "Wicarlink"
        SYSGRATION -> "Sysgration"
        // Named after what they advertise, the box names no brand
        SY_4300 -> "SY-4300"
        // Decoded from the vendor app's code, no YC-0001 was ever captured, see RawSy4300
        YC_0001 -> "YC-0001 (experimental)"
    }

/**
 * With which of its kinds sent [variant], named like on the sensor models page, or the brand alone
 * when it's unknown: a sensor not heard yet
 */
public fun SensorBrand.label(variant: SensorVariant?): String = when (variant) {
    V1 -> "$label v1"
    V2 -> "$label v2"
    null -> label
}

/** The brand of the sensor which sent it, with its kind, see [SensorBrand.label] */
public val Tyre.label: String
    get() = brand.label(variant)
