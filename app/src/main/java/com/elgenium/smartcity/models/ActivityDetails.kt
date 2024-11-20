package com.elgenium.smartcity.models

data class ActivityDetails(
    val activityId: String? = "",
    val activityName: String = "",
    val placeName: String = "",
    val placeAddress: String = "",
    val priorityLevel: String? = null,
    val startTime: String? = "" ,
    val endTime: String? = "",
    val placeId: String = ""
)
