package com.example.gameturbo.data

/** Bảng màu dùng chung cho app (Compose) và cửa sổ nổi (View). Màu dạng ARGB Int. */
class Palette(
    val name: String,
    val a1: Int,
    val a2: Int,
    /** 4 màu đốm sáng nền */
    val blobs: IntArray,
    /** màu chữ trên nền accent */
    val on: Int,
) {
    companion object {
        private fun c(hex: Long): Int = (0xFF000000L or hex).toInt()

        val themes: List<Palette> = listOf(
            Palette("Đêm sao", c(0xffb26b), c(0xff7a1a), intArrayOf(c(0x4f46e5), c(0x8b5cf6), c(0x0fa5be), c(0x7a3fd0)), c(0x2a1200)),
            Palette("Tím neon", c(0xd9a8ff), c(0x9b3dff), intArrayOf(c(0x7a3dff), c(0xff3dcb), c(0x3d6aff), c(0xc43dff)), c(0xffffff)),
            Palette("Xanh biển", c(0x8fe3ff), c(0x1aa0ff), intArrayOf(c(0x1a8cff), c(0x2fe0d0), c(0x3d4aff), c(0x8a3dff)), c(0x00263a)),
            Palette("Hồng sakura", c(0xffc2da), c(0xff4d8d), intArrayOf(c(0xff4d8d), c(0xffb0c8), c(0xc43dff), c(0xff7a5a)), c(0x3a0018)),
            Palette("Xanh lá", c(0xb6ff9a), c(0x2fd45a), intArrayOf(c(0x2fd45a), c(0x1aa890), c(0xb6ff3d), c(0x2f8aff)), c(0x06260e)),
            Palette("Đen titan", c(0xe6e6ee), c(0x9aa0b4), intArrayOf(c(0x6a7088), c(0x3a4058), c(0x8a90a8), c(0x4a5068)), c(0x16161e)),
            Palette("Cam lửa", c(0xffb26b), c(0xff7a1a), intArrayOf(c(0xff6a3d), c(0xc43dff), c(0x2fd4ff), c(0xff3d8a)), c(0x2a1200)),
        )

        /** Giao diện đỏ gaming khi bật Tối thượng */
        val ultimate = Palette("Tối thượng", c(0xff8a8a), c(0xff2a3a), intArrayOf(c(0xff2a3a), c(0xa80f2a), c(0xff6a3d), c(0x5a0a1a)), c(0xffffff))

        fun of(index: Int): Palette = themes[index.coerceIn(0, themes.size - 1)]

        /** Bảng màu đang dùng: đỏ nếu Tối thượng đang bật và người dùng cho phép. */
        fun current(theme: Int, ultimateOn: Boolean, ultSkin: Boolean): Palette =
            if (ultimateOn && ultSkin) ultimate else of(theme)
    }
}
