package com.softbankrobotics.dx.pepperextras.geometry

import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.TransformBuilder
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.MatrixUtils


val IDENTITY_TRANSFORM: Transform = TransformBuilder.create().fromXTranslation(0.0)

operator fun Transform.times(t2: Transform): Transform {
    return Transform(
        rotation * t2.rotation,
        translation + (rotation * t2.translation)
    )
}

fun Transform.inverse(): Transform {
    val r = rotation.toApacheRotation().matrix
    val t = translation
    val matrix = Array2DRowRealMatrix(4, 4)
    matrix.setSubMatrix(Array2DRowRealMatrix(r).transpose().data, 0, 0)
    matrix.setColumn(3, doubleArrayOf(t.x, t.y, t.z, 1.0))
    matrix.setRow(3, doubleArrayOf(0.0, 0.0, 0.0, 1.0))
    val inv = MatrixUtils.inverse(matrix)
    val tinv = inv.getColumn(3)
    val minv = inv.getSubMatrix(0, 2, 0, 2).data
    return Transform(
        Rotation(Array2DRowRealMatrix(minv).transpose().data, 0.1).toQiQuaternion(),
        Vector3(tinv[0], tinv[1], tinv[2])
    )
}
