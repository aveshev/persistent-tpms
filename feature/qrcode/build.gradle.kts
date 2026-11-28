plugins {
    `android-lib`
    `android-test`
    compose
    alias(libs.plugins.metro)
}

android {
    namespace = "com.masselis.tpmsadvanced.feature.qrcode"
}

dependencies {
    implementation(project(":feature:main"))

    implementation(project(":data:vehicle"))
    implementation(project(":data:unit"))
    implementation(project(":data:app"))

    implementation(project(":core:common"))
    implementation(project(":core:ui"))

    implementation(libs.google.mlkit.barecode)
    // Reads the ID printed by a cap's code, when the code itself is too small or blurry
    implementation(libs.google.mlkit.text)
    // Draws the shared sensors' code, MLKit only reads them
    implementation(libs.google.zxing.core)
    implementation(libs.androidx.camera2.core)
    implementation(libs.androidx.camera2.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(project(":core:test"))
}