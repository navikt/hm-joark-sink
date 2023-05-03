# OpenAPI-spesifikasjon for dokarkiv

Fila er hentet herfra:
[https://dokarkiv-q2.dev-fss-pub.nais.io/api/v1/api-docs](https://dokarkiv-q2.dev-fss-pub.nais.io/api/v1/api-docs)

NB! `DokumentVariant.fysiskDokument` har endret datatype fra `array` til `string` med type `byte`.
Dette for at type i Kotlin skal bli `kotlin.ByteArray` (som blir til string i base-64-format i JSON)
og ikke `kotlin.collections.List<kotlin.ByteArray>`.
Dette er egentlig en bug:
[https://github.com/OpenAPITools/openapi-generator/issues/12660](https://github.com/OpenAPITools/openapi-generator/issues/12660)
