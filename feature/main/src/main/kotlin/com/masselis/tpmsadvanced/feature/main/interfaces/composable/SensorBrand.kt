package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_KY
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BEKUBEE_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.PECHAM
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.SYSGRATION
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.TC_TPMS
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.WICARLINK

/** The brand as the user knows it, with its type when the brand makes several */
internal val SensorBrand.label: String
    get() = when (this) {
        PECHAM -> "Pecham"
        // Several kinds of sensors, named after what they advertise
        BEKUBEE_KY -> "Bekubee KY"
        BEKUBEE_TPMS -> "Bekubee TPMS"
        TC_TPMS -> "TC.TPMS"
        WICARLINK -> "Wicarlink"
        SYSGRATION -> "Sysgration"
    }
