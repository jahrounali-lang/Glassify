@file:OptIn(ExperimentalMaterial3Api::class)

package com.liquidclock.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.liquidclock.model.ClockConfig
import com.liquidclock.render.Backgrounds
import com.liquidclock.render.GlassClockRenderer
import com.liquidclock.wallpaper.ClockWallpaperService
import kotlin.math.roundToInt

private val Bg = Color(0xFF07080C)
private val CardBg = Color(0xFF14161D)
private val Accent = Color(0xFFBFD9FF)
private val Muted = Color(0xFFB4B8C4)

@Composable
fun StudioTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Accent, onPrimary = Color.Black, background = Bg, surface = Bg,
        surfaceVariant = CardBg, onSurface = Color.White, onBackground = Color.White),
    content = content
)

private val tabs = listOf("Studio", "Glass", "Type", "Position", "Wallpaper", "Saved", "Apply")
private val ratios = listOf("19.5:9 (A17)" to 1080f / 2340f, "20:9" to 0.45f, "16:9" to 0.5625f, "Tablet" to 0.7f)

@Composable
fun StudioApp(vm: StudioViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var ratio by rememberSaveable { mutableStateOf(0) }
    var aod by rememberSaveable { mutableStateOf(false) }
    var handles by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Box(Modifier.fillMaxWidth().weight(0.46f).padding(10.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> ClockPreviewView(ctx).apply { onChange = { vm.setConfig(it) } } },
                update = { v ->
                    v.config = vm.config
                    v.mode = if (aod) GlassClockRenderer.Mode.AOD else GlassClockRenderer.Mode.LOCK
                    v.editing = handles && !aod && tab != 6
                    v.rev = vm.rev
                },
                modifier = Modifier.fillMaxHeight()
                    .aspectRatio(ratios[ratio].second, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
            )
        }
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Bg, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(
            Modifier.weight(0.54f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            vm.message?.let { Text(it, color = Accent, fontSize = 12.sp) }
            when (tab) {
                0 -> StudioTab(vm, aod, { aod = it }, ratio, { ratio = it }, handles, { handles = it })
                1 -> GlassTab(vm)
                2 -> TypeTab(vm)
                3 -> PositionTab(vm)
                4 -> WallpaperTab(vm)
                5 -> SavedTab(vm)
                else -> ApplyTab()
            }
        }
    }
}

// ---------- shared widgets ----------

@Composable
fun GlassSlider(label: String, value: Float, default: Float, fmt: (Float) -> String = { "${it.roundToInt()}%" }, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp)
            Text(fmt(value), color = Accent, fontSize = 14.sp)
            TextButton(onClick = { onChange(default) }, enabled = value != default, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Reset", fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", Modifier.clickable { onChange((value - 1f).coerceAtLeast(0f)) }.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 20.sp)
            Slider(value, { onChange(it) }, Modifier.weight(1f), valueRange = 0f..100f)
            Text("+", Modifier.clickable { onChange((value + 1f).coerceAtMost(100f)) }.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 20.sp)
        }
    }
}

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { i, o -> FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(o) }) }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
private fun Section(title: String) = Text(title, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun InfoCard(title: String, body: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp); Text(body, fontSize = 13.sp, color = Muted); content()
        }
    }
}

// ---------- tabs ----------

private val langs = listOf("" to "System", "en" to "English", "ar" to "العربية", "de" to "Deutsch", "fr" to "Français",
    "es" to "Español", "tr" to "Türkçe", "ru" to "Русский", "ja" to "日本語")
private val dateFormats = listOf("EEE, d MMM", "EEEE d MMMM", "d/M/yyyy", "MMM d")

@Composable
private fun StudioTab(vm: StudioViewModel, aod: Boolean, setAod: (Boolean) -> Unit, ratio: Int, setRatio: (Int) -> Unit, handles: Boolean, setHandles: (Boolean) -> Unit) {
    val c = vm.config
    Section("PREVIEW")
    Chips(listOf("Lock screen", "Always-on display"), if (aod) 1 else 0) { setAod(it == 1) }
    Chips(ratios.map { it.first }, ratio, setRatio)
    Toggle("Bounding box, drag handles & pinch", handles, setHandles)
    Section("TIME & DATE")
    Toggle("12-hour format", c.hour12) { v -> vm.update { it.copy(hour12 = v) } }
    Toggle("Leading zero", c.leadingZero) { v -> vm.update { it.copy(leadingZero = v) } }
    Toggle("Show seconds", c.seconds) { v -> vm.update { it.copy(seconds = v) } }
    Toggle("Show date", c.showDate) { v -> vm.update { it.copy(showDate = v) } }
    Chips(dateFormats, dateFormats.indexOf(c.dateFormat)) { i -> vm.update { it.copy(dateFormat = dateFormats[i]) } }
    OutlinedTextField(c.dateFormat, { v -> vm.update { it.copy(dateFormat = v) } }, label = { Text("Custom date format") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Chips(langs.map { it.second }, langs.indexOfFirst { it.first == c.language }.coerceAtLeast(0)) { i -> vm.update { it.copy(language = langs[i].first) } }
}

@Composable
private fun GlassTab(vm: StudioViewModel) {
    val c = vm.config; val d = remember { ClockConfig() }
    GlassSlider("Bezel Depth", c.bezel, d.bezel) { v -> vm.update { it.copy(bezel = v) } }
    GlassSlider("Refraction", c.refraction, d.refraction) { v -> vm.update { it.copy(refraction = v) } }
    GlassSlider("Blur", c.blur, d.blur) { v -> vm.update { it.copy(blur = v) } }
    GlassSlider("Frost", c.frost, d.frost) { v -> vm.update { it.copy(frost = v) } }
    GlassSlider("Border Radius", c.radius, d.radius) { v -> vm.update { it.copy(radius = v) } }
    GlassSlider("Material opacity", c.opacity, d.opacity) { v -> vm.update { it.copy(opacity = v) } }
    GlassSlider("Edge intensity", c.edge, d.edge) { v -> vm.update { it.copy(edge = v) } }
    GlassSlider("Reflection", c.reflection, d.reflection) { v -> vm.update { it.copy(reflection = v) } }
    GlassSlider("Glass thickness", c.thickness, d.thickness) { v -> vm.update { it.copy(thickness = v) } }
    GlassSlider("Highlight position", c.highlight, d.highlight, { "${(it * 3.6f).roundToInt()}°" }) { v -> vm.update { it.copy(highlight = v) } }
    GlassSlider("Shadow", c.shadow, d.shadow) { v -> vm.update { it.copy(shadow = v) } }
    OutlinedButton(onClick = {
        vm.update { it.copy(bezel = d.bezel, refraction = d.refraction, blur = d.blur, frost = d.frost, radius = d.radius,
            opacity = d.opacity, edge = d.edge, reflection = d.reflection, thickness = d.thickness, highlight = d.highlight, shadow = d.shadow) }
    }) { Text("Reset all glass values") }
}

@Composable
private fun TypeTab(vm: StudioViewModel) {
    val c = vm.config; val d = remember { ClockConfig() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFont) }
    Chips(listOf("GooglePixelInflat", "System font (explicit)"), if (c.fontSource == "pixel") 0 else 1) { i -> vm.update { it.copy(fontSource = if (i == 0) "pixel" else "system") } }
    if (c.fontSource == "pixel") {
        if (vm.fontAvailable) Text("GooglePixelInflat font file is loaded.", color = Accent, fontSize = 12.sp)
        else Text("GooglePixelInflat is NOT installed. The system font is shown only as a stand-in until you import the TTF/OTF " +
            "(or place it in app/src/main/assets/fonts/). The font is proprietary, so it is not bundled.", color = Color(0xFFFF8A80), fontSize = 12.sp)
    }
    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import font file (TTF/OTF)") }
    GlassSlider("Font weight", c.fontWeight, d.fontWeight, { "${(100 + it * 8).roundToInt()}" }) { v -> vm.update { it.copy(fontWeight = v) } }
    GlassSlider("Font size (within box)", c.fontSize, d.fontSize) { v -> vm.update { it.copy(fontSize = v) } }
    GlassSlider("Letter spacing", c.letterSpacing, d.letterSpacing, { "%.2f em".format(-0.1f + it / 100f * 0.3f) }) { v -> vm.update { it.copy(letterSpacing = v) } }
    GlassSlider("Digit spacing", c.digitSpacing, d.digitSpacing, { "%.2f em".format(it / 100f * 0.2f) }) { v -> vm.update { it.copy(digitSpacing = v) } }
    GlassSlider("Line height", c.lineHeight, d.lineHeight, { "%.2fx".format(0.85f + it / 100f * 0.6f) }) { v -> vm.update { it.copy(lineHeight = v) } }
    Section("TEXT ALIGNMENT")
    Chips(listOf("Left", "Center", "Right"), c.hAlign) { i -> vm.update { it.copy(hAlign = i) } }
}

@Composable
private fun PositionTab(vm: StudioViewModel) {
    val c = vm.config; val d = remember { ClockConfig() }
    Text("${(c.width / 100f * 1080).roundToInt()} × ${(c.height / 100f * 2340).roundToInt()} px on 1080×2340", color = Accent, fontSize = 12.sp)
    GlassSlider("Width", c.width, d.width) { v -> vm.update { it.copy(width = v.coerceAtLeast(10f)) } }
    GlassSlider("Height", c.height, d.height) { v -> vm.update { it.copy(height = v.coerceAtLeast(8f)) } }
    GlassSlider("Horizontal position", c.posX, d.posX) { v -> vm.update { it.copy(posX = v) } }
    GlassSlider("Vertical position", c.posY, d.posY) { v -> vm.update { it.copy(posY = v) } }
    GlassSlider("Rotation", c.rotation, d.rotation, { "${((it - 50f) * 0.9f).roundToInt()}°" }) { v -> vm.update { it.copy(rotation = v) } }
    Section("ORIENTATION")
    Chips(listOf("Stacked (HH / MM)", "Inline (HH:MM)"), c.orientation) { i -> vm.update { it.copy(orientation = i) } }
    Section("VERTICAL ALIGNMENT IN BOX")
    Chips(listOf("Top", "Middle", "Bottom"), c.vAlign) { i -> vm.update { it.copy(vAlign = i) } }
    Section("HORIZONTAL ALIGNMENT IN BOX")
    Chips(listOf("Left", "Center", "Right"), c.hAlign) { i -> vm.update { it.copy(hAlign = i) } }
    OutlinedButton(onClick = { vm.update { it.copy(posX = 50f) } }) { Text("Center horizontally") }
}

@Composable
private fun WallpaperTab(vm: StudioViewModel) {
    val ctx = LocalContext.current; val c = vm.config
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::importWallpaper) }
    val ids = Backgrounds.ids + (if (Backgrounds.customFile(ctx).exists()) listOf("custom") else emptyList())
    Text("The glass refracts this wallpaper. The live wallpaper draws it as well, so the refraction is real.", fontSize = 12.sp, color = Muted)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ids) { id ->
            val img = remember(id, vm.rev) { Backgrounds.get(ctx, id, 108, 234).asImageBitmap() }
            val sel = c.wallpaper == id
            Image(img, id, Modifier.height(130.dp).aspectRatio(108f / 234f).clip(RoundedCornerShape(14.dp))
                .then(if (sel) Modifier.border(2.dp, Accent, RoundedCornerShape(14.dp)) else Modifier)
                .clickable { vm.update { it.copy(wallpaper = id) } })
        }
    }
    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose your own image") }
}

@Composable
private fun SavedTab(vm: StudioViewModel) {
    var newName by remember { mutableStateOf("") }
    val c = vm.config; val list = vm.designs()
    OutlinedTextField(c.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Current design name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newName, { newName = it }, label = { Text("New design name") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { vm.saveAsNew(newName); newName = "" }) { Text("Save as new") }
    }
    Text("The selected design is the one the lock-screen wallpaper shows.", fontSize = 12.sp, color = Muted)
    list.forEach { d ->
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.name + if (d.id == c.id) "  ●" else "", Modifier.weight(1f))
                TextButton(onClick = { vm.select(d.id) }, enabled = d.id != c.id) { Text("Select") }
                TextButton(onClick = { vm.duplicate(d.id) }) { Text("Copy") }
                TextButton(onClick = { vm.delete(d.id) }, enabled = list.size > 1) { Text("Delete") }
            }
        }
    }
}

private fun wallpaperStatus(ctx: Context): List<String> {
    val wm = WallpaperManager.getInstance(ctx)
    val me = ComponentName(ctx, ClockWallpaperService::class.java)
    val home = wm.wallpaperInfo?.component == me
    val lock: Boolean? = if (Build.VERSION.SDK_INT >= 34) wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == me else null
    return listOf(
        "Home screen: " + if (home) "active" else "not active",
        "Lock screen: " + when (lock) { true -> "active"; false -> "not active"; null -> "unknown" }
    )
}

@Composable
private fun ApplyTab() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val status = remember(tick) { wallpaperStatus(ctx) }

    InfoCard("1 · REAL lock screen (live wallpaper)",
        "Android draws this clock behind the lock screen UI as a live wallpaper. PIN, password and biometrics are untouched. " +
            "In the picker, choose Lock screen (or Both).") {
        status.forEach { Text(it, color = Accent, fontSize = 13.sp) }
        Button(onClick = {
            val cn = ComponentName(ctx, ClockWallpaperService::class.java)
            try {
                ctx.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, cn))
            } catch (e: Exception) {
                runCatching { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
        }) { Text("Apply as live wallpaper") }
    }
    InfoCard("2 · Editor preview (NOT the system lock screen)",
        "The preview at the top uses the same renderer, but it is only an in-app preview. The Always-on display preview is also only a preview: AOD is owned by the system.")
    InfoCard("3 · Fallback (manual)",
        "If the picker does not offer the lock screen, open the system wallpaper picker and choose Liquid Glass Clock under live wallpapers.") {
        OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) } }) { Text("Open system wallpaper picker") }
    }
    InfoCard("Samsung limitations",
        "One UI draws its own lock-screen clock above any wallpaper, and no public API lets an app remove it. Place this clock away from the system clock (Position tab), " +
            "or change or hide the Samsung clock in Settings > Lock screen, or with Good Lock > LockStar. " +
            "Notifications, widgets and AOD stay system-owned. Whether Samsung accepts a third-party live wallpaper for the lock screen must be checked on the device.")
}
