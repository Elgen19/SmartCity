package com.elgenium.smartcity.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CustomSpeedometer(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var speed = 0f // Current speed
    private val paint = Paint()

    init {
        paint.isAntiAlias = true
        paint.color = Color.BLUE // Change color as needed
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()

        // Draw the speedometer arc
        val arcRect = RectF(0f, height / 2, width, height)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)

        // Draw the speed needle
        val needleX = (width / 2 + (width / 4) * Math.cos(Math.toRadians((180 + speed * 1.8).toDouble()))).toFloat()
        val needleY = (height / 2 + (height / 4) * Math.sin(Math.toRadians((180 + speed * 1.8).toDouble()))).toFloat()
        paint.color = Color.RED // Needle color
        canvas.drawLine(width / 2, height / 2, needleX, needleY, paint)

        // Draw speed text (optional)
        paint.color = Color.BLACK
        paint.textSize = 50f
        canvas.drawText("${speed.toInt()} km/h", width / 2 - 25, height / 2 - 20, paint)
    }

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
        invalidate() // Refresh the view
    }
}
