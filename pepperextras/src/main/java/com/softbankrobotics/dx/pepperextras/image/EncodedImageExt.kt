package com.softbankrobotics.dx.pepperextras.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aldebaran.qi.sdk.`object`.image.EncodedImage

fun EncodedImage.toBitmap(): Bitmap {
    val buffer = data
    buffer.rewind()
    val pictureBufferSize = buffer.remaining()
    val pictureArray = ByteArray(pictureBufferSize)
    buffer.get(pictureArray)
    return BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize)
}
