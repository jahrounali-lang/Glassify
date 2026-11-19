package com.liquidclock.model

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the REAL current time/date lines. Nothing here is static text. */
object ClockText {
    data class Line(val text: String, val date: Boolean)

    fun lines(c: ClockConfig, now: ZonedDateTime): List<Line> {
        var h = now.hour
        if (c.hour12) { h %= 12; if (h == 0) h = 12 }
        val hh = if (c.leadingZero) String.format(Locale.ROOT, "%02d", h) else h.toString()
        val mm = String.format(Locale.ROOT, "%02d", now.minute)
        val ss = String.format(Locale.ROOT, "%02d", now.second)
        val out = ArrayList<Line>()
        if (c.orientation == 0) {
            out += Line(hh, false); out += Line(mm, false)
            if (c.seconds) out += Line(ss, false)
        } else {
            out += Line(if (c.seconds) "$hh:$mm:$ss" else "$hh:$mm", false)
        }
        if (c.showDate) out += Line(date(c, now), true)
        return out
    }

    private fun date(c: ClockConfig, now: ZonedDateTime): String = runCatching {
        val loc = if (c.language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(c.language)
        DateTimeFormatter.ofPattern(c.dateFormat, loc).format(now)
    }.getOrDefault("—")
}
