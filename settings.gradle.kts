import no.nav.hjelpemidler.gradle.addGitHubMavenRepository

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://navikt.github.io/hotlibs-gradle")
    }
}

plugins {
    id("no.nav.hjelpemidler.hotlibs") version "1.0"
}

addGitHubMavenRepository("navikt/rapids-and-rivers")

rootProject.name = "hm-joark-sink"
