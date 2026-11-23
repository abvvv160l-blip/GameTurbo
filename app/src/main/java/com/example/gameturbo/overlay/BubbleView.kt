package com.example.gameturbo.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Bong bóng tròn kính nổi trên game: chạm mở menu, kéo để di chuyển và tự hít vào mép. */
class BubbleView(ctx: Context, private val host: Host) : View(ctx) {

    interface Host {
        fun bubbleDown()
        fun bubbleDrag(dx: Int, dy: Int)
        fun bubbleUp(moved: Boolean)
    }

    private var skin: Skin? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    val sizePx: Int = ctx.dpi(52f)

    fun setSkin(s: Skin) {
        skin = s
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val s = skin ?: return
        val w = width.toFloat()
        val cx = w / 2f
        val r = w / 2f - context.dpf(2f)
        fill.color = if (s.ultimate) 0xE0200608.toInt() else 0xE00C1230.toInt()
        canvas.drawCircle(cx, cx, r, fill)
        sheen.shader = RadialGradient(cx * 0.7f, cx * 0.45f, r * 1.1f, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cx, r, sheen)
        rim.strokeWidth = context.dpf(1.4f)
        rim.shader = LinearGradient(0f, 0f, w, w, intArrayOf(0xDDFFFFFF.toInt(), 0x22FFFFFF, 0xAAFFFFFF.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cx, r, rim)
        // tia chớp
        path.reset()
        fun p(x: Float, y: Float) = Pair(x * w, y * w)
        val pts = listOf(p(0.57f, 0.16f), p(0.30f, 0.54f), p(0.47f, 0.54f), p(0.41f, 0.86f), p(0.70f, 0.44f), p(0.52f, 0.44f))
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) path.lineTo(pts[i].first, pts[i].second)
        path.close()
        bolt.shader = LinearGradient(0f, w * 0.15f, 0f, w * 0.88f, s.accent, s.accent2, Shader.TileMode.CLAMP)
        canvas.drawPath(path, bolt)
    }

    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                host.bubbleDown()
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!moved && dx * dx + dy * dy > slop * slop * 4) moved = true
                if (moved) host.bubbleDrag(dx.toInt(), dy.toInt())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                host.bubbleUp(moved && event.actionMasked == MotionEvent.ACTION_UP)
                return true
            }
        }
        return true
    }
}
