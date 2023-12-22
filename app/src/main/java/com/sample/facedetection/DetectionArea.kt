package com.sample.facedetection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.View

class GenerateFrame constructor(context: Context,
                                private val text:String, private val rectW:Float, private val rectH:Float,
                                private val R:Int, private val G:Int, private val B:Int) : View(context){
    private val paint: Paint = Paint()
    private val lineStrokeWidth = 10f
    private lateinit var canvasCenter : PointF
    private lateinit var initCanvas: Canvas
    val alpha = 250

    @SuppressLint("DrawAllocation", "LongLogTag")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasCenter = PointF(width/2F, height/2F)
        initCanvas = canvas

        identityCheckFrame(text, rectW, rectH, R, G, B)

    }

    private fun identityCheckFrame(textUsage:String, rectW: Float, rectH: Float, colorR:Int, colorG:Int, colorB:Int){
        val leftPoint = canvasCenter.x - rectW
        val topPoint = canvasCenter.y - rectH
        val rightPoint = canvasCenter.x + rectW
        val bottomPoint = canvasCenter.y + rectH
        val rect = RectF(leftPoint, topPoint, rightPoint, bottomPoint)
        paint.color = Color.argb(alpha, colorR, colorG, colorB)
        // ペイントストロークの太さを設定
        paint.strokeWidth = lineStrokeWidth
        // Styleのストロークを設定する
        paint.style = Paint.Style.STROKE

        val cornersRadiusX = 25F
        val cornersRadiusY = 25F
        val paintText = Paint()
        paintText.setTextSize(50F)
        paintText.setColor(paint.color)
        //文字列幅の取得
        val textWidth:Float = paintText.measureText(textUsage)
        // 四角枠に対して中央揃え
        Log.d("center_x", textWidth.toString())
        initCanvas.drawText(textUsage, canvasCenter.x - textWidth/2F,
            canvasCenter.y - rectH - 20F,
            paintText)
        initCanvas.drawRoundRect(rect, cornersRadiusX, cornersRadiusY, paint)
    }
}

class DetectionArea(context: Context, rect : RectF, previewWidth:Float, previewHeight:Float) : View(context){
    private val paint: Paint = Paint()
    private val lineStrokeWidth = 10f
    private val leftPoint = left
    private val topPoint = top
    private val rightPoint = right
    private val bottomPoint = bottom
    private val detectionArea = rect
    private val pWidth = previewWidth
    private val pHeight = previewHeight


    @SuppressLint("DrawAllocation", "LongLogTag")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthScaleFactor = width.toFloat() / pWidth
        val heightScaleFactor = height.toFloat() / pHeight

        // val rect = RectF(leftPoint, topPoint, rightPoint, bottomPoint)
        paint.color = Color.argb(100, 255, 255, 255)
        // ペイントストロークの太さを設定
        paint.strokeWidth = lineStrokeWidth
        // Styleのストロークを設定する
        paint.style = Paint.Style.STROKE

        val cornersRadiusX = 25F
        val cornersRadiusY = 25F

        val expandedLeft = detectionArea.left * 3
        val expandedTop = detectionArea.top * 3
        val expandedRight = detectionArea.right * 3
        val expandedBottom = detectionArea.bottom * 3
        val expandedRectF = RectF(expandedLeft, expandedTop, expandedRight, expandedBottom)

        // /*
        val centerX = (expandedLeft + expandedRight) / 2
        val centerY = (expandedTop + expandedBottom) / 2

        // 画面中央に対して線対称の領域を考える
        val symmetricLeft = width - expandedRight
        val symmetricRight = width - expandedLeft
        val symmetricTop = expandedTop
        val symmetricBottom = expandedBottom

        canvas.drawRoundRect(detectionArea, cornersRadiusX, cornersRadiusY, paint)
    }
}