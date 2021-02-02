# README
![build-deploy-dev](https://github.com/navikt/hm-joark-sink/workflows/Build%20and%20deploy/badge.svg)

App som lytter på rapid og arkiverer mottatte søknader i joark.


# Lokal køyring

Dokarkiv og AzureAd er mocka med Wiremock

start [backend](https://github.com/navikt/hm-soknad-api) for å starte rapid og evt. populere rapid
start [hm-soknadsbehandling](https://github.com/navikt/hm-soknadsbehandling) for å lagre søknad i db og sende videre på rapid

start hm-joark-sink og vent på melding
