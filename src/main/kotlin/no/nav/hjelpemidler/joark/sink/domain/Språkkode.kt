package no.nav.hjelpemidler.joark.sink.domain

import no.nav.hjelpemidler.joark.sink.førstesidegenerator.models.PostFoerstesideRequest

enum class Språkkode(val førstesidegenerator: PostFoerstesideRequest.Spraakkode) {
    NB(PostFoerstesideRequest.Spraakkode.NB),
    NN(PostFoerstesideRequest.Spraakkode.NN),
    EN(PostFoerstesideRequest.Spraakkode.EN),
    ;
}
