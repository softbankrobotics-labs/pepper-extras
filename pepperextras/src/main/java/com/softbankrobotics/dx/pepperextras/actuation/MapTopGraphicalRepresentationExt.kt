package com.softbankrobotics.dx.pepperextras.actuation

import com.aldebaran.qi.sdk.`object`.actuation.MapTopGraphicalRepresentation
import kotlin.math.cos
import kotlin.math.sin

fun MapTopGraphicalRepresentation.mapToGraphicalCoordinates(xMap: Double, yMap: Double): Pair<Double, Double> {
    val xPixel = 1 / scale * (cos(theta) * (xMap - x) + sin(theta) * (yMap - y))
    val yPixel = 1 / scale * (sin(theta) * (xMap - x) - cos(theta) * (yMap - y))
    return Pair(xPixel, yPixel)
}

fun MapTopGraphicalRepresentation.graphicalToMapCoordinates(xPixel: Double, yPixel: Double): Pair<Double, Double> {
    val xMap = scale * (cos(theta) * xPixel + sin(theta) * yPixel) + x
    val yMap = scale * (sin(theta) * xPixel - cos(theta) * yPixel) + y
    return Pair(xMap, yMap)
}