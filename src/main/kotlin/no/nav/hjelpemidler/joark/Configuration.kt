package no.nav.hjelpemidler.joark

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

internal object Configuration {
    private val localProperties = ConfigurationMap(
        mapOf(
            "APPNAVN" to "hm-joark-sink",
            "application.httpPort" to "8083",
            "application.profile" to Profile.LOCAL.name,

            "kafka.reset.policy" to "latest",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "kafka.brokers" to "host.docker.internal:9092",
            "kafka.truststore.password" to "",

            "KAFKA_CONSUMER_GROUP_ID" to "hm-joark-sink-v1",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",
            "KAFKA_KEYSTORE_PATH" to "",

            "PDF_BASEURL" to "http://host.docker.internal:8088",

            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://localhost:9111/token",
            "AZURE_TENANT_BASEURL" to "http://localhost:9111",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "123",
            "AZURE_APP_CLIENT_SECRET" to "dummy",

            "JOARK_BASEURL" to "http://localhost:9111/dokarkiv",
            "JOARK_SCOPE" to "123",

            "JOARK_PROXY_BASEURL" to "http://localhost:9111/dokarkiv",
            "JOARK_PROXY_SCOPE" to "123",

            "EVENT_NAME" to "hm-SøknadArkivert"
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "APPNAVN" to "hm-joark-sink",
            "application.httpPort" to "8080",
            "application.profile" to Profile.DEV.name,

            "kafka.reset.policy" to "latest",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",

            "KAFKA_CONSUMER_GROUP_ID" to "hm-joark-sink-v2",

            "PDF_BASEURL" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com"
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "APPNAVN" to "hm-joark-sink",
            "application.httpPort" to "8080",
            "application.profile" to Profile.PROD.name,

            "kafka.reset.policy" to "earliest",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",

            "KAFKA_CONSUMER_GROUP_ID" to "hm-joark-sink-v1",

            "PDF_BASEURL" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",

            "JOARK_BASEURL" to "https://dokarkiv.prod-fss-pub.nais.io/rest/journalpostapi/v1",
            "JOARK_SCOPE" to "api://prod-fss.teamdokumenthandtering.dokarkiv/.default",

            "JOARK_PROXY_BASEURL" to "https://digihot-proxy.prod-fss-pub.nais.io/dokarkiv-aad",
            "JOARK_PROXY_SCOPE" to "api://8bdfd270-4760-4428-8a6e-540707d61cf9/.default",

            "EVENT_NAME" to "hm-SøknadArkivert"
        )
    )

    private val configuration = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> systemProperties() overriding EnvironmentVariables overriding localProperties
    }

    operator fun get(name: String): String =
        configuration[Key(name, stringType)]

    val application: Application = Application()
    val pdf: Pdf = Pdf()
    val azure: Azure = Azure()
    val joark: Joark = Joark()

    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to this["APPNAVN"],
        "KAFKA_BROKERS" to this["kafka.brokers"],
        "KAFKA_CONSUMER_GROUP_ID" to this["KAFKA_CONSUMER_GROUP_ID"],
        "KAFKA_RAPID_TOPIC" to this["kafka.topic"],
        "KAFKA_RESET_POLICY" to this["kafka.reset.policy"],
        "KAFKA_TRUSTSTORE_PATH" to this["KAFKA_TRUSTSTORE_PATH"],
        "KAFKA_KEYSTORE_PATH" to this["KAFKA_KEYSTORE_PATH"],
        "KAFKA_CREDSTORE_PASSWORD" to this["KAFKA_CREDSTORE_PASSWORD"],
        "HTTP_PORT" to this["application.httpPort"]
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Application(
        val profile: Profile = this["application.profile"].let { Profile.valueOf(it) },
        val httpPort: Int = this["application.httpPort"].toInt(),
        val eventName: String = this["EVENT_NAME"],
    )

    data class Pdf(
        val baseUrl: String = this["PDF_BASEURL"],
    )

    data class Azure(
        val tenantBaseUrl: String = this["AZURE_TENANT_BASEURL"],
        val tenantId: String = this["AZURE_APP_TENANT_ID"],
        val clientId: String = this["AZURE_APP_CLIENT_ID"],
        val clientSecret: String = this["AZURE_APP_CLIENT_SECRET"],
    )

    data class Joark(
        val baseUrl: String = this["JOARK_BASEURL"],
        val scope: String = this["JOARK_SCOPE"],
        val proxyBaseUrl: String = this["JOARK_PROXY_BASEURL"],
        val proxyScope: String = this["JOARK_PROXY_SCOPE"],
    )

    enum class Profile {
        LOCAL, DEV, PROD
    }
}
