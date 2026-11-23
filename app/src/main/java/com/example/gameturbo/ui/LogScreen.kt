package com.example.gameturbo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gameturbo.app

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val c = LocalContext.current.app.controller
    val log by c.log.collectAsStateCompat()
    val busy by c.busy.collectAsStateCompat()
    val rows = log.asReversed()
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp).glass(26.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            H2("Nhật ký", Modifier.weight(1f))
            GtButton("Dọn app nền ngay", compact = true, enabled = !busy) { c.killNow() }
            Spacer(Modifier.width(8.dp))
            GtButton("Xoá", compact = true) { c.clearLog() }
        }
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Txt("Chưa có hoạt động nào. Vào game hoặc bấm Áp dụng để thấy nhật ký ở đây.", 12.sp, color = Dim)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(rows, key = { i, e -> "${e.time}-$i" }) { _, e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Txt(fmtTime(e.time), 11.sp, FontWeight.Medium, Faint, Modifier.width(62.dp))
                        Box(
                            Modifier.padding(top = 5.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(
                                when (e.level) {
                                    1 -> Ink.amber
                                    2 -> Ink.red
                                    else -> Ink.green
                                }
                            )
                        )
                        Txt(e.text, 12.sp, color = if (e.level == 2) Color(0xFFFFB0A8) else Color.White, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
