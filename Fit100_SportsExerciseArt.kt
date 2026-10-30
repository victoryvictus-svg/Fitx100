package com.fit100.tracker

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * Lightweight, offline, animated exercise coach.
 * Drop-in replacement for the SportsExerciseArt class in WorkoutDashboard.kt.
 * It preserves the existing constructor and needs no third-party dependencies.
 *
 * Walking, jogging, knee push-ups and push-ups: human silhouette.
 * Jumping jacks and mountain climbers: jointed mannequin.
 */
class SportsExerciseArt(c: Context, private val id: String) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lime = Color.rgb(156, 255, 69)
    private val skin = Color.rgb(227, 188, 152)
    private val shirt = Color.rgb(92, 164, 116)
    private val shorts = Color.rgb(38, 65, 57)
    private val mannequin = id == "jacks" || id == "climbers"
    private var epoch = SystemClock.uptimeMillis()

    init {
        contentDescription = "${SportsExercises.get(id).name} animated exercise demonstration"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun stroke(canvas: Canvas, a: Pair<Float,Float>, b: Pair<Float,Float>,
                       color: Int, thick: Float = 8f) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = thick
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(a.first, a.second, b.first, b.second, p)
        p.style = Paint.Style.FILL
    }

    private fun circle(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.FILL
        canvas.drawCircle(x, y, r, p)
    }

    private fun person(canvas: Canvas, phase: Float) {
        val move = sin(phase)
        val arms = if (id == "jacks") abs(move) else move
        val body = if (id == "pushups" || id == "knee" || id == "climbers") 1 else 0
        if (body == 0) {
            val hip = 12f
            val headY = -49f + if (id == "jogging") abs(move)*2f else 0f
            circle(canvas, 0f, headY, 9f, if (mannequin) lime else skin)
            stroke(canvas, 0f to -37f, hip to 1f, if (mannequin) lime else shirt, 12f)
            val wide = if (id == "jacks") 25f*abs(move) else 13f*move
            stroke(canvas, hip to 0f, (-10f-wide) to 40f, if (mannequin) lime else shorts, 9f)
            stroke(canvas, hip to 0f, (14f+wide) to 40f, if (mannequin) lime else shorts, 9f)
            if (id == "jacks") {
                stroke(canvas, 1f to -29f, (-17f-19f*arms) to (-11f-38f*arms), lime, 8f)
                stroke(canvas, 1f to -29f, (17f+19f*arms) to (-11f-38f*arms), lime, 8f)
            } else {
                stroke(canvas, 1f to -29f, (-18f+9f*move) to (-4f-9f*move), if (mannequin) lime else skin, 8f)
                stroke(canvas, 1f to -29f, (18f-9f*move) to (-4f+9f*move), if (mannequin) lime else skin, 8f)
            }
        } else {
            // Side-view pose with animated lowering or alternating knee drive.
            val dip = if (id == "climbers") 0f else (move+1f)*6f
            val shoulder = -28f to (-11f+dip)
            val hip = 12f to (-8f+dip*.45f)
            val head = -41f to (-16f+dip)
            circle(canvas, head.first, head.second, 8f, if (mannequin) lime else skin)
            stroke(canvas, shoulder, hip, if (mannequin) lime else shirt, 12f)
            stroke(canvas, shoulder, (-24f) to 24f, if (mannequin) lime else skin, 8f)
            stroke(canvas, (-24f) to 24f, (-12f) to 26f, if (mannequin) lime else skin, 7f)
            if (id == "knee") {
                stroke(canvas, hip, 29f to 24f, shorts, 9f)
                stroke(canvas, 29f to 24f, 45f to 20f, skin, 7f)
            } else if (id == "climbers") {
                stroke(canvas, hip, (if (move>0) -3f else 30f) to 22f, lime, 9f)
                stroke(canvas, hip, (if (move>0) 30f else -3f) to 22f, lime, 9f)
            } else {
                stroke(canvas, hip, 43f to 22f, shorts, 9f)
            }
        }
        // Joint markers distinguish the mannequin from the human trainer.
        if (mannequin) {
            circle(canvas, 12f, 0f, 4f, Color.WHITE)
            circle(canvas, 0f, -29f, 4f, Color.WHITE)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, w, h,
            Color.rgb(24, 53, 35), Color.rgb(7, 22, 14), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, 22f, 22f, p)
        p.shader = null
        stroke(canvas, 12f to (h*.83f), (w-12f) to (h*.83f),
            Color.rgb(52, 79, 55), 2f)

        val reduceMotion = !isShown || !isAttachedToWindow
        val elapsed = if (reduceMotion) 0L else SystemClock.uptimeMillis()-epoch
        val speed = when (id) { "jogging" -> 2.9f; "jacks" -> 2.4f
            "climbers" -> 3.2f; "walking" -> 1.6f; else -> 1.7f }
        val phase = (elapsed / 1000f)*speed*2f*PI.toFloat()
        canvas.save()
        canvas.translate(w*.5f, h*.52f)
        canvas.scale(min(w/165f, h/115f), min(w/165f, h/115f))
        person(canvas, phase)
        canvas.restore()

        p.color = lime
        p.textSize = (h*.105f).coerceIn(10f, 15f)
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.LEFT
        canvas.drawText(if (mannequin) "FORM GUIDE" else "TRAINER DEMO", 12f, h-12f, p)
        if (!reduceMotion) postInvalidateDelayed(33L)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // onDraw stops scheduling itself once detached.
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        epoch = SystemClock.uptimeMillis()
        invalidate()
    }
}
