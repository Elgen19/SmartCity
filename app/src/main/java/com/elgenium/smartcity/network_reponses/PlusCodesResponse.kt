package com.elgenium.smartcity.network_reponses

data class PlusCodeResponse(
    val plus_code: PlusCode,
    val status: String
)

data class PlusCode(
    val global_code: String,
    val local_code: String,
    val locality: Locality,
    val geometry: LocationGeometry
)

data class Locality(
    val local_address: String
)

data class LocationGeometry(
    val location: Locate,
    val bounds: Bounds
)

data class Locate(
    val lat: Double,
    val lng: Double
)

data class Bounds(
    val northeast: Locate,
    val southwest: Locate
)

