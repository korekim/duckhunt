package com.korekim.duckhunt.util

import kotlin.math.*

object GeoUtils {

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val rlat1 = Math.toRadians(lat1)
        val rlat2 = Math.toRadians(lat2)
        val x = cos(rlat2) * sin(dLon)
        val y = cos(rlat1) * sin(rlat2) - sin(rlat1) * cos(rlat2) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    fun bearingToCardinal(deg: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg + 22.5) / 45).toInt() % 8]
    }

    fun metersToFeet(meters: Double) = meters * 3.28084
}