package com.liquidclock.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.liquidclock.model.ClockConfig
import com.liquidclock.render.Backgrounds
import com.liquidclock.render.GlassClockRenderer
import java.time.ZonedDateTime
import kotlin.math.*

/** Editor preview. Uses the exact same GlassClockRenderer as the wallpaper service. Draw handles + pinch to resize. */
class ClockPreviewView(ctx: Context) : View(ctx) {
    private val renderer = GlassClockRenderer(ctx)
    private val dp = ctx.resources.displayMetrics.density
    var onChange: ((ClockConfig) -> Unit)? = null

    var config = ClockConfig()
        set(v) { field = v; invalidate() }
    var mode = GlassClockRenderer.Mode.LOCK
        set(v) { field = v; invalidate() }
    var editing = true
        set(v) { field = v; invalidate() }
    var rev = 0
        set(v) { field = v; invalidate() }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xCCFFFFFF.toInt(); strokeWidth = 1.5f * dp
        pathEffect = DashPathEffect(floatArrayOf(8 * dp, 6 * dp), 0f)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11 * dp; textAlign = Paint.Align.CENTER }
    private val guide = Paint().apply { color = 0x55FFFFFF; strokeWidth = dp }

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            val now = System.currentTimeMillis()
            postDelayed(this, (if (config.seconds) 1000 - now % 1000 else 60000 - now % 60000) + 20)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width; val h = height
        if (w == 0 || h == 0) return
        val bg = if (mode == GlassClockRenderer.Mode.AOD) null else Backgrounds.get(context, config.wallpaper, w, h)
        renderer.draw(canvas, w, h, config, ZonedDateTime.now(), bg, mode)
        if (!editing) return
        val b = renderer.box; val cx = b.centerX(); val cy = b.centerY()
        if (abs(config.posX - 50f) < 0.01f) canvas.drawLine(w / 2f, 0f, w / 2f, h.toFloat(), guide)
        canvas.save(); canvas.rotate(renderer.boxRotation, cx, cy)
        canvas.drawRect(b, line)
        val r = 6 * dp
        canvas.drawCircle(b.right, cy, r, dot); canvas.drawCircle(cx, b.bottom, r, dot); canvas.drawCircle(b.right, b.bottom, r, dot)
        canvas.restore()
        val txt = "${(config.width / 100f * 1080).roundToInt()} × ${(config.height / 100f * 2340).roundToInt()} px  (1080×2340)"
        canvas.drawText(txt, cx.coerceIn(80 * dp, w - 80 * dp), max(14 * dp, b.top - 10 * dp), label)
    }

    private enum class Drag { NONE, MOVE, RIGHT, BOTTOM, CORNER }
    private var drag = Drag.NONE
    private var lx = 0f; private var ly = 0f; private var rawX = 0f; private var rawY = 0f

    private fun emit(c: ClockConfig) { config = c; onChange?.invoke(c) }

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val c = config; val f = d.scaleFactor
            emit(c.copy(width = (c.width * f).coerceIn(10f, 100f), height = (c.height * f).coerceIn(8f, 100f)))
            return true
        }
    })

    private fun unrot(x: Float, y: Float, translate: Boolean): Pair<Float, Float> {
        val b = renderer.box; val a = Math.toRadians(-renderer.boxRotation.toDouble())
        val dx = if (translate) x - b.centerX() else x; val dy = if (translate) y - b.centerY() else y
        val ux = (dx * cos(a) - dy * sin(a)).toFloat(); val uy = (dx * sin(a) + dy * cos(a)).toFloat()
        return if (translate) Pair(b.centerX() + ux, b.centerY() + uy) else Pair(ux, uy)
    }

    private fun hit(x: Float, y: Float): Drag {
        val b = renderer.box; val (ux, uy) = unrot(x, y, true); val r = 28 * dp
        return when {
            hypot(ux - b.right, uy - b.bottom) < r -> Drag.CORNER
            hypot(ux - b.right, uy - b.centerY()) < r -> Drag.RIGHT
            hypot(ux - b.centerX(), uy - b.bottom) < r -> Drag.BOTTOM
            b.contains(ux, uy) -> Drag.MOVE
            else -> Drag.NONE
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        scaler.onTouchEvent(e)
        if (scaler.isInProgress || e.pointerCount > 1) { drag = Drag.NONE; return true }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                drag = hit(e.x, e.y); lx = e.x; ly = e.y; rawX = config.posX; rawY = config.posY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lx; val dy = e.y - ly; lx = e.x; ly = e.y
                val c = config; val b = renderer.box
                val (ldx, ldy) = unrot(dx, dy, false)
                when (drag) {
                    Drag.MOVE -> {
                        rawX = (rawX + dx / width * 100f).coerceIn(0f, 100f); rawY = (rawY + dy / height * 100f).coerceIn(0f, 100f)
                        emit(c.copy(posX = if (abs(rawX - 50f) < 1.2f) 50f else rawX, posY = rawY))
                    }
                    Drag.RIGHT -> emit(c.copy(width = ((b.width() + 2 * ldx) / width * 100f).coerceIn(10f, 100f)))
                    Drag.BOTTOM -> emit(c.copy(height = ((b.height() + 2 * ldy) / height * 100f).coerceIn(8f, 100f)))
                    Drag.CORNER -> emit(c.copy(
                        width = ((b.width() + 2 * ldx) / width * 100f).coerceIn(10f, 100f),
                        height = ((b.height() + 2 * ldy) / height * 100f).coerceIn(8f, 100f)))
                    Drag.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = Drag.NONE
        }
        return true
    }
}
