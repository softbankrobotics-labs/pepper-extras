package com.softbankrobotics.dx.pepperextras.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.util.FutureUtils

class ExtraLookAtBuilder internal constructor(val qiContext: QiContext) {

    private lateinit var frame: Frame
    private var terminationPolicy = ExtraLookAt.TerminationPolicy.RUN_FOREVER
    private var builder: LookAtBuilder = LookAtBuilder.with(qiContext)

    fun withFrame(frame: Frame): ExtraLookAtBuilder {
        this.frame = frame
        builder = builder.withFrame(frame)
        return this
    }

    fun withTerminationPolicy(terminationPolicy: ExtraLookAt.TerminationPolicy): ExtraLookAtBuilder {
        this.terminationPolicy = terminationPolicy
        return this
    }

    companion object {
        fun with(qiContext: QiContext): ExtraLookAtBuilder {
            return ExtraLookAtBuilder(qiContext)
        }
    }

    fun build(): ExtraLookAt {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<ExtraLookAt> {
        check(::frame.isInitialized) { "Frame required." }
        return builder.buildAsync().andThenApply { lookAt ->
            ExtraLookAt(qiContext, frame, lookAt, terminationPolicy)
        }
    }
}