package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import androidx.lifecycle.ViewModel
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal class DetectedActivitiesViewModel(
    appPreferences: AppPreferences,
    private val activityRecognitionUseCase: ActivityRecognitionUseCase,
) : ViewModel() {

    val showDetectedActivities = appPreferences.showDetectedActivities

    fun requiredPermissions() = activityRecognitionUseCase.requiredPermissions()

    /** null while nothing is to be shown, empty until the first sample arrives */
    @OptIn(ExperimentalCoroutinesApi::class)
    val topActivities: Flow<List<Activity>?> = appPreferences.debugOptionsInUse
        // Only shown while the switch of the debug options is on
        .combine(showDetectedActivities, Boolean::and)
        .distinctUntilChanged()
        .flatMapLatest { shown ->
            if (shown && activityRecognitionUseCase.isPermitted()) {
                activityRecognitionUseCase
                    .probableActivities
                    .map { it.take(TOP_COUNT) }
                    .onStart { emit(emptyList()) }
            } else flowOf(null)
        }

    private companion object {
        const val TOP_COUNT = 3
    }
}
