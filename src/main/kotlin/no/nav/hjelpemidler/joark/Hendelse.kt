package no.nav.hjelpemidler.joark

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import com.fasterxml.jackson.databind.annotation.JsonAppend

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@JacksonAnnotationsInside
@JsonAppend(
    attrs = [
        JsonAppend.Attr(Hendelse.EVENT_ID_PROPERTY),
        JsonAppend.Attr(Hendelse.EVENT_NAME_PROPERTY),
    ],
)
annotation class Hendelse(val navn: String) {
    companion object {
        const val EVENT_ID_PROPERTY = "eventId"
        const val EVENT_NAME_PROPERTY = "eventName"
    }
}
