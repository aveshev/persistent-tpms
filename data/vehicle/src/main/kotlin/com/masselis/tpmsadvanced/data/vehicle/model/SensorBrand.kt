package com.masselis.tpmsadvanced.data.vehicle.model

import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.PERCENT
import com.masselis.tpmsadvanced.data.vehicle.model.SensorBrand.BatteryUnit.VOLT

/**
 * The sensors the app decodes, see the `Raw*` decoders. [code] is what the database stores: it
 * never changes once released, a new brand takes the next one.
 */
@Suppress("MagicNumber")
public enum class SensorBrand(internal val code: Long, public val batteryUnit: BatteryUnit) {
    PECHAM(1, VOLT),
    BEKUBEE_KY(2, VOLT),
    WICARLINK(3, VOLT),
    BEKUBEE_TPMS(4, VOLT),
    SYSGRATION(5, PERCENT),
    TC_TPMS(6, VOLT);

    public enum class BatteryUnit {
        VOLT,
        PERCENT,
    }

    public companion object {
        internal fun of(code: Long): SensorBrand = entries.first { it.code == code }
    }
}
