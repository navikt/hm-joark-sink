package no.nav.hjelpemidler.joark

import io.kotest.matchers.maps.shouldHaveKeys
import no.nav.hjelpemidler.configuration.environmentVariablesIn
import no.nav.hjelpemidler.joark.test.readValue
import kotlin.io.path.Path
import kotlin.test.Test

class ConfigurationTest {
    private val environmentVariables = environmentVariablesIn(Configuration, includeExternal = false)

    @Test
    fun `har alle miljøvariabler definert i Configuration for dev`() {
        val jsonVariables = readJsonVariables("dev.json")

        jsonVariables shouldHaveKeys environmentVariables
    }

    @Test
    fun `har alle miljøvariabler definert i Configuration for dev-q1`() {
        val jsonVariables = readJsonVariables("dev-q1.json")

        jsonVariables shouldHaveKeys environmentVariables
    }

    @Test
    fun `har alle miljøvariabler definert i Configuration for prod`() {
        val jsonVariables = readJsonVariables("prod.json")

        jsonVariables shouldHaveKeys environmentVariables
    }

    private fun readJsonVariables(path: String) =
        jsonMapper.readValue<Map<String, String>>(Path("./nais").resolve(path))

    private infix fun Map<String, String>.shouldHaveKeys(keys: Collection<String>) =
        shouldHaveKeys(*keys.toTypedArray())
}
