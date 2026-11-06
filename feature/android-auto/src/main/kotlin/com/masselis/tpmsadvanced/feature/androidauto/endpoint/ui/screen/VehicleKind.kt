package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import com.masselis.tpmsadvanced.feature.androidauto.R

context(screen: Screen)
internal fun Vehicle.Kind.icon(): CarIcon = CarIcon
    .Builder(
        IconCompat.createWithResource(
            screen.carContext,
            when (this) {
                Vehicle.Kind.CAR, Vehicle.Kind.CAR_WITH_SPARE -> R.drawable.car_hatchback
                Vehicle.Kind.SINGLE_AXLE_TRAILER -> R.drawable.truck_trailer
                Vehicle.Kind.MOTORCYCLE -> R.drawable.motorbike
                Vehicle.Kind.TADPOLE_THREE_WHEELER -> R.drawable.bike_scooter_24px
                Vehicle.Kind.DELTA_THREE_WHEELER -> R.drawable.bike_scooter_24px
                // No closer icon yet
                Vehicle.Kind.MONOWHEEL -> R.drawable.bike_scooter_24px
            }
        )
    )
    .setTint(CarColor.createCustom(0xFF0E6E14.toInt(), 0xFF81DB74.toInt()))
    .build()
