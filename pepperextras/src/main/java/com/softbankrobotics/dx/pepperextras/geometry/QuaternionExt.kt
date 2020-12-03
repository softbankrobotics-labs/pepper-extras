package com.softbankrobotics.dx.pepperextras.geometry

import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import org.apache.commons.math3.geometry.euclidean.threed.Rotation as ApacheRotation
import org.apache.commons.math3.complex.Quaternion as ApacheQuaternion

// Quaternion (QiSDK) <-> Quaternion (Apache Commons Math)

fun Quaternion.toApacheQuaternion(): ApacheQuaternion {
    return ApacheQuaternion(w, x, y, z)
}

fun ApacheQuaternion.toQiQuaternion(): Quaternion {
    return Quaternion(q1, q2, q3, q0)
}

// Quaternion (QiSDK) <-> Rotation (Apache Commons Math)

fun Quaternion.toApacheRotation(): ApacheRotation {
    return ApacheRotation(w, x, y, z, true)
}

fun ApacheRotation.toQiQuaternion(): Quaternion {
    return Quaternion(q1, q2, q3, q0)
}

// Quaternion extensions

operator fun Quaternion.plus(q2: Quaternion): Quaternion {
    return toApacheQuaternion().add(q2.toApacheQuaternion()).toQiQuaternion()
}

operator fun Quaternion.minus(q2: Quaternion): Quaternion {
    return toApacheQuaternion().subtract(q2.toApacheQuaternion()).toQiQuaternion()
}

operator fun Quaternion.times(alpha: Double): Quaternion {
    return toApacheQuaternion().multiply(alpha).toQiQuaternion()
}

operator fun Quaternion.times(q2: Quaternion): Quaternion {
    return toApacheQuaternion().multiply(q2.toApacheQuaternion()).toQiQuaternion()
}

operator fun Quaternion.times(v: Vector3): Vector3 {
    return toApacheRotation().applyInverseTo(v.toApacheVector3D()).toQiVector3()
}

fun Quaternion.getRotatedXAxis(): Vector3 {
    return this * Vector3(1.0, 0.0, 0.0)
}

fun Quaternion.getRotatedYAxis(): Vector3 {
    return this * Vector3(0.0, 1.0, 0.0)
}

fun Quaternion.getRotatedZAxis(): Vector3 {
    return this * Vector3(0.0, 0.0, 1.0)
}