package com.softbankrobotics.dx.pepperextras.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.Mapping
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.geometry.IDENTITY_TRANSFORM


fun Mapping.makeDetachedFrame(base: Frame, transform: Transform = IDENTITY_TRANSFORM, time: Long = 0): Frame {
    return FutureUtils.get(async().makeDetachedFrame(base, transform, time))
}

/**
 * Create a detached frame (free frame), from a base frame and an optional transform.
 * @param base The base frame from which to create the new frame
 * @param transform (Optional) Transform to apply to the base frame to create the new detached frame.
 * @return A new Frame, corresponding to the transform applied to the base frame.
 */
fun Mapping.Async.makeDetachedFrame(base: Frame, transform: Transform = IDENTITY_TRANSFORM, time: Long = 0): Future<Frame> {
    return makeFreeFrame().andThenCompose { freeFrame ->
        freeFrame.async().update(base, transform, time).andThenCompose {
            freeFrame.async().frame()
        }
    }
}

fun Mapping.getFrameAtTimestamp(frame: Frame, timestamp: Long): Frame {
    return FutureUtils.get(async().getFrameAtTimestamp(frame, timestamp))
}

// Return a copy of the frame at the given timestamp. Can be used to get robotFrame or gazeFrame at
// a certain timestamp.
fun Mapping.Async.getFrameAtTimestamp(frame: Frame, timestamp: Long): Future<Frame> {
    return makeFreeFrame().andThenCompose { freeFrameAtTimestamp ->
        val noTransform = TransformBuilder.create().fromXTranslation(0.0)
        freeFrameAtTimestamp.async().update(frame, noTransform, timestamp).andThenCompose {
            freeFrameAtTimestamp.async().frame()
        }
    }
}