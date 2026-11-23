package com.example.gameturbo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Chữ dùng khắp app (màu mặc định trắng, hợp nền kính). */
@Composable
fun Txt(
    text: String,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        lineHeight = (size.value * 1.3f).sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

val Dim = Color(0xB3FFFFFF)
val Faint = Color(0x80FFFFFF)

/** Công tắc bật/tắt. */
@Composable
fun GtSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val ui = LocalUi.current
    val t by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 500f), label = "sw")
    val off = Color.White.copy(alpha = 0.2f)
    Box(
        Modifier
            .gtClickable(enabled = enabled) { onChange(!on) }
            .alpha(if (enabled) 1f else 0.4f)
            .size(46.dp, 27.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(lerp(off, ui.a1, t), lerp(off, ui.a2, t))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .offset(x = (3f + 19f * t).dp, y = 3.dp)
                .size(21.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Nút chọn dạng viên thuốc (chip). */
@Composable
fun GtChip(text: String, selected: Boolean, enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit) {
    val ui = LocalUi.current
    val t by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "chip")
    val base = Color.White.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .gtClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(lerp(base, ui.a1, t), lerp(base, ui.a2, t))))
            .border(1.dp, Color.White.copy(alpha = 0.2f * (1f - t)), shape)
            .padding(horizontal = if (compact) 10.dp else 13.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, if (compact) 11.sp else 12.sp, FontWeight.SemiBold, lerp(Color.White, ui.on, t), maxLines = 1)
    }
}

/** Bố cục tự xuống dòng khi hết chỗ (thay cho FlowRow còn thử nghiệm). */
@Composable
fun Flow(
    modifier: Modifier = Modifier,
    hGap: androidx.compose.ui.unit.Dp = 7.dp,
    vGap: androidx.compose.ui.unit.Dp = 7.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val hg = hGap.roundToPx()
        val vg = vGap.roundToPx()
        val maxW = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var x = 0
        var y = 0
        var rowH = 0
        var used = 0
        val xs = IntArray(placeables.size)
        val ys = IntArray(placeables.size)
        for ((i, p) in placeables.withIndex()) {
            if (x > 0 && maxW != androidx.compose.ui.unit.Constraints.Infinity && x + p.width > maxW) {
                x = 0
                y += rowH + vg
                rowH = 0
            }
            xs[i] = x
            ys[i] = y
            x += p.width
            used = maxOf(used, x)
            x += hg
            rowH = maxOf(rowH, p.height)
        }
        val w = if (maxW == androidx.compose.ui.unit.Constraints.Infinity) used else maxW
        layout(w, y + rowH) {
            for ((i, p) in placeables.withIndex()) p.placeRelative(xs[i], ys[i])
        }
    }
}

/** Hàng chip tự xuống dòng khi hết chỗ. */
@Composable
fun ChipRow(items: List<String>, selected: Int, disabled: Set<Int> = emptySet(), compact: Boolean = false, onSelect: (Int) -> Unit) {
    Flow(hGap = if (compact) 5.dp else 7.dp, vGap = if (compact) 5.dp else 7.dp) {
        items.forEachIndexed { i, s -> GtChip(s, i == selected, enabled = i !in disabled, compact = compact) { onSelect(i) } }
    }
}

/** Thanh chọn có "ống kính" trượt (dọc hoặc ngang). */
@Composable
fun LensSegment(
    count: Int,
    selected: Int,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    tickFor: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
    content: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val ui = LocalUi.current
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.74f, stiffness = 380f), label = "lens")
    BoxWithConstraints(modifier) {
        val iw = if (vertical) maxWidth else maxWidth / count
        val ih = if (vertical) maxHeight / count else maxHeight
        Box(
            Modifier
                .offset(x = if (vertical) 0.dp else iw * pos, y = if (vertical) ih * pos else 0.dp)
                .size(iw, ih)
                .padding(3.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(ui.a1.copy(alpha = 0.55f), ui.a2.copy(alpha = 0.40f))))
                .border(1.2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        )
        if (vertical) {
            Column(Modifier.fillMaxSize()) {
                for (i in 0 until count) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .gtClickable(sound = tickFor(i)) { onSelect(i) },
                        contentAlignment = Alignment.CenterStart,
                    ) { content(i, i == selected) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until count) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .gtClickable(sound = tickFor(i)) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) { content(i, i == selected) }
                }
            }
        }
    }
}

/** Thanh trượt 0..1 tự vẽ, có nhãn giá trị bên phải. */
@Composable
fun GtSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
) {
    val ui = LocalUi.current
    var w by remember { mutableIntStateOf(1) }
    val cb by rememberUpdatedState(onChange)
    val v = value.coerceIn(0f, 1f)
    Row(modifier.alpha(if (enabled) 1f else 0.4f), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(30.dp)
                .onSizeChanged { w = it.width.coerceAtLeast(1) }
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures { o -> cb((o.x / w).coerceIn(0f, 1f)) }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> cb((o.x / w).coerceIn(0f, 1f)) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                cb((change.position.x / w).coerceIn(0f, 1f))
                            },
                        )
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val th = 6.dp.toPx()
                val cy = size.height / 2f
                val pad = 11.dp.toPx()
                val x0 = pad
                val x1 = size.width - pad
                val x = x0 + (x1 - x0) * v
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(x0, cy - th / 2), Size(x1 - x0, th), CornerRadius(th, th))
                drawRoundRect(
                    Brush.horizontalGradient(listOf(ui.a1, ui.a2), startX = x0, endX = x1),
                    Offset(x0, cy - th / 2), Size((x - x0).coerceAtLeast(0f), th), CornerRadius(th, th),
                )
                drawCircle(Color.Black.copy(alpha = 0.25f), 11.dp.toPx(), Offset(x, cy + 1.5.dp.toPx()))
                drawCircle(Color.White, 10.dp.toPx(), Offset(x, cy))
                drawCircle(ui.a2, 4.dp.toPx(), Offset(x, cy))
            }
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Txt(label, 12.sp, FontWeight.SemiBold, Dim, Modifier.width(44.dp), align = TextAlign.End)
        }
    }
}

/** Một dòng cài đặt: tiêu đề (+ dòng phụ) bên trái, điều khiển bên phải. */
@Composable
fun Line(title: String, sub: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, 13.sp, FontWeight.Medium)
            if (sub != null) Txt(sub, 10.5.sp, color = Faint)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Txt(text, 11.sp, FontWeight.SemiBold, Faint, modifier.padding(top = 8.dp, bottom = 5.dp))
}

@Composable
fun H2(text: String, modifier: Modifier = Modifier) {
    Txt(text, 16.sp, FontWeight.Bold, Color.White, modifier.padding(bottom = 4.dp))
}

/** Nút bấm: [primary] = nền màu chủ đề, ngược lại là kính. */
@Composable
fun GtButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .gtClickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .then(modifier)
            .clip(shape)
            .background(
                if (primary) Brush.horizontalGradient(listOf(ui.a1, ui.a2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f)))
            )
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.5f else 0.22f), shape)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, if (compact) 12.sp else 13.sp, FontWeight.Bold, if (primary) ui.on else Color.White, maxLines = 1)
    }
}

/** Thông báo nhỏ kiểu kính ở cuối màn hình. level: 0 thường, 1 cảnh báo, 2 lỗi. */
@Composable
fun GlassToast(text: String, level: Int, modifier: Modifier = Modifier) {
    val dot = when (level) {
        1 -> Ink.amber
        2 -> Ink.red
        else -> Ink.green
    }
    Row(
        modifier.glass(22.dp, dark = true).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Txt(text, 13.sp, FontWeight.Medium, maxLines = 2)
    }
}

/** Hộp thoại kính. */
@Composable
fun GlassDialog(onDismiss: () -> Unit, width: Int = 420, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(width.dp)
                .glass(28.dp, dark = true)
                .padding(20.dp)
        ) { content() }
    }
}

/** Ô nhập chữ một dòng (tìm kiếm). */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Txt(hint, 13.sp, color = Faint, maxLines = 1)
                inner()
            }
        },
    )
}
