package com.masselis.tpmsadvanced.feature.main.interfaces.composable

/** Where a vehicle with a spare shows the spare's readout, see [Vehicle] */
public enum class SpareReadout {
    /** Under the spare, past the bottom of the image */
    UNDER,

    /**
     * Beside the rear right readout, as high, a line leading to it from the spare, for a window
     * where height is short. The vehicle is sized without it, the room for it kept on its right:
     * the vehicle and it are centered together.
     */
    BESIDE,

    /** As [BESIDE], but the vehicle is centered alone, the room for the spare's readout kept on both sides */
    BESIDE_CENTERED,
}
