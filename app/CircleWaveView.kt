package com.navbot.aihelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min


class CircleWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val circlePaint = Paint().apply {
        color = Color.GRAY  // 灰色圆
        style = Paint.Style.FILL
    }

    private val fixedWidth = 100f // 固定宽度
    private val initialHeight = 100f // 初始高度为圆形
    private val maxHeight = 400f // 最大高度，讲话时的最大拉伸高度

    private val spacing = 120 // 圆之间的间距
    private var circleScales = listOf(1f, 1f, 1f, 1f) // 初始化四个圆形高度比例

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthCenter = width / 2
        val heightCenter = height / 2

        for (i in 0..3) {
            val ellipseHeight = initialHeight + (maxHeight - initialHeight) * circleScales[i]

            // 画上下带有圆弧的竖直椭圆
            canvas.drawRoundRect(
                widthCenter.toFloat() + (i - 1.5f) * spacing - fixedWidth / 2,  // 左边
                heightCenter - ellipseHeight / 2,  // 上边
                widthCenter.toFloat() + (i - 1.5f) * spacing + fixedWidth / 2,  // 右边
                heightCenter + ellipseHeight / 2,  // 下边
                fixedWidth / 2,  // x方向的圆角半径
                fixedWidth / 2,  // y方向的圆角半径
                circlePaint
            )
        }
    }

    // 传入一个高度比例列表，更新四个圆的高度
    fun updateCircles(scaleList: List<Float>) {
        if (scaleList.size == 4) {
            circleScales = scaleList
            invalidate()  // 重绘视图
        }
    }

    // 重置圆形为初始状态
    fun resetCircles() {
        circleScales = listOf(1f, 1f, 1f, 1f)  // 重置为圆形
        invalidate()
    }
}
