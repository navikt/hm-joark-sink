package no.nav.hjelpemidler.joark.test

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import java.nio.file.Path

inline fun <reified T> JsonMapper.readValue(path: Path): T =
    readValue<T>(path.toFile())

infix fun <T : Any, U : T> CapturingSlot<T>.shouldHaveCaptured(expected: U?) =
    captured shouldBe expected

inline fun <T : Any> CapturingSlot<T>.assertSoftly(assertions: T.(T) -> Unit) =
    assertSoftly(captured, assertions)
