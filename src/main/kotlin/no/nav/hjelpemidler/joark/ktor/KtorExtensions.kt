package no.nav.hjelpemidler.joark.ktor

import io.ktor.client.request.header
import io.ktor.http.HttpMessageBuilder

fun HttpMessageBuilder.navUserId(opprettetAv: String?) =
    header("Nav-User-Id", opprettetAv)
