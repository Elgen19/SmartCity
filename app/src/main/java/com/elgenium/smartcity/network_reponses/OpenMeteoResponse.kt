package com.elgenium.smartcity.network_responses

import com.google.gson.annotations.SerializedName

data class OpenMeteoResponse(
    @SerializedName("hourly")
    val hourly: Hourly
)

data class Hourly(
    @SerializedName("time")
    val time: List<String>,
    @SerializedName("precipitation")
    val precipitation: List<Double>
)
