package com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.masselis.tpmsadvanced.feature.androidauto.ioc.Bindings.Companion.TabScreenViewModel
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.ui.viewmodel.TabScreenViewModel.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The current vehicle's tyres. Its first row names the vehicle and, when there are others, opens
 * [VehiclePickerScreen] to switch, so it scrolls away with the list rather than taking a tab bar.
 */
@Suppress("OPT_IN_USAGE")
internal class TabsScreen(carContext: CarContext) : Screen(carContext) {

    private val viewModel = TabScreenViewModel()

    init {
        viewModel
            .stateFlow
            .debounce(300.milliseconds)
            // Once a minute too, so the time since each tyre's update goes on
            .map { }
            .let { states -> merge(states, flow { while (true) delay(1.minutes).also { emit(Unit) } }) }
            .onEach { invalidate() }
            .launchIn(lifecycleScope)
    }

    @Suppress("MaxLineLength")
    override fun onGetTemplate(): Template = when (val state = viewModel.stateFlow.value) {
        State.Loading -> MessageTemplate
            .Builder("Loading...")
            .build()

        is State.Tabs -> VehicleTemplate(
            state.displayed,
            state.speech,
            viewModel::silence,
            viewModel::unmute,
            state.readout,
            onChangeVehicle = state.list
                .takeIf { it.size > 1 }
                ?.let { list ->
                    {
                        screenManager.push(
                            VehiclePickerScreen(carContext, list.map { it.vehicle }, state.displayed.vehicle.uuid) { uuid ->
                                viewModel.currentVehicle(uuid)
                            }
                        )
                    }
                },
        )
    }
}
