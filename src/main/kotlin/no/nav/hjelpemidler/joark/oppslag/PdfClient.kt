package no.nav.hjelpemidler.joark.oppslag

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponse
import com.github.kittinunf.fuel.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.net.HttpURLConnection.HTTP_OK

private val logger = KotlinLogging.logger {}

internal class PdfClient(private val baseUrl: String) {
    companion object {
        const val PATH = "api/v1/genpdf/hmb/hmb"
    }

    suspend fun genererPdf(soknadJson: String): ByteArray {
        logger.info { "Generer PDF" }

        return withContext(Dispatchers.IO) {
            kotlin.runCatching {

                "$baseUrl/$PATH".httpPost()
                    .header("Content-Type", "application/json")
                    .jsonBody(soknadJson)
                    .awaitByteArrayResponse()
                    .let { (_, response, byteArray) ->
                        when (response.statusCode) {
                            HTTP_OK -> byteArray
                            else -> throw PdfException(response.responseMessage)
                        }
                    }
            }
                .onFailure {
                    logger.error { it.message }
                }
        }
            .getOrThrow()
    }
}

internal class PdfException(msg: String) : RuntimeException(msg)
