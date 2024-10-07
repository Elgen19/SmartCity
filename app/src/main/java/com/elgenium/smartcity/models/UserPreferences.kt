package com.elgenium.smartcity.models

data class UserPreferences(
    val discoverPlaces: Boolean,
    val findEvents: Boolean,
    val exploreThingsToDo: Boolean,
    val keepUpWithLocalHappenings: Boolean,
    val dailyActivities: List<String>, // Example: ["Work", "Socializing"]
    val preferredTypes: List<String> // Example: ["Cafes", "Parks"]
)
