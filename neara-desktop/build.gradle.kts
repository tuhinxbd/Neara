plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":neara-core"))
    implementation(project(":neara-crypto"))
    implementation(project(":neara-protocol"))
    implementation(project(":neara-discovery"))
    implementation(project(":neara-transport"))
    implementation(project(":neara-storage"))
    implementation(project(":neara-service"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
}

compose.desktop {
    application {
        mainClass = "app.neara.desktop.MainKt"
    }
}
