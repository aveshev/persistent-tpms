import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes the release notes of a Firebase App Distribution upload, passed to
 * `firebase appdistribution:distribute` with `--release-notes-file`.
 */
public abstract class WriteFirebaseReleaseNotesTask : DefaultTask() {

    @get:Input
    public abstract val notes: Property<String>

    @get:OutputFile
    public abstract val releaseNotesFile: RegularFileProperty

    init {
        group = "distribution"
        description = "Writes the release notes of a Firebase App Distribution upload"
    }

    @TaskAction
    internal fun process() {
        releaseNotesFile.get().asFile.writeText(notes.get())
    }
}
