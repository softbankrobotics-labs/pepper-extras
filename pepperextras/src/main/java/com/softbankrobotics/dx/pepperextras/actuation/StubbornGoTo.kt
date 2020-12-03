package com.softbankrobotics.dx.pepperextras.actuation

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.OrientationPolicy
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.GoToBuilder
import com.softbankrobotics.dx.pepperextras.R
import com.softbankrobotics.dx.pepperextras.actuation.internal.RunnableAction
import com.softbankrobotics.dx.pepperextras.geometry.IDENTITY_TRANSFORM
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.*
import kotlin.math.atan2

class StubbornGoTo internal constructor(
    private val qiContext: QiContext,
    private val targetFrame: Frame,
    private val config: Config
): RunnableAction<Boolean, StubbornGoTo.Async>() {

    data class Config(
            var targetFrameMaxDistance: Double = 0.3,
            var walkingAnimationEnabled: Boolean = false,
            var finalOrientationPolicy: OrientationPolicy? = null,
            var maxSpeed: Float? = null,
            var tryWithGotoTimeoutMs: Long = 30 * 1000,
            var maxRetry: Int = 10
    )

    override val _asyncInstance = Async()

    inner class Async internal constructor(): RunnableAction<Boolean, StubbornGoTo.Async>.Async() {

        private var retry = 1

        override fun _run(scope: CoroutineScope): Future<Boolean>
                = scope.asyncFuture(CoroutineName(ACTION_NAME)) {
            val robotFrame = qiContext.actuation.async().robotFrame().await()
            var walkingMonitorJob: Job? = null
            if (config.walkingAnimationEnabled) {
                walkingMonitorJob = launch {
                    val walkingAnimation = initWalkingAnimation()
                    var walkingFuture = Future.cancelled<Void>()
                    monitorRobotWalking { isWalking ->
                        if (isWalking)
                            walkingFuture = walkingAnimation.async().run()
                        else {
                            walkingFuture.requestCancellation()
                            walkingFuture.awaitOrNull()
                        }
                    } }
            }
            retry = 1
            var success = false
            while (retry <= config.maxRetry && !success) {
                success = tryWithGoTo()
                if (!success && retry >= 5)
                    success = tryWithGoStraightTo(robotFrame)
                if (!success && retry >= 10)
                    success = isTargetIsCloseEnough(robotFrame, config.targetFrameMaxDistance)
                retry += 1
                yield()
            }
            if (config.walkingAnimationEnabled)
                walkingMonitorJob?.cancelAndJoin()
            success
        }

        private suspend fun tryWithGoStraightTo(robotFrame: Frame): Boolean {
            Log.d(TAG, "trying with tryWithGoStraightTo ($retry/${config.maxRetry})")
            try {
                turnToFrame(targetFrame).awaitOrNull()
                val translationToTarget =
                        targetFrame.async().computeTransform(robotFrame).await().transform.translation
                val time = translationToTarget.toApacheVector3D().norm / (config.maxSpeed ?: 0.5f)
                goStraightToPos(
                        translationToTarget.x,
                        translationToTarget.y,
                        0.0,
                        time.toFloat()
                ).awaitOrNull()
                if (config.finalOrientationPolicy == OrientationPolicy.ALIGN_X)
                    turnToFrame(targetFrame).awaitOrNull()
                return isTargetIsCloseEnough(robotFrame, 0.05)
            }
            catch (e: TimeoutCancellationException) {
                Log.d(TAG, "GoStraightTo failed (timeout)")
                return false
            }
            catch (e: Exception) {
                Log.d(TAG, "GoStraightTo failed ($e)")
                return false
            }
        }

        private suspend fun tryWithGoTo(): Boolean {
            Log.d(TAG, "trying with GoTo ($retry/${config.maxRetry})")
            try {
                withTimeout(config.tryWithGotoTimeoutMs) {
                    var builder = GoToBuilder.with(qiContext).withFrame(targetFrame)
                    config.finalOrientationPolicy?.let {
                        builder = builder.withFinalOrientationPolicy(it)
                    }
                    config.maxSpeed?.let { builder = builder.withMaxSpeed(it) }
                    builder.buildAsync().await()
                        .async().run().await()
                    Log.d(TAG, "GoTo succeeded")
                }
                return true
            }
            catch (e: TimeoutCancellationException) {
                Log.d(TAG, "GoTo failed (timeout)")
                return false
            }
            catch (e: Exception) {
                Log.d(TAG, "GoTo failed ($e)")
                return false
            }
        }

        private suspend fun isTargetIsCloseEnough(robotFrame: Frame, maxDistance: Double): Boolean {
            val distanceToTargetM = robotFrame.async().distance(targetFrame).awaitOrNull()
            Log.d(TAG, "Is target close enough: $distanceToTargetM")
            return distanceToTargetM != null && distanceToTargetM < maxDistance
        }

        private fun turnToFrame(frame: Frame): Future<Void> {
            return qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
                val toTarget = frame.computeTransform(robotFrame).transform.translation
                val rotationAngle = atan2(toTarget.y, toTarget.x) - atan2(0.0, 1.0)
                // time = angle * rayon / speed
                val time = (rotationAngle * 0.1) / (config.maxSpeed ?: 0.5f)
                goStraightToPos(0.0, 0.0, rotationAngle, time.toFloat())
            }
        }

        private fun goStraightToPos(x: Double, y: Double, theta: Double, time: Float): Future<Void> {
            val animationString = String.format(Locale.US, "[\"Holonomic\", [\"Line\", [%f, %f]], %f, %f]", x, y, theta, time)
            val animation = AnimationBuilder.with(qiContext).withTexts(animationString).build()
            val animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()
            return animate.async().run()
        }

        private suspend fun initWalkingAnimation(): Animate {
            val animation = AnimationBuilder.with(qiContext).withResources(R.raw.traveling)
                    .buildAsync().await()
            return AnimateBuilder.with(qiContext).withAnimation(animation).buildAsync().await()
        }

        private suspend fun monitorRobotWalking(speedThresholdKmH: Double = 0.3,
                                                onRobotWalking: suspend (Boolean) -> Unit)
                = coroutineScope {
            try {
                var robotIsMoving = false
                val robotFrame = qiContext.actuation.async().robotFrame().await()
                val robotFrameSnapshot = qiContext.mapping.async().makeFreeFrame().await()
                val robotFrameInPast = robotFrameSnapshot.async().frame().await()
                val period = 200L //ms
                while (isActive) {
                    robotFrameSnapshot.async().update(robotFrame, IDENTITY_TRANSFORM, 0).await()
                    delay(period)
                    val distance = robotFrame.async().distance(robotFrameInPast).await()
                    val speedkmh = distance * 3600.0 / period
                    if (speedkmh >= speedThresholdKmH && !robotIsMoving) {
                        robotIsMoving = true
                        onRobotWalking(true)
                    } else if (speedkmh < speedThresholdKmH && robotIsMoving) {
                        robotIsMoving = false
                        onRobotWalking(false)
                    }
                }
            } catch (c: CancellationException) {
                onRobotWalking(false)
            }
        }
    }
}
