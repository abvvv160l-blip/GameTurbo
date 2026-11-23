package com.example.gameturbo.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.gameturbo.audio.SoundBank
import com.example.gameturbo.data.Settings
import com.example.gameturbo.engine.Stats
import com.example.gameturbo.engine.TurboController

// ---------------------------------------------------------------- các nút nhỏ dùng trong menu

/** Công tắc dạng viên thuốc, knob trượt mượt. */
class PillSwitch(ctx: Context) : View(ctx) {
    var skin: Skin? = null
        set(v) {
            field = v
            invalidate()
        }
    var onChange: ((Boolean) -> Unit)? = null
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    var checked: Boolean = false
        private set

    init {
        setOnClickListener {
            setChecked(!checked, true)
            onChange?.invoke(checked)
        }
    }

    fun setChecked(v: Boolean, animate: Boolean) {
        if (v == checked && animate) return
        checked = v
        val target = if (v) 1f else 0f
        animator?.cancel()
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(progress, target).setDuration(180)
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        animator = a
        a.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dpi(40f), context.dpi(22f))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = skin
        val onColor = s?.accent2 ?: 0xFFFF7A1A.toInt()
        p.color = blend(0x33FFFFFF, withAlpha(onColor, 0.95f), progress)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, p)
        val pad = context.dpf(2.5f)
        val r = h / 2f - pad
        val cx = pad + r + (w - 2 * pad - 2 * r) * progress
        p.color = Color.WHITE
        canvas.drawCircle(cx, h / 2f, r, p)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt()
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b))
        )
    }
}

/** Thanh chọn 1 trong N, có "ống kính" trượt dưới mục đang chọn. */
class SegView(ctx: Context, private val labels: List<String>) : View(ctx) {
    var skin: Skin? = null
        set(v) {
            field = v
            invalidate()
        }
    var onPick: ((Int) -> Unit)? = null
    private var pos = 1f
    private var sel = 1
    private var animator: ValueAnimator? = null
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()

    fun select(i: Int, animate: Boolean) {
        if (i == sel && animate) return
        sel = i
        animator?.cancel()
        if (!animate) {
            pos = i.toFloat()
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(pos, i.toFloat()).setDuration(260)
        a.interpolator = OvershootInterpolator(0.9f)
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        animator = a
        a.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, context.dpi(38f))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = skin
        val n = labels.size
        val seg = (w - 6f) / n
        bg.color = 0x26FFFFFF
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, bg)
        val a1 = s?.accent ?: 0xFFFFB26B.toInt()
        val a2 = s?.accent2 ?: 0xFFFF7A1A.toInt()
        lens.shader = android.graphics.LinearGradient(0f, 0f, w, 0f, a1, a2, android.graphics.Shader.TileMode.CLAMP)
        rect.set(3f + seg * pos, 3f, 3f + seg * pos + seg, h - 3f)
        canvas.drawRoundRect(rect, (h - 6f) / 2f, (h - 6f) / 2f, lens)
        txt.textSize = context.dpf(11.5f)
        for (i in 0 until n) {
            val near = 1f - Math.min(1f, Math.abs(pos - i))
            txt.color = if (near > 0.5f) (s?.on ?: 0xFF2A1200.toInt()) else 0xCCFFFFFF.toInt()
            canvas.drawText(labels[i], 3f + seg * i + seg / 2f, h / 2f + txt.textSize * 0.35f, txt)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val seg = (width - 6f) / labels.size
            val i = ((event.x - 3f) / seg).toInt().coerceIn(0, labels.size - 1)
            onPick?.invoke(i)
        }
        return true
    }
}

// ---------------------------------------------------------------- menu chính

interface MenuHost {
    val settings: Settings
    val controller: TurboController
    val sounds: SoundBank
    fun menuClose()
    fun menuEditHud()
    fun menuOpenApp()
    fun menuMoveStart()
    fun menuMoveBy(dx: Int, dy: Int)
    fun menuMoveEnd()
}

/**
 * Menu liquid-glass trong game. Chạm đúp thanh trên cùng để vào chế độ di chuyển (viền phát sáng),
 * kéo bất kỳ đâu để dời, chạm đúp thanh trên lần nữa để lưu vị trí.
 */
class MenuView(ctx: Context, private val host: MenuHost) : FrameLayout(ctx) {

    private var skin: Skin = Skin.of(host.settings)
    private val glass = GlassDrawable(ctx.dpf(24f), ctx.dpf(1.2f), skin.base, skin.accent)

    private val content = LinearLayout(ctx)
    private val topBar = LinearLayout(ctx)
    private val titleTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val chipTemp = TextView(ctx)
    private val chipFps = TextView(ctx)
    private val chipMode = TextView(ctx)
    private val seg = SegView(ctx, listOf("Pin", "Ổn định", "Tối thượng", "Tùy chỉnh"))
    private val swHud = PillSwitch(ctx)
    private val swFixed = PillSwitch(ctx)
    private val swLock = PillSwitch(ctx)
    private val lockLabel = TextView(ctx)
    private val buttons = ArrayList<TextView>()

    var moveMode: Boolean = false
        private set
    private var pulse: ValueAnimator? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f

    private val barDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            enterMove()
            return true
        }
    })

    private val moveDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (e.y <= topBar.bottom) exitMove()
            return true
        }
    })

    init {
        background = glass
        val pad = ctx.dpi(14f)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(pad, ctx.dpi(10f), pad, pad)
        addView(content, LayoutParams(ctx.dpi(312f), ViewGroup.LayoutParams.WRAP_CONTENT))

        buildTopBar()
        buildChips()
        content.addView(seg, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10f))
        seg.onPick = { i ->
            if (i != 2) host.sounds.tick()
            seg.select(i, true)
            host.controller.setMode(i)
            refreshChips()
        }
        buildToggles()
        buildButtons()
        applySkin(skin)
        bind()
    }

    private fun lp(w: Int, h: Int, top: Float = 0f, weight: Float = 0f): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = context.dpi(top)
        if (weight > 0f) p.weight = weight
        return p
    }

    private fun buildTopBar() {
        val ctx = context
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        titleTv.text = "Game Turbo"
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 15f
        titleTv.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        subTv.setTextColor(0xB3FFFFFF.toInt())
        subTv.textSize = 11f
        subTv.maxLines = 1
        col.addView(titleTv)
        col.addView(subTv)
        topBar.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(ctx)
        close.text = "✕"
        close.gravity = Gravity.CENTER
        close.setTextColor(Color.WHITE)
        close.textSize = 14f
        close.background = pillBg(0x26FFFFFF, 0x33FFFFFF)
        close.setOnClickListener {
            host.sounds.tick()
            host.menuClose()
        }
        topBar.addView(close, LinearLayout.LayoutParams(ctx.dpi(32f), ctx.dpi(32f)))
        topBar.setPadding(0, 0, 0, ctx.dpi(2f))
        topBar.setOnTouchListener { _, ev ->
            barDetector.onTouchEvent(ev)
            true
        }
        content.addView(topBar, lp(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dpi(44f)))
    }

    private fun pillBg(fillColor: Int, strokeColor: Int, radiusDp: Float = 16f): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = context.dpf(radiusDp)
        g.setColor(fillColor)
        g.setStroke(context.dpi(1f), strokeColor)
        return g
    }

    private fun buildChips() {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        for ((i, c) in listOf(chipTemp, chipFps, chipMode).withIndex()) {
            c.textSize = 11.5f
            c.setTextColor(Color.WHITE)
            c.gravity = Gravity.CENTER
            c.background = pillBg(0x1FFFFFFF, 0x2EFFFFFF, 12f)
            c.setPadding(context.dpi(8f), context.dpi(4f), context.dpi(8f), context.dpi(4f))
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) p.leftMargin = context.dpi(6f)
            row.addView(c, p)
        }
        content.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 6f))
    }

    private fun toggleRow(label: TextView, sw: PillSwitch): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        label.setTextColor(Color.WHITE)
        label.textSize = 13f
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun buildToggles() {
        val l1 = TextView(context).apply { text = "Hiển thị HUD" }
        val l2 = TextView(context).apply { text = "Hiệu năng cố định" }
        lockLabel.text = "Khoá xung (cần root)"
        content.addView(toggleRow(l1, swHud), lp(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(34f), top = 8f))
        content.addView(toggleRow(l2, swFixed), lp(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(34f)))
        content.addView(toggleRow(lockLabel, swLock), lp(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(34f)))
        swHud.onChange = {
            host.sounds.tick()
            host.settings.hudOn = it
        }
        swFixed.onChange = {
            host.sounds.tick()
            host.settings.fixedPerf = it
            host.controller.reapply()
        }
        swLock.onChange = {
            host.sounds.tick()
            host.settings.lockClock = it
            host.controller.reapply()
        }
    }

    private fun buildButtons() {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        val defs: List<Pair<String, () -> Unit>> = listOf(
            "Dọn app nền" to { host.controller.killNow() },
            "Chỉnh HUD" to { host.menuEditHud() },
            "Mở app" to { host.menuOpenApp() },
        )
        for ((i, d) in defs.withIndex()) {
            val b = TextView(context)
            b.text = d.first
            b.gravity = Gravity.CENTER
            b.setTextColor(Color.WHITE)
            b.textSize = 12f
            b.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            b.background = pillBg(0x26FFFFFF, 0x38FFFFFF)
            b.isClickable = true
            b.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                false
            }
            b.setOnClickListener {
                host.sounds.tick()
                d.second()
            }
            val p = LinearLayout.LayoutParams(0, context.dpi(36f), 1f)
            if (i > 0) p.leftMargin = context.dpi(6f)
            row.addView(b, p)
            buttons.add(b)
        }
        content.addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, top = 10f))
    }

    // ------------------------------------------------------------ dữ liệu

    fun applySkin(s: Skin) {
        skin = s
        glass.update(if (host.settings.glassQuality == 0) s.base else withAlpha(s.base, 0.93f), s.accent)
        seg.skin = s
        swHud.skin = s
        swFixed.skin = s
        swLock.skin = s
        invalidate()
    }

    /** Đọc lại trạng thái từ cài đặt (gọi khi mở menu hoặc khi cài đặt đổi). */
    fun bind() {
        val st = host.settings
        val sess = host.controller.session.value
        subTv.text = if (moveMode) "Kéo để di chuyển · chạm đúp để lưu" else (sess?.label ?: "Chưa vào game")
        seg.select(st.mode.coerceIn(0, 3), false)
        swHud.setChecked(st.hudOn, false)
        swFixed.setChecked(st.fixedPerf, false)
        val root = host.controller.hasRoot()
        swLock.setChecked(st.lockClock && root, false)
        swLock.isEnabled = root
        swLock.alpha = if (root) 1f else 0.4f
        lockLabel.alpha = if (root) 1f else 0.5f
        refreshChips()
    }

    fun rebindSwitchesOnly() {
        val st = host.settings
        seg.select(st.mode.coerceIn(0, 3), true)
        swHud.setChecked(st.hudOn, true)
        swFixed.setChecked(st.fixedPerf, true)
        refreshChips()
    }

    private var lastStats = Stats()

    fun setStats(s: Stats) {
        lastStats = s
        refreshChips()
    }

    private fun refreshChips() {
        val s = lastStats
        val t = s.tempC
        chipTemp.text = if (t < 0f) "Nhiệt --" else String.format("%.0f°C", t)
        chipTemp.setTextColor(
            when {
                t >= 80f -> 0xFFFF6B5A.toInt()
                t >= 72f -> 0xFFFFCC33.toInt()
                else -> Color.WHITE
            }
        )
        chipFps.text = if (s.fps < 0f) "-- FPS" else String.format("%.0f FPS", s.fps)
        chipMode.text = host.controller.modeName(host.settings.mode)
    }

    // ------------------------------------------------------------ hoạt ảnh mở/đóng

    fun playOpen(pivotX: Float, pivotY: Float) {
        this.pivotX = pivotX
        this.pivotY = pivotY
        alpha = 0f
        scaleX = 0.86f
        scaleY = 0.86f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(OvershootInterpolator(1.15f)).start()
    }

    fun playClose(end: () -> Unit) {
        animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
            .setInterpolator(DecelerateInterpolator()).withEndAction { end() }.start()
    }

    // ------------------------------------------------------------ chế độ di chuyển

    private fun enterMove() {
        if (moveMode) return
        moveMode = true
        host.sounds.swoosh()
        subTv.text = "Kéo để di chuyển · chạm đúp để lưu"
        pulse?.cancel()
        val a = ValueAnimator.ofFloat(0.1f, 1f).setDuration(650)
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener { glass.glow = it.animatedValue as Float }
        pulse = a
        a.start()
        animate().scaleX(1.02f).scaleY(1.02f).setDuration(140).start()
    }

    private fun exitMove() {
        if (!moveMode) return
        moveMode = false
        pulse?.cancel()
        pulse = null
        glass.glow = 0f
        host.sounds.ping()
        host.menuMoveEnd()
        animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        bind()
    }

    fun cancelAnimations() {
        pulse?.cancel()
        animate().cancel()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = moveMode

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (!moveMode) host.menuClose()
            return true
        }
        if (!moveMode) return super.onTouchEvent(ev)
        moveDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = true
                host.menuMoveStart()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    downX = ev.rawX
                    downY = ev.rawY
                    dragging = true
                    host.menuMoveStart()
                }
                host.menuMoveBy((ev.rawX - downX).toInt(), (ev.rawY - downY).toInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}
