package com.example.gameturbo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gameturbo.app
import com.example.gameturbo.data.Palette

object Ink {
    val green = Color(0xFF4CD964)
    val amber = Color(0xFFFFCC00)
    val red = Color(0xFFFF5A4A)
}

/** Màu và kiểu kính đang dùng cho toàn giao diện (đã hoạt ảnh khi đổi chủ đề). */
@Immutable
class UiState(
    val a1: Color,
    val a2: Color,
    val on: Color,
    val blobs: List<Color>,
    val tint: Color,
    val frost: Float,
    val quality: Int,
    val ultimate: Boolean,
    val solid: Color,
) {
    companion object {
        val Default = UiState(
            a1 = Color(0xFFFFB26B), a2 = Color(0xFFFF7A1A), on = Color(0xFF2A1200),
            blobs = listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6), Color(0xFF0FA5BE), Color(0xFF7A3FD0)),
            tint = Color.Transparent, frost = 1f, quality = 0, ultimate = false, solid = Color(0xFF12163A),
        )
    }
}

val LocalUi = compositionLocalOf { UiState.Default }

@Composable
fun rememberUiState(): UiState {
    val st = LocalContext.current.app.settings
    val ult = st.mode == 2 && st.ultSkin
    val pal = Palette.current(st.theme, st.mode == 2, st.ultSkin)
    val a1 = animateColorAsState(Color(pal.a1), tween(700), label = "a1").value
    val a2 = animateColorAsState(Color(pal.a2), tween(700), label = "a2").value
    val on = animateColorAsState(Color(pal.on), tween(500), label = "on").value
    val b0 = animateColorAsState(Color(pal.blobs[0]), tween(900), label = "b0").value
    val b1 = animateColorAsState(Color(pal.blobs[1]), tween(900), label = "b1").value
    val b2 = animateColorAsState(Color(pal.blobs[2]), tween(900), label = "b2").value
    val b3 = animateColorAsState(Color(pal.blobs[3]), tween(900), label = "b3").value
    val tint = when (st.tint) {
        1 -> Color(0x610A0A20)
        2 -> Color(0x29FFFFFF)
        3 -> a2.copy(alpha = 0.26f)
        else -> Color.Transparent
    }
    val frost = 0.5f + st.blur.coerceIn(0f, 1f) * 0.9f
    val solid = lerp(Color(0xFF0B0F28), b0, 0.22f)
    return UiState(a1, a2, on, listOf(b0, b1, b2, b3), tint, frost, st.glassQuality.coerceIn(0, 2), ult, solid)
}

/**
 * Nền kính (liquid glass). Ba mức chất lượng:
 * Cao = nhiều lớp (bóng, gradient, vệt sáng, viền sáng), Thấp = nền mờ phẳng + viền, Tắt = nền đặc.
 */
@Composable
fun Modifier.glass(radius: Dp = 28.dp, dark: Boolean = false): Modifier {
    val ui = LocalUi.current
    val q = ui.quality
    val tint = ui.tint
    val frost = ui.frost
    val solid = ui.solid
    return this.drawWithCache {
        val r = minOf(radius.toPx(), size.minDimension / 2f)
        val cr = CornerRadius(r, r)
        val w = size.width
        val h = size.height
        val hairline = 1.dp.toPx()
        val rimW = 1.6.dp.toPx()
        val fill = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.28f * frost),
                Color.White.copy(alpha = 0.07f * frost),
                Color.White.copy(alpha = 0.15f * frost),
            ),
            start = Offset.Zero, end = Offset(w, h),
        )
        val hi1 = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.5f * minOf(frost, 1.2f)), Color.Transparent),
            center = Offset(w * 0.08f, -h * 0.1f), radius = maxOf(w * 0.6f, h * 0.8f),
        )
        val hi2 = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.26f * minOf(frost, 1.2f)), Color.Transparent),
            center = Offset(w, h * 1.15f), radius = maxOf(w * 0.5f, h * 0.7f),
        )
        val rim = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.9f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.7f)),
            start = Offset.Zero, end = Offset(w, h),
        )
        onDrawBehind {
            when (q) {
                2 -> {
                    drawRoundRect(solid, cornerRadius = cr)
                    if (dark) drawRoundRect(Color(0x66000010), cornerRadius = cr)
                    drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = cr, style = Stroke(hairline))
                }
                1 -> {
                    drawRoundRect(Color(0x2E000018), cornerRadius = cr)
                    if (dark) drawRoundRect(Color(0x99060818), cornerRadius = cr)
                    drawRoundRect(tint, cornerRadius = cr)
                    drawRoundRect(Color.White.copy(alpha = 0.14f * frost), cornerRadius = cr)
                    drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = cr, style = Stroke(hairline))
                }
                else -> {
                    for (i in 1..4) {
                        val g = i * 4.dp.toPx()
                        drawRoundRect(
                            Color(0xFF080020).copy(alpha = 0.07f),
                            topLeft = Offset(-g, -g + 14.dp.toPx()),
                            size = Size(w + 2 * g, h + 2 * g),
                            cornerRadius = CornerRadius(r + g, r + g),
                        )
                    }
                    drawRoundRect(Color(0x24000020), cornerRadius = cr)
                    if (dark) drawRoundRect(Color(0xB30A0C20), cornerRadius = cr)
                    drawRoundRect(tint, cornerRadius = cr)
                    drawRoundRect(fill, cornerRadius = cr)
                    drawRoundRect(hi1, cornerRadius = cr)
                    drawRoundRect(hi2, cornerRadius = cr)
                    drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = cr, style = Stroke(hairline))
                    drawRoundRect(rim, cornerRadius = cr, style = Stroke(rimW))
                }
            }
        }
    }
}

/** Chạm: phát tiếng nút, hiệu ứng nhấn thu nhỏ rồi bật lại, rồi gọi [onClick]. */
@Composable
fun Modifier.gtClickable(
    enabled: Boolean = true,
    sound: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val app = LocalContext.current.app
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val snd by rememberUpdatedState(sound)
    val hasLong = onLongClick != null
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled, hasLong) {
            if (enabled) {
                val lp: (Offset) -> Unit = { long?.invoke() }
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onLongPress = if (hasLong) lp else null,
                    onTap = {
                        if (snd) app.sounds.tick()
                        click()
                    },
                )
            }
        }
}
