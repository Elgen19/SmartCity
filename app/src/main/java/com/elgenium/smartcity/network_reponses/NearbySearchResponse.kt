package com.elgenium.smartcity.network_reponses

data class NearbySearchResponse(
    val results: List<PlaceResult>
)

data class PlaceResult(
    val name: String,
    val vicinity: String
)

