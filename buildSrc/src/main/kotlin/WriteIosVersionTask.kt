import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes the iOS app's version as an xcconfig, `ios/Version.xcconfig`, which `ios/Version.base.xcconfig`
 * includes, so the iOS app's version is the Android app's, from the gitflow plugin:
 * - `MARKETING_VERSION` (`CFBundleShortVersionString`): the plain version, `0.9.0`, since App Store
 *   Connect only takes up to three integers there
 * - `CURRENT_PROJECT_VERSION` (`CFBundleVersion`): the Android `versionCode`, which grows with each
 *   beta and release like App Store Connect requires of each upload
 * - `APP_VERSION_NAME`: the Android `versionName`, `0.9.0-dev+<sha>`, shown in the app settings
 * - `APP_VERSION_COMMIT`: the commit it was written for, so an Xcode build can tell it's out of date
 */
public abstract class WriteIosVersionTask : DefaultTask() {

    @get:Input
    public abstract val version: Property<String>

    @get:Input
    public abstract val versionCode: Property<Int>

    @get:Input
    public abstract val versionName: Property<String>

    @get:Input
    public abstract val commit: Property<String>

    @get:OutputFile
    public abstract val xcconfigFile: RegularFileProperty

    init {
        group = "ios"
        description = "Writes the iOS app's version, the Android app's, to ios/Version.xcconfig"
    }

    @TaskAction
    internal fun process() {
        xcconfigFile.get()
            .asFile
            .writeText(
                """
                |// Written by `./gradlew writeIosVersion`, don't edit or commit it: see Version.base.xcconfig
                |MARKETING_VERSION = ${version.get()}
                |CURRENT_PROJECT_VERSION = ${versionCode.get()}
                |APP_VERSION_NAME = ${versionName.get()}
                |APP_VERSION_COMMIT = ${commit.get().trim()}
                |
                """.trimMargin()
            )
    }
}
