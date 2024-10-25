package com.elgenium.smartcity.network_reponses

data class GeocodingResponse(
    val results: List<Result>,
    val status: String
)

data class Result(
    val address_components: List<AddressComponent>,
    val place_id: String,  // Add place_id field
    val formatted_address: String  // Optional: You may want this for convenience
)

data class AddressComponent(
    val types: List<String>,
    val long_name: String
)

