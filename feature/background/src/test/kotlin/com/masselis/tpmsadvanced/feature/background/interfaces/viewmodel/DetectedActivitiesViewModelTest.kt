package com.masselis.tpmsadvanced.feature.background.interfaces.viewmodel

import app.cash.turbine.test
import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.IN_VEHICLE
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.ON_FOOT
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.STILL
import com.masselis.tpmsadvanced.feature.background.usecase.ActivityRecognitionUseCase.Activity.Type.UNKNOWN
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DetectedActivitiesViewModelTest {

    private lateinit var debugOptions: MutableStateFlow<Boolean>
    private lateinit var showDetectedActivities: MutableStateFlow<Boolean>
    private lateinit var permitted: MutableStateFlow<Boolean>
    private lateinit var probableActivities: MutableSharedFlow<List<Activity>>

    private fun test() = DetectedActivitiesViewModel(
        mockk<AppPreferences> {
            every { debugOptionsInUse } returns this@DetectedActivitiesViewModelTest.debugOptions
            every { showDetectedActivities } returns this@DetectedActivitiesViewModelTest.showDetectedActivities
        },
        mockk<ActivityRecognitionUseCase> {
            every { isPermitted() } answers { permitted.value }
            every { probableActivities } returns this@DetectedActivitiesViewModelTest.probableActivities
        },
    )

    @Before
    fun setup() {
        debugOptions = MutableStateFlow(true)
        showDetectedActivities = MutableStateFlow(true)
        permitted = MutableStateFlow(true)
        probableActivities = MutableSharedFlow()
    }

    @Test
    fun `the three most confident activities are shown once the first sample arrives`() = runTest {
        test().topActivities.test {
            assertEquals(emptyList(), awaitItem())
            probableActivities.emit(
                listOf(
                    Activity(IN_VEHICLE, 40),
                    Activity(ON_FOOT, 15),
                    Activity(UNKNOWN, 10),
                    Activity(STILL, 5),
                )
            )
            assertEquals(
                listOf(Activity(IN_VEHICLE, 40), Activity(ON_FOOT, 15), Activity(UNKNOWN, 10)),
                awaitItem(),
            )
        }
    }

    @Test
    fun `nothing is shown while the setting is off`() = runTest {
        showDetectedActivities.value = false
        test().topActivities.test {
            assertNull(awaitItem())
            showDetectedActivities.value = true
            assertEquals(emptyList(), awaitItem())
            showDetectedActivities.value = false
            assertNull(awaitItem())
        }
    }

    @Test
    fun `nothing is shown while the debug options are off`() = runTest {
        debugOptions.value = false
        test().topActivities.test {
            assertNull(awaitItem())
            debugOptions.value = true
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `nothing is shown without the permission`() = runTest {
        permitted.value = false
        test().topActivities.test { assertNull(awaitItem()) }
    }
}
