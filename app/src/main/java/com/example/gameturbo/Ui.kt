package com.example.gameturbo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

private const val HOT_CELSIUS = 43f

private val SCALE_OPTIONS = listOf(
    "Gốc" to 1f, "90%" to 0.9f, "80%" to 0.8f, "70%" to 0.7f, "60%" to 0.6f, "50%" to 0.5f,
)
private val FPS_OPTIONS = listOf(
    "Mặc định" to 0, "30" to 30, "45" to 45, "60" to 60, "90" to 90, "120" to 120,
)
private val REFRESH_OPTIONS = listOf("Mặc định" to 0, "60 Hz" to 60, "90 Hz" to 90, "120 Hz" to 120)
private val RESOLUTION_OPTIONS = listOf("Gốc" to 100, "90%" to 90, "80%" to 80, "70%" to 70)

@Composable
fun GameTurboApp(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ready = ui.status == ShizukuStatus.READY
    val temp = ui.tempC

    val visibleApps = remember(ui.apps, ui.query) {
        val q = ui.query.trim()
        if (q.isEmpty()) ui.apps
        else ui.apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(ui.busy) }
            item { StatusCard(ui, vm) }
            if (temp != null && temp >= HOT_CELSIUS) {
                item { HotBanner(temp, ready, vm) }
            }
            item { SystemCard(ui, ready, vm) }
            item {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Tìm game hoặc app") },
                )
            }
            if (ui.loadingApps) {
                item { Hint("Đang tải danh sách app...") }
            } else if (visibleApps.isEmpty()) {
                item { Hint("Không tìm thấy app nào.") }
            }
            items(visibleApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    profile = ui.profiles[app.packageName] ?: GameProfile(),
                    expanded = ui.selected == app.packageName,
                    ready = ready,
                    vm = vm,
                )
            }
            item { LogCard(ui.log) }
        }
    }
}

// ------------------------------------------------------------------ các khối giao diện

@Composable
private fun Header(busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "⚡ Game Turbo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Tối ưu hiệu năng game qua Shizuku",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusCard(ui: UiState, vm: MainViewModel) {
    val (dot, title, subtitle) = when (ui.status) {
        ShizukuStatus.READY -> Triple(Color(0xFF4CD964), "Shizuku đã kết nối", "Sẵn sàng chạy lệnh")
        ShizukuStatus.CONNECTING -> Triple(Color(0xFFFFC107), "Đang kết nối...", "Vui lòng chờ")
        ShizukuStatus.NEED_PERMISSION -> Triple(Color(0xFFFFC107), "Cần cấp quyền", "Cho phép Game Turbo dùng Shizuku")
        ShizukuStatus.UNAVAILABLE -> Triple(
            Color(0xFFFF5C5C),
            if (ui.shizukuInstalled) "Shizuku chưa chạy" else "Chưa cài Shizuku",
            if (ui.shizukuInstalled) "Mở Shizuku rồi bấm Start" else "Cài Shizuku để dùng các tính năng",
        )
    }

    TCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (ui.status) {
                ShizukuStatus.NEED_PERMISSION -> Button(onClick = vm::requestPermission) { Text("Cấp quyền") }
                ShizukuStatus.UNAVAILABLE ->
                    if (ui.shizukuInstalled) Button(onClick = vm::openShizuku) { Text("Mở Shizuku") }
                    else Button(onClick = vm::openShizukuDownload) { Text("Tải Shizuku") }
                else -> Unit
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Nhiệt độ pin", ui.tempC?.let { "%.1f °C".format(it) } ?: "--", Modifier.weight(1f))
            Stat("Trạng thái nhiệt", thermalLabel(ui.thermalStatus), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HotBanner(temp: Float, ready: Boolean, vm: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Máy đang nóng (${"%.1f".format(temp)} °C)",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Nên tắt Turbo để tránh bị giảm xung, hao pin và hại pin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = vm::restoreAll,
                enabled = ready,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Khôi phục tất cả") }
        }
    }
}

@Composable
private fun SystemCard(ui: UiState, ready: Boolean, vm: MainViewModel) {
    TCard {
        Text("Hệ thống", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))
        Label("Tần số quét tối thiểu")
        ChipRow(REFRESH_OPTIONS, ui.refreshRate, ready) { vm.setRefreshRate(it) }
        Text(
            "Một số hãng (Samsung, Xiaomi...) bỏ qua cài đặt này.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hiệu năng cố định", fontWeight = FontWeight.Medium)
                Text(
                    "Giữ xung nhịp ổn định, ít bị giảm xung. Chỉ một số máy hỗ trợ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = ui.fixedPerformance,
                onCheckedChange = { vm.setFixedPerformance(it) },
                enabled = ready,
            )
        }

        Spacer(Modifier.height(12.dp))
        Label("Độ phân giải toàn màn hình")
        ChipRow(RESOLUTION_OPTIONS, ui.resolutionPercent, ready) { vm.setResolution(it) }
        Text(
            "Ảnh hưởng cả hệ thống. Nếu hiển thị lỗi, chạy: adb shell wm size reset",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::killBackground, enabled = ready, modifier = Modifier.weight(1f)) {
                Text("Dọn RAM")
            }
            Button(onClick = vm::restoreAll, enabled = ready, modifier = Modifier.weight(1f)) {
                Text("Khôi phục tất cả")
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    profile: GameProfile,
    expanded: Boolean,
    ready: Boolean,
    vm: MainViewModel,
) {
    TCard(Modifier.clickable { vm.select(app.packageName) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (app.isGame) {
                    Text("GAME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (!profile.isDefault) {
                    Text(
                        profileSummary(profile),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Label("Độ phân giải game")
            ChipRow(SCALE_OPTIONS, profile.downscale, true) { vm.setDownscale(app.packageName, it) }

            Spacer(Modifier.height(8.dp))
            Label("Giới hạn FPS")
            ChipRow(FPS_OPTIONS, profile.fps, true) { vm.setFps(app.packageName, it) }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.applyGame(app.packageName, true) },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) { Text("Chơi ngay") }
                OutlinedButton(
                    onClick = { vm.applyGame(app.packageName, false) },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) { Text("Áp dụng") }
                TextButton(onClick = { vm.resetGame(app.packageName) }, enabled = ready) { Text("Đặt lại") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Cần đóng hẳn game rồi mở lại thì thay đổi mới có hiệu lực. " +
                    "Chỉ game hỗ trợ Game Mode mới đổi độ phân giải/FPS (Android 13 trở lên).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(44.dp))
    } else {
        Box(Modifier.size(44.dp))
    }
}

@Composable
private fun LogCard(log: List<String>) {
    TCard {
        Text("Nhật ký lệnh", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                if (log.isEmpty()) "Chưa có lệnh nào." else log.joinToString("\n\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ thành phần dùng chung

@Composable
private fun TCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(label) },
            )
        }
    }
}

private fun profileSummary(p: GameProfile): String {
    val parts = mutableListOf<String>()
    if (p.downscale < 0.999f) parts += "${(p.downscale * 100).roundToInt()}%"
    if (p.fps > 0) parts += "${p.fps} FPS"
    return parts.joinToString(" · ")
}

private fun thermalLabel(status: Int): String = when (status) {
    0 -> "Bình thường"
    1 -> "Hơi ấm"
    2 -> "Nóng vừa"
    3 -> "Nóng"
    4 -> "Rất nóng"
    5 -> "Khẩn cấp"
    6 -> "Sắp tắt máy"
    else -> "Không rõ"
}
