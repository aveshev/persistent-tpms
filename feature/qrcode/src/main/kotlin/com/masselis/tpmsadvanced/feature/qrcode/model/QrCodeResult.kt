package com.masselis.tpmsadvanced.feature.qrcode.model

import android.os.Parcelable
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.Location.Wheel
import kotlinx.parcelize.Parcelize

/**
 * What a scanned QR code holds. The camera closes once one is found, see `QrCodeScan`: the result
 * is shown on the screen the scan started from, by `QrCodeResultDialog`.
 */
public sealed interface QrCodeResult : Parcelable {

    @Parcelize
    @JvmInline
    public value class Sensors internal constructor(internal val sensors: QrCodeSensors) : QrCodeResult

    @Parcelize
    @JvmInline
    public value class DuplicateWheelLocation internal constructor(internal val wheels: List<Wheel>) : QrCodeResult

    @Parcelize
    @JvmInline
    public value class DuplicateId internal constructor(internal val ids: List<Int>) : QrCodeResult

    /** A Wicarlink code holds a single sensor, assigned to the wheel the scan was started from */
    @Parcelize
    @JvmInline
    public value class Wicarlink internal constructor(internal val id: Int) : QrCodeResult
}
