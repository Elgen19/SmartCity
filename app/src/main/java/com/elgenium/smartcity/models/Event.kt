package com.elgenium.smartcity.models

data class Event(
    val eventName: String? = null,
    val location: String? = null,
    val additionalInfo: String? = null,
    val images: List<String>? = null,
    val eventCategory: String? = null,
    val eventDescription: String? = null,
    val startedDateTime: String? = null,
    val endedDateTime: String? = null,
    val placeLatLng: String? = null,
    val checker: String? = null,
    val placeId: String? = null,
    val submittedBy: String? = null,
    val submittedAt: String? = null,
    val userId: String?= null,
    val status: String?= null,

    )

