plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(rootProject)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback.classic)
}

ktlint {
    version.set(libs.versions.ktlint.app.get())
}

application {
    mainClass.set("io.ktor.server.cio.EngineMain")
}
