package no.nav.hjelpemidler.joark.test

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

inline fun <reified T> JsonMapper.readValue(path: Path): T =
    readValue<T>(path.toFile())
