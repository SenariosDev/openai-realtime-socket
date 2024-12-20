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
        color = Color.GRAY  // 圆的颜色
        style = Paint.Style.FILL
    }

    private val fixedWidth = 150f // 固定宽度，增大圆的宽度
    private val initialHeight = 150f // 初始高度保证圆的比例增大
    private val maxHeight = 400f // 最大拉伸高度

    private val spacing = 170f // 增加圆之间的间距，保证圆不挤在一起
    private var circleScales = listOf(1f, 1f, 1f, 1f) // 初始比例为1

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 计算绘制区域的中心
        val widthCenter = width / 2
        val heightCenter = height / 2

        for (i in 0 until 4) {
            val ellipseHeight = initialHeight * circleScales[i] // 根据比例调整高度

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
            circleScales = scaleList.map { 1f + (it * 2f) } // 基于原始圆形比例进行拉伸
            invalidate()  // 重新绘制视图
        }
    }

    // 重置圆形为初始状态
    fun resetCircles() {
        circleScales = listOf(1f, 1f, 1f, 1f)  // 重置为标准圆形
        invalidate()
    }
}

