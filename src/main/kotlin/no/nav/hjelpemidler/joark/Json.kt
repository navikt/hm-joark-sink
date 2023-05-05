package no.nav.hjelpemidler.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.helse.rapids_rivers.MessageContext
import java.util.UUID

val jsonMapper: JsonMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

fun <T : Any> MessageContext.publish(key: String, message: T) =
    publish(key, jsonMapper.writeValueAsString(message))

fun JsonNode.uuidValue(): UUID =
    UUID.fromString(textValue())
