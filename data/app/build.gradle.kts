plugins {
    `android-lib`
    alias(libs.plugins.metro)
}

android {
    namespace = "com.masselis.tpmsadvanced.data.app"
    buildFeatures   {
        buildConfig = true
    }
    defaultConfig {
        // Read here rather than handed down by the app, so that any module reads it through AppTier
        // without a dependency graph. app/phone reads the same property for the app ID and name.
        when (val tier = providers.gradleProperty("app.tier").getOrElse("pro")) {
            "pro", "lite" -> buildConfigField("String", "APP_TIER", "\"$tier\"")
            else -> error("Unknown app.tier \"$tier\", expected pro or lite")
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
}
