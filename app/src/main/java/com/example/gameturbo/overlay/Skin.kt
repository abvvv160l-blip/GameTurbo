package com.example.gameturbo.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.example.gameturbo.data.Palette
import com.example.gameturbo.data.Settings

/** Màu cho các cửa sổ nổi. Tối thượng đang bật thì chuyển sang tông đỏ. */
class Skin(
    val accent: Int,
    val accent2: Int,
    val on: Int,
    val ultimate: Boolean,
    val base: Int,
) {
    companion object {
        fun of(settings: Settings): Skin {
            val ult = settings.mode == 2 && settings.ultSkin
            val p = Palette.current(settings.theme, settings.mode == 2, settings.ultSkin)
            val base = if (ult) 0xD0180508.toInt() else 0xD00A0F2A.toInt()
            return Skin(p.a1, p.a2, p.on, ult, base)
        }
    }
}

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.dpi(v: Float): Int = Math.round(v * resources.displayMetrics.density)

fun withAlpha(color: Int, alpha: Float): Int =
    Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

/** Nền kính: tối mờ, vệt sáng góc, viền sáng. [glow] 0..1 làm viền phát sáng màu accent (chế độ di chuyển). */
class GlassDrawable(
    private val radius: Float,
    private val strokePx: Float,
    var base: Int,
    var accent: Int,
) : Drawable() {
    private val rect = RectF()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx * 2.4f }
    private var a = 255

    var glow: Float = 0f
        set(v) {
            field = v
            invalidateSelf()
        }

    fun update(newBase: Int, newAccent: Int) {
        base = newBase
        accent = newAccent
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        val h = strokePx / 2f
        rect.set(bounds.left + h, bounds.top + h, bounds.right - h, bounds.bottom - h)
        val w = rect.width()
        val ht = rect.height()
        sheen.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0x38FFFFFF, 0x0CFFFFFF, 0x1CFFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        hi.shader = RadialGradient(
            rect.left + w * 0.1f, rect.top - ht * 0.1f, Math.max(w * 0.7f, ht),
            intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0xCCFFFFFF.toInt(), 0x22FFFFFF, 0x22FFFFFF, 0xAAFFFFFF.toInt()),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        basePaint.color = base
        basePaint.alpha = (Color.alpha(base) * a / 255)
        canvas.drawRoundRect(rect, radius, radius, basePaint)
        sheen.alpha = a
        canvas.drawRoundRect(rect, radius, radius, sheen)
        hi.alpha = a
        canvas.drawRoundRect(rect, radius, radius, hi)
        rim.alpha = a
        canvas.drawRoundRect(rect, radius, radius, rim)
        if (glow > 0f) {
            glowPaint.color = withAlpha(accent, 0.25f + 0.65f * glow)
            canvas.drawRoundRect(rect, radius, radius, glowPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        a = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
