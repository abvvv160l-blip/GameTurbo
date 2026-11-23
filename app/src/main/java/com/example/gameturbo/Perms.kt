package com.example.gameturbo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Kiểm tra và xin các quyền app cần. */
object Perms {
    /** Quyền vẽ cửa sổ nổi (bong bóng, menu, HUD) lên trên game. */
    fun canOverlay(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || try {
            Settings.canDrawOverlays(ctx)
        } catch (_: Throwable) {
            false
        }

    fun overlayIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Quyền hiện thông báo (Android 13+). Không có quyền này dịch vụ vẫn chạy, chỉ là thông báo bị ẩn. */
    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
