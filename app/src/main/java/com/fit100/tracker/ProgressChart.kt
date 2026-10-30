package com.fit100.tracker

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

class ProgressChart(context: Context, private val points: List<ProgressPoint>, private val unit: String, private val accent: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected = points.lastIndex
    private val density = resources.displayMetrics.density
    private val scale = resources.displayMetrics.scaledDensity
    init {
        isFocusable = true; isClickable = true
        contentDescription = points.joinToString(". ") { "${it.date}: ${"%.1f".format(it.value)} $unit" }
    }
    private fun x(index: Int, left: Float, right: Float): Float {
        if (points.size < 2) return (left + right) / 2
        val first = points.first().date.toEpochDay(); val span = (points.last().date.toEpochDay() - first).coerceAtLeast(1)
        return left + (right - left) * (points[index].date.toEpochDay() - first).toFloat() / span
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); if (points.isEmpty()) return
        val left = 43 * density; val right = width - 16 * density
        val top = 46 * density; val bottom = height - 34 * density
        if (right <= left || bottom <= top) return
        val minimum = points.minOf { it.value }; val maximum = points.maxOf { it.value }
        val padding = max((maximum - minimum) * .2, if (maximum > 10) 1.0 else .1)
        val low = max(0.0, minimum - padding); val high = maximum + padding
        fun y(value: Double) = bottom - ((value - low) / (high - low) * (bottom - top)).toFloat()
        pen.typeface = Typeface.DEFAULT; pen.textSize = 10 * scale; pen.strokeWidth = density
        repeat(4) { step ->
            val value = low + (high - low) * step / 3
            val position = y(value)
            pen.color = Color.rgb(37, 63, 41); pen.style = Paint.Style.STROKE; canvas.drawLine(left, position, right, position, pen)
            pen.color = Color.rgb(183, 204, 177); pen.style = Paint.Style.FILL; pen.textAlign = Paint.Align.RIGHT
            canvas.drawText("%.1f".format(value), left - 7 * density, position + 3 * density, pen)
        }
        val line = Path().apply { points.indices.forEach { i -> if (i == 0) moveTo(x(i, left, right), y(points[i].value)) else lineTo(x(i, left, right), y(points[i].value)) } }
        if (points.size > 1) {
            val area = Path(line).apply { lineTo(x(points.lastIndex, left, right), bottom); lineTo(x(0, left, right), bottom); close() }
            pen.shader = LinearGradient(0f, top, 0f, bottom, Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(area, pen); pen.shader = null
            pen.style = Paint.Style.STROKE; pen.strokeWidth = 2.5f * density; pen.color = accent; canvas.drawPath(line, pen)
        }
        pen.style = Paint.Style.FILL; pen.color = accent
        points.forEachIndexed { i, p -> canvas.drawCircle(x(i, left, right), y(p.value), (if (i == selected) 5f else 3.5f) * density, pen) }
        val labels = setOf(0, points.lastIndex / 2, points.lastIndex)
        pen.textSize = 9 * scale; pen.color = Color.rgb(183, 204, 177)
        labels.forEach { i ->
            pen.textAlign = when (i) { 0 -> Paint.Align.LEFT; points.lastIndex -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(points[i].date.format(DateTimeFormatter.ofPattern("d MMM")), x(i, left, right), bottom + 21 * density, pen)
        }
        val chosen = points[selected.coerceIn(0, points.lastIndex)]
        val value = "${"%.1f".format(chosen.value)} $unit  |  ${chosen.date.format(DateTimeFormatter.ofPattern("d MMM"))}"
        pen.textSize = 11 * scale; pen.typeface = Typeface.DEFAULT_BOLD; pen.textAlign = Paint.Align.LEFT
        val boxWidth = (pen.measureText(value) + 20 * density).coerceAtMost(width - 8 * density)
        val boxX = (x(selected, left, right) - boxWidth / 2).coerceIn(4 * density, width - boxWidth - 4 * density)
        pen.color = Color.rgb(36, 65, 37); canvas.drawRoundRect(RectF(boxX, 5 * density, boxX + boxWidth, 34 * density), 8 * density, 8 * density, pen)
        pen.color = Color.WHITE; canvas.drawText(value, boxX + 10 * density, 24 * density, pen)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && points.isNotEmpty()) {
            selected = points.indices.minByOrNull { abs(x(it, 43 * density, width - 16 * density) - event.x) } ?: 0
            invalidate(); performClick(); return true
        }
        return event.action == MotionEvent.ACTION_DOWN
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

class ProgressBody : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 100f, bounds.height() / 200f)
        pen.color = Color.rgb(75, 124, 65); pen.style = Paint.Style.FILL
        canvas.drawOval(RectF(38f, 3f, 62f, 30f), pen)
        val body = Path().apply {
            moveTo(41f, 31f); lineTo(59f, 31f); lineTo(64f, 38f); lineTo(75f, 42f); lineTo(88f, 98f); lineTo(82f, 101f)
            lineTo(69f, 62f); lineTo(66f, 102f); lineTo(65f, 145f); lineTo(61f, 195f); lineTo(49f, 195f); lineTo(51f, 147f)
            lineTo(50f, 114f); lineTo(49f, 147f); lineTo(47f, 195f); lineTo(35f, 195f); lineTo(34f, 145f); lineTo(34f, 102f)
            lineTo(31f, 62f); lineTo(18f, 101f); lineTo(12f, 98f); lineTo(25f, 42f); lineTo(36f, 38f); close()
        }
        canvas.drawPath(body, pen); pen.color = Color.rgb(151, 233, 109); pen.style = Paint.Style.STROKE; pen.strokeWidth = .8f; canvas.drawPath(body, pen)
        canvas.drawLine(35f, 59f, 65f, 59f, pen); canvas.drawLine(50f, 46f, 50f, 96f, pen)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { pen.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { pen.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
