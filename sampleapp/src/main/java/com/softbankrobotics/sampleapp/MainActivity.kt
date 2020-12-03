package com.softbankrobotics.sampleapp

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.LocalizeBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.dx.pepperextras.actuation.*
import com.softbankrobotics.dx.pepperextras.geometry.IDENTITY_TRANSFORM
import com.softbankrobotics.dx.pepperextras.geometry.inverse
import com.softbankrobotics.dx.pepperextras.geometry.times
import com.softbankrobotics.dx.pepperextras.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException


class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY)
        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)
    }

    override fun onDestroy() {
        QiSDK.unregister(this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained.")
        ExtraLookAtDoc_Example1(qiContext)
        ExtraLookAtDoc_Example2(qiContext)
        StubbornGoToDoc_Example(qiContext)
        ExplorationMapViewDoc_Example(qiContext)
        GeometryComputationDoc_Example1(qiContext)
        GeometryComputationDoc_Example2(qiContext)
        GeometryComputationDoc_Example3(qiContext)
        NewCoroutineScopeDoc_MonitorRobotSpeed(qiContext)
    }

    override fun onRobotFocusLost() {
        Log.w(TAG, "Robot focus lost.")
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "Robot focus refused because $reason.")
    }

    fun ExtraLookAtDoc_Example1(qiContext: QiContext) {

        // Compute a target frame to look at.
        // We create a frame 5 meters front of Pepper, 2 meter on its left
        val targetFrame: Frame = qiContext.mapping.makeFreeFrame().apply {
            update(qiContext.actuation.robotFrame(),
                TransformBuilder.create().from2DTranslation(5.0, 2.0), 0)
        }.frame()

        // Build the action.
        val extraLookAt: ExtraLookAt = ExtraLookAtBuilder.with(qiContext)
            .withFrame(targetFrame)
            .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE)
            .build().apply {
                policy = LookAtMovementPolicy.HEAD_ONLY
            }

        // Run the action synchronously, it terminates once Pepper is looking at, or if it can't
        // look at and does not move anymore.
        extraLookAt.run()

        // extraLookAt does not run anymore.

        // If BackgroundMovement or BasicAwareness are not held, Pepper head will come back to
        // neutral position, or to where BasicAwareness was looking at.
        // Hold BackgroundMovement and BasicAwareness if you want Pepper head stays where
        // extraLookAt put it.
    }

    fun ExtraLookAtDoc_Example2(qiContext: QiContext) {

        val transformOnTheLeft = TransformBuilder.create().from2DTranslation(5.0, 2.0)
        val transformOnTheRight = TransformBuilder.create().from2DTranslation(5.0, -2.0)
        val robotFrame = qiContext.actuation.robotFrame()

        // Create a FreeFrame
        val targetFreeFrame: FreeFrame = qiContext.mapping.makeFreeFrame()

        // Put it 5 meters front of Pepper, 2 meter on its left
        targetFreeFrame.update(robotFrame, transformOnTheLeft, 0)

        // Build the action.
        val extraLookAt: ExtraLookAt = ExtraLookAtBuilder.with(qiContext)
            .withFrame(targetFreeFrame.frame())
            .withTerminationPolicy(ExtraLookAt.TerminationPolicy.RUN_FOREVER)
            .build().apply {
                LookAtMovementPolicy.HEAD_AND_BASE
                policy = LookAtMovementPolicy.HEAD_ONLY
            }.apply {
                addOnStatusChangedListener(object: ExtraLookAt.OnStatusChangedListener {
                    override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                        Log.i("ExampleExtraLookAt", "ExtraLookAt status changes to: $status")
                    }
                })
            }

        // Run the action asynchronously
        Log.i("ExampleExtraLookAt", "Starting")
        val extraLookAtFuture: Future<Void> = extraLookAt.async().run()

        // Wait 3 seconds
        SystemClock.sleep(3000)

        // Now put the target frame on the right of Pepper
        Log.i("ExampleExtraLookAt", "Changing the target to the right")
        targetFreeFrame.update(robotFrame, transformOnTheRight, 0)

        // Wait 3 seconds
        SystemClock.sleep(3000)

        // Stop the ExtraLookAt
        extraLookAtFuture.requestCancellation()
    }

    fun StubbornGoToDoc_Example(qiContext: QiContext) {
        // Create a Frame 2 meter in front of the robot
        val frame2MeterInFront = qiContext.mapping.makeFreeFrame(). apply {
            update(qiContext.actuation.robotFrame(),
                TransformBuilder.create().fromXTranslation(3.0), 0)
        }.frame()

        // Create the StubbornGoTo action
        val goto = StubbornGoToBuilder.with(qiContext)
            .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
            .withMaxRetry(10)
            .withMaxSpeed(0.5f)
            .withMaxDistanceFromTargetFrame(0.3)
            .withWalkingAnimationEnabled(true)
            .withFrame(frame2MeterInFront).build()

        // And run int
        goto.run()
    }

    fun ExplorationMapViewDoc_Example(qiContext: QiContext) {

        // 0) Create a minimal map
        // -----------------------

        var localizeAndMapFuture: Future<Void>? = null

        // Create a localize and map
        val localizeAndMap = LocalizeAndMapBuilder.with(qiContext).build()

        // Stop it once the robot is localized
        localizeAndMap.addOnStatusChangedListener {
            if (it == LocalizationStatus.LOCALIZED) {
                localizeAndMapFuture?.requestCancellation()
            }
        }

        // Run the action asynchronously to get a future
        localizeAndMapFuture = localizeAndMap.async().run()

        // Wait for the future to be cancelled (it will be cancelled in the listener once the robot
        // is LOCALIZED)
        try {
            localizeAndMapFuture.get()
        } catch (e: CancellationException) {

        }

        // Retrieve the explorationMap
        val explorationMap = localizeAndMap.dumpMap()

        // 1) Display the map
        // ------------------

        // Pass the ExplorationMap to the ExplorationMapView, it will get displayed
        explorationMapView.setExplorationMap(explorationMap.topGraphicalRepresentation)

        // 2) Optionally, display the robot position in the map
        // ----------------------------------------------------

        // Create a localize
        val localize = LocalizeBuilder.with(qiContext).withMap(explorationMap).build()

        // Run asynchronously
        localize.async().run()

        // Retrieve the robot Frame
        val robotFrame = qiContext.actuation.robotFrame()

        // Retrieve the origin of the map Frame
        val mapFrame = qiContext.mapping.mapFrame()
        while (true) {
            // Compute the position of the robot relatively to the map Frame
            val robotPos = robotFrame.computeTransform(mapFrame).transform

            // Set the position in the ExplorationMapView widget, it will be displayed as a
            // red circle
            explorationMapView.setRobotPosition(robotPos)

            // Wait a little bit before updating again the robot position
            SystemClock.sleep(500)
        }
    }

    fun GeometryComputationDoc_Example1(qiContext: QiContext) {
        val robotFrame = qiContext.actuation.robotFrame()
        val gazeFrame = qiContext.actuation.gazeFrame()

        val distance: Double = robotFrame.distance(gazeFrame)
        val translation: Vector3 = robotFrame.computeTranslation(gazeFrame)
        val distanceFuture: Future<Double> = robotFrame.async().distance(gazeFrame)
        val translationFuture: Future<Vector3> = robotFrame.async().computeTranslation(gazeFrame)
    }

    fun GeometryComputationDoc_Example2(qiContext: QiContext) {
        val robotFrame = qiContext.actuation.robotFrame()
        val gazeFrame = qiContext.actuation.gazeFrame()

        // Compute the transform from gazeFrame to robotFrame
        val gazeToRobot = robotFrame.computeTransform(gazeFrame).transform

        // Taking the inverse of that transform, is equivalent to computing the transform between
        // robotFrame to gazeFrame:
        val robotToGaze1 = gazeToRobot.inverse()
        val robotToGaze2 = gazeFrame.computeTransform(robotFrame).transform

        // robotToGaze1 and robotToGaze2 are equivalent
    }

    fun GeometryComputationDoc_Example3(qiContext: QiContext) {

        val robotFrame = qiContext.actuation.robotFrame()
        val gazeFrame = qiContext.actuation.gazeFrame()

        // Multiplying transform is useful to change from coordinate system
        // Imagine there is the transform of an object expressed in gazeFrame coordinates,
        // with coordinate (1, 3) in gazeFrame
        val gazeToObject = TransformBuilder.create().from2DTranslation(1.0, 3.0)

        // Can we compute the coordinate of that object in robotFrame ?
        // Its easy, get the gazeToRobot transform
        val gazeToRobot = robotFrame.computeTransform(gazeFrame).transform
        val robotToObject = gazeToRobot.inverse() * gazeToObject

        Log.i("Geometry", robotToObject.toString())
    }

    fun NewCoroutineScopeDoc_MonitorRobotSpeed(qiContext: QiContext) {
        val appscope = SingleThread.newCoroutineScope()
        val future: Future<Unit> = appscope.asyncFuture {
            val robotFrame = qiContext.actuation.async().robotFrame().await()
            val robotFrameSnapshot = qiContext.mapping.async().makeFreeFrame().await()
            val robotFrameInPast = robotFrameSnapshot.async().frame().await()
            val period = 200L //ms
            while (isActive) {
                robotFrameSnapshot.async().update(robotFrame, IDENTITY_TRANSFORM, 0).await()
                delay(period)
                val distance = robotFrame.async().distance(robotFrameInPast).await()
                val speedkmh = distance * 3600.0 / period
                Log.i(TAG, "Robot is going at $speedkmh km/h")
            }
        }
        runBlocking {
            delay(60000)
            future.requestCancellation()
            future.awaitOrNull()
        }
    }
}





