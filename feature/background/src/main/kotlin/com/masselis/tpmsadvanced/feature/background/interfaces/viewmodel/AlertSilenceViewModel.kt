package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.SilenceAlertsUseCase.State
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The main screen's button silencing the alerts' speech, see docs/alerts.md */
internal class AlertSilenceViewModel(
    private val silenceAlertsUseCase: SilenceAlertsUseCase,
) : ViewModel() {

    val stateFlow: StateFlow<State> = silenceAlertsUseCase
        .button
        .stateIn(viewModelScope, WhileSubscribed(), State.Idle)

    fun silence(isCritical: Boolean) = silenceAlertsUseCase.silence(isCritical)

    fun unmute() = silenceAlertsUseCase.unmute()
}
