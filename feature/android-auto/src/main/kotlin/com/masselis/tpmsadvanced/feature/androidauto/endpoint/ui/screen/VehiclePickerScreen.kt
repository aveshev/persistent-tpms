package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.constraints.ConstraintManager.CONTENT_LIMIT_TYPE_LIST
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.masselis.tpmsadvanced.core.common.R as CommonR
import com.masselis.tpmsadvanced.core.common.localized
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle
import java.util.UUID

/**
 * Picks the vehicle to show among [vehicles], [current] selected, then goes back. As many as the
 * host lists, the current one first so it's always there, the others by name.
 */
internal class VehiclePickerScreen(
    carContext: CarContext,
    private val vehicles: List<Vehicle>,
    private val current: UUID,
    private val onPick: (UUID) -> Unit,
) : Screen(carContext) {

    @OptIn(ExperimentalCarApi::class)
    override fun onGetTemplate(): Template = vehicles
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { if (it.uuid == current) "" else it.name })
        .take(carContext.getCarService(ConstraintManager::class.java).getContentLimit(CONTENT_LIMIT_TYPE_LIST))
        .let { shown ->
            ListTemplate
                .Builder()
                .setHeader(
                    Header
                        .Builder()
                        .setStartHeaderAction(Action.BACK)
                        .setTitle(localized(CommonR.string.vehicles))
                        .build()
                )
                .setSingleList(
                    ItemList
                        .Builder()
                        .apply {
                            shown.forEach { vehicle ->
                                Row
                                    .Builder()
                                    .setTitle(vehicle.name)
                                    .setImage(vehicle.kind.icon(), Row.IMAGE_TYPE_SMALL)
                                    .build()
                                    .also(::addItem)
                            }
                        }
                        .setSelectedIndex(0)
                        .setOnSelectedListener { index ->
                            onPick(shown[index].uuid)
                            screenManager.pop()
                        }
                        .build()
                )
                .build()
        }
}
