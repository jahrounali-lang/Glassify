package com.liquidclock.render

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.liquidclock.model.ClockConfig
import java.io.File

/**
 * GooglePixelInflat is proprietary, so it is NOT bundled. It is loaded from
 *  1) a file imported in the app, or 2) assets/fonts/GooglePixelInflat.(ttf|otf).
 * If neither exists the UI shows a visible warning; the system font is used only until the font is supplied.
 */
object FontProvider {
    private const val NAME = "GooglePixelInflat"
    @Volatile var revision = 0
        private set
    private val cache = HashMap<String, Typeface>()

    fun imported(ctx: Context) = File(ctx.filesDir, "fonts/$NAME.ttf")

    private fun assetPath(ctx: Context): String? = runCatching {
        ctx.assets.list("fonts")?.firstOrNull {
            it.startsWith(NAME, true) && (it.endsWith(".ttf", true) || it.endsWith(".otf", true))
        }?.let { "fonts/$it" }
    }.getOrNull()

    fun isAvailable(ctx: Context) = imported(ctx).exists() || assetPath(ctx) != null

    @Synchronized
    fun get(ctx: Context, c: ClockConfig): Typeface {
        val w = c.weight()
        if (c.fontSource == "system") return Typeface.create(Typeface.DEFAULT, w, false)
        return cache.getOrPut("$w:$revision") { load(ctx, w) ?: Typeface.create(Typeface.DEFAULT, w, false) }
    }

    private fun load(ctx: Context, w: Int): Typeface? {
        val f = imported(ctx); val v = "\u0027wght\u0027 $w"
        return runCatching {
            if (f.exists()) Typeface.Builder(f).setFontVariationSettings(v).setWeight(w).build()
            else assetPath(ctx)?.let { Typeface.Builder(ctx.assets, it).setFontVariationSettings(v).setWeight(w).build() }
        }.getOrNull()
    }

    @Synchronized
    fun import(ctx: Context, uri: Uri): Boolean = runCatching {
        val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
        val tmp = File(dir, "tmp.font")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        if (Typeface.Builder(tmp).build() == null) { tmp.delete(); return@runCatching false }
        tmp.copyTo(imported(ctx), overwrite = true); tmp.delete()
        cache.clear(); revision++
        true
    }.getOrDefault(false)
}
