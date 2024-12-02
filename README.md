# README
![build-deploy-dev](https://github.com/navikt/hm-joark-sink/actions/workflows/deploy-dev.yaml/badge.svg)

App som lytter på rapid og arkiverer mottatte søknader i joark.


# Dokumenter vi arkiverer

| Inn / Ut | Område                   | Tittel                                                                  | Dokumenttittel (hvis ulik)                         | Brevkode                                    | Avsender (inn) / Mottaker (ut) | Begrunnelse for valg av avsender/mottaker                                                                                                                                        |
|----------|--------------------------|-------------------------------------------------------------------------|----------------------------------------------------|---------------------------------------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Inn      | Hjelpemidler             | Søknad om hjelpemidler                                                  | Søknad om: (...)                                   | NAV 10-07.03                                | Bruker selv                    | På hjelpemiddelområdet er det brukeren som har rettigheten, og fra lovens ståsted bidrar bare kommunal formidler med utfylling (typisk med fullmakt)                             |
| Inn      | Hjelpemidler             | Bestilling av tekniske hjelpemidler                                     | Bestilling av: (...)                               | NAV 10-07.05                                | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Inn      | Hjelpemidler             | Bytte av hjelpemiddel                                                   | Bytte av: (...)                                    | NAV 10-07.31                                | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Inn      | Hjelpemidler             | Brukerpassbytte av hjelpemiddel                                         | Bytte av: (...)                                    | NAV 10-07.31                                | Bruker selv                    | Bruker er avsender: brukerpassbrukere søker for seg selv                                                                                                                         |
| Inn      | Briller (direkteoppgjør) | Tilskudd ved kjøp av briller til barn via optiker                       | -                                                  | krav_barnebriller_optiker                   | Bruker selv                    | Barnet er den med rettigheten, men optiker er den som sender inn krav om oppgjør for utlegg av stønad til barnet. Barnet skal ha rett på innsyn, så barnet er satt som avsender. |
| Inn      | Briller (direkteoppgjør) | Stanset behandling av tilskudd til kjøp av briller til barn via optiker | -                                                  | krav_barnebriller_optiker_avvisning         | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Inn      | Briller (manuell saksb.) | Tilskudd ved kjøp av briller til barn                                   | -                                                  | NAV 10-07.34                                | Bruker selv                    | Barnet er den med rettigheten, men forelder eller verge er den som sender inn søknaden og som får utbetalt for utlegget de har hatt. Barnet skal ha innsyn.                      |
| Inn      | Briller (manuell saksb.) | Ettersendelse til tilskudd ved kjøp av briller til barn                 | -                                                  | NAVe 10-07.34                               | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Ut       | Briller (manuell saksb.) | Vedtaksbrev barnebriller                                                | Tilskudd ved kjøp av briller til barn              | vedtaksbrev_barnebriller_hotsak             | Bruker selv                    | Barnet er den med rettigheten, og har rett til innsyn. Vedtak sendes dermed til barnet.                                                                                          |
| Ut       | Briller (manuell saksb.) | Avslag: Vedtaksbrev barnebriller                                        | Avslag: Tilskudd ved kjøp av briller til barn      | vedtaksbrev_barnebriller_hotsak_avslag      | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Ut       | Briller (manuell saksb.) | Innvilgelse: Vedtaksbrev barnebriller                                   | Innvilgelse: Tilskudd ved kjøp av briller til barn | vedtaksbrev_barnebriller_hotsak_innvilgelse | Bruker selv                    | Samme som over                                                                                                                                                                   |
| Ut       | Briller (manuell saksb.) | Briller til barn: NAV etterspør opplysninger                            | -                                                  | innhente_opplysninger_barnebriller          | Bruker selv                    | Barnet er den med rettigheten, men forelder eller verge er typisk den som har sendt inn søknaden. Utgående brev for innhenting av opplysninger adresseres likevel til barnet.    |


# Lokal køyring

Dokarkiv og AzureAd er mocka med Wiremock

- start [backend](https://github.com/navikt/hm-soknad-api) for å starte rapid og evt. populere rapid
- start [hm-soknadsbehandling](https://github.com/navikt/hm-soknadsbehandling) for å lagre søknad i db og sende videre på rapid

- start hm-joark-sink og vent på melding


# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #digihot-dev.