package com.softbankrobotics.dx.pepperextras.actuation

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAt
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.geometry.IDENTITY_TRANSFORM
import com.softbankrobotics.dx.pepperextras.geometry.toApacheRotation
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.atan


class ExtraLookAt internal constructor(
    private val qiContext: QiContext,
    private val frame: Frame,
    private val lookAt: LookAt,
    private val terminationPolicy: TerminationPolicy,
    private val alignedThreshold: Double = 0.1,
    private val minDisplacementAngleRad: Double = 0.04 // 2.3 degrees
): LookAt by lookAt {

    private val ACTION_NAME = javaClass.simpleName

    interface OnStatusChangedListener {
        fun onStatusChanged(status: LookAtStatus)
    }

    enum class LookAtStatus {
        NOT_STARTED,
        LOOKING_AT,
        NOT_LOOKING_AT,
        NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE
    }

    enum class TerminationPolicy {
        RUN_FOREVER,
        TERMINATE_WHEN_LOOKING_AT,
        TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE
    }

    override fun run() {
        FutureUtils.get(async().run())
    }

    fun resetStatus() {
        FutureUtils.get(async().resetStatus())
    }

    fun addOnStatusChangedListener(listener: OnStatusChangedListener) {
        FutureUtils.get(async().addOnStatusChangedListener(listener))
    }

    fun removeOnStatusChangedListener(listener: OnStatusChangedListener) {
        FutureUtils.get(async().removeOnStatusChangedListener(listener))
    }

    fun removeAllOnStatusChangedListeners() {
        FutureUtils.get(async().removeAllOnStatusChangedListeners())
    }

    override fun removeAllOnStartedListeners() {
        FutureUtils.get(async().removeAllOnStartedListeners())
    }

    private val _asyncInstance = Async()
    override fun async(): Async {
        return _asyncInstance
    }

    inner class Async internal constructor(): LookAt.Async by lookAt.async() {

        private val TAG = ACTION_NAME
        private val listeners = mutableListOf<OnStatusChangedListener>()
        private var runningFuture: Future<Void>? = null
        private var cancelledBecauseShouldTerminate = false

        private var lookAtStatus = LookAtStatus.NOT_STARTED
            private set(value) {
                // Set the status of the lookat only if it has changed
                if (field != value) {
                    Log.d(TAG, "LookAt status change to $value")
                    field = value
                    if (value != LookAtStatus.NOT_STARTED) {
                        // Call all the onStatusChanged listeners
                        listeners.forEach {
                            try {
                                Log.d(TAG, "LookAt calling callback")
                                it.onStatusChanged(field)
                                Log.d(TAG, "LookAt callback called")
                            } catch (e: java.lang.Exception) {
                                Log.e(TAG, "Uncaught exception in onLookingAt: $e")
                            }
                        }
                    }
                    // Stop the lookAt if needed
                    if (shouldTerminate()) {
                        cancelledBecauseShouldTerminate = true
                        runningFuture?.requestCancellation()
                    }
                }
            }

        // Check the TerminationPolicy and the lookAtStatus and return true if lookAt should stop.
        private fun shouldTerminate(): Boolean {
            return (terminationPolicy == TerminationPolicy.TERMINATE_WHEN_LOOKING_AT
                    && lookAtStatus == LookAtStatus.LOOKING_AT)
                    || (terminationPolicy == TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE
                    && (lookAtStatus == LookAtStatus.LOOKING_AT || lookAtStatus == LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE))
        }

        override fun run(): Future<Void> {
            return run (SingleThread.GlobalScope)
        }

        fun run(scope: CoroutineScope): Future<Void> {
            Log.d(TAG, "Action starting")
            coroutineScope = scope
            val promise = Promise<Void>()
            val future = lookAt.async().run()
            future.thenConsume {
                monitorFuture?.requestCancellation()
                if (future.isSuccess) {
                    promise.setValue(null)
                } else if (future.hasError()) {
                    promise.setError(future.errorMessage)
                } else {
                    // Promise cancelled
                    if (cancelledBecauseShouldTerminate)
                        promise.setValue(null)
                    else
                        promise.setCancelled()
                }
            }
            runningFuture = future
            promise.setOnCancel {
                future.requestCancellation()
            }
            return promise.future
        }

        private var reseted = false
        fun resetStatus(): Future<Unit> = SingleThread.GlobalScope.asyncFuture(CoroutineName(ACTION_NAME)) {
            lookAtStatus = LookAtStatus.NOT_STARTED
            reseted = true
        }


        fun addOnStatusChangedListener(listener: OnStatusChangedListener): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName(ACTION_NAME)) {
            listeners.add(listener)
            Unit
        }

        fun removeOnStatusChangedListener(listener: OnStatusChangedListener): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName(ACTION_NAME))  {
            listeners.remove(listener)
            Unit
        }

        fun removeAllOnStatusChangedListeners(): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName(ACTION_NAME))  {
            listeners.clear()
        }

        private var monitorFuture: Future<Unit>? = null
        private lateinit var coroutineScope: CoroutineScope

        val onStartedListener = LookAt.OnStartedListener {
            monitorFuture = coroutineScope.asyncFuture(CoroutineName("$ACTION_NAME|Monitor")) {
                monitorRobotState()
            }
        }

        init {
            lookAt.addOnStartedListener(onStartedListener)
        }

        override fun removeAllOnStartedListeners(): Future<Void> {
            return lookAt.async().removeAllOnStartedListeners().andThenCompose {
                lookAt.async().addOnStartedListener(onStartedListener)
            }
        }

        private lateinit var gazeFrame: Frame
        private lateinit var robotFrame: Frame

        private suspend fun initFrames() {
            if (!::gazeFrame.isInitialized || !::robotFrame.isInitialized) {
                gazeFrame = qiContext.actuation.async().gazeFrame().await()
                robotFrame = qiContext.actuation.async().robotFrame().await()
            }
        }

        private suspend fun isRobotLookingAtFrame(): Boolean {
            val t = frame.async().computeTranslation(gazeFrame).await()
            val gazeAndTargetAligned =
                    t.x >= 0 && atan(abs(t.y / t.x)) < alignedThreshold && atan(abs(t.z / t.x)) < alignedThreshold
            Log.d(TAG, "isRobotLookingAtFrame: Gaze aligned: $gazeAndTargetAligned; " +
                    "Gaze to target angle: Gaze to target translation: $t")
            if (gazeAndTargetAligned && lookAt.policy == LookAtMovementPolicy.HEAD_AND_BASE) {
                // We should wait for body to be aligned
                val tb = frame.async().computeTranslation(robotFrame).await()
                val bodyAndTargetAligned = tb.x >= 0 && atan(abs(tb.y / tb.x)) < alignedThreshold
                Log.d(TAG, "isRobotLookingAtFrame: Body aligned: $bodyAndTargetAligned; " +
                        "Body to target translation: $tb")
                return bodyAndTargetAligned
            } else
                return gazeAndTargetAligned
        }

        private suspend fun hasTheRobotMoved(robotFrameSnapshot: Frame, gazeFrameSnapshot: Frame): Boolean {
            val robotRotationAngle = robotFrame.async().computeTransform(robotFrameSnapshot).await()
                .transform.rotation.toApacheRotation().angle
            val gazeRotationAngle = gazeFrame.async().computeTransform(gazeFrameSnapshot).await()
                .transform.rotation.toApacheRotation().angle
            val result = (robotRotationAngle > minDisplacementAngleRad)
                    || (gazeRotationAngle > minDisplacementAngleRad)
            Log.d(TAG, "hasTheRobotMoved: $result (robotRotationAngle: $robotRotationAngle; " +
                    "gazeRotationAngle: $gazeRotationAngle)")
            return result
        }

        private var robotIsMoving = false

        private suspend fun monitorRobotState() {
            initFrames()
            if (isRobotLookingAtFrame()) {
                lookAtStatus = LookAtStatus.LOOKING_AT
            } else {
                lookAtStatus = LookAtStatus.NOT_LOOKING_AT
            }
            val robotFrameSnapshot = qiContext.mapping.async().makeFreeFrame().await()
            val gazeFrameSnapshot = qiContext.mapping.async().makeFreeFrame().await()
            loop@while (true) {
                robotFrameSnapshot.async().update(robotFrame, IDENTITY_TRANSFORM, 0).await()
                gazeFrameSnapshot.async().update(gazeFrame, IDENTITY_TRANSFORM, 0).await()
                delay(200)
                robotIsMoving = hasTheRobotMoved(robotFrameSnapshot.async().frame().await(),
                        gazeFrameSnapshot.async().frame().await())
                if (reseted) {
                    reseted = false
                    delay(200)
                    continue@loop
                }
                lookAtStatus = when {
                    isRobotLookingAtFrame() -> LookAtStatus.LOOKING_AT
                    robotIsMoving -> LookAtStatus.NOT_LOOKING_AT
                    else -> LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE
                }

                yield()
            }
        }
    }
}
