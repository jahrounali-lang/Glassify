package com.liquidclock.render

import android.content.Context
import android.graphics.*
import com.liquidclock.model.ClockConfig
import com.liquidclock.model.ClockText
import java.time.ZonedDateTime
import kotlin.math.*

/** The single renderer used by BOTH the editor preview and the live wallpaper (the real lock-screen path). */
class GlassClockRenderer(private val ctx: Context) {
    enum class Mode { LOCK, AOD }

    private class G(val s: String, val x: Float, val y: Float, val size: Float)
    private class Placed(val glyphs: List<G>, val w: Float, val h: Float)
    private class LineM(val text: String, val sz: Float, val w: Float, val pitch: Float, val dh: Float)

    private val shader = RuntimeShader(GlassShader.SRC)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = shader }
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE6E6E6.toInt() }
    private val path = Path()
    private val tmp = Path()

    private var bgRef: Bitmap? = null
    private var bgShader: BitmapShader? = null
    private var maskShader: BitmapShader? = null
    private var heightShader: BitmapShader? = null
    private var key = ""

    /** Unrotated clock bounding box in px from the last draw (used by the editor overlay). */
    val box = RectF()
    var boxRotation = 0f
        private set

    private fun linear(b: Bitmap) = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    private fun place(c: ClockConfig, lines: List<ClockText.Line>, base: Float, tf: Typeface): Placed {
        mp.typeface = tf
        val ls = c.letterEm(); val ds = c.digitEm(); val lh = c.lineMul()
        val r = Rect()
        val ms = lines.map { ln ->
            val sz = if (ln.date) base * 0.2f else base
            mp.textSize = sz
            mp.getTextBounds("0", 0, 1, r)
            val dh = r.height().toFloat().coerceAtLeast(1f)
            var wd = 0f
            for (i in ln.text.indices) {
                wd += mp.measureText(ln.text, i, i + 1)
                if (i < ln.text.lastIndex) {
                    wd += ls * sz
                    if (ln.text[i].isDigit() && ln.text[i + 1].isDigit()) wd += ds * sz
                }
            }
            LineM(ln.text, sz, wd, if (ln.date) dh * 1.9f else dh * lh, dh)
        }
        val bw = ms.maxOf { it.w }.coerceAtLeast(1f)
        var y = 0f
        val out = ArrayList<G>()
        for (m in ms) {
            mp.textSize = m.sz
            var x = when (c.hAlign) { 0 -> 0f; 1 -> (bw - m.w) / 2f; else -> bw - m.w }
            val baseline = y + (m.pitch + m.dh) / 2f
            for (i in m.text.indices) {
                out += G(m.text[i].toString(), x, baseline, m.sz)
                x += mp.measureText(m.text, i, i + 1) + c.letterEm() * m.sz
                if (i < m.text.lastIndex && m.text[i].isDigit() && m.text[i + 1].isDigit()) x += ds * m.sz
            }
            y += m.pitch
        }
        return Placed(out, bw, y.coerceAtLeast(1f))
    }

    fun draw(canvas: Canvas, w: Int, h: Int, c: ClockConfig, now: ZonedDateTime, bg: Bitmap?, mode: Mode = Mode.LOCK) {
        if (w <= 0 || h <= 0) return
        if (mode == Mode.AOD || bg == null) canvas.drawColor(Color.BLACK) else canvas.drawBitmap(bg, 0f, 0f, bgPaint)

        val lines = ClockText.lines(c, now)
        val boxW = max(8f, c.width / 100f * w); val boxH = max(8f, c.height / 100f * h)
        val cx = c.posX / 100f * w; val cy = c.posY / 100f * h
        val rot = c.rotationDeg()
        box.set(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f); boxRotation = rot

        // Fit: ONE uniform scale for the whole block -> no stretching/clipping, whatever the box size.
        val tf = FontProvider.get(ctx, c)
        val ref = place(c, lines, 100f, tf)
        val big = 100f * min(boxW / ref.w, boxH / ref.h) * 0.94f * c.sizeMul()
        val pl = place(c, lines, big, tf)
        val ox = when (c.hAlign) { 0 -> 0f; 1 -> (boxW - pl.w) / 2f; else -> boxW - pl.w }
        val oy = when (c.vAlign) { 0 -> 0f; 1 -> (boxH - pl.h) / 2f; else -> boxH - pl.h }
        path.rewind(); glyphPaint.typeface = tf
        for (g in pl.glyphs) {
            glyphPaint.textSize = g.size; tmp.rewind()
            glyphPaint.getTextPath(g.s, 0, g.s.length, g.x + ox, g.y + oy, tmp)
            path.addPath(tmp)
        }

        if (mode == Mode.AOD) {
            aodPaint.strokeWidth = max(1.5f, big * 0.012f)
            canvas.save(); canvas.translate(cx, cy); canvas.rotate(rot); canvas.translate(-boxW / 2f, -boxH / 2f)
            canvas.drawPath(path, aodPaint); canvas.restore()
            return
        }
        if (bg == null) return

        val ds = min(w, h) / 1080f
        val bezelPx = max(2f, big * (0.02f + c.bezel / 100f * 0.22f))
        val strokeW = c.radius / 100f * 0.05f * big     // rounded joins = "border radius" of the glyph shapes
        val half = hypot(boxW, boxH) / 2f + bezelPx * 3f + strokeW + 24f * ds
        val side = ceil(half * 2f).toInt().coerceIn(64, 4096)

        val newKey = listOf(
            lines.joinToString("|") { it.text }, w, h, c.bezel, c.radius, c.fontWeight, c.fontSize, c.letterSpacing,
            c.lineHeight, c.digitSpacing, c.hAlign, c.vAlign, c.width, c.height, c.rotation, c.orientation,
            c.fontSource, FontProvider.revision
        ).joinToString(",")
        if (newKey != key || maskShader == null) {
            key = newKey
            val mb = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
            val hb = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL_AND_STROKE; strokeWidth = strokeW; strokeJoin = Paint.Join.ROUND; color = Color.BLACK
            }
            fun render(bmp: Bitmap) {
                val cv = Canvas(bmp)
                cv.translate(side / 2f, side / 2f); cv.rotate(rot); cv.translate(-boxW / 2f, -boxH / 2f)
                cv.drawPath(path, p)
            }
            render(mb)
            p.maskFilter = BlurMaskFilter(bezelPx, BlurMaskFilter.Blur.NORMAL)
            render(hb)
            maskShader = linear(mb); heightShader = linear(hb)
        }
        if (bg !== bgRef) { bgRef = bg; bgShader = linear(bg) }

        val left = cx - side / 2f; val top = cy - side / 2f
        val m = Matrix().apply { setTranslate(left, top) }
        maskShader!!.setLocalMatrix(m); heightShader!!.setLocalMatrix(m)
        shader.setInputShader("bg", bgShader!!)
        shader.setInputShader("mask", maskShader!!)
        shader.setInputShader("hmap", heightShader!!)
        shader.setFloatUniform("refr", c.refraction / 100f * bezelPx * 2.2f)
        shader.setFloatUniform("gradScale", bezelPx)
        shader.setFloatUniform("blurPx", c.blur / 100f * 28f * ds)
        shader.setFloatUniform("frost", c.frost / 100f)
        shader.setFloatUniform("opac", c.opacity / 100f)
        shader.setFloatUniform("edgeI", c.edge / 100f)
        shader.setFloatUniform("reflI", c.reflection / 100f)
        shader.setFloatUniform("thick", c.thickness / 100f)
        shader.setFloatUniform("shadowI", c.shadow / 100f)
        shader.setFloatUniform("shadowOff", bezelPx * 0.5f)
        val a = Math.toRadians(c.highlight * 3.6)
        shader.setFloatUniform("lightDir", cos(a).toFloat(), (-sin(a)).toFloat())
        canvas.drawRect(left, top, left + side, top + side, shaderPaint)
    }
}
