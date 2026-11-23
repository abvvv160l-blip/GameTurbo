package com.example.gameturbo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gameturbo.Perms
import com.example.gameturbo.ShizukuStatus
import com.example.gameturbo.app

fun shizukuStatusText(st: ShizukuStatus, root: Boolean, suRoot: Boolean): String = when {
    st == ShizukuStatus.READY && root -> "Shizuku · root"
    st == ShizukuStatus.READY -> "Shizuku · ADB"
    suRoot -> "Root (su)"
    st == ShizukuStatus.NEED_PERMISSION -> "Cần cấp quyền Shizuku"
    st == ShizukuStatus.CONNECTING -> "Đang kết nối Shizuku…"
    else -> "Chưa có Shizuku"
}

@Composable
fun ShizukuDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st by app.shizuku.status.collectAsStateCompat()
    rememberResumeTick()
    GlassDialog(onDismiss, width = 460) {
        H2("Kết nối Shizuku")
        Txt(
            "Game Turbo dùng Shizuku để chạy lệnh hệ thống (đổi độ phân giải, Game Mode, tần số quét, dọn app nền) mà không cần root.",
            12.sp, color = Dim,
        )
        Spacer(Modifier.height(8.dp))
        Txt("Trạng thái: " + shizukuStatusText(st, app.shizuku.isRootMode, com.example.gameturbo.RootShell.available), 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Txt("1. Cài app Shizuku (nút bên dưới).", 12.sp)
        Txt("2. Android 11 trở lên: bật Tùy chọn nhà phát triển → Gỡ lỗi không dây, mở Shizuku và chọn Bắt đầu qua gỡ lỗi không dây.", 12.sp)
        Txt("    Android 6–10: cần cắm máy tính chạy lệnh ADB mà Shizuku hiển thị, và làm lại mỗi lần khởi động lại máy.", 12.sp, color = Dim)
        Txt("3. Quay lại đây rồi bấm Cấp quyền.", 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GtButton("Đóng", compact = true, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            GtButton("Kiểm tra lại", compact = true) { app.shizuku.refresh() }
            Spacer(Modifier.width(8.dp))
            if (st == ShizukuStatus.NEED_PERMISSION) {
                GtButton("Cấp quyền", primary = true, compact = true) { app.shizuku.requestPermission() }
            } else {
                GtButton(if (shizukuInstalled(ctx)) "Mở Shizuku" else "Tải Shizuku", primary = true, compact = true) { openShizuku(ctx) }
            }
        }
    }
}

@Composable
private fun StepRow(title: String, sub: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(if (ok) "✅" else "⬜", 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, 13.sp, FontWeight.SemiBold)
            Txt(sub, 10.5.sp, color = Faint)
        }
        if (!ok && action != null) GtButton(action, compact = true, onClick = onAction)
    }
}

@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st by app.shizuku.status.collectAsStateCompat()
    val tick = rememberResumeTick()
    val overlay = remember(tick) { Perms.canOverlay(ctx) }
    val notif = remember(tick) { Perms.canNotify(ctx) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    GlassDialog(onDone, width = 480) {
        H2("Chào mừng đến Game Turbo")
        Txt("Ba bước để mọi thứ chạy đủ. Bạn có thể làm sau, app vẫn mở được.", 12.sp, color = Dim)
        Spacer(Modifier.height(10.dp))
        StepRow(
            "Kết nối Shizuku", "Để đổi độ phân giải, Game Mode, dọn app nền", st == ShizukuStatus.READY || app.exec.hasShell,
            if (st == ShizukuStatus.NEED_PERMISSION) "Cấp quyền" else if (shizukuInstalled(ctx)) "Mở Shizuku" else "Tải Shizuku",
        ) { if (st == ShizukuStatus.NEED_PERMISSION) app.shizuku.requestPermission() else openShizuku(ctx) }
        Spacer(Modifier.height(10.dp))
        StepRow("Hiển thị trên ứng dụng khác", "Để vẽ bong bóng, menu và HUD trong game", overlay, "Mở cài đặt") {
            try {
                ctx.startActivity(Perms.overlayIntent(ctx))
            } catch (_: Throwable) {
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(10.dp))
            StepRow("Thông báo", "Để dịch vụ nền hiện thông báo (có thể bỏ qua)", notif, "Cho phép") {
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GtButton("Xong", primary = true, compact = true, onClick = onDone)
        }
    }
}
