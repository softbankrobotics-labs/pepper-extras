package com.softbankrobotics.dx.pepperextras

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.util.QiSDKTestActivity
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.dx.pepperextras.util.withRobotFocus
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtraLookAtTest {

    @get:Rule
    val activityRule = ActivityTestRule(QiSDKTestActivity::class.java)

    @Test
    fun testLookAtOnStartedListenerStillWorks() = withRobotFocus(activityRule.activity) { qiContext ->

        val freeFrame = qiContext.mapping.makeFreeFrame()
        freeFrame.update(qiContext.actuation.robotFrame(),
            TransformBuilder.create().from2DTranslation(2.0, 3.0), 0)
        val targetFrame = freeFrame.frame()

        val dxLookAt = ExtraLookAtBuilder.with(qiContext).withFrame(targetFrame).build()
        var lookAtFuture: Future<Void>? = null
        dxLookAt.addOnStatusChangedListener(object : ExtraLookAt.OnStatusChangedListener {
            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                when (status) {
                    ExtraLookAt.LookAtStatus.NOT_STARTED -> {}
                    ExtraLookAt.LookAtStatus.LOOKING_AT -> {
                        lookAtFuture?.cancel(true)
                    }
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT -> {}
                    ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE -> {
                        lookAtFuture?.cancel(true)
                    }
                }
            }
        })
        dxLookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
        var onStartedCalled = false
        dxLookAt.removeAllOnStartedListeners()
        dxLookAt.addOnStartedListener {
            onStartedCalled = true
        }
        lookAtFuture = dxLookAt.async().run()
        try {
            runBlocking {
                withTimeout(5000) {
                    Assert.assertNull(lookAtFuture.awaitOrNull())
                }
            }
        } catch (e: TimeoutCancellationException) {
            Assert.assertTrue(false)
        }
        Assert.assertTrue(onStartedCalled)
    }
}