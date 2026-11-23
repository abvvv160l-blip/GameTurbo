package com.example.gameturbo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gameturbo.BuildConfig
import com.example.gameturbo.RootShell
import com.example.gameturbo.ShizukuStatus
import com.example.gameturbo.app
import com.example.gameturbo.core.Cores
import com.example.gameturbo.data.AppEntry
import com.example.gameturbo.data.Palette
import kotlinx.coroutines.launch
import java.util.Locale

private val pageNames = listOf("Giao diện", "Âm thanh", "Hiệu năng", "Root", "Về app")
private val pageIcons = listOf("🎨", "🔊", "⚡", "🔓", "ℹ️")

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var page by remember { mutableIntStateOf(0) }
    Row(modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(190.dp).fillMaxHeight().glass(26.dp).padding(12.dp)) {
            H2("Cài đặt")
            LensSegment(
                count = pageNames.size, selected = page, vertical = true,
                modifier = Modifier.fillMaxWidth().height((pageNames.size * 42).dp),
                onSelect = { page = it },
            ) { i, _ ->
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(pageIcons[i], 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Txt(pageNames[i], 13.sp, FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().glass(26.dp)) {
            Crossfade(page, label = "settingsPage") { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (p) {
                        0 -> PageTheme()
                        1 -> PageSound()
                        2 -> PagePerf()
                        3 -> PageRoot()
                        else -> PageAbout()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ Giao diện

@Composable
private fun BgTile(name: String, selected: Boolean, onClick: () -> Unit, preview: @Composable BoxScope.() -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .gtClickable(onClick = onClick)
            .size(104.dp, 66.dp)
            .clip(shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) ui.a1 else Color(0x33FFFFFF), shape)
    ) {
        preview()
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        ) { Txt(name, 10.sp, FontWeight.SemiBold, maxLines = 1) }
    }
}

private fun persistRead(ctx: Context, uri: Uri) {
    try {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Throwable) {
    }
}

private fun displayName(ctx: Context, uri: Uri): String {
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: ""
        }
    } catch (_: Throwable) {
    }
    return uri.lastPathSegment ?: "video"
}

@Composable
private fun PageTheme() {
    val ctx = LocalContext.current
    val st = ctx.app.settings
    var tab by remember { mutableIntStateOf(st.bgType.coerceIn(0, 2)) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            persistRead(ctx, uri)
            st.bgImageUri = uri.toString()
            st.bgImage = 4
            st.bgType = 1
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            persistRead(ctx, uri)
            st.bgVideoUri = uri.toString()
            st.bgVideoName = displayName(ctx, uri)
            st.bgType = 2
        }
    }

    H2("Màu và kính")
    Label("Màu chủ đề")
    Flow(hGap = 12.dp, vGap = 10.dp) {
        Palette.themes.forEachIndexed { i, p ->
            val sel = st.theme == i
            Box(
                Modifier
                    .gtClickable { st.theme = i }
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == 0) Brush.linearGradient(listOf(Color(p.blobs[1]), Color(p.blobs[0]), Color(p.blobs[2])))
                        else Brush.linearGradient(listOf(Color(p.a1), Color(p.a2)))
                    )
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color(0x44FFFFFF), CircleShape)
            )
        }
    }
    Txt(Palette.of(st.theme).name, 11.sp, FontWeight.SemiBold, Dim, Modifier.padding(top = 6.dp))

    Label("Màu kính")
    ChipRow(listOf("Trong suốt", "Tối", "Sáng", "Theo chủ đề"), st.tint) { st.tint = it }
    Label("Độ mờ kính")
    GtSlider(st.blur, { st.blur = it }, Modifier.fillMaxWidth(), "${(st.blur * 100).toInt()}%")
    Label("Độ tối của nền")
    GtSlider((st.dim / 0.8f).coerceIn(0f, 1f), { st.dim = it * 0.8f }, Modifier.fillMaxWidth(), "${(st.dim * 100).toInt()}%")
    Label("Chất lượng kính")
    ChipRow(listOf("Cao", "Thấp", "Tắt"), st.glassQuality) { st.glassQuality = it }
    Txt("Chọn Thấp hoặc Tắt nếu máy yếu, giao diện sẽ mượt và đỡ tốn pin hơn.", 10.5.sp, color = Faint, modifier = Modifier.padding(top = 4.dp))
    Line("Giao diện đỏ gaming khi Tối thượng", "Tự đổi sang tông đỏ khi bật Tối thượng, tắt thì về như cũ") {
        GtSwitch(st.ultSkin, { st.ultSkin = it })
    }

    Spacer(Modifier.height(10.dp))
    H2("Nền ứng dụng (nằm sau lớp kính)")
    LensSegment(3, tab, vertical = false, modifier = Modifier.fillMaxWidth().height(42.dp), onSelect = { tab = it }) { i, _ ->
        Txt(listOf("🎨 Màu", "🖼 Ảnh", "🎬 Video")[i], 12.sp, FontWeight.SemiBold)
    }
    Spacer(Modifier.height(10.dp))
    Flow(hGap = 10.dp, vGap = 10.dp) {
        when (tab) {
            0 -> BgColorNames.forEachIndexed { i, n ->
                BgTile(n, st.bgType == 0 && st.bgColor == i, { st.bgColor = i; st.bgType = 0 }) {
                    Box(Modifier.fillMaxSize().background(bgColorBrush(i)))
                }
            }
            1 -> {
                BgImageNames.forEachIndexed { i, n ->
                    BgTile(n, st.bgType == 1 && st.bgImage == i, { st.bgImage = i; st.bgType = 1 }) {
                        Canvas(Modifier.fillMaxSize()) { drawPreset(i, size) }
                    }
                }
                if (st.bgImageUri.isNotEmpty()) {
                    BgTile("Ảnh của bạn", st.bgType == 1 && st.bgImage == 4, { st.bgImage = 4; st.bgType = 1 }) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F66), Color(0xFF14183A)))), contentAlignment = Alignment.Center) { Txt("🖼", 22.sp) }
                    }
                }
                BgTile("＋ Từ máy bạn", false, { pickImage.launch(arrayOf("image/*")) }) {
                    Box(Modifier.fillMaxSize().background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) { Txt("＋", 22.sp) }
                }
            }
            else -> {
                if (st.bgVideoUri.isNotEmpty()) {
                    BgTile(st.bgVideoName.ifEmpty { "Video" }, st.bgType == 2, { st.bgType = 2 }) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0F6A5A), Color(0xFF7A2AD8)))), contentAlignment = Alignment.Center) { Txt("▶", 20.sp) }
                    }
                }
                BgTile("＋ Từ máy bạn", false, { pickVideo.launch(arrayOf("video/*")) }) {
                    Box(Modifier.fillMaxSize().background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) { Txt("＋", 22.sp) }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    if (tab == 2) {
        Line("Tạm dừng video khi chơi game", "Tiết kiệm GPU và pin") { GtSwitch(st.pauseVideoInGame, { st.pauseVideoInGame = it }) }
        Line("Phát âm thanh của video", "Dùng tiếng gốc trong file video") { GtSwitch(st.videoSound, { st.videoSound = it }) }
        Label("Âm lượng video")
        GtSlider(st.videoVolume, { st.videoVolume = it }, Modifier.fillMaxWidth(), "${(st.videoVolume * 100).toInt()}%", enabled = st.videoSound)
    }
    Txt(
        "Ảnh và video lấy từ thư viện máy bạn. Video nền chỉ chạy trong app, tự dừng khi vào game hoặc khi pin dưới 20%.",
        10.5.sp, color = Faint, modifier = Modifier.padding(top = 8.dp),
    )
}

// ------------------------------------------------------------------------------------------ Âm thanh

@Composable
private fun PageSound() {
    val app = LocalContext.current.app
    val st = app.settings
    H2("Âm thanh nút bấm")
    Line("Âm thanh khi chạm nút", "Tắt thì mọi nút im lặng") { GtSwitch(st.btnSound, { st.btnSound = it }) }
    Label("Kiểu âm")
    ChipRow(listOf("Kính", "Cơ học", "Gaming"), st.soundPack) {
        st.soundPack = it
        app.sounds.tickPreview()
    }
    Label("Âm lượng nút")
    GtSlider(st.btnVolume, { st.btnVolume = it }, Modifier.fillMaxWidth(), "${(st.btnVolume * 100).toInt()}%")
    Spacer(Modifier.height(8.dp))
    GtButton("▶ Nghe thử", compact = true) { app.sounds.tickPreview() }
    Txt("Âm lượng đi theo âm lượng media của máy. Máy đang ở chế độ im lặng hoặc rung thì app cũng im.", 10.5.sp, color = Faint, modifier = Modifier.padding(top = 8.dp))

    Spacer(Modifier.height(16.dp))
    H2("Âm thanh hiệu ứng")
    Line("Kích hoạt Tối thượng", "Tiếng tích tụ rồi nổ khi giao diện đổi sang đỏ") { GtSwitch(st.sfxUlt, { st.sfxUlt = it }) }
    Line("Cảnh báo nóng", "Bíp từ 72 °C, cảnh báo dài ở 80 °C") { GtSwitch(st.sfxWarn, { st.sfxWarn = it }) }
    Line("Báo khi tự về Ổn định", "Tiếng hạ xung rồi chuông nhẹ") { GtSwitch(st.sfxFall, { st.sfxFall = it }) }
    Line("Tiếng vào game", "Khi bấm Chơi ngay") { GtSwitch(st.sfxGame, { st.sfxGame = it }) }
    Line("Âm thanh video nền", "Chỉnh ở Giao diện › Nền ứng dụng › Video") { GtSwitch(st.videoSound, { st.videoSound = it }) }
}

// ------------------------------------------------------------------------------------------ Hiệu năng

@Composable
private fun PagePerf() {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    H2("Dọn app nền")
    Line("Dùng lệnh mạnh (force-stop)", "Mặc định dùng lệnh nhẹ, chỉ tắt app đang chạy nền") { GtSwitch(st.killStrong, { st.killStrong = it }) }
    Label("Khi nào dọn")
    ChipRow(listOf("Tự động khi vào game", "Chỉ khi bấm nút"), st.killTrigger) { st.killTrigger = it }
    Label("App được giữ lại")
    Txt("Luôn giữ: màn hình chính, bàn phím, điện thoại, tin nhắn, Shizuku, Game Turbo.", 10.5.sp, color = Faint)
    var q by remember { mutableStateOf("") }
    val all = remember(app.games.games, app.games.otherApps) {
        (app.games.games.map { AppEntry(it.pkg, it.label) } + app.games.otherApps).sortedBy { it.label.lowercase() }
    }
    val filtered = remember(q, all) {
        val n = norm(q)
        all.filter { n.isEmpty() || norm(it.label).contains(n) }
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 14.dp, vertical = 9.dp)) {
        SearchField(q, { q = it }, "Tìm app để giữ lại", Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF))) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(a.pkg, a.label, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Txt(a.label, 12.sp, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    GtSwitch(a.pkg in st.keepList, { st.toggleKeep(a.pkg) })
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    H2("Bảo vệ nhiệt")
    Line("Tự về Ổn định khi quá nóng", "CPU/SoC ≥ 80 °C (không đọc được thì dùng nhiệt độ pin ≥ 45 °C)") { GtSwitch(st.guardOn, { st.guardOn = it }) }

    Spacer(Modifier.height(14.dp))
    H2("Thử tốc độ lõi tính toán")
    Txt(
        "Lõi đang dùng: " + Cores.best.name + (if (Cores.rust == null) " (không tải được Rust, đang dùng Kotlin dự phòng)" else ""),
        11.sp, color = Dim,
    )
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BenchResult?>(null) }
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(8.dp))
    GtButton(if (running) "Đang chạy…" else "Chạy thử nghiệm", compact = true, enabled = !running) {
        running = true
        scope.launch {
            result = try {
                Benchmark.run()
            } catch (_: Throwable) {
                null
            }
            running = false
        }
    }
    result?.let { r ->
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Txt("Bài thử (${r.iterations} lần)", 10.5.sp, FontWeight.Bold, Faint, Modifier.weight(1f))
            Txt("Rust", 10.5.sp, FontWeight.Bold, Faint, Modifier.width(80.dp), align = TextAlign.End)
            Txt("Kotlin", 10.5.sp, FontWeight.Bold, Faint, Modifier.width(80.dp), align = TextAlign.End)
        }
        var rt = 0.0
        var kt = 0.0
        for (row in r.rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Txt(row.name, 11.5.sp, modifier = Modifier.weight(1f))
                Txt(if (row.rustMs >= 0) String.format(Locale.US, "%.1f ms", row.rustMs) else "—", 11.5.sp, modifier = Modifier.width(80.dp), align = TextAlign.End)
                Txt(String.format(Locale.US, "%.1f ms", row.kotlinMs), 11.5.sp, modifier = Modifier.width(80.dp), align = TextAlign.End)
            }
            if (row.rustMs >= 0) rt += row.rustMs
            kt += row.kotlinMs
        }
        Spacer(Modifier.height(6.dp))
        val verdict = when {
            !r.rustAvailable -> "Không có lõi Rust để so sánh."
            rt <= 0.0 -> ""
            rt < kt -> String.format(Locale.US, "Tổng: Rust nhanh hơn %.0f%% (%.1f ms so với %.1f ms).", (kt - rt) / kt * 100, rt, kt)
            else -> String.format(Locale.US, "Tổng: Kotlin nhanh hơn %.0f%% trên bài này (gọi JNI tốn thêm chi phí chuyển chuỗi).", (rt - kt) / rt * 100)
        }
        Txt(verdict, 12.sp, FontWeight.Bold)
        Txt(
            "Đây chỉ là tốc độ phần tính toán, mỗi lần chỉ vài chục micro giây. Thời gian thật của app chủ yếu nằm ở lệnh hệ thống, nên chênh lệch bạn cảm nhận được rất nhỏ.",
            10.5.sp, color = Faint, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ------------------------------------------------------------------------------------------ Root

@Composable
private fun PageRoot() {
    val app = LocalContext.current.app
    val st = app.settings
    val hasRoot = rememberHasRoot()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    H2("Root")
    Txt(
        "Root là tuỳ chọn. Không root: Tối thượng vẫn dùng Game Mode hiệu năng, hiệu năng cố định và tần số quét cao nhất. " +
            "Có root: thêm khoá xung CPU/GPU ở mức cao nhất.",
        12.sp, color = Dim,
    )
    Spacer(Modifier.height(8.dp))
    Txt("Hiện tại: " + if (hasRoot) "có quyền root ✓" else "chưa có quyền root", 13.sp, FontWeight.Bold)
    Txt("Nếu Shizuku đang chạy bằng root thì cũng đủ, không cần bật su ở đây.", 10.5.sp, color = Faint, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.height(10.dp))
    Line("Dùng su khi mở app", "Chỉ bật nếu máy đã root (Magisk, KernelSU…)") { GtSwitch(st.rootEnabled, { st.rootEnabled = it }) }
    Spacer(Modifier.height(6.dp))
    GtButton(if (checking) "Đang kiểm tra…" else "Kiểm tra root", compact = true, enabled = !checking) {
        checking = true
        st.rootEnabled = true
        scope.launch {
            val ok = RootShell.check()
            msg = if (ok) "Đã có root. Khoá xung dùng được." else "Không lấy được root. Máy chưa root hoặc bạn đã từ chối."
            checking = false
        }
    }
    if (msg.isNotEmpty()) Txt(msg, 12.sp, FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Txt(
        "Khoá xung ghi vào sysfs của CPU/GPU và tự khôi phục khi thoát game. Máy sẽ nóng và hao pin hơn.",
        10.5.sp, color = Faint, modifier = Modifier.padding(top = 10.dp),
    )
}

// ------------------------------------------------------------------------------------------ Về app

@Composable
private fun PageAbout() {
    val ctx = LocalContext.current
    val app = ctx.app
    val st by app.shizuku.status.collectAsStateCompat()
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf(app.lastCrash()) }
    var shizukuHelp by remember { mutableStateOf(false) }
    H2("Game Turbo")
    Txt("Phiên bản ${BuildConfig.VERSION_NAME}", 12.sp, color = Dim)
    Txt("Lõi tính toán: ${Cores.best.name}", 12.sp, color = Dim)
    Txt("Kết nối: " + shizukuStatusText(st, app.shizuku.isRootMode, RootShell.available), 12.sp, color = Dim)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GtButton("Hướng dẫn Shizuku", compact = true) { shizukuHelp = true }
        GtButton("Quét lại game", compact = true) { scope.launch { app.games.reload() } }
        if (st == ShizukuStatus.NEED_PERMISSION) GtButton("Cấp quyền Shizuku", primary = true, compact = true) { app.shizuku.requestPermission() }
    }
    Spacer(Modifier.height(16.dp))
    H2("Lỗi gần nhất")
    val c = crash
    if (c == null) {
        Txt("Chưa có lỗi nào làm app thoát bất ngờ.", 12.sp, color = Dim)
    } else {
        Txt("Nếu app từng thoát đột ngột, hãy bấm Sao chép và gửi nội dung này để mình sửa.", 10.5.sp, color = Faint)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33000000)).verticalScroll(rememberScrollState()).padding(10.dp)) {
            Txt(c.takeLast(2500), 10.sp, color = Color(0xFFFFD0C8))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GtButton("Sao chép", compact = true, primary = true) {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Game Turbo lỗi", c))
                    app.controller.emit(com.example.gameturbo.engine.TurboEvent.Toast("Đã sao chép nội dung lỗi"))
                } catch (_: Throwable) {
                }
            }
            GtButton("Xoá", compact = true) {
                app.clearCrash()
                crash = null
            }
        }
    }
    if (shizukuHelp) ShizukuDialog { shizukuHelp = false }
}
