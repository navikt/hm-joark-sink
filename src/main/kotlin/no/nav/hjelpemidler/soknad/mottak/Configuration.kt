package no.nav.hjelpemidler.soknad.mottak

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
        "application.httpPort" to "8082",
        "application.profile" to "LOCAL",
        "db.host" to "host.docker.internal",
        "db.database" to "soknadsbehandling",
        "db.password" to "postgres",
        "db.port" to "5434",
        "db.username" to "postgres",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "kafka.truststore.password" to "foo",
        "KAFKA_TRUSTSTORE_PATH" to "bla/bla",
        "KAFKA_CREDSTORE_PASSWORD" to "foo",
        "KAFKA_KEYSTORE_PATH" to "bla/bla",
        "kafka.brokers" to "host.docker.internal:9092",
        "IS_KAFKA_CLOUD" to "false"
    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "DEV",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "IS_KAFKA_CLOUD" to "true"
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.profile" to "PROD",
        "kafka.reset.policy" to "earliest",
        "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
        "IS_KAFKA_CLOUD" to "true"
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
    val database: Database = Database()
    val application: Application = Application()
    val rapidApplication: Map<String, String> = mapOf(
        "RAPID_KAFKA_CLUSTER" to "gcp",
        "RAPID_APP_NAME" to "hm-soknadsbehandling",
        "KAFKA_BOOTSTRAP_SERVERS" to config()[Key("kafka.brokers", stringType)],
        "KAFKA_CONSUMER_GROUP_ID" to application.id,
        "KAFKA_RAPID_TOPIC" to config()[Key("kafka.topic", stringType)],
        "KAFKA_RESET_POLICY" to config()[Key("kafka.reset.policy", stringType)],
        "KAFKA_TRUSTSTORE_PATH" to config()[Key("KAFKA_TRUSTSTORE_PATH", stringType)],
        "KAFKA_TRUSTSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "KAFKA_KEYSTORE_PATH" to config()[Key("KAFKA_KEYSTORE_PATH", stringType)],
        "KAFKA_KEYSTORE_PASSWORD" to config()[Key("KAFKA_CREDSTORE_PASSWORD", stringType)],
        "IS_KAFKA_CLOUD" to config()[Key("IS_KAFKA_CLOUD", stringType)],
        "HTTP_PORT" to config()[Key("application.httpPort", stringType)],
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

    data class Database(
        val host: String = config()[Key("db.host", stringType)],
        val port: String = config()[Key("db.port", stringType)],
        val name: String = config()[Key("db.database", stringType)],
        val user: String? = config().getOrNull(Key("db.username", stringType)),
        val password: String? = config().getOrNull(Key("db.password", stringType))
    )

    data class Application(
        val id: String = config().getOrElse(Key("", stringType), "hm-soknadsbehandling-v1"),
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)]
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
