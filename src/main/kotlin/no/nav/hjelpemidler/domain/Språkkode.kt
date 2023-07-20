package no.nav.hjelpemidler.domain

import no.nav.hjelpemidler.førstesidegenerator.models.PostFoerstesideRequest

enum class Språkkode(val førstesidegenerator: PostFoerstesideRequest.Spraakkode) {
    NB(PostFoerstesideRequest.Spraakkode.NB),
    NN(PostFoerstesideRequest.Spraakkode.NN),
    EN(PostFoerstesideRequest.Spraakkode.EN),
    ;
}
