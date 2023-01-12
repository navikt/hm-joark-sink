rootProject.name = "hm-joark-sink"

sourceControl {
    gitRepository(uri("https://github.com/navikt/hm-http.git")) {
        producesModule("no.nav.hjelpemidler.http:hm-http")
    }
}
