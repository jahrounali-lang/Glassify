package com.liquidclock.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.liquidclock.model.ClockConfig
import com.liquidclock.model.ConfigStore
import com.liquidclock.render.Backgrounds
import com.liquidclock.render.GlassClockRenderer
import java.time.ZonedDateTime

/**
 * REAL system integration: a live wallpaper. When the user selects it for the lock screen, Android draws
 * this engine behind the lock UI. Authentication (PIN / fingerprint / biometrics) is never touched.
 * Battery: draws only when visible; once per minute (TIME_TICK) or once per second only if seconds are shown.
 */
class ClockWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ClockEngine()

    private inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val store by lazy { ConfigStore(applicationContext) }
        private var cfg = ClockConfig()
        private var renderer: GlassClockRenderer? = null
        private var w = 0
        private var h = 0
        private var visible = false
        private var registered = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { draw() }
        }
        private val prefs = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            cfg = store.active(); draw(); reschedule()
        }
        private val ticker = object : Runnable {
            override fun run() { draw(); reschedule() }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            cfg = store.active(); store.register(prefs)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height; draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) { draw() }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) { cfg = store.active(); register(); draw(); reschedule() }
            else { unregister(); handler.removeCallbacks(ticker) }
        }

        override fun onDestroy() {
            handler.removeCallbacks(ticker); unregister(); store.unregister(prefs)
            super.onDestroy()
        }

        private fun register() {
            if (registered) return
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(this@ClockWallpaperService, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }

        private fun unregister() {
            if (!registered) return
            runCatching { this@ClockWallpaperService.unregisterReceiver(receiver) }
            registered = false
        }

        private fun reschedule() {
            handler.removeCallbacks(ticker)
            if (visible && cfg.seconds) handler.postDelayed(ticker, 1000 - System.currentTimeMillis() % 1000)
        }

        private fun draw() {
            if (w <= 0 || h <= 0) return
            val sh = surfaceHolder
            val canvas: Canvas = (try { sh.lockHardwareCanvas() } catch (e: Exception) { null }) ?: return
            try {
                val r = renderer ?: GlassClockRenderer(applicationContext).also { renderer = it }
                val bg = Backgrounds.get(applicationContext, cfg.wallpaper, w, h)
                r.draw(canvas, w, h, cfg, ZonedDateTime.now(), bg)
            } finally {
                sh.unlockCanvasAndPost(canvas)
            }
        }
    }
}
