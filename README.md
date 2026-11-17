# Game Turbo (Android, Kotlin + Jetpack Compose + Shizuku)

App tăng hiệu năng game **không cần root**, chạy lệnh hệ thống qua [Shizuku](https://shizuku.rikka.app/).

## Tính năng

| Tính năng | Lệnh dùng | Yêu cầu |
|---|---|---|
| Game Mode = Performance cho từng game | `cmd game set --mode performance ...` (dự phòng: `cmd game mode performance`) | Android 12+ |
| Giảm độ phân giải riêng từng game | `--downscale 0.5 … 0.9` | Android 13+ (theo hiểu biết của mình, cần thử trên máy thật) |
| Giới hạn FPS riêng từng game | `--fps 30/45/60/90/120` | Android 13+ |
| Khoá tần số quét tối thiểu | `settings put system min_refresh_rate` | Tuỳ hãng máy |
| Hiệu năng cố định | `cmd power set-fixed-performance-mode-enabled` | Chỉ một số máy |
| Giảm độ phân giải cả màn hình | `wm size`, `wm density` | Mọi máy |
| Dọn RAM | `am kill-all` | Mọi máy |
| Hiện nhiệt độ pin và trạng thái nhiệt | `BatteryManager`, `PowerManager` | Không cần Shizuku |

**Không làm được:** ép xung CPU/GPU hoặc khóa xung nhịp tối đa. Việc này cần root vì SELinux chặn quyền shell ghi vào `/sys/.../cpufreq`.

## Cách build ra file .apk

### Cách 1: Android Studio (dễ nhất)
1. Cài [Android Studio](https://developer.android.com/studio).
2. `File > Open` và chọn thư mục `GameTurbo`. Chờ Gradle sync xong (lần đầu tải khá lâu).
3. Menu `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
4. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Chép sang điện thoại rồi cài.

Nếu Android Studio đề nghị nâng cấp phiên bản Android Gradle Plugin, cứ chấp nhận.

### Cách 2: GitHub Actions (không cần cài gì)
1. Tạo repo GitHub và đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào tab **Actions > Build APK > Run workflow**.
3. Khi chạy xong, tải file `GameTurbo-apk` ở phần Artifacts.

### Cách 3: Dòng lệnh
```
./gradlew assembleDebug
```
Cần JDK 17 và Android SDK (đặt biến `ANDROID_HOME`).

## Cách dùng trên điện thoại
1. Cài **Shizuku** từ Google Play hoặc GitHub.
2. Bật *Tuỳ chọn nhà phát triển > Gỡ lỗi không dây* (Android 11+), mở Shizuku và làm theo hướng dẫn để bấm **Start**. Phải làm lại sau mỗi lần khởi động lại máy.
3. Mở Game Turbo, bấm **Cấp quyền**.
4. Chọn game, chọn độ phân giải và FPS, bấm **Chơi ngay**. Nếu game đang chạy, hãy đóng hẳn rồi mở lại để thay đổi có hiệu lực.

## Nếu màn hình bị lỗi sau khi đổi độ phân giải toàn màn hình
Cắm máy vào PC và chạy:
```
adb shell wm size reset
adb shell wm density reset
```

## Cấu trúc code
- `ShellService.kt` chạy trong tiến trình Shizuku, thực thi lệnh shell.
- `ShizukuShell.kt` quản lý kết nối, quyền và gọi lệnh.
- `MainViewModel.kt` chứa toàn bộ logic (profile game, tuỳ chọn hệ thống, giám sát nhiệt độ).
- `Ui.kt`, `Theme.kt`, `MainActivity.kt` là giao diện Compose.

## Đổi tên package
Hiện là `com.example.gameturbo`. Nếu muốn đổi, sửa `namespace` và `applicationId` trong `app/build.gradle.kts`, đổi tên thư mục trong `java/` và `aidl/`, rồi sửa dòng `package` ở đầu mỗi file.
