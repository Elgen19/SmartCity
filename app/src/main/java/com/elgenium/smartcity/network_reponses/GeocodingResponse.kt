package com.elgenium.smartcity.network_reponses

data class GeocodingResponse(
    val addresses: List<AddressResult>
)

data class AddressResult(
    val address: Address
)

data class Address(
    val freeformAddress: String
)
