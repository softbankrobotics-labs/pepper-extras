package com.softbankrobotics.dx.pepperextras.util

import android.app.Activity
import android.os.Bundle
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.util.FutureUtils

fun <T, R> withRobotFocus(activity: T, block: (QiContext) -> R) where T: QiSDKTestActivity {
    val promise = Promise<Void>()
    activity.setListener(object : RobotLifecycleCallbacks {
        override fun onRobotFocusGained(qiContext: QiContext) {
            block(qiContext)
            promise.setValue(null)
        }

        override fun onRobotFocusLost() {
            promise.setError("Focus lost")
        }

        override fun onRobotFocusRefused(reason: String) {
            promise.setError("Focus refused: $reason")
        }
    })
    FutureUtils.get(promise.future)
}

open class QiSDKTestActivity: Activity(), RobotLifecycleCallbacks {

    private enum class FocusState {
        UNKNOWN,
        OWNED,
        LOST,
        REFUSED
    }

    private var qiContext: QiContext? = null
    private var listener: RobotLifecycleCallbacks? = null
    private var focusState: FocusState =
        FocusState.UNKNOWN
    private var reason = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
    }

    override fun onDestroy() {
        QiSDK.unregister(this)
        super.onDestroy()
    }

    fun setListener(listener: RobotLifecycleCallbacks) {
        this.listener = listener
        when (focusState) {
            FocusState.UNKNOWN -> {}
            FocusState.OWNED -> { listener.onRobotFocusGained(qiContext) }
            FocusState.LOST -> { listener.onRobotFocusLost() }
            FocusState.REFUSED -> { listener.onRobotFocusRefused(reason) }
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        focusState = FocusState.OWNED
        listener?.onRobotFocusGained(qiContext)
    }

    override fun onRobotFocusLost() {
        this.qiContext = null
        focusState = FocusState.LOST
        listener?.onRobotFocusLost()
    }

    override fun onRobotFocusRefused(reason: String) {
        this.reason = reason
        focusState = FocusState.REFUSED
        listener?.onRobotFocusRefused(reason)
    }
}