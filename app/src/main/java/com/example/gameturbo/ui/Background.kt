package com.example.gameturbo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gameturbo.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Tên các nền màu: chỉ số khớp settings.bgColor. */
val BgColorNames = listOf("Đốm màu động", "Tím đêm", "Xanh đêm", "Đen OLED", "Cam đất", "Xanh rêu")
val BgImageNames = listOf("Hoàng hôn", "Thành phố đêm", "Rừng sương", "Biển")

private val colorBases: List<List<Color>> = listOf(
    listOf(Color(0xFF050816), Color(0xFF0A0F2E), Color(0xFF060A1E)),
    listOf(Color(0xFF1A0F46), Color(0xFF3A1A7A)),
    listOf(Color(0xFF06203A), Color(0xFF0A4A7A)),
    listOf(Color(0xFF000000), Color(0xFF000000)),
    listOf(Color(0xFF4A1A0A), Color(0xFFB0400A)),
    listOf(Color(0xFF04301A), Color(0xFF0A7A4A)),
)

/** Phối màu nền xem trước cho ô chọn nền (dùng cả ở Cài đặt). */
fun bgColorBrush(i: Int): Brush {
    val c = colorBases[i.coerceIn(0, colorBases.size - 1)]
    return if (i == 0) Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6), Color(0xFF0FA5BE)))
    else Brush.linearGradient(c)
}

/** Toàn bộ nền phía sau giao diện (màu / ảnh / video) + lớp tối + lớp đỏ Tối thượng. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    val ui = LocalUi.current
    val session by app.controller.session.collectAsStateCompat()
    val resumed = rememberResumed()
    val batt = rememberBattery()

    Box(modifier.fillMaxSize().background(Color(0xFF060A1E))) {
        when (st.bgType) {
            1 -> ImageBg(st.bgImage, st.bgImageUri)
            2 -> if (st.bgVideoUri.isNotEmpty()) {
                val paused = !resumed || (session != null && st.pauseVideoInGame) || (batt.pct in 0..19 && !batt.charging)
                VideoBg(st.bgVideoUri, st.videoSound, st.videoVolume, paused)
            } else ColorBg(0, ui)
            else -> ColorBg(st.bgColor, ui)
        }
        if (st.dim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = st.dim.coerceIn(0f, 0.85f))))
        UltimateLayers(ui.ultimate)
    }
}

// ---------------------------------------------------------------------------------------- nền màu

private class StarDot(val x: Float, val y: Float, val r: Float, val a: Float, val warm: Boolean)

@Composable
private fun ColorBg(index: Int, ui: UiState) {
    val i = index.coerceIn(0, colorBases.size - 1)
    val animate = ui.quality != 2
    val trans = rememberInfiniteTransition(label = "bg")
    val drift: State<Float> = if (animate) trans.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "drift"
    ) else remember { object : State<Float> { override val value: Float get() = 0.5f } }
    val twinkle: State<Float> = if (animate) trans.animateFloat(
        0.55f, 0.28f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "twk"
    ) else remember { object : State<Float> { override val value: Float get() = 0.45f } }
    val stars = remember {
        val r = Random(3)
        List(110) { StarDot(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.1f, 0.5f + r.nextFloat() * 0.5f, r.nextInt(3) == 0) }
    }
    val bases = colorBases[i]
    val blobs = ui.blobs
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(if (bases.size == 2) Brush.linearGradient(bases, Offset(0f, 0f), Offset(w * 0.4f, h)) else Brush.verticalGradient(bases))
        if (i == 0) {
            val d = drift.value
            blob(blobs[0], Offset(w * 0.12f + 50f * d, h * 0.45f - 40f * d), h * 0.62f)
            blob(blobs[1], Offset(w * 0.52f + 50f * d, h * 0.08f - 40f * d), h * 0.6f)
            blob(blobs[2], Offset(w * 0.62f - 40f * d, h * 0.86f - 30f * d), h * 0.58f)
            blob(blobs[3], Offset(w * 0.96f - 30f * d, h * 0.6f + 30f * d), h * 0.56f)
            // glow viền
            glow(blobs[2], Offset(w * 0.5f, h * 1.14f), w * 0.36f, 0.52f)
            glow(blobs[1], Offset(w * 0.5f, -h * 0.14f), w * 0.4f, 0.55f)
            glow(blobs[0], Offset(-w * 0.08f, h * 0.62f), h * 0.6f, 0.48f)
            glow(blobs[3], Offset(w * 1.08f, h * 0.4f), h * 0.55f, 0.3f)
            val ta = twinkle.value
            for (s in stars) {
                val c = if (s.warm) Color(0xFFFFE2AA) else Color.White
                drawCircle(c.copy(alpha = ta * s.a * 1.4f), s.r.dp.toPx(), Offset(s.x * w, s.y * h))
            }
        }
        drawRect(
            Brush.radialGradient(
                0.52f to Color.Transparent, 1f to Color(0x99010210),
                center = Offset(w * 0.5f, h * 0.46f), radius = max(w, h) * 0.78f,
            )
        )
    }
}

private fun DrawScope.blob(c: Color, center: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.62f), c.copy(alpha = 0.22f), Color.Transparent), center, r), r, center)
}

private fun DrawScope.glow(c: Color, center: Offset, r: Float, a: Float) {
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, r), r, center)
}

// ---------------------------------------------------------------------------------------- nền ảnh

@Composable
private fun ImageBg(preset: Int, uri: String) {
    if (preset == 4 && uri.isNotEmpty()) {
        val ctx = LocalContext.current
        val bmp by produceState<ImageBitmap?>(null, uri) { value = loadBitmap(ctx, uri) }
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0F2E)))
        }
    } else {
        Canvas(Modifier.fillMaxSize()) { drawPreset(preset.coerceIn(0, 3), size) }
    }
}

private suspend fun loadBitmap(ctx: Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val u = Uri.parse(uri)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        val longSide = max(o.outWidth, o.outHeight)
        while (longSide / sample > 2000) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

/** Vẽ 4 ảnh nền có sẵn bằng đồ hoạ vector (không tốn dung lượng file). */
fun DrawScope.drawPreset(kind: Int, size: Size) {
    val w = size.width
    val h = size.height
    when (kind) {
        0 -> { // Hoàng hôn
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF2A0F4A), 0.35f to Color(0xFF7A2A8A), 0.62f to Color(0xFFFF5E7E), 0.78f to Color(0xFFFFB04A)
                ),
                size = Size(w, h * 0.78f),
            )
            val c = Offset(w * 0.5f, h * 0.66f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3A8), Color(0xFFFF8A3D), Color.Transparent), c, h * 0.3f), h * 0.3f, c)
            drawCircle(Color(0xFFFFF3A8), h * 0.1f, c)
            val hill = Path().apply {
                moveTo(0f, h * 0.78f)
                cubicTo(w * 0.2f, h * 0.7f, w * 0.35f, h * 0.76f, w * 0.5f, h * 0.78f)
                cubicTo(w * 0.7f, h * 0.72f, w * 0.85f, h * 0.74f, w, h * 0.77f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(hill, Color(0xFF1A0F33))
            drawRect(Brush.verticalGradient(listOf(Color(0x33FF8A3D), Color.Transparent), h * 0.78f, h), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
        }
        1 -> { // Thành phố đêm
            drawRect(Brush.verticalGradient(0f to Color(0xFF070A24), 0.7f to Color(0xFF14246A), 1f to Color(0xFF2A1A5A)))
            val rs = Random(5)
            repeat(70) { drawCircle(Color.White.copy(alpha = 0.3f + rs.nextFloat() * 0.5f), 0.6.dp.toPx() + rs.nextFloat() * 0.8f, Offset(rs.nextFloat() * w, rs.nextFloat() * h * 0.5f)) }
            drawCircle(Color(0x33CFE0FF), h * 0.09f, Offset(w * 0.78f, h * 0.2f))
            drawCircle(Color(0xFFEAF2FF), h * 0.035f, Offset(w * 0.78f, h * 0.2f))
            skyline(w, h, 11, Color(0xFF0E1544), 0.34f, 0.1f, 0.46f, 0.55f)
            skyline(w, h, 23, Color(0xFF080C2C), 0.28f, 0.08f, 0.55f, 1f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x442A1A5A)), h * 0.8f, h), Offset(0f, h * 0.8f), Size(w, h * 0.2f))
        }
        2 -> { // Rừng sương
            drawRect(Brush.verticalGradient(listOf(Color(0xFF04201A), Color(0xFF0B4A3A), Color(0xFF052A2A))))
            drawCircle(Brush.radialGradient(listOf(Color(0x8C78FFAA), Color.Transparent), Offset(w * 0.3f, h * 0.85f), w * 0.5f), w * 0.5f, Offset(w * 0.3f, h * 0.85f))
            drawCircle(Brush.radialGradient(listOf(Color(0x8050C8C8), Color.Transparent), Offset(w * 0.75f, h * 0.3f), w * 0.45f), w * 0.45f, Offset(w * 0.75f, h * 0.3f))
            trees(w, h, 3, Color(0xFF06302A), 0.42f, 0.5f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55CFFFE8), Color.Transparent), h * 0.5f, h * 0.72f), Offset(0f, h * 0.5f), Size(w, h * 0.22f))
            trees(w, h, 9, Color(0xFF031C1A), 0.34f, 0.72f)
        }
        else -> { // Biển
            drawRect(Brush.verticalGradient(0f to Color(0xFF0A3A6A), 0.55f to Color(0xFF1AA0B8), 1f to Color(0xFF0A2A5A)))
            val c = Offset(w * 0.5f, h * 1.2f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), c, h * 0.8f), h * 0.8f, c)
            for (k in 0 until 14) {
                val y = h * (0.42f + k * 0.04f)
                val p = Path()
                p.moveTo(0f, y)
                var x = 0f
                while (x <= w) {
                    p.lineTo(x, y + sin((x / w * 6f + k) * PI.toFloat()) * (2f + k * 0.5f).dp.toPx())
                    x += 12f
                }
                drawPath(p, Color.White.copy(alpha = 0.05f + k * 0.006f), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.skyline(w: Float, h: Float, seed: Int, col: Color, tall: Float, minW: Float, baseY: Float, lit: Float) {
    val r = Random(seed)
    var x = -w * 0.02f
    var idx = 0
    while (x < w) {
        val bw = w * (minW * 0.25f + r.nextFloat() * 0.07f)
        val bh = h * (0.08f + r.nextFloat() * tall)
        val top = h - bh * 1.0f - h * (1f - 0.97f) * 0f
        val y0 = max(h * baseY, top)
        drawRect(col, Offset(x, y0), Size(bw, h - y0))
        if (lit > 0.9f) {
            val ww = 3.dp.toPx()
            val gap = 6.dp.toPx()
            var yy = y0 + gap
            var row = 0
            while (yy < h - gap) {
                var xx = x + gap * 0.6f
                var c = 0
                while (xx + ww < x + bw - 2f) {
                    if ((idx * 31 + row * 17 + c * 13) % 7 < 3) {
                        val warm = (idx + row + c) % 5 != 0
                        drawRect(if (warm) Color(0xFFFFE28C).copy(alpha = 0.85f) else Color(0xFF8CC8FF).copy(alpha = 0.8f), Offset(xx, yy), Size(ww, ww * 1.3f))
                    }
                    xx += gap
                    c++
                }
                yy += gap * 1.3f
                row++
            }
        }
        x += bw + w * 0.004f
        idx++
    }
}

private fun DrawScope.trees(w: Float, h: Float, seed: Int, col: Color, tall: Float, baseY: Float) {
    val r = Random(seed)
    var x = -w * 0.03f
    while (x < w) {
        val tw = w * (0.05f + r.nextFloat() * 0.05f)
        val th = h * (0.15f + r.nextFloat() * tall)
        val y = h * baseY + h * 0.3f * r.nextFloat()
        val p = Path().apply {
            moveTo(x + tw / 2, y - th)
            lineTo(x + tw, y + h)
            lineTo(x, y + h)
            close()
        }
        drawPath(p, col)
        x += tw * 0.7f
    }
}

// ---------------------------------------------------------------------------------------- nền video

private class VideoHolder(private val ctx: Context) : TextureView.SurfaceTextureListener {
    val view = TextureView(ctx).also { it.surfaceTextureListener = this }
    private var mp: MediaPlayer? = null
    private var surface: Surface? = null
    private var uri: Uri? = null
    private var prepared = false
    private var vw = 0
    private var vh = 0
    var play = true
    var vol = 0f

    fun setSource(u: Uri) {
        uri = u
        restart()
    }

    private fun restart() {
        stopPlayer()
        val u = uri ?: return
        val s = surface ?: return
        try {
            val p = MediaPlayer()
            mp = p
            p.setSurface(s)
            p.setDataSource(ctx, u)
            p.isLooping = true
            p.setOnPreparedListener {
                prepared = true
                applyVolume()
                if (play) it.start()
            }
            p.setOnVideoSizeChangedListener { _, w, h ->
                vw = w
                vh = h
                transform()
            }
            p.setOnErrorListener { _, _, _ -> true }
            p.prepareAsync()
        } catch (_: Throwable) {
            stopPlayer()
        }
    }

    fun applyVolume() {
        try {
            mp?.setVolume(vol, vol)
        } catch (_: Throwable) {
        }
    }

    fun applyPlay() {
        try {
            val p = mp ?: return
            if (!prepared) return
            if (play && !p.isPlaying) p.start() else if (!play && p.isPlaying) p.pause()
        } catch (_: Throwable) {
        }
    }

    fun transform() {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f || vw <= 0 || vh <= 0) return
        val va = vw.toFloat() / vh
        val wa = w / h
        val m = Matrix()
        if (va > wa) m.setScale(va / wa, 1f, w / 2f, h / 2f) else m.setScale(1f, wa / va, w / 2f, h / 2f)
        view.setTransform(m)
    }

    private fun stopPlayer() {
        prepared = false
        try {
            mp?.setOnPreparedListener(null)
            mp?.stop()
        } catch (_: Throwable) {
        }
        try {
            mp?.release()
        } catch (_: Throwable) {
        }
        mp = null
    }

    fun release() {
        stopPlayer()
        try {
            surface?.release()
        } catch (_: Throwable) {
        }
        surface = null
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(st)
        restart()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = transform()

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        stopPlayer()
        try {
            surface?.release()
        } catch (_: Throwable) {
        }
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}

@Composable
private fun VideoBg(uri: String, sound: Boolean, volume: Float, paused: Boolean) {
    val ctx = LocalContext.current
    val holder = remember { VideoHolder(ctx) }
    DisposableEffect(uri) {
        holder.setSource(Uri.parse(uri))
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { holder.release() } }
    val v = if (sound) volume.coerceIn(0f, 1f) else 0f
    LaunchedEffect(v) {
        holder.vol = v
        holder.applyVolume()
    }
    LaunchedEffect(paused) {
        holder.play = !paused
        holder.applyPlay()
    }
    AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
}

// ---------------------------------------------------------------------------------------- Tối thượng

/** Lớp đỏ gaming phía sau nội dung: sọc chéo, vệt quét, vignette đỏ, khung viền đỏ. */
@Composable
private fun UltimateLayers(on: Boolean) {
    val t by animateFloatAsState(if (on) 1f else 0f, tween(800), label = "ultT")
    if (t < 0.01f) return
    val inf = rememberInfiniteTransition(label = "ult")
    val sweep = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val pulse = inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF8A0010).copy(alpha = 0.18f * t))
        // sọc chéo
        val step = 22.dp.toPx()
        var x = -h
        while (x < w) {
            drawLine(Color(0xFFFF2A3A).copy(alpha = 0.05f * t), Offset(x, h), Offset(x + h, 0f), 2.dp.toPx())
            x += step
        }
        // vệt quét
        val sx = -w * 0.3f + sweep.value * w * 1.6f
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFFFF3A4A).copy(alpha = 0.16f * t), Color.Transparent),
                startX = sx - w * 0.15f, endX = sx + w * 0.15f,
            )
        )
        drawRect(
            Brush.radialGradient(
                0.4f to Color.Transparent, 1f to Color(0xB3500008).copy(alpha = 0.7f * t),
                center = Offset(w / 2, h / 2), radius = max(w, h) * 0.75f,
            )
        )
        val fw = 3.dp.toPx()
        drawRect(Color(0xFFFF2A3A).copy(alpha = pulse.value * 0.75f * t), Offset(fw / 2, fw / 2), Size(w - fw, h - fw), style = Stroke(fw))
        drawRect(Color(0xFFFF2A3A).copy(alpha = pulse.value * 0.18f * t), Offset(fw * 2, fw * 2), Size(w - fw * 4, h - fw * 4), style = Stroke(fw * 4))
    }
}

/** Nhãn ULTIMATE + hiệu ứng kích hoạt (loé sáng, sóng, biển hiệu). Đặt trên cùng. */
@Composable
fun UltimateFxOverlay(on: Boolean, modifier: Modifier = Modifier) {
    val fx = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(on) {
        if (first) {
            first = false
        } else if (on) {
            fx.snapTo(0f)
            fx.animateTo(1f, tween(1400, easing = LinearEasing))
        }
    }
    val t = fx.value
    Box(modifier.fillMaxSize()) {
        if (t < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val flash = (1f - t * 3.5f).coerceIn(0f, 1f)
                if (flash > 0f) drawRect(Color(0xFFFF2A3A).copy(alpha = flash * 0.55f))
                val maxR = max(size.width, size.height)
                drawCircle(Color(0xFFFF6A6A).copy(alpha = (1f - t) * 0.8f), maxR * t, Offset(size.width / 2, size.height / 2), style = Stroke(6.dp.toPx() * (1f - t) + 1f))
                val bandA = sin(PI.toFloat() * t).coerceIn(0f, 1f)
                val bh = 86.dp.toPx()
                val y = size.height / 2 - bh / 2
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCC50000C).copy(alpha = 0.85f * bandA), Color.Transparent)),
                    Offset(0f, y), Size(size.width, bh),
                )
            }
            val a = sin(PI.toFloat() * t).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().alpha(a), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("ULTIMATE", (40f + 10f * (1f - t)).sp, FontWeight.Black, Color.White, align = TextAlign.Center)
                    Txt("CHẾ ĐỘ TỐI THƯỢNG · KÍCH HOẠT", 12.sp, FontWeight.Bold, Color(0xFFFFB0B0), align = TextAlign.Center)
                }
            }
        }
        if (on) {
            val pulse = rememberInfiniteTransition(label = "pill").animateFloat(
                0.4f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pp"
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC3A0008))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF4A5A).copy(alpha = pulse.value)))
                Spacer(Modifier.width(7.dp))
                Txt("ULTIMATE · ROOT/ADB", 10.sp, FontWeight.Bold, Color(0xFFFFC0C0), maxLines = 1)
            }
        }
    }
}
