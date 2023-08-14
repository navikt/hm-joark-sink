package no.nav.hjelpemidler.joark.domain

import no.nav.hjelpemidler.joark.førstesidegenerator.models.PostFoerstesideRequest

enum class Språkkode(val førstesidegenerator: PostFoerstesideRequest.Spraakkode) {
    NB(PostFoerstesideRequest.Spraakkode.NB),
    NN(PostFoerstesideRequest.Spraakkode.NN),
    EN(PostFoerstesideRequest.Spraakkode.EN),
    ;
}
