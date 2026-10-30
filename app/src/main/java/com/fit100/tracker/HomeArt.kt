package com.fit100.tracker

import android.graphics.*
import android.graphics.drawable.Drawable

class HomeArt(private val kind: String, private val fraction: Float = 0f, private val accent: Int = Color.rgb(158, 250, 77)) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        when (kind) {
            "ring" -> {
                val stroke = w * .07f; pen.style = Paint.Style.STROKE; pen.strokeWidth = stroke; pen.strokeCap = Paint.Cap.ROUND
                val rect = RectF(stroke, stroke, w - stroke, h - stroke)
                pen.color = Color.rgb(43, 66, 47); canvas.drawOval(rect, pen)
                pen.color = accent; canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, pen)
            }
            "glass" -> {
                val cup = Path().apply { moveTo(w * .17f, h * .12f); lineTo(w * .83f, h * .12f); lineTo(w * .73f, h * .88f); quadTo(w * .5f, h * .98f, w * .27f, h * .88f); close() }
                pen.style = Paint.Style.FILL; pen.color = Color.rgb(231, 241, 238); canvas.drawPath(cup, pen)
                canvas.save(); canvas.clipPath(cup)
                pen.shader = LinearGradient(0f, h * .1f, w, h, Color.rgb(105, 224, 255), Color.rgb(0, 150, 215), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h * (.9f - .78f * fraction.coerceIn(0f, 1f)), w, h, pen); pen.shader = null; canvas.restore()
                pen.style = Paint.Style.STROKE; pen.strokeWidth = w * .035f; pen.color = Color.rgb(116, 157, 163); canvas.drawPath(cup, pen)
                canvas.drawOval(RectF(w * .17f, h * .07f, w * .83f, h * .2f), pen)
            }
            else -> {
                pen.style = Paint.Style.FILL
                pen.shader = LinearGradient(0f, 0f, w, h, Color.rgb(35, 77, 34), Color.rgb(4, 20, 15), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, pen); pen.shader = null
                pen.shader = RadialGradient(w * .78f, h * .3f, w * .3f, Color.argb(120, 215, 253, 93), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, pen); pen.shader = null
                pen.color = Color.rgb(223, 248, 107); canvas.drawCircle(w * .78f, h * .3f, h * .055f, pen)
                listOf(.35f to Color.rgb(68, 99, 49), .52f to Color.rgb(29, 64, 40), .7f to Color.rgb(8, 37, 29)).forEachIndexed { layer, (level, color) ->
                    val mountain = Path().apply {
                        moveTo(0f, h); lineTo(0f, h * level)
                        lineTo(w * .12f, h * (level + .12f)); lineTo(w * .29f, h * (level - .13f)); lineTo(w * .4f, h * (level + .1f))
                        lineTo(w * .58f, h * (level - .17f)); lineTo(w * .71f, h * (level + .04f)); lineTo(w * .88f, h * (level - .2f))
                        lineTo(w, h * (level + .08f)); lineTo(w, h); close()
                    }
                    pen.color = color; canvas.drawPath(mountain, pen)
                }
                pen.shader = LinearGradient(0f, h * .4f, 0f, h, Color.TRANSPARENT, Color.argb(200, 3, 16, 10), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, pen); pen.shader = null
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { pen.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { pen.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
