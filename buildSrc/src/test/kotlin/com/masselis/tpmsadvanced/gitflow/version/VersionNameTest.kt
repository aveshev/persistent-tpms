package com.masselis.tpmsadvanced.gitflow.version

import SemanticVersion
import StricSemanticVersion
import junit.framework.TestCase.assertEquals
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class VersionNameTest {

    private val version = StricSemanticVersion("0.9.0")
    private val providers = ProjectBuilder.builder().build().providers
    private val sha = providers.provider { "f491a8a8c0ffee0123456789abcdef0123456789" }

    private fun compute(branch: String) =
        VersionName.compute(version, branch, "release/0.9.0", "main", providers.provider { 3 }, sha)

    @Test
    fun `on main it is the release`() {
        assertEquals("0.9.0", compute("main"))
    }

    @Test
    fun `on the release branch it is a beta numbered by the build count`() {
        assertEquals("0.9.0-beta.3", compute("release/0.9.0"))
    }

    @Test
    fun `elsewhere it is a dev build carrying the short sha`() {
        assertEquals("0.9.0-dev+f491a8a", compute("develop"))
    }

    @Test
    fun `detached HEAD sentinel is a dev build`() {
        assertEquals("0.9.0-dev+f491a8a", compute(""))
    }

    @Test
    fun `every name is a valid semantic version`() {
        listOf("main", "release/0.9.0", "develop").forEach { SemanticVersion(compute(it)) }
    }
}
