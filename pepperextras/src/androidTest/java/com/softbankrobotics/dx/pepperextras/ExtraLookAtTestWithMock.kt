package com.softbankrobotics.dx.pepperextras

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.TransformTime
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.geometry.toQiQuaternion
import com.softbankrobotics.dx.pepperextras.util.QiSDKTestActivity
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.dx.pepperextras.util.withRobotFocus
import io.mockk.*
import kotlinx.coroutines.*
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

fun makeRotationTransformAroundZ(angle: Double): Transform {
    val zAxis = Vector3D(0.0, 0.0, 1.0)
    return TransformBuilder.create().fromRotation(
        Rotation(zAxis, angle, RotationConvention.FRAME_TRANSFORM).toQiQuaternion()
    )
}

fun makeTranslationTransform(x: Double, y: Double, z: Double): Transform {
    return TransformBuilder.create().fromTranslation(Vector3(x, y, z))
}

class LookAtMock: LookAt by mockk() {
    val onStartedListener = slot<LookAt.OnStartedListener>()
    init {
        every { addOnStartedListener(capture(onStartedListener)) } just Runs
    }

    val policy = slot<LookAtMovementPolicy>()
    init {
        every { setPolicy(capture(policy)) } just Runs
        every { getPolicy() } answers {
            if (policy.isCaptured)
                policy.captured
            else
                LookAtMovementPolicy.HEAD_AND_BASE
        }
    }

    override fun async() = object : LookAt.Async by mockk() {
        init {
            every { run() } answers {
                if (onStartedListener.isCaptured)
                    onStartedListener.captured.onStarted()
                // Run never finishes, so return a neverending future.
                val p = Promise<Void>()
                p.setOnCancel { p.setCancelled() }
                p.future
            }
        }
    }
}
class FrameMock(val asyncBlock: (Frame.Async.() -> Unit)? = null): Frame by mockk() {
    override fun async() = object : Frame.Async by mockk() {
        init {
            asyncBlock?.invoke(this)
        }
    }
}
class FreeFrameMock(val actualframe: Frame): FreeFrame by mockk() {
    override fun async() = object : FreeFrame.Async by mockk() {
        init {
            every { update(any(), any(), any()) } returns Future.of(null)
            every { frame() } returns Future.of(actualframe)
        }
    }
}


@RunWith(AndroidJUnit4::class)
class ExtraLookAtTestWithMock {

    lateinit var robotDisplacementTransform: TransformTime
    lateinit var gazeDisplacementTransform: TransformTime
    lateinit var robotToTargetTransform: TransformTime
    lateinit var gazeToTargetTransform: TransformTime
    lateinit var targetFrame: Frame
    lateinit var qiContextMock: QiContext

    @get:Rule
    val activityRule = ActivityTestRule(QiSDKTestActivity::class.java)

    fun setUpTest(qiContext: QiContext) {
        robotDisplacementTransform = TransformTime(makeRotationTransformAroundZ(0.1), 0)
        gazeDisplacementTransform = TransformTime(makeRotationTransformAroundZ(0.1), 0)
        robotToTargetTransform = TransformTime(makeTranslationTransform(1.0, 1.0, 0.0), 0)
        gazeToTargetTransform = TransformTime(makeTranslationTransform(1.0, 1.0, 0.0), 0)

        val freeFrameFrame = FrameMock()
        val freeFrame = FreeFrameMock(freeFrameFrame)

        val robotFrame = FrameMock {
            every { computeTransform(freeFrameFrame) } returns Future.of(robotDisplacementTransform)
        }
        val gazeFrame = FrameMock {
            every { computeTransform(freeFrameFrame) } returns Future.of(gazeDisplacementTransform)
        }
        targetFrame = FrameMock {
            every { computeTransform(robotFrame) } returns Future.of(robotToTargetTransform)
            every { computeTransform(gazeFrame) } returns Future.of(gazeToTargetTransform)
        }
        val lookAt = LookAtMock()//mockkLookAt()

        qiContextMock = spyk(qiContext) {
            every { actuation } returns mockk<Actuation> {
                every { async() } returns mockk<Actuation.Async> {
                    every { makeLookAt(any(), any()) } returns Future.of(lookAt)
                    every { robotFrame() } returns Future.of(robotFrame)
                    every { gazeFrame() } returns Future.of(gazeFrame)
                }
            }
            every { mapping } returns mockk<Mapping> {
                every { async() } returns mockk<Mapping.Async> {
                    every { makeFreeFrame() } returns Future.of(freeFrame)
                }
            }
        }
    }

    @Test
    fun testLookingAtStatusTriggeredWhenLookingWithHeadAndBase() =
        withRobotFocus(activityRule.activity) { qiContext ->
            setUpTest(qiContext)

            val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
            var lookAtFuture: Future<Void>? = null
            var gotLookingAtStatus = false
            dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
                override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                    when (status) {
                        ExtraLookAt.LookAtStatus.NOT_STARTED -> {}
                        ExtraLookAt.LookAtStatus.LOOKING_AT -> {
                            gotLookingAtStatus = true
                            lookAtFuture?.requestCancellation()
                        }
                        ExtraLookAt.LookAtStatus.NOT_LOOKING_AT -> {}
                        ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE -> { }
                    }
                }
            })
            dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
            lookAtFuture = dxLookAt.async().run()

            runBlocking {
                val job = launch {
                    delay(1000)
                    // Simulate robot is looking toward the target
                    gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                    delay(1000)
                    // Simulate base is looking toward the target
                    robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                }
                val time = measureTimeMillis {
                    Assert.assertNull(lookAtFuture.awaitOrNull())
                    Assert.assertTrue(gotLookingAtStatus)
                    // We cancelled the future
                    Assert.assertTrue(lookAtFuture.isCancelled)
                }
                job.cancelAndJoin()
                Assert.assertTrue(time >= 2 * 1000)
                Assert.assertTrue(time <= 2.5 * 1000)
            }
        }

    @Test
    fun testLookAtStopWhenLookingWithHeadAndBase()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock)
                .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT)
                .withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
        val lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(1000)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                delay(1000)
                // Simulate base is looking toward the target
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
            }
            val time = measureTimeMillis {
                Assert.assertNull(lookAtFuture.awaitOrNull())
                // Lookat terminated successfully because of TerminationPolicy
                Assert.assertTrue(lookAtFuture.isSuccess)
            }
            job.cancelAndJoin()
            Assert.assertTrue(time >= 2 * 1000)
            Assert.assertTrue(time <= 2.5 * 1000)
        }
    }

    @Test
    fun testLookAtStopWhenNotMovingAnymore()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock)
                .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE)
                .withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
        val lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(500)
                // Simulate robot stops moving
                robotDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                gazeDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
            }
            Assert.assertNull(lookAtFuture.awaitOrNull())
            // Lookat terminated successfully because of TerminationPolicy
            Assert.assertTrue(lookAtFuture.isSuccess)
            job.cancelAndJoin()
        }
    }

    @Test
    fun testLookingAtStatusTriggeredWhenLookingWithHeadOnly()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
        var lookAtFuture: Future<Void>? = null
        var gotLookingAtStatus = false
        dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                when (status) {
                    ExtraLookAt.LookAtStatus.NOT_STARTED -> {}
                    ExtraLookAt.LookAtStatus.LOOKING_AT -> {
                        gotLookingAtStatus = true
                        lookAtFuture?.requestCancellation()
                    }
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT -> {}
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE -> {}
                }
            }
        })
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(1000)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                delay(1000)
                // Simulate base is looking toward the target
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
            }
            val time = measureTimeMillis {
                Assert.assertNull(lookAtFuture.awaitOrNull())
                Assert.assertTrue(gotLookingAtStatus)
                // We cancelled the future
                Assert.assertTrue(lookAtFuture.isCancelled)
            }
            job.cancelAndJoin()
            Assert.assertTrue(time >= 1 * 1000)
            Assert.assertTrue(time <= 1.5 * 1000)
        }
    }

    @Test
    fun testLookAtStopWhenLookingWithHeadOnly()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock)
                .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT)
                .withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        val lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(1000)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                delay(1000)
                // Simulate base is looking toward the target
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
            }
            val time = measureTimeMillis {
                Assert.assertNull(lookAtFuture.awaitOrNull())
                // Lookat terminated successfully because of TerminationPolicy
                Assert.assertTrue(lookAtFuture.isSuccess)
            }
            job.cancelAndJoin()
            Assert.assertTrue(time >= 1 * 1000)
            Assert.assertTrue(time <= 1.5 * 1000)
        }
    }

    @Test
    fun testCannotLookAtStatusTriggeredWhenRobotDoesNotMoveAnymore()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
        var lookAtFuture: Future<Void>? = null
        var gotCannotLookAtStatus = false
        dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                when (status) {
                    ExtraLookAt.LookAtStatus.NOT_STARTED -> {}
                    ExtraLookAt.LookAtStatus.LOOKING_AT -> {}
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT -> {}
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE -> {
                        gotCannotLookAtStatus = true
                        lookAtFuture?.requestCancellation()
                    }
                }
            }
        })
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(500)
                // Simulate robot is not moving anymore
                robotDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                gazeDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
            }
            val time = measureTimeMillis {
                Assert.assertNull(lookAtFuture.awaitOrNull())
                Assert.assertTrue(gotCannotLookAtStatus)
                // We cancelled the future
                Assert.assertTrue(lookAtFuture.isCancelled)
            }
            job.cancelAndJoin()
            Assert.assertTrue(time >= 0.5 * 1000)
            Assert.assertTrue(time <= 1.0 * 1000)
        }
    }

    @Test
    fun testLookAtStopWhenCancelled() = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        val lookAtFuture = dxLookAt.async().run()

        runBlocking {
            val job = launch {
                delay(500)
                lookAtFuture.requestCancellation()
            }
            val time = measureTimeMillis {
                Assert.assertNull(lookAtFuture.awaitOrNull())
                // We cancelled the future
                Assert.assertTrue(lookAtFuture.isCancelled)
            }
            job.cancelAndJoin()
            Assert.assertTrue(time >= 0.5 * 1000)
            Assert.assertTrue(time <= 1.0 * 1000)
        }
    }

    @Test
    fun testLookAtStatusListener()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        // Create a lookAt that run forever and look at targetFrame
        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        val lookAtFuture = dxLookAt.async().run()

        // Everytime the onLookingAt listerner is called, save the status
        var lookAtStatus = ExtraLookAt.LookAtStatus.NOT_STARTED
        dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                lookAtStatus = status
            }
        })

        runBlocking {
            // Simulate the robot lookat / don't lookat, then see if listerner is always called
            val job = launch {
                delay(500)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                delay(500)
                Assert.assertEquals(lookAtStatus, ExtraLookAt.LookAtStatus.LOOKING_AT)
                // Simulate robot is not looking anymore at target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 1.0, 0.0)
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 1.0, 0.0)
                delay(500)
                Assert.assertEquals(lookAtStatus, ExtraLookAt.LookAtStatus.NOT_LOOKING_AT)
                // Simulate robot is not moving anymore
                robotDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                gazeDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                delay(500)
                Assert.assertEquals(lookAtStatus, ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE)
                robotDisplacementTransform.transform = makeRotationTransformAroundZ(1.0)
                gazeDisplacementTransform.transform = makeRotationTransformAroundZ(1.0)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.0)
                delay(500)
                Assert.assertEquals(lookAtStatus, ExtraLookAt.LookAtStatus.LOOKING_AT)
                lookAtFuture.requestCancellation()
            }
            Assert.assertNull(lookAtFuture.awaitOrNull())
            job.cancelAndJoin()
        }
    }

    @Test
    fun testLookAtCorrectStatus()
            = withRobotFocus(activityRule.activity) { qiContext ->
        setUpTest(qiContext)

        // Create a lookAt that run forever and look at targetFrame
        val dxLookAt = ExtraLookAtBuilder.with(qiContextMock).withFrame(targetFrame).build()
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_ONLY)
        val lookAtFuture = dxLookAt.async().run()

        // Everytime the onLookingAt listerner is called, save the status and count
        var lookAtStatus = ExtraLookAt.LookAtStatus.NOT_STARTED
        var counter = 0
        dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                lookAtStatus = status
                counter += 1
            }
        })

        runBlocking {
            // Simulate the robot lookat / don't lookat, then see if listerner is always called
            val job = launch {
                delay(500)
                // Simulate robot is looking toward the target
                gazeToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.05)
                robotToTargetTransform.transform = makeTranslationTransform(1.0, 0.0, 0.05)
                // And not moving anymore
                robotDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                gazeDisplacementTransform.transform = makeRotationTransformAroundZ(0.0)
                delay(1000)
                Assert.assertEquals(ExtraLookAt.LookAtStatus.LOOKING_AT, lookAtStatus)
                Assert.assertEquals(2, counter)
                lookAtFuture.requestCancellation()
            }
            Assert.assertNull(lookAtFuture.awaitOrNull())
            job.cancelAndJoin()
        }
    }
}
