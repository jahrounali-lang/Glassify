package com.liquidclock.render

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import kotlin.math.max

/** Wallpapers the glass refracts. The same bitmap is drawn under the clock in preview and on the lock screen. */
object Backgrounds {
    val ids = listOf("aurora", "sunset", "ocean", "graphite", "neon")
    private val cache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 5
    }

    fun customFile(ctx: Context) = File(ctx.filesDir, "custom_bg.img")

    @Synchronized
    fun get(ctx: Context, id: String, w: Int, h: Int): Bitmap {
        val f = customFile(ctx)
        val custom = id == "custom" && f.exists()
        val key = if (custom) "custom:${f.lastModified()}:${w}x$h" else "$id:${w}x$h"
        return cache.getOrPut(key) { (if (custom) cover(f, w, h) else null) ?: procedural(if (custom) "aurora" else id, w, h) }
    }

    @Synchronized
    fun importUri(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { i -> customFile(ctx).outputStream().use { i.copyTo(it) } }
        cache.clear(); true
    }.getOrDefault(false)

    private fun cover(f: File, w: Int, h: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sc = max(w / src.width.toFloat(), h / src.height.toFloat())
        val c = Canvas(out)
        c.translate((w - src.width * sc) / 2f, (h - src.height * sc) / 2f)
        c.scale(sc, sc)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    private class Blob(val x: Float, val y: Float, val r: Float, val c: Int)

    private fun procedural(id: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (a, b, blobs) = when (id) {
            "sunset" -> Triple(0xFF1B1038.toInt(), 0xFFFF7A59.toInt(), listOf(Blob(.2f, .25f, .6f, 0xFFFFC371.toInt()), Blob(.85f, .7f, .7f, 0xFFB5179E.toInt())))
            "ocean" -> Triple(0xFF031A2E.toInt(), 0xFF0C7C8C.toInt(), listOf(Blob(.8f, .2f, .6f, 0xFF4CC9F0.toInt()), Blob(.2f, .8f, .7f, 0xFF1D3FBF.toInt())))
            "graphite" -> Triple(0xFF0E0E10.toInt(), 0xFF3A3D45.toInt(), listOf(Blob(.7f, .3f, .6f, 0xFF8A93A6.toInt()), Blob(.2f, .75f, .5f, 0xFF50566A.toInt())))
            "neon" -> Triple(0xFF0A0014.toInt(), 0xFF2B0B5E.toInt(), listOf(Blob(.25f, .3f, .55f, 0xFFFF2BD6.toInt()), Blob(.8f, .65f, .6f, 0xFF00E5FF.toInt())))
            else -> Triple(0xFF061224.toInt(), 0xFF16345E.toInt(), listOf(Blob(.2f, .2f, .6f, 0xFF3DDC97.toInt()), Blob(.85f, .45f, .6f, 0xFF7B61FF.toInt()), Blob(.4f, .85f, .7f, 0xFF00B4D8.toInt())))
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), a, b, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        for (bl in blobs) {
            p.shader = RadialGradient(bl.x * w, bl.y * h, bl.r * max(w, h), bl.c, bl.c and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        // Fine concentric lines: they make the refraction clearly visible through the glass.
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, w * 0.0025f); p.color = 0x26FFFFFF
        val step = w * 0.045f
        var r = step
        while (r < h) { c.drawCircle(w * 0.5f, h * 0.62f, r, p); r += step }
        return bmp
    }
}
