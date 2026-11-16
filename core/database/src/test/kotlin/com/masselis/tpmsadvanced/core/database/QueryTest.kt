package com.masselis.tpmsadvanced.core.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.masselis.tpmsadvanced.core.database.QueryList.Companion.asList
import com.masselis.tpmsadvanced.core.database.QueryOne.Companion.asOne
import com.masselis.tpmsadvanced.core.database.QueryOneOrNull.Companion.asOneOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** On a real SQLite in memory, a query is told of a change the way the generated code tells it */
@OptIn(ExperimentalCoroutinesApi::class)
internal class QueryTest {

    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            .also { it.execute(null, "CREATE TABLE row (value INTEGER NOT NULL)", 0) }
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun insert(vararg values: Long) = values.forEach {
        driver.execute(null, "INSERT INTO row VALUES ($it)", 0)
        driver.notifyListeners("row")
    }

    private fun delete() {
        driver.execute(null, "DELETE FROM row", 0)
        driver.notifyListeners("row")
    }

    private fun update(value: Long) {
        driver.execute(null, "UPDATE row SET value = $value", 0)
        driver.notifyListeners("row")
    }

    private fun rows(): Query<Long> =
        Query(1, arrayOf("row"), driver, "SELECT value FROM row ORDER BY value") { it.getLong(0)!! }

    /** Skips the rows which aren't readable, as readings decoded when read back do */
    private fun Long.takeIfReadable() = takeIf { it != UNREADABLE }

    private val TestScope.dispatcher get() = UnconfinedTestDispatcher(testScheduler)

    @Test
    fun `a list gives the rows`() {
        insert(2, 1)
        assertEquals(listOf(1L, 2L), rows().asList().execute())
    }

    @Test
    fun `a list leaves the rows out which can't be read`() {
        insert(1, UNREADABLE, 2)
        assertEquals(listOf(1L, 2L), rows().asList { it.takeIfReadable() }.execute())
    }

    @Test
    fun `a list's flow starts with the rows, then follows them`() = runTest {
        insert(1)
        rows().asList().asFlow(dispatcher).test {
            assertEquals(listOf(1L), awaitItem())
            insert(2)
            assertEquals(listOf(1L, 2L), awaitItem())
            delete()
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `a list's flow leaves the rows out which can't be read`() = runTest {
        insert(1, UNREADABLE)
        rows().asList { it.takeIfReadable() }.asFlow(dispatcher).test {
            assertEquals(listOf(1L), awaitItem())
            insert(2)
            assertEquals(listOf(1L, 2L), awaitItem())
        }
    }

    @Test
    fun `a list's chill flow only tells the changes`() = runTest {
        insert(1)
        rows().asList().asChillFlow(dispatcher).test {
            expectNoEvents()
            insert(2)
            assertEquals(listOf(1L, 2L), awaitItem())
        }
    }

    @Test
    fun `a list's state starts with the rows, then follows them`() = runTest {
        insert(1)
        val state = rows().asList { it.takeIfReadable() }.asStateFlow(backgroundScope + dispatcher, Eagerly)
        assertEquals(listOf(1L), state.value)
        insert(UNREADABLE, 2)
        runCurrent()
        assertEquals(listOf(1L, 2L), state.value)
    }

    @Test
    fun `a single row is given`() {
        insert(1)
        assertEquals(1L, rows().asOne().execute())
    }

    @Test
    fun `a single row which isn't there fails`() {
        assertFailsWith<NullPointerException> { rows().asOne().execute() }
    }

    @Test
    fun `a single row which is several fails`() {
        insert(1, 2)
        assertFailsWith<IllegalStateException> { rows().asOne().execute() }
    }

    @Test
    fun `a single row's flow starts with the row, then follows it`() = runTest {
        insert(1)
        rows().asOne().asFlow(dispatcher).test {
            assertEquals(1L, awaitItem())
            update(2)
            assertEquals(2L, awaitItem())
        }
    }

    @Test
    fun `a single row's flow fails once the row is gone`() = runTest {
        insert(1)
        rows().asOne().asFlow(dispatcher).test {
            assertEquals(1L, awaitItem())
            delete()
            assertIs<NullPointerException>(awaitError())
        }
    }

    @Test
    fun `a single row's chill flow only tells the changes`() = runTest {
        insert(1)
        rows().asOne().asChillFlow(dispatcher).test {
            expectNoEvents()
            update(2)
            assertEquals(2L, awaitItem())
        }
    }

    @Test
    fun `a single row's state starts with the row, then follows it`() = runTest {
        insert(1)
        val state = rows().asOne().asStateFlow(backgroundScope + dispatcher, Eagerly)
        assertEquals(1L, state.value)
        update(2)
        runCurrent()
        assertEquals(2L, state.value)
    }

    @Test
    fun `an optional row is null when it isn't there`() {
        assertNull(rows().asOneOrNull().execute())
        insert(1)
        assertEquals(1L, rows().asOneOrNull().execute())
    }

    @Test
    fun `an optional row is null when it can't be read`() {
        insert(UNREADABLE)
        assertNull(rows().asOneOrNull { it.takeIfReadable() }.execute())
    }

    @Test
    fun `an optional row which is several fails`() {
        insert(1, 2)
        assertFailsWith<IllegalStateException> { rows().asOneOrNull().execute() }
    }

    @Test
    fun `an optional row's flow follows it coming and going`() = runTest {
        rows().asOneOrNull().asFlow(dispatcher).test {
            assertNull(awaitItem())
            insert(1)
            assertEquals(1L, awaitItem())
            delete()
            assertNull(awaitItem())
        }
    }

    @Test
    fun `an optional row's flow is null while it can't be read`() = runTest {
        rows().asOneOrNull { it.takeIfReadable() }.asFlow(dispatcher).test {
            assertNull(awaitItem())
            insert(UNREADABLE)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `an optional row's chill flow only tells the changes`() = runTest {
        rows().asOneOrNull().asChillFlow(dispatcher).test {
            expectNoEvents()
            insert(1)
            assertEquals(1L, awaitItem())
        }
    }

    @Test
    fun `an optional row's state starts with the row, then follows it`() = runTest {
        val state = rows().asOneOrNull().asStateFlow(backgroundScope + dispatcher, Eagerly)
        assertNull(state.value)
        insert(1)
        runCurrent()
        assertEquals(1L, state.value)
        delete()
        runCurrent()
        assertNull(state.value)
    }

    private companion object {
        const val UNREADABLE = -1L
    }
}
