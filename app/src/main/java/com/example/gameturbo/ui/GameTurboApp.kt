package com.example.gameturbo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gameturbo.Perms
import com.example.gameturbo.RootShell
import com.example.gameturbo.ShizukuStatus
import com.example.gameturbo.app
import com.example.gameturbo.engine.Stats
import com.example.gameturbo.engine.TurboEvent
import com.example.gameturbo.service.TurboService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val tabIcons = listOf("🎮", "📊", "📜", "⚙️")
private val tabNames = listOf("Game", "HUD", "Nhật ký", "Cài đặt")

@Composable
fun GameTurboApp() {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    val c = app.controller
    val ui = rememberUiState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<TurboEvent.Toast?>(null) }
    var toastShown by remember { mutableStateOf<TurboEvent.Toast?>(null) }
    var onboarding by remember { mutableStateOf(!st.onboarded) }
    var shizukuDialog by remember { mutableStateOf(false) }
    var permDialog by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(Stats()) }

    val running by TurboService.running.collectAsStateCompat()
    val shStatus by app.shizuku.status.collectAsStateCompat()
    val session by c.session.collectAsStateCompat()
    val resumed = rememberResumed()
    val batt = rememberBattery()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        c.events.collect { e ->
            if (e is TurboEvent.Toast) {
                toastShown = e
                toast = e
                app.sounds.ping()
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }
    // Ngoài game: tự đo nhiệt độ cho thanh trên
    LaunchedEffect(resumed, session == null) {
        if (resumed && session == null) {
            while (true) {
                temp = try {
                    withContext(Dispatchers.Default) { c.sampler.sample(null, false, false) }
                } catch (_: Throwable) {
                    Stats()
                }
                delay(4000)
            }
        }
    }

    fun setWatch(on: Boolean) {
        if (on) {
            if (!Perms.canOverlay(ctx)) {
                permDialog = true
                return
            }
            if (Build.VERSION.SDK_INT >= 33 && !Perms.canNotify(ctx)) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            TurboService.start(ctx)
        } else {
            TurboService.stop(ctx)
        }
    }

    CompositionLocalProvider(LocalUi provides ui) {
        Box(Modifier.fillMaxSize()) {
            AppBackground()
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(vertical = 10.dp)) {
                TopBar(
                    query = query, onQuery = { query = it },
                    shLabel = shizukuStatusText(shStatus, app.shizuku.isRootMode, RootShell.available),
                    shColor = when {
                        shStatus == ShizukuStatus.READY || RootShell.available -> Ink.green
                        shStatus == ShizukuStatus.UNAVAILABLE -> Ink.red
                        else -> Ink.amber
                    },
                    temp = temp, batteryPct = batt.pct, charging = batt.charging,
                    watching = running,
                    onWatch = { setWatch(it) },
                    onShizuku = {
                        if (shStatus == ShizukuStatus.NEED_PERMISSION) app.shizuku.requestPermission() else shizukuDialog = true
                    },
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(tab, label = "tab") { t ->
                        when (t) {
                            0 -> HomeScreen()
                            1 -> HudScreen()
                            2 -> LogScreen()
                            else -> SettingsScreen()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Dock(tab) { tab = it }
            }

            if (query.isNotBlank()) {
                val n = norm(query)
                val gameHits = app.games.games.filter { norm(it.label).contains(n) }.take(4)
                val appHits = app.games.otherApps.filter { norm(it.label).contains(n) }.take(4)
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 58.dp).width(340.dp).glass(20.dp, dark = true).padding(8.dp)
                ) {
                    if (gameHits.isEmpty() && appHits.isEmpty()) Txt("Không tìm thấy", 12.sp, color = Dim, modifier = Modifier.padding(8.dp))
                    for (g in gameHits) {
                        Row(
                            Modifier.fillMaxWidth().gtClickable {
                                st.selectedGame = g.pkg
                                tab = 0
                                query = ""
                            }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GameIcon(g.pkg, g.label, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Txt(g.label, 13.sp, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                            Txt("GAME", 9.sp, FontWeight.Bold, Faint)
                        }
                    }
                    for (a in appHits) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            GameIcon(a.pkg, a.label, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Txt(a.label, 13.sp, FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                            GtButton("＋ Thêm làm game", compact = true) {
                                st.addGame(a.pkg)
                                st.selectedGame = a.pkg
                                scope.launch { app.games.reload() }
                                tab = 0
                                query = ""
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = toast != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                val t = toastShown
                if (t != null) GlassToast(t.text, t.level)
            }

            UltimateFxOverlay(on = st.mode == 2 && st.ultSkin)

            if (onboarding) OnboardingDialog {
                st.onboarded = true
                onboarding = false
            }
            if (shizukuDialog) ShizukuDialog { shizukuDialog = false }
            if (permDialog) OverlayPermissionDialog { permDialog = false }
        }
    }
}

@Composable
private fun TopBar(
    query: String,
    onQuery: (String) -> Unit,
    shLabel: String,
    shColor: Color,
    temp: Stats,
    batteryPct: Int,
    charging: Boolean,
    watching: Boolean,
    onWatch: (Boolean) -> Unit,
    onShizuku: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.height(38.dp).glass(19.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("⚡", 15.sp)
            Spacer(Modifier.width(6.dp))
            Txt("Game Turbo", 14.sp, FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 340.dp).fillMaxWidth().height(38.dp).glass(19.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("🔍", 13.sp)
                Spacer(Modifier.width(8.dp))
                SearchField(query, onQuery, "Tìm game hoặc app", Modifier.weight(1f))
                if (query.isNotEmpty()) {
                    Txt("✕", 13.sp, color = Dim, modifier = Modifier.gtClickable { onQuery("") }.padding(start = 8.dp))
                }
            }
        }
        Row(
            Modifier.gtClickable(onClick = onShizuku).height(38.dp).glass(19.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(shColor))
            Spacer(Modifier.width(7.dp))
            Txt(shLabel, 12.sp, FontWeight.SemiBold, maxLines = 1)
        }
        Row(Modifier.height(38.dp).glass(19.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (temp.tempC > 0) {
                Txt("🌡 " + (if (temp.tempSource == 0) "CPU " else "Pin ") + "%.0f °C".format(temp.tempC), 12.sp, FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(10.dp))
            }
            if (batteryPct >= 0) Txt((if (charging) "⚡" else "🔋") + "$batteryPct%", 12.sp, FontWeight.Medium, maxLines = 1)
        }
        Row(Modifier.height(38.dp).glass(19.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Theo dõi game", 12.sp, FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            GtSwitch(watching, onWatch)
        }
    }
}

@Composable
private fun Dock(tab: Int, onTab: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(370.dp).height(52.dp).glass(26.dp)) {
            LensSegment(
                count = 4, selected = tab, vertical = false,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                onSelect = onTab,
            ) { i, _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(tabIcons[i], 15.sp)
                    Spacer(Modifier.width(5.dp))
                    Txt(tabNames[i], 12.sp, FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
