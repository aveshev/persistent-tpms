package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import com.masselis.tpmsadvanced.core.common.now as nowSeconds
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.Speech
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.State

/**
 * The vehicle, then its tyres, one row each, then the alerts' speech. The vehicle's row opens the
 * switch to another one with [onChangeVehicle], null while there's no other.
 */
context(screen: Screen)
@Suppress("FunctionName", "FunctionNaming")
internal fun VehicleTemplate(
    tab: State.Tabs.Tab.Displayed,
    speech: Speech? = null,
    onSilence: (isCritical: Boolean) -> Unit = {},
    onUnmute: () -> Unit = {},
    readout: State.Tabs.Readout = State.Tabs.Readout(),
    onChangeVehicle: (() -> Unit)? = null,
    now: Double = nowSeconds(),
) = ListTemplate
    .Builder()
    .setSingleList(
        ItemList
            .Builder()
            .apply {
                Row
                    .Builder()
                    .setTitle(tab.vehicle.name)
                    .setImage(tab.vehicle.kind.icon(), Row.IMAGE_TYPE_SMALL)
                    .apply {
                        onChangeVehicle?.also { onClick ->
                            addText("Change vehicle")
                            setBrowsable(true)
                            setOnClickListener(onClick)
                        }
                    }
                    .build()
                    .also(::addItem)
                tab.vehicle
                    .kind
                    .locations
                    .map { location ->
                        TyreRow(
                            location,
                            tab.tyres[location]!!.first,
                            tab.tyres[location]!!.second,
                            readout,
                            now,
                        )
                    }
                    .forEach(::addItem)
                speech?.let { SpeechRow(it, onSilence, onUnmute) }?.also(::addItem)
            }
            .build()
    )
    .build()
