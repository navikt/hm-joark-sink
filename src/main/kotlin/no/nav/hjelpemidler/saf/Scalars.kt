package no.nav.hjelpemidler.saf

import com.expediagroup.graphql.client.converter.ScalarConverter
import java.time.LocalDate
import java.time.LocalDateTime

class DateScalarConverter : ScalarConverter<LocalDate> {
    override fun toJson(value: LocalDate): Any = value.toString()

    override fun toScalar(rawValue: Any): LocalDate = when (rawValue) {
        is String -> LocalDate.parse(rawValue)
        else -> error("Kan ikke gjøre om $rawValue til LocalDate")
    }
}

class DateTimeScalarConverter : ScalarConverter<LocalDateTime> {
    override fun toJson(value: LocalDateTime): Any = value.toString()

    override fun toScalar(rawValue: Any): LocalDateTime = when (rawValue) {
        is String -> LocalDateTime.parse(rawValue)
        else -> error("Kan ikke gjøre om $rawValue til LocalDateTime")
    }
}
