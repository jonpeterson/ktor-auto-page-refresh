import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

tasks.wrapper {
    gradleVersion = "9.3.0"
}

group = "io.github.jonpeterson"
description = "A Ktor plugin for reloading the browser when the server reloads"
version = "0.1.0"

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

jreleaser {
    project {
        license = "MIT"
        links.homepage = "https://github.com/jonpeterson/ktor-auto-page-refresh"
        authors.add("Jon Peterson")
        maintainers.add("jonpeterson")
        inceptionYear = "2026"
    }
    release.github {
        repoOwner = "jonpeterson"
        sign = true
    }
    signing.pgp {
        active = Active.ALWAYS
        armored = true
    }
    deploy.maven.mavenCentral {
        register("main") {
            active = Active.RELEASE
            url = "https://central.sonatype.com/api/v1/publisher"
            stagingRepository("target/staging-deploy")
        }
    }
}
