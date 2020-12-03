package com.softbankrobotics.dx.pepperextras.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D


fun Frame.distance(other: Frame): Double {
    return FutureUtils.get(async().distance(other))
}

fun Frame.computeTranslation(other: Frame): Vector3 {
    return FutureUtils.get(async().computeTranslation(other))
}

fun Frame.Async.distance(other: Frame): Future<Double> {
    return computeTranslation(other).andThenApply {
        it.toApacheVector3D().norm
    }
}

fun Frame.Async.computeTranslation(other: Frame): Future<Vector3> {
    return this.computeTransform(other).andThenApply {
        it.transform.translation
    }
}