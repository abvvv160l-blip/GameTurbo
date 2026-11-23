package com.example.gameturbo.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.gameturbo.Perms
import com.example.gameturbo.app
import com.example.gameturbo.data.GameInfo
import com.example.gameturbo.engine.TurboEvent
import com.example.gameturbo.service.TurboService
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

private val modeEmoji = listOf("🔋", "⚖️", "🚀", "🎛️")
private val modeNames = listOf("Tiết kiệm pin", "Ổn định", "Tối thượng", "Tùy chỉnh")
private val modeSubs = listOf("mát, bền pin", "mượt, mát", "root · ADB", "tự chọn")
private val modeInfo = listOf(
    "Tối đa 30 FPS · độ phân giải ≤70% · màn hình 60 Hz. Mát máy và tiết kiệm pin.",
    "Tối đa 60 FPS · độ phân giải ≤90%. Mượt, mát, tự giảm tải khi máy nóng.",
    "⚠ Dễ nóng máy. Game Mode hiệu năng + hiệu năng cố định + tần số quét cao nhất; có root thì khoá xung. Tự về Ổn định khi CPU ≥ 80 °C.",
    "Bạn tự chọn Game Mode, tần số quét, hiệu năng cố định, khoá xung (cột Hệ thống bên phải).",
)

val resValues = listOf(100, 90, 80, 70, 60)
val resLabels = listOf("Gốc", "90%", "80%", "70%", "60%")
val fpsValues = listOf(0, 30, 60, 90)
val fpsLabels = listOf("Mặc định", "30", "60", "90")

/** Icon game thật của máy; chưa tải xong thì hiện ô màu có chữ cái đầu. */
@Composable
fun GameIcon(pkg: String, label: String, size: Dp, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val icon by produceState<ImageBitmap?>(null, pkg) { value = app.games.icon(pkg) }
    val ic = icon
    val shape = RoundedCornerShape(size * 0.24f)
    if (ic != null) {
        Image(ic, null, modifier.size(size).clip(shape))
    } else {
        val h = (pkg.hashCode() and 0x7fffffff) % 360
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color.hsv(h.toFloat(), 0.55f, 1f), Color.hsv(((h + 40) % 360).toFloat(), 0.8f, 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Txt(label.take(1).uppercase(), (size.value * 0.46f).sp, FontWeight.Black, Color.White)
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.app
    var addDialog by remember { mutableStateOf(false) }
    var permDialog by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<GameInfo?>(null) }
    val games = app.games.games

    Row(modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ModesPanel(Modifier.width(196.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (games.isEmpty()) {
                EmptyGames(Modifier.weight(1f).fillMaxWidth()) { addDialog = true }
            } else {
                val sel = app.settings.selectedGame
                val idx = games.indexOfFirst { it.pkg == sel }.let { if (it < 0) 0 else it }
                GameCarousel(
                    games = games,
                    index = idx,
                    onIndex = { app.settings.selectedGame = games[it].pkg },
                    onAdd = { addDialog = true },
                    onOptions = { optionsFor = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                DetailPanel(games[idx], onNeedPermission = { permDialog = true })
            }
        }
        SystemPanel(Modifier.width(212.dp).fillMaxHeight())
    }

    if (addDialog) AddGameDialog { addDialog = false }
    if (permDialog) OverlayPermissionDialog { permDialog = false }
    optionsFor?.let { g -> GameOptionsDialog(g) { optionsFor = null } }
}

// ------------------------------------------------------------------------------------------ chế độ

@Composable
private fun ModesPanel(modifier: Modifier) {
    val app = LocalContext.current.app
    val st = app.settings
    val mode = st.mode
    Column(modifier.glass(26.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
        H2("Chế độ")
        LensSegment(
            count = 4, selected = mode, vertical = true,
            modifier = Modifier.fillMaxWidth().height(188.dp),
            tickFor = { it != 2 },
            onSelect = { app.controller.setMode(it) },
        ) { i, sel ->
            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(modeEmoji[i], 18.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Txt(modeNames[i], 13.sp, FontWeight.SemiBold, maxLines = 1)
                    Txt(modeSubs[i], 10.sp, color = if (sel) Color.White.copy(alpha = 0.85f) else Faint, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val warn = mode == 2
        val shape = RoundedCornerShape(14.dp)
        Crossfade(targetState = mode, label = "info") { m ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (m == 2) Color(0x33FF3A2A) else Color(0x1AFFFFFF))
                    .border(1.dp, if (m == 2) Color(0x66FF6A5A) else Color(0x22FFFFFF), shape)
                    .padding(10.dp)
            ) {
                Txt(modeInfo[m.coerceIn(0, 3)], 11.sp, color = if (m == 2) Color(0xFFFFC8C0) else Dim)
            }
        }
        if (mode == 3) {
            Label("Game Mode")
            ChipRow(listOf("Không đổi", "Pin", "Ổn định", "Hiệu năng"), st.customGm, compact = true) {
                st.customGm = it
                app.controller.reapply()
            }
        }
        if (warn) Spacer(Modifier.height(2.dp))
    }
}

// ------------------------------------------------------------------------------------------ hệ thống

@Composable
private fun SystemPanel(modifier: Modifier) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    val c = app.controller
    val hasRoot = rememberHasRoot()
    val maxHz = remember { maxRefreshHz(ctx) }
    val hzVals = listOf(0, 60, 90, 120)
    val hzSel = hzVals.indexOf(st.minHz).let { if (it < 0) 0 else it }
    val disabled = hzVals.indices.filter { hzVals[it] > 0 && maxHz > 0 && hzVals[it] > maxHz + 1 }.toSet()
    val keepCount = remember(st.keepList) { c.defaultKeep().size + st.keepList.size }
    val maxA = if (Build.VERSION.SDK_INT >= 31) 0.8f else 1f

    Column(modifier.glass(26.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
        H2("Hệ thống")
        Label("Tần số quét tối thiểu")
        ChipRow(listOf("Mặc định", "60", "90", "120"), hzSel, disabled, compact = true) {
            st.minHz = hzVals[it]
            c.reapply()
        }
        Line("Hiệu năng cố định") { GtSwitch(st.fixedPerf, { st.fixedPerf = it; c.reapply() }) }
        Line("Tắt app nền", "Giữ lại $keepCount app") { GtSwitch(st.killBg, { st.killBg = it }) }
        Line("Hiển thị HUD") { GtSwitch(st.hudOn, { st.hudOn = it }) }
        Label("Độ mờ HUD")
        val v = ((st.hudAlpha - 0.2f) / (maxA - 0.2f)).coerceIn(0f, 1f)
        GtSlider(v, { st.hudAlpha = 0.2f + it * (maxA - 0.2f) }, Modifier.fillMaxWidth(), "${(st.hudAlpha.coerceAtMost(maxA) * 100).toInt()}%")
        Line("Khoá xung", if (hasRoot) "giữ CPU/GPU ở xung cao nhất" else "cần root") {
            GtSwitch(st.lockClock && hasRoot, { st.lockClock = it; c.reapply() }, enabled = hasRoot)
        }
    }
}

// ------------------------------------------------------------------------------------------ carousel

private val offsF = floatArrayOf(0f, 1.5f, 2.5f, 3.25f)
private val scaleF = floatArrayOf(1.12f, 0.8f, 0.62f, 0.5f)
private val alphaF = floatArrayOf(1f, 0.92f, 0.6f, 0f)

private fun interp(arr: FloatArray, a: Float): Float {
    val x = a.coerceIn(0f, 3f)
    val i = floor(x).toInt().coerceAtMost(2)
    val f = x - i
    return arr[i] + (arr[i + 1] - arr[i]) * f
}

@Composable
private fun GameCarousel(
    games: List<GameInfo>,
    index: Int,
    onIndex: (Int) -> Unit,
    onAdd: () -> Unit,
    onOptions: (GameInfo) -> Unit,
    modifier: Modifier,
) {
    val app = LocalContext.current.app
    val total = games.size + 1 // thẻ cuối là "Thêm game"
    val cur by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.8f, stiffness = 260f), label = "car")
    BoxWithConstraints(modifier) {
        val cs = min(maxHeight.value * 0.76f, 112f).dp
        var acc by remember { mutableStateOf(0f) }
        val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { (cs * 0.8f).toPx() }
        fun move(d: Int) {
            val n = (index + d).coerceIn(0, games.size - 1)
            if (n != index) {
                app.sounds.tick()
                onIndex(n)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(index, games.size) {
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { acc = 0f }) { change, dx ->
                        change.consume()
                        acc += dx
                        while (acc <= -stepPx) { acc += stepPx; move(1) }
                        while (acc >= stepPx) { acc -= stepPx; move(-1) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            for (i in 0 until total) {
                val d = i - cur
                val a = min(abs(d), 3f)
                val alpha = if (abs(d) > 3f) 0f else interp(alphaF, a)
                if (alpha <= 0.01f) continue
                val x = cs * (interp(offsF, a) * (if (d < 0) -1f else 1f))
                val sc = interp(scaleF, a)
                val z = 10f - abs(d)
                val isAdd = i == games.size
                Box(
                    Modifier
                        .zIndex(z)
                        .offset(x = x)
                        .graphicsLayer {
                            scaleX = sc
                            scaleY = sc
                            this.alpha = alpha
                        }
                ) {
                    if (isAdd) {
                        AddCard(cs, onAdd)
                    } else {
                        val g = games[i]
                        GameCard(g, cs, selected = i == index, onClick = { if (i == index) {} else onIndex(i) }, onLong = { onOptions(g) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(g: GameInfo, cs: Dp, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val ui = LocalUi.current
    Column(
        Modifier
            .gtClickable(onLongClick = onLong, onClick = onClick)
            .width(cs)
            .glass(24.dp)
            .then(if (selected) Modifier.border(2.dp, ui.a1.copy(alpha = 0.85f), RoundedCornerShape(24.dp)) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameIcon(g.pkg, g.label, cs * 0.52f)
        Spacer(Modifier.height(6.dp))
        Txt(g.label, 11.sp, FontWeight.Bold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Txt("GAME", 8.sp, FontWeight.SemiBold, Faint)
    }
}

@Composable
private fun AddCard(cs: Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .gtClickable(onClick = onClick)
            .width(cs)
            .height(cs * 0.95f)
            .glass(24.dp)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt("＋", 30.sp, FontWeight.Light)
        Txt("Thêm game", 11.sp, FontWeight.SemiBold, Dim, maxLines = 1)
    }
}

@Composable
private fun EmptyGames(modifier: Modifier, onAdd: () -> Unit) {
    val app = LocalContext.current.app
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).glass(28.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(if (app.games.loading) "Đang tìm game trên máy…" else "Chưa thấy game nào", 15.sp, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Txt(
                "Game được nhận từ danh mục ứng dụng của máy. Game không có danh mục thì bấm Thêm game để tự chọn.",
                11.sp, color = Dim, align = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            GtButton("＋ Thêm game", primary = true, onClick = onAdd)
        }
    }
}

// ------------------------------------------------------------------------------------------ chi tiết game

@Composable
private fun DetailPanel(g: GameInfo, onNeedPermission: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    val c = app.controller
    val hasShell = rememberHasShell()
    val res = st.resOf(g.pkg)
    val fps = st.fpsOf(g.pkg)
    val ms = st.playMs[g.pkg] ?: 0L
    val last = st.lastPlayed[g.pkg] ?: 0L
    val session by c.session.collectAsStateCompat()

    fun changed() {
        val s = session
        if (s != null && s.pkg == g.pkg) c.reapply()
    }

    Column(Modifier.fillMaxWidth().glass(26.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(g.label, 15.sp, FontWeight.Bold, maxLines = 1)
                val sum = (if (res == 100) "Gốc" else "$res%") + " · " + (if (fps == 0) "FPS gốc" else "$fps FPS")
                val extra = when {
                    !hasShell -> " · cần Shizuku"
                    ms > 0 -> " · đã chơi ${fmtDuration(ms)}" + (if (last > 0) ", ${fmtLast(last)}" else "")
                    else -> ""
                }
                Txt(sum + extra, 10.5.sp, color = Dim, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            GtButton("Đặt lại", compact = true) { c.resetGame(g.pkg) }
            Spacer(Modifier.width(6.dp))
            GtButton("Áp dụng", compact = true) { c.applyNow(g.pkg) }
            Spacer(Modifier.width(6.dp))
            GtButton("▶ Chơi ngay", primary = true, compact = true) {
                if (!Perms.canOverlay(ctx)) {
                    onNeedPermission()
                } else {
                    TurboService.start(ctx)
                    if (!c.play(g.pkg)) c.emit(TurboEvent.Toast("Không mở được ${g.label}", 2))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Độ phân giải", 10.5.sp, FontWeight.SemiBold, Faint, Modifier.width(78.dp))
            ChipRow(resLabels, resValues.indexOf(res), compact = true) {
                st.setRes(g.pkg, resValues[it])
                changed()
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Giới hạn FPS", 10.5.sp, FontWeight.SemiBold, Faint, Modifier.width(78.dp))
            ChipRow(fpsLabels, fpsValues.indexOf(fps), compact = true) {
                st.setFps(g.pkg, fpsValues[it])
                changed()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ hộp thoại

@Composable
fun OverlayPermissionDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    GlassDialog(onDismiss) {
        H2("Cần quyền hiển thị trên ứng dụng khác")
        Txt(
            "Bong bóng, menu và HUD trong game là cửa sổ nổi nên Android yêu cầu quyền này. " +
                "Bấm Mở cài đặt, bật công tắc cho Game Turbo rồi quay lại app.",
            12.sp, color = Dim,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GtButton("Để sau", compact = true, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            GtButton("Mở cài đặt", primary = true, compact = true) {
                try {
                    ctx.startActivity(Perms.overlayIntent(ctx))
                } catch (_: Throwable) {
                }
                onDismiss()
            }
        }
    }
}

@Composable
fun AddGameDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val list = remember(q, app.games.otherApps) {
        val n = norm(q)
        app.games.otherApps.filter { n.isEmpty() || norm(it.label).contains(n) }
    }
    GlassDialog(onDismiss, width = 380) {
        H2("Thêm game")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 14.dp, vertical = 9.dp)
        ) { SearchField(q, { q = it }, "Tìm app trong máy", Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
            items(list, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(a.pkg, a.label, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Txt(a.label, 13.sp, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    GtButton("Thêm", compact = true) {
                        app.settings.addGame(a.pkg)
                        app.settings.selectedGame = a.pkg
                        scope.launch { app.games.reload() }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GtButton("Đóng", compact = true, onClick = onDismiss) }
    }
}

@Composable
private fun GameOptionsDialog(g: GameInfo, onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    GlassDialog(onDismiss, width = 340) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameIcon(g.pkg, g.label, 40.dp)
            Spacer(Modifier.width(10.dp))
            Txt(g.label, 15.sp, FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        GtButton("Xoá thời gian chơi", Modifier.fillMaxWidth()) {
            app.settings.clearPlay(g.pkg)
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        GtButton("Ẩn khỏi danh sách game", Modifier.fillMaxWidth()) {
            app.settings.hideGame(g.pkg)
            scope.launch { app.games.reload() }
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        GtButton("Đóng", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
