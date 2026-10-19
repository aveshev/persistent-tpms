package com.masselis.tpmsadvanced.interfaces.viewmodel

import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.data.app.interfaces.OtherApp
import com.masselis.tpmsadvanced.data.vehicle.interfaces.DatabaseExport
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Failed
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Idle
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.NothingToImport
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Offer
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.Unreachable
import com.masselis.tpmsadvanced.interfaces.viewmodel.ImportViewModel.State.UpdateNeeded
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.InputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var otherAppLabel: String? = null
    private var otherAppStatus: OtherApp.Status? = null
    private var ourLatestReading: Double? = null
    private var ourData = false
    private var snapshot: InputStream? = null
    private var stagedVersion = 0L
    private var committed = false
    private var discarded = false
    private lateinit var offeredMarker: File

    private fun test() = ImportViewModel(
        mockk<OtherApp> {
            every { packageName } returns "com.aveshev.persistenttpms.lite"
            every { label() } answers { otherAppLabel }
            coEvery { status() } answers { otherAppStatus }
            coEvery { openSnapshot() } answers { snapshot }
        },
        mockk<DatabaseExport> {
            coEvery { latestReading() } answers { ourLatestReading }
            coEvery { hasData() } answers { ourData }
        },
        offeredMarker,
        stage = { stagedVersion },
        commitAndRestart = { committed = true },
        discardStaged = { discarded = true },
    )

    @Before
    fun setup() {
        otherAppLabel = "Persistent TPMS Lite"
        otherAppStatus = status(latestReading = NEWER)
        ourLatestReading = null
        ourData = false
        snapshot = "snapshot".byteInputStream()
        stagedVersion = DatabaseExport.version
        committed = false
        discarded = false
        offeredMarker = File(temporaryFolder.root, "import-offered")
    }

    private fun status(
        latestReading: Double?,
        hasData: Boolean = true,
        databaseVersion: Long = DatabaseExport.version,
    ) = OtherApp.Status(
        persistentScanning = false,
        databaseVersion = databaseVersion,
        latestReading = latestReading,
        hasData = hasData,
    )

    @Test
    fun `an app without data is offered the other app's data, with its latest reading`() {
        val viewModel = test()
        viewModel.offerOnce()
        val offer = assertIs<Offer>(viewModel.state.value)
        assertEquals("Persistent TPMS Lite", offer.otherApp)
        assertEquals(NEWER, offer.theirLatestReading)
        assertFalse(offer.replacing)
    }

    @Test
    fun `an app with data is offered newer data, with both dates`() {
        ourData = true
        ourLatestReading = OLDER
        val viewModel = test()
        viewModel.offerOnce()
        val offer = assertIs<Offer>(viewModel.state.value)
        assertEquals(NEWER, offer.theirLatestReading)
        assertEquals(OLDER, offer.ourLatestReading)
        assertTrue(offer.replacing)
    }

    @Test
    fun `an app with data isn't offered older data when it opens`() {
        ourData = true
        ourLatestReading = NEWER
        otherAppStatus = status(latestReading = OLDER)
        val viewModel = test()
        viewModel.offerOnce()
        assertEquals(Idle, viewModel.state.value)
    }

    @Test
    fun `the offer isn't made again after "don't ask again"`() {
        test().also { it.offerOnce() }.neverAsk()
        assertTrue(offeredMarker.exists())
        val viewModel = test()
        viewModel.offerOnce()
        assertEquals(Idle, viewModel.state.value)
    }

    @Test
    fun `the offer is made again after "not now"`() {
        test().also { it.offerOnce() }.dismiss()
        assertFalse(offeredMarker.exists())
        val viewModel = test()
        viewModel.offerOnce()
        assertIs<Offer>(viewModel.state.value)
    }

    @Test
    fun `the update message is only shown once`() {
        otherAppStatus = status(latestReading = NEWER, databaseVersion = DatabaseExport.version + 1)
        test().also { it.offerOnce() }.dismiss()
        assertTrue(offeredMarker.exists())
    }

    @Test
    fun `an offer left unanswered is made again on the next opening`() {
        test().offerOnce()
        assertFalse(offeredMarker.exists())
        val viewModel = test()
        viewModel.offerOnce()
        assertIs<Offer>(viewModel.state.value)
    }

    @Test
    fun `nothing to offer isn't checked again`() {
        ourData = true
        ourLatestReading = NEWER
        otherAppStatus = status(latestReading = OLDER)
        test().offerOnce()
        assertTrue(offeredMarker.exists())
    }

    @Test
    fun `nothing is offered, and asked again later, while the other app isn't installed or can't tell`() {
        otherAppLabel = null
        test().offerOnce()
        otherAppLabel = "Persistent TPMS Lite"
        otherAppStatus = null
        val viewModel = test()
        viewModel.offerOnce()
        assertEquals(Idle, viewModel.state.value)
        assertFalse(offeredMarker.exists())
    }

    @Test
    fun `nothing is offered while the other app has no data`() {
        otherAppStatus = status(latestReading = null, hasData = false)
        val viewModel = test()
        viewModel.offerOnce()
        assertEquals(Idle, viewModel.state.value)
    }

    @Test
    fun `the other app's newer database asks to update this one`() {
        otherAppStatus = status(latestReading = NEWER, databaseVersion = DatabaseExport.version + 1)
        val viewModel = test()
        viewModel.offerOnce()
        assertEquals(UpdateNeeded("Persistent TPMS Lite"), viewModel.state.value)
    }

    @Test
    fun `the settings offer older data too`() {
        ourData = true
        ourLatestReading = NEWER
        otherAppStatus = status(latestReading = OLDER)
        offeredMarker.createNewFile()
        val viewModel = test()
        viewModel.requestImport()
        val offer = assertIs<Offer>(viewModel.state.value)
        assertTrue(offer.fromSettings)
        assertTrue(offer.replacing)
    }

    @Test
    fun `the settings tell why there's nothing to import`() {
        val viewModel = test()
        otherAppStatus = null
        viewModel.requestImport()
        assertEquals(Unreachable("Persistent TPMS Lite"), viewModel.state.value)
        otherAppStatus = status(latestReading = null, hasData = false)
        viewModel.requestImport()
        assertEquals(NothingToImport("Persistent TPMS Lite"), viewModel.state.value)
    }

    @Test
    fun `confirming stages the snapshot and restarts with it`() {
        val viewModel = test()
        viewModel.offerOnce()
        viewModel.confirm()
        assertTrue(committed)
        assertFalse(discarded)
    }

    @Test
    fun `a snapshot of a newer database is discarded`() {
        stagedVersion = DatabaseExport.version + 1
        val viewModel = test()
        viewModel.offerOnce()
        viewModel.confirm()
        assertEquals(UpdateNeeded("Persistent TPMS Lite"), viewModel.state.value)
        assertFalse(committed)
        assertTrue(discarded)
    }

    @Test
    fun `an unreadable snapshot fails the import`() {
        snapshot = null
        val viewModel = test()
        viewModel.offerOnce()
        viewModel.confirm()
        assertEquals(Failed("Persistent TPMS Lite"), viewModel.state.value)
        assertFalse(committed)
    }

    private companion object {
        const val OLDER = 1_791_000_000.0
        const val NEWER = 1_792_400_000.0
    }
}
