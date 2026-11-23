package com.example.gameturbo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.gameturbo.MainActivity
import com.example.gameturbo.Perms
import com.example.gameturbo.R
import com.example.gameturbo.app
import com.example.gameturbo.engine.TurboEvent
import com.example.gameturbo.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dịch vụ chạy nền: nhận biết game đang mở, đo FPS/CPU/GPU, giữ bong bóng + HUD,
 * và hạ chế độ khi máy nóng.
 */
class TurboService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.gameturbo.STOP"
        const val ACTION_EDIT_HUD = "com.example.gameturbo.EDIT_HUD"
        private const val CHANNEL = "gt_service"
        private const val NOTIF_ID = 1001

        private val _running = MutableStateFlow(false)

        /** Dịch vụ đang chạy hay không (giao diện dùng để hiện công tắc "Theo dõi game"). */
        val running: StateFlow<Boolean> = _running

        fun start(ctx: Context) {
            val i = Intent(ctx, TurboService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Throwable) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, TurboService::class.java))
            } catch (_: Throwable) {
            }
        }

        /** Hiện HUD trên màn hình để kéo/phóng to, chạm ✓ để lưu. Cần quyền cửa sổ nổi. */
        fun editHud(ctx: Context) {
            val i = Intent(ctx, TurboService::class.java).setAction(ACTION_EDIT_HUD)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Throwable) {
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var overlay: OverlayManager? = null
    private val jobs = ArrayList<Job>()
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val c = app.controller
            c.scope.launch {
                c.onGameBack("Dừng dịch vụ")
            }
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        enterForeground()
        if (!started) {
            started = true
            overlay = OverlayManager(this, app.controller, app.settings, app.sounds)
            beginWork()
        }
        if (intent?.action == ACTION_EDIT_HUD) {
            val ov = overlay
            if (ov != null) {
                if (Perms.canOverlay(this)) ov.editHud { } else app.controller.emit(TurboEvent.Toast("Cần cấp quyền hiển thị trên ứng dụng khác", 1))
            }
        }
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        }
    }

    private fun enterForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TurboService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
        @Suppress("DEPRECATION")
        b.addAction(Notification.Action.Builder(0, getString(R.string.notif_stop), stop).build())
        val n = b.build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (_: Throwable) {
            // Một số máy từ chối foreground service: vẫn chạy tiếp như service thường
        }
    }

    private fun beginWork() {
        val c = app.controller
        val games = app.games

        jobs += scope.launch { if (games.games.isEmpty()) games.reload() }

        // Phiên chơi bắt đầu/kết thúc → hiện/ẩn bong bóng + HUD
        jobs += scope.launch {
            var prev: String? = null
            c.session.collect { s ->
                withContext(Dispatchers.Main) {
                    val ov = overlay ?: return@withContext
                    if (s != null && prev != s.pkg) {
                        if (Perms.canOverlay(this@TurboService)) ov.showSession()
                    } else if (s == null) {
                        ov.hideSession()
                    }
                }
                prev = s?.pkg
            }
        }

        // Sự kiện: thông báo nổi trên game
        jobs += scope.launch {
            c.events.collect { e ->
                if (e is TurboEvent.Toast) {
                    withContext(Dispatchers.Main) {
                        if (Perms.canOverlay(this@TurboService)) overlay?.toast(e.text, e.level)
                    }
                }
            }
        }

        jobs += scope.launch { watchLoop() }
        jobs += scope.launch { sampleLoop() }
    }

    private val ignorePkgs = setOf(
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    /** Hỏi hệ thống app nào đang ở trên cùng; thấy game trong danh sách thì bắt đầu phiên. */
    private suspend fun watchLoop() {
        val c = app.controller
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        var other = 0
        while (scope.isActive) {
            try {
                val interactive = try {
                    pm.isInteractive
                } catch (_: Throwable) {
                    true
                }
                if (!interactive) {
                    delay(4000)
                    continue
                }
                val pkg = c.sampler.focusedPackage()
                val cur = c.session.value
                if (pkg.isNotEmpty()) {
                    val games = app.games.gamePackages()
                    if (pkg in games) {
                        other = 0
                        c.onGameFront(pkg)
                    } else if (cur != null) {
                        if (pkg in ignorePkgs) {
                            other = 0
                        } else {
                            other++
                            if (other >= 3 && System.currentTimeMillis() - cur.startMs > 8000) {
                                other = 0
                                c.onGameBack("Rời game")
                            }
                        }
                    } else {
                        other = 0
                    }
                }
            } catch (_: Throwable) {
            }
            delay(if (c.session.value != null) 2000 else 3000)
        }
    }

    /** Đo số liệu mỗi giây khi đang chơi, đẩy lên HUD và kiểm tra nhiệt độ. */
    private suspend fun sampleLoop() {
        val c = app.controller
        val st = app.settings
        while (scope.isActive) {
            val s = c.session.value
            if (s == null) {
                delay(1000)
                continue
            }
            try {
                val hud = st.hudOn
                val r = c.sampler.sample(s.pkg, wantFps = hud && st.hudShowFps, wantGpu = hud && st.hudShowGpu)
                c.publish(r)
                c.guard(r)
                delay(if (hud) 1000 else 3000)
            } catch (_: Throwable) {
                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        try {
            val c = app.controller
            c.scope.launch { c.onGameBack("Dừng dịch vụ") }
        } catch (_: Throwable) {
        }
        try {
            overlay?.destroy()
        } catch (_: Throwable) {
        }
        overlay = null
        jobs.forEach { it.cancel() }
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }
}
