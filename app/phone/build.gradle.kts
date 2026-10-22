@file:Suppress("LocalVariableName", "UnstableApiUsage")

import Keys.Companion.keys
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.gms.googleservices.GoogleServicesPlugin
import com.masselis.tpmsadvanced.bitwarden.BitwardenExtension
import com.masselis.tpmsadvanced.gitflow.GitflowExtension
import com.masselis.tpmsadvanced.playstore.PlayStoreExtension
import com.masselis.tpmsadvanced.playstore.PlayStorePlugin
import com.masselis.tpmsadvanced.playstore.task.UpdatePlayStoreScreenshots

plugins {
    `android-app`
    `android-test`
    compose
    alias(libs.plugins.metro)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}

val keysFile = rootProject.layout.projectDirectory.file("secrets/keys.json")
rootProject.configure<BitwardenExtension> {
    files.putAll(
        mapOf(
            // All of theses files are required in this gradle project
            "publisher-service-account.json" to rootProject.layout.projectDirectory.file("secrets/publisher-service-account.json"),
            "google-services.json" to rootProject.layout.projectDirectory.file("app/phone/google-services.json"),
            "app-keystore" to rootProject.layout.projectDirectory.file("secrets/app-keystore"),
            "keys.json" to keysFile,
        )
    )
}

if (file("google-services.json").exists()) {
    // Needs the google-services.json file to work
    apply<GoogleServicesPlugin>()
    apply<CrashlyticsPlugin>()
}

rootProject.file("secrets/publisher-service-account.json").takeIf { it.exists() }?.also { saFile ->
    apply<PlayStorePlugin>()
    configure<PlayStoreExtension> {
        version = rootProject.the<GitflowExtension>().version
        serviceAccountCredentials = saFile
    }
    tasks.register<WriteReleaseNoteTask>("writeReleaseNote") {
        this.noteText = providers.gradleProperty("version.releaseNote")
        this.releaseNotesDir = project.the<PlayStoreExtension>().releaseNotesDir
        this.targetVersion = rootProject.the<GitflowExtension>().version
    }
}

// The release notes of a Firebase App Distribution upload (`--release-notes-file`): the version, the
// commit, and the database version, which tells whether the build can open the data already on a
// phone (a build below it crashes with "Can't downgrade database")
tasks.register<WriteFirebaseReleaseNotesTask>("firebaseReleaseNotes") {
    val gitflow = rootProject.the<GitflowExtension>()
    val migrations = rootProject.file("data/vehicle/src/main/sqldelight/migrations")
    notes = providers
        .exec { commandLine("git", "rev-parse", "--abbrev-ref", "HEAD") }
        .standardOutput
        .asText
        .zip(providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText) { branch, sha ->
            "${branch.trim()} @ ${sha.trim()}"
        }
        .zip(gitflow.versionName.zip(gitflow.versionCode) { name, code -> "$name ($code)" }) { commit, version ->
            migrations
                .listFiles { file -> file.extension == "sqm" }
                .orEmpty()
                .maxOfOrNull { it.nameWithoutExtension.toInt() }
                .let { (it ?: 0) + 1 }
                .let { "$version · $commit · database v$it" }
        }
    releaseNotesFile = layout.buildDirectory.file("firebase/release-notes.txt")
}

// Lite's permissions: see its manifest
if (providers.gradleProperty("app.tier").orNull == "lite") {
    androidComponents.onVariants { it.sources.manifests.addStaticManifestFile("src/lite/AndroidManifest.xml") }
}

android {
    defaultConfig {
        namespace = "com.masselis.tpmsadvanced"
        // Builds as Persistent TPMS by default. `-Papp.brand=tpmsadvanced` builds the same code as
        // TPMS Advanced instead: another app ID with its own data, so a change can be tried on a
        // phone beside the Persistent TPMS that runs there, without touching it. Only the ID, the
        // name and the launcher icon's colours change.
        // `-Papp.tier=lite` builds Lite: a separate app beside Pro, with its own data. data/app reads
        // the same property for the code (AppTier). Pro's name ends in "Pro" and its launcher icon has
        // a "PRO" ribbon (TPMS Advanced, local only, keeps one name for both).
        val lite = providers.gradleProperty("app.tier").orNull == "lite"
        val brand = providers.gradleProperty("app.brand").getOrElse("persistenttpms")
        // Lite's and Pro's IDs: each one reaches the other through AppLinkProvider
        val (liteId, proId) = when (brand) {
            // Each tier has its own suffix, so that neither is taken for the other
            "persistenttpms" -> "com.aveshev.persistenttpms.lite" to "com.aveshev.persistenttpms.pro"
            "tpmsadvanced" -> "com.masselis.tpmsadvanced.lite" to "com.masselis.tpmsadvanced"
            else -> error("Unknown app.brand \"$brand\", expected persistenttpms or tpmsadvanced")
        }
        applicationId = if (lite) liteId else proId
        // Each brand gives its glyph colour
        when (brand) {
            "persistenttpms" -> {
                resValue("string", "app_name", if (lite) "Persistent TPMS" else "Persistent TPMS Pro")
                // Near black rather than black, so the icon's edge stays visible on dark wallpapers
                resValue("color", "ic_launcher_background", "#FF101410")
                resValue("color", "ic_launcher_dot", "#FF8BE08F")
                // Pro's ribbon: yellow "PRO" on black, within its yellow edges
                resValue("color", "ic_launcher_pro_band", if (lite) "#00000000" else "#FF000000")
                resValue("color", "ic_launcher_pro_text", if (lite) "#00000000" else "#FFFFD600")
                "#FF4CAF50"
            }

            "tpmsadvanced" -> {
                // Lite keeps the brand's name, Pro is told apart by its own
                resValue("string", "app_name", if (lite) "TPMS Advanced" else "TPMS Advanced Pro")
                // The stock look, black on green, and no "always on" dot
                resValue("color", "ic_launcher_background", "#FF4CAF50")
                resValue("color", "ic_launcher_dot", "#00000000")
                // Pro's ribbon: black "PRO" on yellow
                resValue("color", "ic_launcher_pro_band", if (lite) "#00000000" else "#FFFFD600")
                resValue("color", "ic_launcher_pro_text", if (lite) "#00000000" else "#FF000000")
                "#FF000000"
            }

            else -> error("Unknown app.brand \"$brand\", expected persistenttpms or tpmsadvanced")
        }.also { glyph ->
            resValue("color", "ic_launcher_glyph", glyph)
            // The edges of the launcher icon's "PRO" ribbon, drawn on Pro only
            resValue("color", "ic_launcher_pro_badge", if (lite) "#00000000" else "#FFFFD600")
            // What the ribbon leaves of the themed icon's tyre, which would show in its empty band otherwise
            resValue(
                "string",
                "ic_launcher_unribboned",
                if (lite) "M0,0 H432 V432 H0 Z" else "M0,0 H432 V432 H0 Z M-90.9,98.6 L333.4,522.9 L378.6,477.6 L-45.6,53.4 Z"
            )
        }
        // Lite and Pro of a brand reach each other through AppLinkProvider: both declare its
        // signature permission, under the same name, and each needs to see the other one from
        // Android 11. The code reads both from the manifest, see OtherApp.
        manifestPlaceholders["appLinkPermission"] = "${liteId.removeSuffix(".lite")}.permission.APP_LINK"
        manifestPlaceholders["otherApplicationId"] = if (lite) proId else liteId
    }
    keys(keysFile.asFile)?.also { keys ->
        signingConfigs.create("release") {
            keyAlias = keys.appKeyAlias
            keyPassword = keys.appKeyStorePwd
            storeFile = file("../../secrets/app-keystore")
            storePassword = keys.appKeyStorePwd
        }
    }
    buildTypes.getByName("release") {
        signingConfig = signingConfigs.findByName("release") ?: signingConfigs["debug"]
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        )
    }
    buildFeatures.buildConfig = true
    buildFeatures.resValues = true
    val pixel2api34 = testOptions.managedDevices.localDevices.getByName("pixel2api34")
    val copyScreenshot by tasks.registering(Copy::class) {
        dependsOn("${pixel2api34.name}DebugAndroidTest")
        group = "publishing"
        description =
            "Copy and rename the screenshots from the phone in order to be uploaded to the play store listing"
        from(layout.buildDirectory.dir("outputs/managed_device_android_test_additional_output/debug/${pixel2api34.name}"))
        into("$projectDir/src/main/play/listings/en-US/graphics/phone-screenshots")
        eachFile {
            name = when {
                name.startsWith("light_main") -> "1.png"
                name.startsWith("light_settings") -> "2.png"
                name.startsWith("light_manage_sensors") -> "3.png"
                name.startsWith("dark_main") -> "4.png"
                name.startsWith("dark_settings") -> "5.png"
                name.startsWith("dark_manage_sensors") -> "6.png"
                else -> throw GradleException("File with name $name not recognized")
            }
        }
    }
    tasks.withType<UpdatePlayStoreScreenshots> {
        dependsOn(copyScreenshot)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:debug-ui"))

    implementation(project(":data:app"))
    implementation(project(":data:unit"))
    implementation(project(":data:vehicle"))

    implementation(project(":feature:background"))
    implementation(project(":feature:main"))
    implementation(project(":feature:unlocated"))
    implementation(project(":feature:qrcode"))
    implementation(project(":feature:shortcut"))
    implementation(project(":feature:unit"))
    implementation(project(":feature:android-auto"))

    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(project(":core:test"))
}
