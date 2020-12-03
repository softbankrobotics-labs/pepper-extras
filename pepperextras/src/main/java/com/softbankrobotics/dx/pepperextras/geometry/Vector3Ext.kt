package com.softbankrobotics.dx.pepperextras.geometry

import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D as ApacheVector3D

// Vector3 (QiSDK) <-> Vector3D (Apache Commons Math)

fun Vector3.toApacheVector3D(): ApacheVector3D {
    return ApacheVector3D(x, y, z)
}

fun ApacheVector3D.toQiVector3(): Vector3 {
    return Vector3(x, y, z)
}

// Vector3 extensions:

operator fun Vector3.plus(v2: Vector3): Vector3 {
    return Vector3(x + v2.x, y + v2.y, z + v2.z)
}

operator fun Vector3.minus(v2: Vector3): Vector3 {
    return Vector3(x - v2.x, y - v2.y, z - v2.z)
}

operator fun Vector3.div(d: Double): Vector3 {
    return Vector3(x / d, y / d, z / d)
}

operator fun Vector3.times(d: Double): Vector3 {
    return Vector3(x * d, y * d, z * d)
}
