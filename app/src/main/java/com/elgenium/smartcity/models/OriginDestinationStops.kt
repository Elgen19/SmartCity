package com.elgenium.smartcity.models

import java.io.Serializable

data class OriginDestinationStops(
    val name: String,
    val address: String,
    var type: String,
    val latlng: String,
    val placeid: String
): Serializable



