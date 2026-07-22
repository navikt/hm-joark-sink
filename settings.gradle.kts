fun RepositoryHandler.github(repository: String) {
    maven("https://maven.pkg.github.com/$repository") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        github("navikt/hotlibs")
        github("navikt/rapids-and-rivers")
        // plassert under github som fallback
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    versionCatalogs {
        create("libs") {
            val hotlibsKatalogVersion = providers.gradleProperty("hotlibsKatalogVersion").get()
            from("no.nav.hjelpemidler:katalog:$hotlibsKatalogVersion")
        }
    }
}

rootProject.name = "hm-joark-sink"
