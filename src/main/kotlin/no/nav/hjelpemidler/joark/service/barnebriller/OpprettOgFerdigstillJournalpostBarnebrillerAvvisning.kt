package no.nav.hjelpemidler.joark.service.barnebriller

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.hjelpemidler.joark.brev.BrevService
import no.nav.hjelpemidler.joark.brev.FlettefelterAvvisning
import no.nav.hjelpemidler.joark.domain.Dokumenttype
import no.nav.hjelpemidler.joark.jsonMapper
import no.nav.hjelpemidler.joark.service.AsyncPacketListener
import no.nav.hjelpemidler.joark.service.JournalpostService
import no.nav.hjelpemidler.joark.uuidValue
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Barnebrilleavvisning er opprettet i optikerløsningen
 */
class OpprettOgFerdigstillJournalpostBarnebrillerAvvisning(
    rapidsConnection: RapidsConnection,
    private val journalpostService: JournalpostService,
    private val brevService: BrevService,
) : AsyncPacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("eventName", "hm-brille-avvisning") }
            validate {
                it.requireKey(
                    "eventId",
                    "opprettet",
                    "fnrBarn",
                    "navnBarn",
                    "orgnr",
                    "orgNavn",
                    "brilleseddel",
                    "bestillingsdato",
                    "eksisterendeVedtakDato",
                    "årsaker",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].uuidValue()
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.fnrBarn get() = this["fnrBarn"].textValue()
    private val JsonMessage.navnBarn get() = this["navnBarn"].textValue()
    private val JsonMessage.orgnr get() = this["orgnr"].textValue()
    private val JsonMessage.orgNavn get() = this["orgNavn"].textValue()
    private val JsonMessage.brilleseddel get() = this["brilleseddel"].let { jsonMapper.readValue<Brilleseddel>(it.toString()) }
    private val JsonMessage.bestillingsdato get() = this["bestillingsdato"].asLocalDate()
    private val JsonMessage.eksisterendeVedtakDato get() = this["eksisterendeVedtakDato"].asOptionalLocalDate()
    private val JsonMessage.årsaker get() = this["årsaker"].let { it.toList().mapNotNull { it.textValue() } }

    override suspend fun onPacketAsync(packet: JsonMessage, context: MessageContext) {
        log.info("Oppretter og journalfører avvisningsbrev for direkteoppgjørsløsningen (eventId=${packet.eventId})")

        val locale = Locale.forLanguageTag("nb-NO")
        val f = DateTimeFormatter.ofPattern("dd. MMMM yyyy").withLocale(locale)

        coroutineScope {
            launch {
                // Generer fysisk dokument
                val flettefelter = FlettefelterAvvisning(
                    brevOpprettetDato = packet.opprettet.toLocalDate().format(f),
                    barnetsFulleNavn = packet.navnBarn,
                    barnetsFodselsnummer = packet.fnrBarn,
                    mottattDato = packet.opprettet.toLocalDate().format(f),
                    bestillingsDato = packet.bestillingsdato.format(f),
                    optikerForretning = "${packet.orgNavn} (${packet.orgnr})",
                    sfæriskStyrkeHøyre = packet.brilleseddel.høyreSfære.toString(),
                    sfæriskStyrkeVenstre = packet.brilleseddel.venstreSfære.toString(),
                    cylinderstyrkeHøyre = packet.brilleseddel.høyreSylinder.toString(),
                    cylinderstyrkeVenstre = packet.brilleseddel.venstreSylinder.toString(),
                    forrigeBrilleDato = packet.eksisterendeVedtakDato?.format(f) ?: "",
                )

                val årsaker = packet.årsaker.map {
                    when (it) {
                        "HarIkkeVedtakIKalenderåret" -> "stansetEksisterendeVedtak"
                        "Under18ÅrPåBestillingsdato" -> "stansetOver18"
                        "MedlemAvFolketrygden" -> "stansetIkkeMedlem"
                        "Brillestyrke" -> "stansetForLavBrillestyrke"
                        "Bestillingsdato" -> "stansetBestillingsdatoEldreEnn6Mnd"
                        else -> {
                            throw RuntimeException("Ukjent identifikator fra brille-api mottatt, kan ikke opprette avvisningsbrev (årsak=${it})")
                        }
                    }
                }

                val fysiskDokument = brevService.lagStansetBrev(flettefelter, årsaker)

                // Opprett og ferdigstill journalpost
                val journalpostId = journalpostService.opprettUtgåendeJournalpost(
                    fnrMottaker = packet.fnrBarn,
                    fnrBruker = packet.fnrBarn,
                    dokumenttype = Dokumenttype.VEDTAKSBREV_BARNEBRILLER_OPTIKER_AVVISNING,
                    forsøkFerdigstill = true,
                ) {
                    dokument(fysiskDokument = fysiskDokument)
                    optikerGenerellSak()
                    eksternReferanseId = UUID.randomUUID().toString()
                    datoMottatt = packet.opprettet
                }

                log.info("Stanset fra direkteoppgjørsløsningen: journalpostId: $journalpostId")
            }
        }
    }

    companion object {
        data class Brilleseddel(
            val høyreSfære: Double,
            val høyreSylinder: Double,
            val høyreAdd: Double = 0.0,
            val venstreSfære: Double,
            val venstreSylinder: Double,
            val venstreAdd: Double = 0.0,
        )
    }
}