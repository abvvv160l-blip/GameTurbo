# Game Turbo

App Android tăng hiệu năng game, giao diện kính lỏng (liquid glass) nằm ngang. **Không cần root**: chạy lệnh hệ thống qua [Shizuku](https://shizuku.rikka.app/). Có root thì mở thêm khoá xung CPU/GPU.

Hỗ trợ Android 6 đến 15 (minSdk 23). Viết bằng Kotlin + Jetpack Compose, phần tính toán nặng bằng **Rust** (có bản Kotlin dự phòng).

## Tính năng

- **Bong bóng nổi trong game** → chạm mở menu kính: đổi chế độ, bật/tắt HUD, khoá xung, dọn app nền. Chạm đúp thanh trên của menu để di chuyển, chạm đúp lần nữa để lưu vị trí.
- **HUD** FPS, GHz, %CPU, %GPU, nhiệt độ: kéo/phóng to được, chạm xuyên qua, chỉnh độ mờ (Android 12+ tối đa 80% để chạm xuyên được).
- **4 chế độ**: Tiết kiệm pin, Ổn định, Tối thượng, Tùy chỉnh. Độ phân giải và giới hạn FPS đặt riêng cho từng game.
- **Tối thượng** tự về Ổn định khi CPU/SoC đạt 80 °C (đọc nhiệt độ CPU, không phải pin; không đọc được thì dùng nhiệt độ pin 45 °C). Giao diện đỏ gaming khi bật.
- **Dọn app nền** khi vào game (tự động hoặc bấm tay), có danh sách app giữ lại.
- **Cài đặt**: màu chủ đề, độ mờ/màu kính, chất lượng kính (Cao/Thấp/Tắt), nền màu/ảnh/video (video có tiếng riêng), âm thanh nút và hiệu ứng.
- Nhật ký hoạt động, thời gian chơi từng game, thử tốc độ Rust so với Kotlin.

| Việc | Lệnh | Yêu cầu |
|---|---|---|
| Game Mode (pin / ổn định / hiệu năng) | `cmd game set --mode …` (dự phòng `cmd game mode …`) | Android 12+ |
| Giảm độ phân giải, giới hạn FPS từng game | `cmd game set --downscale … --fps …` | Android 12/13+ tuỳ hãng |
| Tần số quét tối thiểu | `settings put system min_refresh_rate` | Tuỳ hãng máy |
| Hiệu năng cố định | `cmd power set-fixed-performance-mode-enabled` | Chỉ một số máy |
| Khoá xung CPU/GPU | ghi vào `/sys/.../cpufreq`, `kgsl` | **Cần root** |
| Dọn app nền | `am kill` / `am force-stop` | Shizuku hoặc root |

## Rust làm gì, Kotlin làm gì

| Phần | Ngôn ngữ | Lý do |
|---|---|---|
| Tính %CPU, xung, GPU, FPS, nhiệt độ, nhận biết game, lập kế hoạch lệnh, chọn app cần dọn, bảo vệ nhiệt | **Rust** (`rust/`, thư viện `libgtcore.so`) | Xử lý chuỗi/số liệu nhanh, không rác bộ nhớ |
| Giao diện, cửa sổ nổi, dịch vụ nền, Shizuku, âm thanh | Kotlin | Bắt buộc dùng API Android (Rust không tăng tốc được) |

Hợp đồng giữa hai bên nằm ở `NATIVE_API.md`. Nếu bước build Rust lỗi hoặc máy không tải được thư viện, app tự dùng `core/KotlinCore.kt` (cùng kết quả, đã kiểm tra byte-by-byte trên kế hoạch lệnh).

## Build ra file .apk bằng GitHub Actions (làm hoàn toàn trên điện thoại)

1. Đẩy toàn bộ thư mục này lên repo GitHub (nhánh `main`).
2. Vào tab **Actions** → **Build APK** → **Run workflow**.
3. Chờ khoảng 3–8 phút (lần đầu có thêm bước biên dịch Rust).
4. Mở lần chạy xong → mục **Artifacts** → tải **GameTurbo-apk** → giải nén ra `app-debug.apk` → cài.
5. Nếu build **lỗi**, mở trang lần chạy đó: phần **Summary** có sẵn khối "Lỗi biên dịch". Chụp màn hình gửi để sửa.

Lưu ý: mỗi lần build ra file ký khác chữ ký cũ; nếu báo xung đột khi cài thì gỡ bản cũ rồi cài lại.

### Hoặc dùng Android Studio
`File > Open` chọn thư mục này, chờ Gradle sync, rồi `Build > Build APK(s)`. Muốn có lõi Rust: cài `cargo-ndk` và chạy `cd rust && cargo ndk -t arm64-v8a -t armeabi-v7a -P 23 -o ../app/src/main/jniLibs build --release` trước.

## Cài Shizuku

1. Cài app Shizuku.
2. Android 11+: Tùy chọn nhà phát triển → bật **Gỡ lỗi không dây**, mở Shizuku → *Bắt đầu qua gỡ lỗi không dây* (ghép nối theo hướng dẫn trong app Shizuku).
3. Android 6–10: cần cắm máy tính chạy lệnh ADB mà Shizuku hiển thị, làm lại sau mỗi lần khởi động lại máy.
4. Mở Game Turbo → chấm trạng thái ở góc trên phải → **Cấp quyền**.
5. Bật **Theo dõi game** (cần quyền "Hiển thị trên ứng dụng khác").

## Giới hạn cần biết

- Phần mềm này viết khi chưa có máy Android để chạy thử; nhiều thứ phụ thuộc hãng máy (đo FPS qua SurfaceFlinger, đường dẫn đọc GPU, lệnh `cmd game`). Chỗ nào không đọc/áp dụng được app hiện `--` hoặc ghi vào Nhật ký, không báo thành công giả.
- Khoá xung chỉ có khi có root. Không root thì Tối thượng dùng Game Mode hiệu năng + hiệu năng cố định + tần số quét cao nhất.
- Nếu app thoát bất ngờ: **Cài đặt → Về app → Lỗi gần nhất → Sao chép** rồi gửi nội dung đó.

## Cấu trúc

```
rust/                       lõi Rust (cargo test chạy được trên máy tính)
app/src/main/java/com/example/gameturbo/
  core/      Core.kt (JNI), KotlinCore.kt (dự phòng), SysReader.kt
  engine/    TurboController, Sampler, Exec
  service/   TurboService (dịch vụ nền)
  overlay/   bong bóng, menu kính, HUD
  ui/        giao diện Compose (Game, HUD, Nhật ký, Cài đặt, nền)
  data/      Settings, GameRepo, Palette
  audio/     SoundBank (âm thanh trong res/raw)
```
