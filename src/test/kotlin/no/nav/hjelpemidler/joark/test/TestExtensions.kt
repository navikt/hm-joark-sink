package no.nav.hjelpemidler.joark.test

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.CapturingSlot
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

infix fun <T : Any, U : T> CapturingSlot<T>.shouldHaveCaptured(expected: U?) = captured shouldBe expected

inline fun <T : Any> CapturingSlot<T>.assertSoftly(assertions: T.(T) -> Unit) = assertSoftly(captured, assertions)

fun <T> MockRequestHandleScope.respondJson(value: T, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(
        jsonMapper.writeValueAsBytes(value),
        status,
        headersOf(HttpHeaders.ContentType, "application/json")
    )
