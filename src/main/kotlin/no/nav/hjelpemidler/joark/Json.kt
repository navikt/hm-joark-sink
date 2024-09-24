package no.nav.hjelpemidler.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.hjelpemidler.joark.metrics.Prometheus
import java.util.UUID
import kotlin.reflect.full.findAnnotation

val jsonMapper: JsonMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

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

fun JsonNode.uuidValue(): UUID? =
    textValue()?.let(UUID::fromString)

@Deprecated("Serialiser til JSON med Jackson", ReplaceWith("MessageContext.publish(key, message)"))
fun jsonMessage(block: (JsonMessage) -> Unit): JsonMessage =
    JsonMessage.newMessage(emptyMap(), Prometheus.registry).also(block)
