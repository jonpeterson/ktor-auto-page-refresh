plugins {
    alias(libs.plugins.axion.release)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.maven.publish)
}

tasks.wrapper {
    gradleVersion = "9.3.0"
}

group = "io.github.jonpeterson"
version = scmVersion.version
description = "A Ktor plugin for refreshing the browser when the server reloads"

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.ktor.server.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.ktor.client.java)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.logback.classic)
}

ktlint {
    version.set(libs.versions.ktlint.app.get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()

    pom {
        name = "Ktor Auto Page Refresh Plugin"
        description = project.description
        url = "https://github.com/jonpeterson/ktor-auto-page-refresh"
        inceptionYear = "2026"
        licenses {
            license {
                name = "MIT"
                url = "https://mit-license.org"
            }
        }
        developers {
            developer {
                id = "jonpeterson"
                name = "Jon Peterson"
                url = "https://github.com/jonpeterson/"
            }
        }
        scm {
            url = "https://github.com/jonpeterson/ktor-auto-page-refresh/"
        }
    }
}
