package com.elgenium.smartcity.models

data class GeocodingResponse(
    val addresses: List<AddressResult>
)

data class AddressResult(
    val address: Address
)

data class Address(
    val freeformAddress: String
)
