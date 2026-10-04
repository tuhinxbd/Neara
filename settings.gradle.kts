rootProject.name = "neara"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":neara-core")
include(":neara-crypto")
include(":neara-protocol")
include(":neara-discovery")
include(":neara-transport")
include(":neara-storage")
include(":neara-service")
include(":neara-desktop")
include(":neara-android")
