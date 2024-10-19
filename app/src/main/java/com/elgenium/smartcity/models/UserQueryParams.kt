package com.elgenium.smartcity.models

data class UserQueryParams(
    val intent: String,
    val keywords: String,
    val placeType: String,
    val openNow: Boolean,
    val rating: Double,
    val priceLevels: List<Int>
)
