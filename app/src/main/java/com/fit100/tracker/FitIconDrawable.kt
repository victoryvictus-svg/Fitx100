package com.fit100.tracker

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Consistent 24-unit outline icons for the profile and navigation. */
class FitIconDrawable(private val kind: String, color: Int) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, pen)
        fun path(vararg points: Float) {
            val shape = Path(); shape.moveTo(points[0], points[1]); var i = 2
            while (i < points.size) { shape.lineTo(points[i], points[i + 1]); i += 2 }; canvas.drawPath(shape, pen)
        }
        fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = 2f) = canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, pen)
        when (kind) {
            "water" -> { val shape = Path().apply { moveTo(12f, 2f); cubicTo(10f, 7f, 4f, 11f, 4f, 15f); cubicTo(4f, 24f, 20f, 24f, 20f, 15f); cubicTo(20f, 11f, 14f, 7f, 12f, 2f); close() }; canvas.drawPath(shape, pen); canvas.drawArc(RectF(8f, 10f, 16f, 19f), 0f, 80f, false, pen) }
            "timer" -> { canvas.drawCircle(12f, 14f, 8f, pen); line(9f, 2f, 15f, 2f); line(12f, 2f, 12f, 6f); path(12f, 9f, 12f, 14f, 15f, 16f); line(18f, 6f, 20f, 4f) }
            "fire" -> { val shape = Path().apply { moveTo(12f, 2f); cubicTo(14f, 8f, 22f, 11f, 19f, 18f); cubicTo(17f, 24f, 5f, 24f, 4f, 16f); cubicTo(3f, 12f, 7f, 8f, 8f, 6f); lineTo(10f, 12f); cubicTo(13f, 10f, 12f, 6f, 12f, 2f); close() }; canvas.drawPath(shape, pen) }
            "check" -> { box(4f, 5f, 20f, 22f); box(9f, 2f, 15f, 7f); path(8f, 14f, 11f, 17f, 17f, 11f) }
            "wallet" -> { box(3f, 6f, 21f, 21f); path(4f, 6f, 6f, 3f, 18f, 3f); box(15f, 11f, 22f, 16f, 1f); canvas.drawPoint(18f, 13.5f, pen) }
            "apple" -> { val shape = Path().apply { moveTo(12f, 8f); cubicTo(5f, 1f, 1f, 10f, 5f, 18f); cubicTo(8f, 24f, 10f, 20f, 12f, 21f); cubicTo(16f, 23f, 19f, 20f, 21f, 13f); cubicTo(24f, 5f, 16f, 3f, 12f, 8f) }; canvas.drawPath(shape, pen); path(12f, 6f, 12f, 2f, 16f, 1f) }
            "leaf" -> { pen.style = Paint.Style.FILL; val shape = Path().apply { moveTo(3f, 21f); cubicTo(2f, 5f, 8f, 2f, 22f, 2f); cubicTo(22f, 12f, 13f, 19f, 6f, 18f); close() }; canvas.drawPath(shape, pen); pen.style = Paint.Style.STROKE; val previous = pen.color; pen.color = android.graphics.Color.rgb(5, 22, 12); path(4f, 21f, 10f, 12f, 18f, 7f); pen.color = previous }
            "home" -> { path(3f, 10f, 12f, 3f, 21f, 10f); path(5f, 9f, 5f, 21f, 10f, 21f, 10f, 14f, 14f, 14f, 14f, 21f, 19f, 21f, 19f, 9f) }
            "user" -> { canvas.drawCircle(12f, 7f, 3.5f, pen); canvas.drawArc(RectF(4f, 13f, 20f, 25f), 180f, 180f, false, pen); line(4f, 19f, 20f, 19f) }
            "diet" -> { line(5f, 3f, 5f, 9f); line(8f, 3f, 8f, 21f); line(11f, 3f, 11f, 9f); canvas.drawArc(RectF(5f, 6f, 11f, 12f), 0f, 180f, false, pen); path(19f, 21f, 19f, 3f, 16f, 7f, 16f, 12f, 19f, 12f) }
            "chart" -> { box(3f, 13f, 7f, 21f, 1f); box(10f, 7f, 14f, 21f, 1f); box(17f, 3f, 21f, 21f, 1f) }
            "calendar" -> { box(4f, 5f, 20f, 21f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f); line(4f, 10f, 20f, 10f); path(8f, 15f, 11f, 18f, 16f, 13f) }
            "workout" -> { canvas.save(); canvas.rotate(-30f, 12f, 12f); line(6f, 12f, 18f, 12f); line(3f, 9f, 3f, 15f); line(6f, 6f, 6f, 18f); line(18f, 6f, 18f, 18f); line(21f, 9f, 21f, 15f); canvas.restore() }
            "bell" -> { val shape = Path().apply { moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f); cubicTo(7f, 2f, 17f, 2f, 17f, 9f); lineTo(17f, 14f); lineTo(19f, 17f); close() }; canvas.drawPath(shape, pen); canvas.drawArc(RectF(9f, 17f, 15f, 22f), 0f, 180f, false, pen); line(12f, 2f, 12f, 3f) }
            "camera" -> { path(3f, 7f, 7f, 7f, 9f, 4f, 15f, 4f, 17f, 7f, 21f, 7f, 21f, 20f, 3f, 20f, 3f, 7f); canvas.drawCircle(12f, 13f, 4f, pen) }
            "edit" -> { path(4f, 20f, 5f, 15f, 17f, 3f, 21f, 7f, 9f, 19f, 4f, 20f); line(14f, 6f, 18f, 10f) }
            else -> { canvas.drawCircle(12f, 12f, 6f, pen); canvas.drawCircle(12f, 12f, 2f, pen); repeat(8) { canvas.save(); canvas.rotate(it * 45f, 12f, 12f); line(12f, 3f, 12f, 5f); canvas.restore() } }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { pen.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { pen.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    companion object {
        fun forResource(icon: Int) = when (icon) {
            android.R.drawable.ic_menu_myplaces -> "user"
            android.R.drawable.ic_menu_agenda -> "diet"
            android.R.drawable.ic_menu_today -> "calendar"
            android.R.drawable.ic_menu_manage -> "workout"
            android.R.drawable.ic_lock_idle_alarm -> "bell"
            android.R.drawable.ic_menu_camera -> "camera"
            else -> "settings"
        }
    }
}
