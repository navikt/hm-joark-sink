package no.nav.hjelpemidler.joark.pdf

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.joark.test.TestOpenIDClient
import no.nav.hjelpemidler.joark.test.respondJson
import kotlin.test.Test
import kotlin.test.fail

class FørstesidegeneratorClientTest {
    @Test
    fun `lager førsteside`() = runTest {
        val client = FørstesidegeneratorClient(azureADClient = TestOpenIDClient(), engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Post -> {
                    request.url.fullPath shouldBe "/api/foerstesidegenerator/v1/foersteside"

                    respondJson(OpprettFørstesideResponse(ByteArray(0), "1"), HttpStatusCode.Created)
                }

                HttpMethod.Get -> {
                    request.url.fullPath shouldBe "/api/foerstesidegenerator/v1/foersteside/1"

                    respondJson(FørstesideResponse())
                }

                else -> {
                    fail()
                }
            }
        })
        val lagRequest = OpprettFørstesideRequestConfigurer("test", "")
        client.lagFørsteside(lagRequest())
    }
}
