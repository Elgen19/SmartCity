package com.elgenium.smartcity.network_reponses

data class GeocodingResponse(
    val results: List<Result>,
    val status: String
)

data class Result(
    val address_components: List<AddressComponent>
)

data class AddressComponent(
    val types: List<String>,
    val long_name: String
)

