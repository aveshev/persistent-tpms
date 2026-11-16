import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `a plain version has no prerelease or metadata`() {
        SemanticVersion("1.20.3").apply {
            assertEquals(1, major)
            assertEquals(20, minor)
            assertEquals(3, patch)
            assertNull(prerelease)
            assertNull(buildmetadata)
        }
    }

    @Test
    fun `a prerelease and metadata are read apart`() {
        SemanticVersion("1.2.3-beta.1+sha.abc").apply {
            assertEquals("beta.1", prerelease)
            assertEquals("sha.abc", buildmetadata)
        }
    }

    @Test
    fun `written back as it was read`() {
        listOf("0.0.0", "1.2.3", "1.2.3-rc1", "1.2.3+sha", "1.2.3-beta.1+sha.abc", "1.2.3-0.3.7", "1.2.3-x-y-z").forEach {
            assertEquals(it, SemanticVersion(it).toString())
        }
    }

    @Test
    fun `anything else is refused`() {
        listOf(
            "",
            "1",
            "1.2",
            "1.2.3.4",
            "a.b.c",
            " 1.2.3",
            "1.2.3 ",
            "v1.2.3",
            // No leading zeros
            "01.2.3",
            "1.02.3",
            "1.2.03",
            "1.2.3-01",
            // Nothing after its separator
            "1.2.3-",
            "1.2.3+",
            "1.2.3-a..b",
        ).forEach { version ->
            assertThrows(version, GradleException::class.java) { SemanticVersion(version) }
        }
    }

    @Test
    fun `from its parts`() {
        assertEquals("1.2.3-rc1+sha", SemanticVersion(1, 2, 3, "rc1", "sha").toString())
    }

    @Test
    fun `a strict version has neither`() {
        SemanticVersion(StricSemanticVersion("1.2.3")).apply {
            assertEquals("1.2.3", toString())
            assertNull(prerelease)
            assertNull(buildmetadata)
        }
    }

    @Test
    fun `compared by major, then minor, then patch`() {
        val version = SemanticVersion("2.3.4")
        assertTrue(version > StricSemanticVersion("1.9.9"))
        assertTrue(version > StricSemanticVersion("2.2.9"))
        assertTrue(version > StricSemanticVersion("2.3.3"))
        assertTrue(version < StricSemanticVersion("3.0.0"))
        assertTrue(version < StricSemanticVersion("2.4.0"))
        assertTrue(version < StricSemanticVersion("2.3.5"))
        assertEquals(0, version.compareTo(StricSemanticVersion("2.3.4")))
    }

    @Test
    fun `numbers compare as numbers`() {
        assertTrue(SemanticVersion("1.10.0") > StricSemanticVersion("1.9.0"))
    }

    @Test
    fun `a prerelease or metadata can't be compared`() {
        listOf("1.2.3-rc1", "1.2.3+sha").forEach { version ->
            assertThrows(version, IllegalStateException::class.java) {
                SemanticVersion(version).compareTo(StricSemanticVersion("1.2.3"))
            }
        }
    }
}
