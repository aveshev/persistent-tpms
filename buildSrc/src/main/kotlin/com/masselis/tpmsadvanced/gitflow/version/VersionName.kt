package com.masselis.tpmsadvanced.gitflow.version

import StricSemanticVersion
import org.gradle.api.provider.Provider

/**
 * The `versionName` shown to people, as a SemVer string that matches the `versionCode` computed by
 * `VersionCode` on the same branch:
 * - `main`: the release itself, `0.9.0`
 * - the release branch: a tester build, `0.9.0-beta.<build count>`, the same count as the
 *   `versionCode`'s last 3 digits
 * - anywhere else: `0.9.0-dev+<short sha>`, since every such build shares one `versionCode`
 */
internal object VersionName {

    fun compute(
        version: StricSemanticVersion,
        currentBranch: String,
        releaseBranch: String,
        mainBranch: String,
        releaseBuildCount: Provider<Int>,
        headSha: Provider<String>,
    ): String = when (currentBranch) {
        mainBranch -> "$version"
        releaseBranch -> "$version-beta.${releaseBuildCount.get()}"
        else -> "$version-dev+${headSha.get().take(SHORT_SHA_LENGTH)}"
    }

    private const val SHORT_SHA_LENGTH = 7
}
