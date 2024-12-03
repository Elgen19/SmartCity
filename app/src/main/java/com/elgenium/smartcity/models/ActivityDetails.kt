package com.elgenium.smartcity.models

import java.io.Serializable

data class ActivityDetails(
    val activityId: String? = "",
    val activityName: String = "",
    val placeName: String = "",
    val placeAddress: String = "",
    val priorityLevel: String? = null,
    var startTime: String? = "",
    var endTime: String? = "",
    val placeId: String = "",
    var placeLatlng: String = "",
    var status: String = "Upcoming",
    var containerStatus: String = "Unscheduled",
    val placeTypes: String = ""
): Serializable
