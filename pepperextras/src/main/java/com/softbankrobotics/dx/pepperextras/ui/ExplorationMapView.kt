package com.softbankrobotics.dx.pepperextras.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.aldebaran.qi.sdk.`object`.actuation.MapTopGraphicalRepresentation
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.softbankrobotics.dx.pepperextras.actuation.mapToGraphicalCoordinates
import com.softbankrobotics.dx.pepperextras.geometry.toApacheRotation
import com.softbankrobotics.dx.pepperextras.image.toBitmap
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.math.min


open class ExplorationMapView(context: Context, attributeSet: AttributeSet)
    : View(context, attributeSet) {

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#808080"))

        explorationMapImage?.let {
            canvas.drawBitmap(it, null, targetDisplayRect, null)
        }
        robotCoordinates?.let {
            canvas.drawCircle(it.x, it.y, robotCircleSize.toFloat(), robotPaint)
            robotVector?.let { v ->
                canvas.drawLine(it.x, it.y, it.x + v.x, it.y + v.y, robotPaint)
            }
        }
    }

    // Map image to draw
    private var explorationMapImage: Bitmap? = null

    // Where to draw the map image on the screen
    private var targetDisplayRect: RectF = RectF()

    // The map graphical representation (useful to convert world coordinate to graphical coordinate)
    private var mapTopGraphicalRepresentation: MapTopGraphicalRepresentation? = null

    // How much the map image was scaled to fit the screen of Pepper tablet
    private var scaleFactor: Float = 1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        explorationMapImage?.let { img ->
            computeTargetDisplayRectAndScaleFactor(img, w, h)
            postInvalidate()
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun computeTargetDisplayRectAndScaleFactor(img: Bitmap, width: Int, height: Int) {
        scaleFactor = min(width / img.width.toDouble(), height / img.height.toDouble()).toFloat()
        val offsetX = (width - img.width.toDouble() * scaleFactor).toFloat() / 2
        val offsetY = (height - img.height.toDouble() * scaleFactor).toFloat() / 2
        targetDisplayRect = RectF(offsetX, offsetY, offsetX+img.width * scaleFactor,
            offsetY + img.height * scaleFactor)
    }

    fun setExplorationMap(map: MapTopGraphicalRepresentation) {
        mapTopGraphicalRepresentation = map
        val img = map.image.toBitmap()
        computeTargetDisplayRectAndScaleFactor(img, width, height)
        explorationMapImage = img
        clearRobotPosition()
        postInvalidate()
    }

    // The robot coordinates on the view
    private var robotCoordinates: PointF? = null

    // The robot orientation vector
    private var robotVector: PointF? = null

    // Paint for the robot
    protected val robotPaint =  Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Size of the circle representing the robot
    protected val robotCircleSize: Int = 20

    // Size of the vector representing the robot
    protected val robotVectorSize: Double = robotCircleSize * 0.8

    fun setRobotPosition(positionInMap: Transform) {
        // Compute the position of the robot in the View
        robotCoordinates = mapToViewCoordinates(positionInMap.translation.x, positionInMap.translation.y)

        // Compute the rotation of a vector representing the robot orientation
        val mapTheta: Double = mapTopGraphicalRepresentation?.theta?.toDouble() ?: 0.0
        val angle = mapTheta + positionInMap.rotation.toApacheRotation().let {
            it.angle * it.getAxis(RotationConvention.FRAME_TRANSFORM).z }
        val v = Vector3D(robotVectorSize, 0.0, 0.0)
        val r = Rotation(Vector3D(0.0, 0.0, 1.0), angle, RotationConvention.FRAME_TRANSFORM)
        r.applyTo(v).let { robotVector = PointF(it.x.toFloat(), it.y.toFloat()) }

        postInvalidate()
    }

    fun clearRobotPosition() { robotCoordinates = null }

    protected fun mapToViewCoordinates(x: Double, y: Double): PointF {
        mapTopGraphicalRepresentation?.let { map ->
            val (xPixel, yPixel) = map.mapToGraphicalCoordinates(x, y)
            return PointF(
                targetDisplayRect.left + (xPixel * scaleFactor).toFloat(),
                targetDisplayRect.top + (yPixel * scaleFactor).toFloat())
        }
        return PointF(0f, 0f)
    }
}
