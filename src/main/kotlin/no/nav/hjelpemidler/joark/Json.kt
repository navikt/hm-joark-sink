package no.nav.hjelpemidler.joark

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.hjelpemidler.joark.metrics.Prometheus
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import java.util.UUID
import kotlin.reflect.full.findAnnotation

inline fun <reified T : Any> MessageContext.publish(key: String, message: T) =
    when (val hendelse = message::class.findAnnotation<Hendelse>()) {
        null -> publish(key, jsonMapper.writeValueAsString(message))
        else -> publish(
            key,
            jsonMapper
                .writerFor(jacksonTypeRef<T>())
                .withAttribute(Hendelse.EVENT_ID_PROPERTY, UUID.randomUUID())
                .withAttribute(Hendelse.EVENT_NAME_PROPERTY, hendelse.navn)
                .writeValueAsString(message)
        )
    }

@Deprecated("Serialiser til JSON med Jackson", ReplaceWith("MessageContext.publish(key, message)"))
fun jsonMessage(block: (JsonMessage) -> Unit): JsonMessage =
    JsonMessage.newMessage(emptyMap(), Prometheus.registry).also(block)
