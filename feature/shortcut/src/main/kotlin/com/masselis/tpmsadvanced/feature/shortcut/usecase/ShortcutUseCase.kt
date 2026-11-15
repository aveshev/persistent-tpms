package com.masselis.tpmsadvanced.feature.shortcut.usecase

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.masselis.tpmsadvanced.core.common.appContext
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.CAR_WITH_SPARE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.DELTA_THREE_WHEELER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MONOWHEEL
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.MOTORCYCLE
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.SINGLE_AXLE_TRAILER
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind.TADPOLE_THREE_WHEELER
import com.masselis.tpmsadvanced.feature.main.usecase.VehicleListUseCase
import com.masselis.tpmsadvanced.feature.shortcut.R
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(DelicateCoroutinesApi::class)
internal class ShortcutUseCase(
    vehicleListUseCase: VehicleListUseCase,
) {

    private val logger = Logger.withTag("ShortcutUseCase")

    init {
        vehicleListUseCase
            .vehicleListFlow
            .onEach { vehicles ->
                // Shortcuts are a convenience: a throw here, at every start, would crash the whole
                // app, monitoring included
                runCatching {
                    ShortcutManagerCompat.setDynamicShortcuts(
                        appContext,
                        vehicles
                            // The builder refuses an empty label
                            .filter { it.name.isNotBlank() }
                            // The system throws above its maximum, 15 on most phones
                            .take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(appContext))
                            .map { vehicle ->
                                ShortcutInfoCompat.Builder(appContext, vehicle.uuid.toString())
                                    .setIcon(
                                        IconCompat.createWithResource(
                                            appContext, when (vehicle.kind) {
                                                CAR, CAR_WITH_SPARE -> R.drawable.car_convertible
                                                SINGLE_AXLE_TRAILER -> R.drawable.truck_trailer
                                                MOTORCYCLE -> R.drawable.motorbike
                                                TADPOLE_THREE_WHEELER -> R.drawable.atv
                                                DELTA_THREE_WHEELER -> R.drawable.atv
                                                // No closer icon yet
                                                MONOWHEEL -> R.drawable.motorbike
                                            }
                                        )
                                    )
                                    .setShortLabel(vehicle.name)
                                    .setIntent(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "tpmsadvanced://vehicle/${vehicle.uuid}".toUri(),
                                        )
                                            // Lite, Pro and TPMS Advanced all handle this scheme
                                            .setPackage(appContext.packageName)
                                    )
                                    .build()
                            }
                    )
                }
                    // Only in the background, where updates are rate-limited. The previous
                    // shortcuts stay.
                    .onSuccess { if (it.not()) logger.i { "Rate-limited, the shortcuts weren't updated" } }
                    .onFailure { logger.w(it) { "Cannot publish the vehicle shortcuts" } }
            }
            .launchIn(GlobalScope)
    }
}
