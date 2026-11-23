package com.example.gameturbo.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.example.gameturbo.MainActivity
import com.example.gameturbo.audio.SoundBank
import com.example.gameturbo.data.Settings
import com.example.gameturbo.engine.TurboController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quản lý các cửa sổ nổi vẽ đè lên game: bong bóng, HUD, menu, thông báo.
 * Mọi hàm gọi trên luồng chính.
 */
class OverlayManager(
    private val ctx: Context,
    override val controller: TurboController,
    override val settings: Settings,
    override val sounds: SoundBank,
) : HudView.Host, BubbleView.Host, MenuHost {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var hud: HudView? = null
    private var hudLp: WindowManager.LayoutParams? = null
    private var menu: MenuView? = null
    private var menuLp: WindowManager.LayoutParams? = null
    private var toast: TextView? = null

    private var inSession = false
    private var editing = false
    private var skin: Skin = Skin.of(settings)

    private val fadeBubble = Runnable { bubble?.animate()?.alpha(0.5f)?.setDuration(400)?.start() }
    private val hideToast = Runnable { removeToast() }

    init {
        // Khi Activity đã đóng thì không còn giao diện Compose nào báo thay đổi cài đặt cho snapshotFlow,
        // nên tự gửi thông báo định kỳ để các công tắc trong menu game vẫn có tác dụng.
        scope.launch {
            while (isActive) {
                try {
                    Snapshot.sendApplyNotifications()
                } catch (_: Throwable) {
                }
                delay(250)
            }
        }
        scope.launch { controller.stats.collect { s -> hud?.setStats(s); menu?.setStats(s) } }
        scope.launch {
            snapshotFlow { Triple(settings.mode, settings.theme, settings.ultSkin) }.collect { refreshSkin() }
        }
        scope.launch {
            snapshotFlow {
                listOf<Any>(
                    settings.hudOn, settings.hudAlpha, settings.hudScale, settings.hudShowFps,
                    settings.hudShowGhz, settings.hudShowCpu, settings.hudShowGpu, settings.hudShowTemp,
                )
            }.collect { applyHud() }
        }
        scope.launch {
            snapshotFlow { listOf<Any>(settings.fixedPerf, settings.lockClock, settings.hudOn, settings.mode) }
                .collect { menu?.rebindSwitchesOnly() }
        }
    }

    // ------------------------------------------------------------ tiện ích cửa sổ

    private fun screen(): Point {
        val p = Point()
        try {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
        } catch (_: Throwable) {
            val dm = ctx.resources.displayMetrics
            p.set(dm.widthPixels, dm.heightPixels)
        }
        return p
    }

    private fun params(w: Int, h: Int, extraFlags: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = extraFlags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val lp = WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        return lp
    }

    private fun safeAdd(v: View, lp: WindowManager.LayoutParams): Boolean = try {
        wm.addView(v, lp)
        true
    } catch (_: Throwable) {
        false
    }

    private fun safeRemove(v: View?) {
        if (v == null) return
        try {
            wm.removeViewImmediate(v)
        } catch (_: Throwable) {
        }
    }

    private fun safeUpdate(v: View?, lp: WindowManager.LayoutParams?) {
        if (v == null || lp == null) return
        try {
            wm.updateViewLayout(v, lp)
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------ phiên chơi: bong bóng + HUD

    fun showSession() {
        inSession = true
        showBubble()
        if (settings.hudOn) showHud(false)
    }

    fun hideSession() {
        inSession = false
        closeMenuImmediate()
        if (!editing) {
            removeHud()
        }
        removeBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        val b = BubbleView(ctx, this)
        b.setSkin(skin)
        val lp = params(b.sizePx, b.sizePx, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val sc = screen()
        val x = if (settings.bubbleX < 0) sc.x - b.sizePx else settings.bubbleX.coerceIn(0, Math.max(0, sc.x - b.sizePx))
        lp.x = x
        lp.y = settings.bubbleY.coerceIn(0, Math.max(0, sc.y - b.sizePx))
        if (safeAdd(b, lp)) {
            bubble = b
            bubbleLp = lp
            b.alpha = 0f
            b.animate().alpha(1f).setDuration(260).start()
            scheduleFade()
        }
    }

    private fun removeBubble() {
        main.removeCallbacks(fadeBubble)
        safeRemove(bubble)
        bubble = null
        bubbleLp = null
    }

    private fun scheduleFade() {
        main.removeCallbacks(fadeBubble)
        main.postDelayed(fadeBubble, 3500)
    }

    private fun showHud(forEdit: Boolean) {
        if (hud != null) return
        val h = HudView(ctx, settings, this)
        h.setSkin(skin)
        h.setStats(controller.stats.value)
        val lp = params(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        lp.x = settings.hudX
        lp.y = settings.hudY
        lp.alpha = settings.hudWindowAlpha(Build.VERSION.SDK_INT)
        if (safeAdd(h, lp)) {
            hud = h
            hudLp = lp
        }
        if (forEdit) startEditState()
    }

    private fun removeHud() {
        safeRemove(hud)
        hud = null
        hudLp = null
    }

    private fun applyHud() {
        val h = hud
        val lp = hudLp
        if (h != null && lp != null) {
            if (!settings.hudOn && !editing) {
                removeHud()
                return
            }
            lp.alpha = if (editing) 1f else settings.hudWindowAlpha(Build.VERSION.SDK_INT)
            h.requestLayout()
            h.invalidate()
            safeUpdate(h, lp)
        } else if (inSession && settings.hudOn && !editing) {
            showHud(false)
        }
    }

    private fun refreshSkin() {
        skin = Skin.of(settings)
        bubble?.setSkin(skin)
        hud?.setSkin(skin)
        menu?.applySkin(skin)
    }

    // ------------------------------------------------------------ chỉnh HUD

    /** Hiện HUD để kéo/phóng to. Ngoài game dùng số liệu mẫu. */
    fun editHud(onDone: () -> Unit) {
        closeMenuImmediate()
        editDone = onDone
        if (hud == null) showHud(true) else startEditState()
        showToast("Kéo để di chuyển · kéo góc để đổi cỡ · chạm ✓ để lưu", 0, 4500)
    }

    private var editDone: (() -> Unit)? = null
    private var hudStartX = 0
    private var hudStartY = 0
    private var hudStartScale = 1f

    private fun startEditState() {
        val h = hud ?: return
        val lp = hudLp ?: return
        editing = true
        h.demo = !inSession
        h.editing = true
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        lp.alpha = 1f
        safeUpdate(h, lp)
    }

    override fun hudDragStart() {
        hudStartX = hudLp?.x ?: 0
        hudStartY = hudLp?.y ?: 0
    }

    override fun hudDrag(dx: Int, dy: Int) {
        val lp = hudLp ?: return
        val sc = screen()
        val h = hud
        val w = h?.width ?: 0
        val ht = h?.height ?: 0
        lp.x = (hudStartX + dx).coerceIn(0, Math.max(0, sc.x - w))
        lp.y = (hudStartY + dy).coerceIn(0, Math.max(0, sc.y - ht))
        safeUpdate(h, lp)
    }

    override fun hudScaleStart() {
        hudStartScale = settings.hudScale
    }

    override fun hudScale(dx: Float) {
        settings.hudScale = (hudStartScale + dx / ctx.dpf(180f)).coerceIn(0.6f, 1.8f)
        hudRelayout()
    }

    override fun hudRelayout() {
        val h = hud ?: return
        h.requestLayout()
        h.invalidate()
        safeUpdate(h, hudLp)
    }

    override fun hudEditDone() {
        val lp = hudLp
        if (lp != null) {
            settings.hudX = lp.x
            settings.hudY = lp.y
        }
        editing = false
        val h = hud
        if (h != null) {
            h.editing = false
            h.demo = false
        }
        if (lp != null && h != null) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.alpha = settings.hudWindowAlpha(Build.VERSION.SDK_INT)
            safeUpdate(h, lp)
        }
        if (!inSession || !settings.hudOn) removeHud()
        val cb = editDone
        editDone = null
        cb?.invoke()
        sounds.ping()
    }

    // ------------------------------------------------------------ bong bóng

    private var bubbleStartX = 0
    private var bubbleStartY = 0

    override fun bubbleDown() {
        main.removeCallbacks(fadeBubble)
        bubble?.animate()?.alpha(1f)?.setDuration(120)?.start()
        bubbleStartX = bubbleLp?.x ?: 0
        bubbleStartY = bubbleLp?.y ?: 0
    }

    override fun bubbleDrag(dx: Int, dy: Int) {
        val lp = bubbleLp ?: return
        val b = bubble ?: return
        val sc = screen()
        lp.x = (bubbleStartX + dx).coerceIn(0, Math.max(0, sc.x - b.sizePx))
        lp.y = (bubbleStartY + dy).coerceIn(0, Math.max(0, sc.y - b.sizePx))
        safeUpdate(b, lp)
    }

    override fun bubbleUp(moved: Boolean) {
        val lp = bubbleLp
        val b = bubble
        if (moved && lp != null && b != null) {
            // Hít vào mép gần nhất
            val sc = screen()
            val target = if (lp.x + b.sizePx / 2 < sc.x / 2) 0 else sc.x - b.sizePx
            val from = lp.x
            val anim = android.animation.ValueAnimator.ofInt(from, target).setDuration(180)
            anim.addUpdateListener {
                lp.x = it.animatedValue as Int
                safeUpdate(b, lp)
            }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    settings.bubbleX = lp.x
                    settings.bubbleY = lp.y
                }
            })
            anim.start()
        } else if (!moved) {
            sounds.tick()
            toggleMenu()
        }
        scheduleFade()
    }

    // ------------------------------------------------------------ menu

    private fun toggleMenu() {
        if (menu != null) menuClose() else openMenu()
    }

    private fun openMenu() {
        if (menu != null || editing) return
        val mv = MenuView(ctx, this)
        mv.applySkin(skin)
        mv.setStats(controller.stats.value)
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        var blur = false
        if (Build.VERSION.SDK_INT >= 31 && settings.glassQuality == 0) {
            try {
                blur = wm.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
            }
        }
        if (blur) flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, flags)
        if (blur && Build.VERSION.SDK_INT >= 31) lp.blurBehindRadius = 28

        val sc = screen()
        mv.measure(
            View.MeasureSpec.makeMeasureSpec(sc.x, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(sc.y, View.MeasureSpec.AT_MOST)
        )
        val w = mv.measuredWidth
        val h = mv.measuredHeight
        val bl = bubbleLp
        val bsize = bubble?.sizePx ?: 0
        val margin = ctx.dpi(10f)
        if (settings.menuX >= 0 && settings.menuY >= 0) {
            lp.x = settings.menuX.coerceIn(0, Math.max(0, sc.x - w))
            lp.y = settings.menuY.coerceIn(0, Math.max(0, sc.y - h))
        } else {
            val bubbleRight = bl != null && bl.x + bsize / 2 > sc.x / 2
            lp.x = if (bubbleRight) Math.max(margin, sc.x - w - bsize - margin) else Math.min(sc.x - w - margin, bsize + margin)
            val cy = (bl?.y ?: sc.y / 2) + bsize / 2
            lp.y = (cy - h / 2).coerceIn(margin, Math.max(margin, sc.y - h - margin))
        }
        if (safeAdd(mv, lp)) {
            menu = mv
            menuLp = lp
            val px = if (bl != null) (bl.x + bsize / 2 - lp.x).toFloat() else w / 2f
            val py = if (bl != null) (bl.y + bsize / 2 - lp.y).toFloat() else h / 2f
            mv.playOpen(px, py)
            sounds.swoosh()
        }
    }

    private fun closeMenuImmediate() {
        val m = menu ?: return
        m.cancelAnimations()
        safeRemove(m)
        menu = null
        menuLp = null
    }

    override fun menuClose() {
        val m = menu ?: return
        menu = null
        menuLp = null
        m.playClose {
            safeRemove(m)
        }
    }

    override fun menuEditHud() {
        menuClose()
        editHud { }
    }

    override fun menuOpenApp() {
        menuClose()
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }

    private var menuStartX = 0
    private var menuStartY = 0

    override fun menuMoveStart() {
        menuStartX = menuLp?.x ?: 0
        menuStartY = menuLp?.y ?: 0
    }

    override fun menuMoveBy(dx: Int, dy: Int) {
        val lp = menuLp ?: return
        val m = menu ?: return
        val sc = screen()
        lp.x = (menuStartX + dx).coerceIn(0, Math.max(0, sc.x - m.width))
        lp.y = (menuStartY + dy).coerceIn(0, Math.max(0, sc.y - m.height))
        safeUpdate(m, lp)
    }

    override fun menuMoveEnd() {
        val lp = menuLp ?: return
        settings.menuX = lp.x
        settings.menuY = lp.y
    }

    // ------------------------------------------------------------ thông báo nổi

    fun toast(text: String, level: Int) {
        showToast(text, level, 2800)
    }

    private fun showToast(text: String, level: Int, ms: Long) {
        removeToast()
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(0xFFFFFFFF.toInt())
        t.textSize = 13f
        t.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        val pad = ctx.dpi(14f)
        t.setPadding(pad, ctx.dpi(9f), pad, ctx.dpi(9f))
        val accent = when (level) {
            2 -> 0xFFFF4D4D.toInt()
            1 -> 0xFFFFC233.toInt()
            else -> skin.accent
        }
        val g = GlassDrawable(ctx.dpf(22f), ctx.dpf(1.2f), if (level == 2) 0xE0300808.toInt() else skin.base, accent)
        g.glow = if (level > 0) 0.6f else 0f
        t.background = g
        val lp = params(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = ctx.dpi(22f)
        if (safeAdd(t, lp)) {
            toast = t
            t.alpha = 0f
            t.translationY = -ctx.dpf(12f)
            t.animate().alpha(1f).translationY(0f).setDuration(220).start()
            main.removeCallbacks(hideToast)
            main.postDelayed(hideToast, ms)
        }
    }

    private fun removeToast() {
        main.removeCallbacks(hideToast)
        val t = toast ?: return
        toast = null
        t.animate().alpha(0f).setDuration(160).withEndAction { safeRemove(t) }.start()
    }

    fun destroy() {
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        closeMenuImmediate()
        safeRemove(toast)
        toast = null
        removeHud()
        removeBubble()
    }
}
