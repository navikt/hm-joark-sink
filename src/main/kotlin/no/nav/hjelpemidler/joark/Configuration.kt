package no.nav.hjelpemidler.joark

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8083",
        "application.profile" to "LOCAL",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "kafka.truststore.password" to "",
        "KAFKA_TRUSTSTORE_PATH" to "",
        "KAFKA_CREDSTORE_PASSWORD" to "",
        "KAFKA_KEYSTORE_PATH" to "",
        "kafka.brokers" to "host.docker.internal:9092",
        "pdf.baseurl" to "http://host.docker.internal:8088",
        "AZURE_TENANT_BASEURL" to "http://localhost:9099",
        "AZURE_APP_TENANT_ID" to "123",
        "AZURE_APP_CLIENT_ID" to "123",
        "AZURE_APP_CLIENT_SECRET" to "dummy",
        "joark.baseurl" to "http://localhost:9099/dokarkiv",
        "JOARK_SCOPE" to "123"
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "pdf.baseurl" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "joark.baseurl" to "https://digihot-proxy.dev-fss-pub.nais.io/dokarkiv-aad",
        "JOARK_SCOPE" to "api://9a0d62f4-00cf-462a-9acc-0e76e7a360ae/.default"
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "pdf.baseurl" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
        "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
        "joark.baseurl" to "https://digihot-proxy.prod-fss-pub.nais.io/dokarkiv",
        "JOARK_SCOPE" to "123"
    )
)

private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
    "dev-gcp" -> systemProperties() overriding EnvironmentVariables overriding devProperties
    "prod-gcp" -> systemProperties() overriding EnvironmentVariables overriding prodProperties
    else -> {
        systemProperties() overriding EnvironmentVariables overriding localProperties
    }
}

internal object Configuration {
    val application: Application = Application()
    val pdf: Pdf = Pdf()
    val azure: Azure = Azure()
    val joark: Joark = Joark()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-joark-sink",
        "KAFKA_BOOTSTRAP_SERVERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to application.id,
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "NAV_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "NAV_TRUSTSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "KAFKA_KEYSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "HTTP_PORT" to config()[Key("application.httpPort", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Application(
        val id: String = config().getOrElse(Key("", stringType), "hm-joark-sink-v1"),
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)]
    )

    data class Pdf(
        val baseUrl: String = config()[Key("pdf.baseurl", stringType)]
    )

    data class Azure(
        val tenantBaseUrl: String = config()[Key("AZURE_TENANT_BASEURL", stringType)],
        val tenantId: String = config()[Key("AZURE_APP_TENANT_ID", stringType)],
        val clientId: String = config()[Key("AZURE_APP_CLIENT_ID", stringType)],
        val clientSecret: String = config()[Key("AZURE_APP_CLIENT_SECRET", stringType)]
    )

    data class Joark(
        val baseUrl: String = config()[Key("joark.baseurl", stringType)],
        val joarkScope: String = config()[Key("JOARK_SCOPE", stringType)]

    )
}

enum class Profile {
    LOCAL, DEV, PROD
}

private fun getHostname(): String {
    return try {
        val addr: InetAddress = InetAddress.getLocalHost()
        addr.hostName
    } catch (e: UnknownHostException) {
        "unknown"
    }
}

private fun String.readFile() =
    File(this).readText(Charsets.UTF_8)
