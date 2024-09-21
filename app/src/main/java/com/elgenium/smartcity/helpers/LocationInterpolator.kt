package com.elgenium.smartcity.helpers

import android.location.Location
import android.os.SystemClock
import java.util.LinkedList

class LocationInterpolator {
    private val locations = LinkedList<TimedLocation>()
    private val MAX_LOCATIONS = 5

    fun addLocation(location: Location) {
        val timedLocation = TimedLocation(location, SystemClock.elapsedRealtime())
        locations.addLast(timedLocation)
        if (locations.size > MAX_LOCATIONS) {
            locations.removeFirst()
        }
    }

    fun getInterpolatedLocation(time: Long): Location? {
        if (locations.size < 2) return locations.lastOrNull()?.location

        val (prev, next) = locations.zipWithNext().find { (_, next) ->
            next.time >= time
        } ?: return locations.last().location

        val ratio = (time - prev.time).toFloat() / (next.time - prev.time)
        return interpolateLocation(prev.location, next.location, ratio)
    }

    private fun interpolateLocation(start: Location, end: Location, ratio: Float): Location {
        val interpolatedLocation = Location("")
        interpolatedLocation.latitude = start.latitude + (end.latitude - start.latitude) * ratio
        interpolatedLocation.longitude = start.longitude + (end.longitude - start.longitude) * ratio
        interpolatedLocation.bearing = interpolateBearing(start.bearing, end.bearing, ratio)
        return interpolatedLocation
    }

    private fun interpolateBearing(start: Float, end: Float, ratio: Float): Float {
        val diff = (end - start + 360) % 360
        val shortestPath = if (diff > 180) diff - 360 else diff
        return (start + shortestPath * ratio + 360) % 360
    }

    data class TimedLocation(val location: Location, val time: Long)
}