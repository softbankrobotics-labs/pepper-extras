package com.softbankrobotics.dx.pepperextras.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.OrientationPolicy
import com.aldebaran.qi.sdk.util.FutureUtils

class StubbornGoToBuilder private constructor(val qiContext: QiContext) {

    private val config = StubbornGoTo.Config()
    private lateinit var frame: Frame

    fun withFrame(frame: Frame): StubbornGoToBuilder {
        this.frame = frame
        return this
    }

    fun withMaxRetry(maxRetry: Int): StubbornGoToBuilder {
        check(maxRetry >= 1, { "MaxRetry must be positive and greater than 1."})
        this.config.maxRetry = maxRetry
        return this
    }

    fun withMaxDistanceFromTargetFrame(maxDistanceFromTargetFrame: Double): StubbornGoToBuilder {
        check(maxDistanceFromTargetFrame >= 0.0, { "MaxDistanceFromTargetFrame must be positive."})
        this.config.targetFrameMaxDistance = maxDistanceFromTargetFrame
        return this
    }

    fun withMaxSpeed(maxSpeed: Float): StubbornGoToBuilder {
        check(maxSpeed >= 0.0, { "MaxSpeed must be positive."})
        this.config.maxSpeed = maxSpeed
        return this
    }

    fun withFinalOrientationPolicy(finalOrientationPolicy: OrientationPolicy): StubbornGoToBuilder {
        this.config.finalOrientationPolicy = finalOrientationPolicy
        return this
    }

    fun withWalkingAnimationEnabled(walkingAnimationEnabled: Boolean): StubbornGoToBuilder {
        this.config.walkingAnimationEnabled = walkingAnimationEnabled
        return this
    }

    companion object {
        fun with(qiContext: QiContext): StubbornGoToBuilder {
            return StubbornGoToBuilder(qiContext)
        }
    }

    fun build(): StubbornGoTo {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<StubbornGoTo> {
        check(::frame.isInitialized) { "Frame required." }
        return Future.of(StubbornGoTo(qiContext, frame, config))
    }
}