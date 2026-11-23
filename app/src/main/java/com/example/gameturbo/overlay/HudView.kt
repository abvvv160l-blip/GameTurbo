package com.example.gameturbo.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.example.gameturbo.data.Settings
import com.example.gameturbo.engine.Stats

/** HUD vẽ trực tiếp bằng Canvas để nhẹ: FPS, GHz, %CPU, %GPU, nhiệt độ. */
class HudView(ctx: Context, private val settings: Settings, private val host: Host) : View(ctx) {

    interface Host {
        fun hudDragStart()
        fun hudDrag(dx: Int, dy: Int)
        fun hudScaleStart()
        fun hudScale(dx: Float)
        fun hudEditDone()
        fun hudRelayout()
    }

    private var stats = Stats()
    private var skin: Skin = Skin.of(settings)

    var editing: Boolean = false
        set(v) {
            field = v
            requestLayout()
            invalidate()
        }

    /** Hiện số liệu mẫu khi chỉnh HUD ngoài game. */
    var demo: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val bg = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPath = Path()

    private class Cell(val label: String, val value: String, val color: Int)

    private val sample = Stats(fps = 60f, cpuPct = 38f, gpuPct = 52f, maxMhz = 2840f, avgMhz = 2100f, tempC = 46f)

    fun setStats(s: Stats) {
        stats = s
        invalidate()
    }

    fun setSkin(s: Skin) {
        skin = s
        invalidate()
    }

    private fun scale(): Float = settings.hudScale.coerceIn(0.6f, 1.8f)

    private fun cells(): List<Cell> {
        val s = if (demo) sample else stats
        val out = ArrayList<Cell>(5)
        if (settings.hudShowFps) {
            val c = when {
                s.fps < 0f -> 0xFFB0B8C8.toInt()
                s.fps >= 55f -> 0xFF4CD964.toInt()
                s.fps >= 30f -> 0xFFFFCC00.toInt()
                else -> 0xFFFF5A4A.toInt()
            }
            out.add(Cell("FPS", if (s.fps < 0f) "--" else String.format("%.0f", s.fps), c))
        }
        if (settings.hudShowGhz) {
            out.add(Cell("GHz", if (s.maxMhz < 0f) "--" else String.format("%.2f", s.maxMhz / 1000f), Color.WHITE))
        }
        if (settings.hudShowCpu) {
            out.add(Cell("CPU", if (s.cpuPct < 0f) "--" else String.format("%.0f%%", s.cpuPct), Color.WHITE))
        }
        if (settings.hudShowGpu) {
            val c = when {
                s.gpuPct >= 0f -> Cell("GPU", String.format("%.0f%%", s.gpuPct), Color.WHITE)
                s.gpuMhz >= 0f -> Cell("GPU MHz", String.format("%.0f", s.gpuMhz), Color.WHITE)
                else -> Cell("GPU", "--", Color.WHITE)
            }
            out.add(c)
        }
        if (settings.hudShowTemp) {
            val t = s.tempC
            val col = when {
                t < 0f -> 0xFFB0B8C8.toInt()
                t >= 80f -> 0xFFFF5A4A.toInt()
                t >= 72f -> 0xFFFFCC00.toInt()
                else -> Color.WHITE
            }
            out.add(Cell(if (s.tempSource == 1 && !demo) "PIN" else "°C", if (t < 0f) "--" else String.format("%.0f", t), col))
        }
        if (out.isEmpty()) out.add(Cell("FPS", "--", Color.WHITE))
        return out
    }

    private fun cellW() = context.dpf(54f) * scale()
    private fun hgt() = context.dpf(42f) * scale()
    private fun pad() = context.dpf(8f) * scale()
    private fun editPad() = if (editing) context.dpf(16f) else 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val n = cells().size
        val w = cellW() * n + pad() * 2 + editPad() * 2
        val h = hgt() + editPad() * 2
        setMeasuredDimension(Math.ceil(w.toDouble()).toInt(), Math.ceil(h.toDouble()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cs = cells()
        val sc = scale()
        val ep = editPad()
        val w = cellW() * cs.size + pad() * 2
        val h = hgt()
        bg.set(ep, ep, ep + w, ep + h)
        val r = h * 0.34f

        bgPaint.color = if (skin.ultimate) 0xB3180508.toInt() else 0xB30A0F2A.toInt()
        canvas.drawRoundRect(bg, r, r, bgPaint)
        strokePaint.strokeWidth = context.dpf(1f)
        strokePaint.color = withAlpha(if (skin.ultimate) skin.accent2 else Color.WHITE, if (skin.ultimate) 0.55f else 0.28f)
        canvas.drawRoundRect(bg, r, r, strokePaint)

        labelPaint.textSize = context.dpf(8.5f) * sc
        labelPaint.color = withAlpha(Color.WHITE, 0.62f)
        valuePaint.textSize = context.dpf(15f) * sc
        sepPaint.color = withAlpha(Color.WHITE, 0.14f)

        var x = ep + pad()
        val cw = cellW()
        for ((i, c) in cs.withIndex()) {
            val cx = x + cw / 2f
            canvas.drawText(c.label, cx, bg.top + h * 0.34f, labelPaint)
            valuePaint.color = c.color
            canvas.drawText(c.value, cx, bg.top + h * 0.78f, valuePaint)
            x += cw
            if (i < cs.size - 1) canvas.drawRect(x - 0.5f, bg.top + h * 0.22f, x + 0.5f, bg.bottom - h * 0.22f, sepPaint)
        }

        if (editing) {
            editPaint.strokeWidth = context.dpf(1.5f)
            editPaint.color = skin.accent
            editPaint.pathEffect = DashPathEffect(floatArrayOf(context.dpf(6f), context.dpf(4f)), 0f)
            canvas.drawRoundRect(bg.left - 4f, bg.top - 4f, bg.right + 4f, bg.bottom + 4f, r, r, editPaint)
            // tay nắm đổi cỡ (góc dưới phải)
            fillPaint.color = skin.accent
            val hr = context.dpf(9f)
            canvas.drawCircle(bg.right, bg.bottom, hr, fillPaint)
            // nút ✓ (góc trên phải)
            canvas.drawCircle(bg.right, bg.top, hr + 1f, fillPaint)
            strokePaint.color = skin.on
            strokePaint.strokeWidth = context.dpf(2f)
            strokePaint.strokeCap = Paint.Cap.ROUND
            checkPath.reset()
            checkPath.moveTo(bg.right - hr * 0.45f, bg.top + hr * 0.05f)
            checkPath.lineTo(bg.right - hr * 0.1f, bg.top + hr * 0.4f)
            checkPath.lineTo(bg.right + hr * 0.5f, bg.top - hr * 0.35f)
            canvas.drawPath(checkPath, strokePaint)
            // mũi tên nhỏ trên tay nắm
            canvas.drawLine(bg.right - hr * 0.35f, bg.bottom + hr * 0.35f, bg.right + hr * 0.35f, bg.bottom - hr * 0.35f, strokePaint)
        }
    }

    private enum class Mode { NONE, MOVE, SCALE, DONE }

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editing) return false
        val ep = editPad()
        val w = cellW() * cells().size + pad() * 2
        val h = hgt()
        val right = ep + w
        val top = ep
        val bottom = ep + h
        val hr = context.dpf(16f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                val dxTop = event.x - right
                val dyTop = event.y - top
                val dxBot = event.x - right
                val dyBot = event.y - bottom
                mode = when {
                    dxTop * dxTop + dyTop * dyTop <= hr * hr -> Mode.DONE
                    dxBot * dxBot + dyBot * dyBot <= hr * hr -> {
                        host.hudScaleStart()
                        Mode.SCALE
                    }
                    else -> {
                        host.hudDragStart()
                        Mode.MOVE
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (mode == Mode.MOVE) host.hudDrag(dx.toInt(), dy.toInt())
                else if (mode == Mode.SCALE) host.hudScale(dx)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.DONE) {
                    val dxTop = event.x - right
                    val dyTop = event.y - top
                    if (dxTop * dxTop + dyTop * dyTop <= hr * hr) host.hudEditDone()
                }
                mode = Mode.NONE
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE
                return true
            }
        }
        return true
    }
}
