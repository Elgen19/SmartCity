package com.elgenium.smartcity.models

sealed class ActivityItem {
    data class Header(val title: String) : ActivityItem()
    data class Activity(val details: ActivityDetails) : ActivityItem()
}
