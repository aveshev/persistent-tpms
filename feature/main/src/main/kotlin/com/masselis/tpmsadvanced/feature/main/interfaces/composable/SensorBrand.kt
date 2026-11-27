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
