# gtcore: hợp đồng API giữa Kotlin và Rust

Thư viện Rust `rust/` (crate `gtcore`, cdylib `libgtcore.so`) được Kotlin gọi qua JNI.
Kotlin: `package com.example.gameturbo.core` → `object Native { @JvmStatic external fun ... }`.
Ký hiệu JNI: `Java_com_example_gameturbo_core_Native_<tên hàm>` (hàm static, tham số thứ 2 là `JClass`).

Quy ước chung
- Mọi chuỗi vào/ra là UTF-8. Xuống dòng dùng `\n` (chấp nhận cả `\r\n` khi đọc).
- Hàm KHÔNG được panic ra ngoài JNI: bọc `catch_unwind`, lỗi thì trả giá trị mặc định (ghi ở từng hàm).
- Trạng thái toàn cục (cpuUsage, guard) giữ trong `static Mutex`. Mutex bị poison thì vẫn dùng tiếp (`into_inner`).
- Số thực trả về dạng `Float` (f32). Giá trị "không có" = `-1.0`.
- Mọi hàm logic phải là hàm Rust thuần (không phụ thuộc JNI) để test được trên máy chủ bằng `cargo test`; lớp JNI chỉ là vỏ mỏng.

## Danh sách hàm

| # | Tên | Tham số | Trả về |
|---|---|---|---|
| 1 | `version` | () | String, ví dụ `gtcore 0.1.0` (lấy từ `CARGO_PKG_VERSION`) |
| 2 | `cpuUsage` | (procStat: String) | Float |
| 3 | `cpuUsageReset` | () | void |
| 4 | `freqStats` | (lines: String) | String |
| 5 | `gpuNodes` | () | String |
| 6 | `gpuParse` | (kind: String, path: String, content: String) | Float |
| 7 | `fpsFromLatency` | (text: String) | Float |
| 8 | `findLayer` | (list: String, pkg: String) | String |
| 9 | `focusedPackage` | (dump: String) | String |
| 10 | `cpuTemp` | (dump: String) | Float |
| 11 | `planMode` | (params: String) | String |
| 12 | `planRestore` | (params: String) | String |
| 13 | `runningThirdParty` | (pmList: String, ps: String) | String |
| 14 | `killList` | (running: String, keep: String, game: String) | String |
| 15 | `guardUpdate` | (tempC: Float, source: Int) | Int |
| 16 | `guardReset` | () | void |

Giá trị mặc định khi lỗi: Float → `-1.0`, String → `""`, Int → `0`.

### 2. cpuUsage
Đầu vào: toàn bộ nội dung `/proc/stat`. Lấy dòng đầu bắt đầu bằng `cpu ` (tổng, có khoảng trắng sau `cpu`).
Các trường (jiffies): `user nice system idle iowait irq softirq steal [guest guest_nice]`.
`total` = tổng 8 trường đầu (thiếu thì coi là 0), `idleAll` = `idle + iowait`.
So với mẫu lưu lần trước: `pct = 100 * (Δtotal − Δidle) / Δtotal`, kẹp 0..100.
Trả `-1` nếu: chưa có mẫu trước (nhưng vẫn lưu mẫu), không parse được (không lưu), `Δtotal <= 0`, hoặc bộ đếm giảm (lưu mẫu mới).
`cpuUsageReset` xoá mẫu đã lưu.

### 4. freqStats
Mỗi dòng: `cpu0 1804800` hoặc chỉ `1804800` (đơn vị kHz). Dòng có giá trị ≤ 0 hoặc không parse được thì bỏ qua (lõi tắt thường trả `-1` hoặc rỗng).
Trả `max=<MHz>;avg=<MHz>;n=<số lõi hợp lệ>` với MHz = kHz/1000, 1 chữ số thập phân, dấu chấm. Không có lõi hợp lệ: `max=-1;avg=-1;n=0`.

### 5. gpuNodes
Trả danh sách ứng viên, mỗi dòng `kind|path`, kind ∈ {`busy`,`freq`}, path có thể chứa ký tự glob `*` (Kotlin sẽ giải bằng `ls -d`). Thứ tự ưu tiên, tối thiểu gồm:
```
busy|/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
busy|/sys/class/kgsl/kgsl-3d0/gpubusy
freq|/sys/class/kgsl/kgsl-3d0/gpuclk
freq|/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq
busy|/sys/devices/platform/*.mali/utilization
busy|/sys/class/devfreq/*.mali/load
freq|/sys/class/devfreq/*.mali/cur_freq
busy|/sys/class/devfreq/*.gpu/load
freq|/sys/class/devfreq/*.gpu/cur_freq
busy|/sys/class/misc/mali0/device/utilization
busy|/sys/kernel/gpu/gpu_busy
freq|/sys/kernel/gpu/gpu_clock
busy|/proc/mali/utilization
```

### 6. gpuParse(kind, path, content)
- `kind == "busy"`:
  - path kết thúc bằng `gpubusy`: nội dung `"<busy> <total>"` (2 số nguyên) → `busy/total*100` (total = 0 → 0).
  - còn lại: lấy số đầu tiên trong nội dung (chấp nhận `45`, `45 %`, `45@800000000Hz`, `45.5`) → kẹp 0..100.
- `kind == "freq"`: lấy số đầu tiên v; v ≥ 1e7 → Hz (v/1e6); 1e4 ≤ v < 1e7 → kHz (v/1e3); còn lại coi là MHz. Trả MHz.
- Lỗi: `-1`.

### 7. fpsFromLatency
Đầu vào: kết quả `dumpsys SurfaceFlinger --latency "<layer>"`. Dòng 1 = chu kỳ làm tươi (ns). Các dòng sau (tối đa 127) gồm 3 số nguyên: `desiredPresent actualPresent frameReady`.
Dùng cột 2 (`actualPresent`). Bỏ dòng có giá trị `<= 0` hoặc `9223372036854775807` (i64::MAX) hoặc không đủ 3 số.
Lấy `t_last` = lớn nhất; chọn các mốc trong cửa sổ `[t_last − 1_000_000_000, t_last]`; nếu có n ≥ 2 mốc và khoảng `span = t_max − t_min > 0` thì `fps = (n−1) / (span/1e9)`, kẹp tối đa 240; ngược lại `-1`.

### 8. findLayer(list, pkg)
Đầu vào: kết quả `dumpsys SurfaceFlinger --list` (mỗi dòng một tên layer). Chọn dòng chứa `pkg`, bỏ dòng chứa `Background for`, `Bounds for`, `Splash`, `Letterbox`.
Điểm: chứa `SurfaceView` +3; chứa `(BLAST)` +1; chứa `pkg + "/"` +1. Chọn điểm cao nhất, hòa thì dòng đầu. Trả dòng đó đã `trim` (nguyên tên layer, không thêm dấu nháy). Không có → `""`.

### 9. focusedPackage(dump)
Đầu vào: kết quả `dumpsys window` (hoặc `dumpsys activity activities`). Tên gói = ký tự `[A-Za-z0-9_.]+` ngay trước dấu `/`. Thứ tự ưu tiên (lấy cái đầu tiên tìm được, bỏ giá trị `null`):
1. `mCurrentFocus=Window{<hex> u<số> <pkg>/<activity>}`
2. `mFocusedApp=...ActivityRecord{<hex> u<số> <pkg>/<activity> t<số>}`
3. `topResumedActivity=ActivityRecord{<hex> u<số> <pkg>/...`
4. `mResumedActivity: ActivityRecord{<hex> u<số> <pkg>/...`
5. `ResumedActivity` bất kỳ dạng tương tự
Nếu cửa sổ focus không có dấu `/` (ví dụ `StatusBar`, `NotificationShade`) thì bỏ qua và thử mục tiếp theo. Không tìm được → `""`.

### 10. cpuTemp(dump)
Mỗi dòng `type<TAB>value` (hoặc type và value cách nhau bằng khoảng trắng; value là token cuối). `type` đọc từ `thermal_zone*/type`, value từ `thermal_zone*/temp`.
Chuẩn hoá: `v >= 1000` → `v/1000`; `150 <= v < 1000` → `v/10`; còn lại giữ nguyên.
Chọn vùng "giống CPU": type (viết thường) chứa một trong `cpu`, `soc`, `tsens`, `mtktscpu`, `cluster`, `big`, `little`, `prime`, `gold`, `silver`, `apc`, `ap_therm`, `ap-therm`, `ssoc`, và KHÔNG chứa `battery`, `batt`, `skin`, `charger`, `wifi`, `modem`, `gpu`, `ddr`, `usb`, `xo`, `pa-`, `quiet`, `flash`, `camera`, `display`.
Chỉ nhận giá trị trong 10..130. Trả `max` của các vùng hợp lệ, làm tròn 0.1. Không có → `-1`.

### 11. planMode(params)
`params` là các dòng `key=value`. Khoá:
`mode` (0 tiết kiệm pin, 1 ổn định, 2 tối thượng, 3 tùy chỉnh), `sdk` (Build.VERSION.SDK_INT), `pkg` (gói game, có thể rỗng), `res` (50..100, % độ phân giải gốc của game, 100 = gốc), `fps` (0 = không giới hạn, hoặc số), `refresh` (chỉ dùng cho mode 3; 0 = không đổi, hoặc Hz), `maxrefresh` (Hz tối đa của màn hình, 0 = không biết), `fixed` (0/1, chỉ mode 3), `gm` (chỉ mode 3: `none|battery|standard|performance`), `root` (0/1: root dùng được), `boost` (0/1, chỉ mode 3: ép xung nhịp bằng root).
Thiếu khoá → mặc định: mode 1, sdk 0, pkg "", res 100, fps 0, refresh 0, maxrefresh 0, fixed 0, gm performance, root 0, boost 0.

Giá trị thật sự áp dụng theo mode (`res0`,`fps0` là giá trị người dùng đặt cho game):
| mode | gm | res | fps | refresh | fixed | root boost |
|---|---|---|---|---|---|---|
| 0 | battery | min(res0, 70) | fps0==0 hoặc >30 → 30, ngược lại fps0 | 60 | 0 | không |
| 1 | standard | min(res0, 90) | fps0==0 hoặc >60 → 60, ngược lại fps0 | không đổi | 0 | không |
| 2 | performance | res0 | fps0 | maxrefresh nếu >0, ngược lại không đổi | 1 | có nếu root=1 |
| 3 | theo `gm` | res0 | fps0 | refresh | fixed | boost && root |

Đầu ra: mỗi dòng `X|lệnh`, X là:
- `S`: chạy bằng quyền shell (Shizuku) hoặc root
- `R`: chỉ chạy được bằng root (`su`)
- `F`: dự phòng, chỉ chạy nếu dòng ngay trước đó (loại `S`) thất bại

Thứ tự dòng:
1. Nếu `sdk >= 31` và `pkg` không rỗng và gm ≠ none:
   `S|cmd game set --mode <gm> [--downscale <res/100, 1 chữ số thập phân>] [--fps <n>] <pkg>`
   (`--downscale` chỉ khi res < 100, `--fps` chỉ khi fps > 0; số thập phân luôn dùng dấu chấm), rồi
   `F|cmd game mode <gm> <pkg>`.
2. Nếu refresh > 0: `S|settings put system min_refresh_rate <n>` và `S|settings put system peak_refresh_rate <n>`.
3. Nếu fixed = 1: `S|cmd power set-fixed-performance-mode-enabled true`.
4. Nếu root boost: các dòng `R|` gồm (a) lưu trạng thái CPU một lần vào `/data/local/tmp/gt_cpu_prev` (chỉ khi file chưa có), mỗi dòng file là `<policy_dir> <governor> <scaling_min_freq>`; (b) với mọi `/sys/devices/system/cpu/cpufreq/policy*`: ghi `cpuinfo_max_freq` vào `scaling_max_freq`, rồi vào `scaling_min_freq`, rồi `performance` vào `scaling_governor`; (c) nếu có `/sys/class/kgsl/kgsl-3d0` thì ghi 1 vào `force_clk_on`, `force_bus_on`, `force_rail_on` và nếu có `devfreq/max_freq` thì chép sang `devfreq/min_freq`.
Mỗi dòng `R|` là một lệnh sh hợp lệ trên một dòng (dùng `;`, `{ }`, vòng `for`), mọi lỗi ghi file bị bỏ qua (`2>/dev/null`, `|| true`). Kiểm tra bằng `sh -n -c "<lệnh>"` trong test.

### 12. planRestore(params)
Khoá dùng: `sdk`, `pkg`, `root`. Đầu ra, theo thứ tự:
- Nếu `sdk >= 31` và pkg không rỗng: `S|cmd game reset <pkg>`
- `S|settings delete system min_refresh_rate`, `S|settings delete system peak_refresh_rate`
- `S|cmd power set-fixed-performance-mode-enabled false`
- Nếu root = 1: `R|` khôi phục governor và `scaling_min_freq` từ `/data/local/tmp/gt_cpu_prev` (đọc từng dòng `<policy_dir> <governor> <min>` bằng `while read`), rồi xoá file đó; sau đó ghi 0 vào `force_clk_on`, `force_bus_on`, `force_rail_on` nếu có kgsl.

### 13. runningThirdParty(pmList, ps)
`pmList`: kết quả `pm list packages -3` (mỗi dòng `package:com.x`; chấp nhận cả `package:/path/base.apk=com.x`, lấy phần sau dấu `=` cuối cùng).
`ps`: kết quả `ps -A -o NAME` (có thể có dòng tiêu đề `NAME`). Lấy token cuối mỗi dòng, bỏ phần sau dấu `:` (tiến trình phụ).
Trả các gói có trong `pmList` và đang chạy, không trùng, mỗi dòng một gói, theo thứ tự xuất hiện trong `ps`.

### 14. killList(running, keep, game)
`running`, `keep`: mỗi dòng một tên gói. Trả `running` trừ `keep`, trừ `game`, trừ danh sách bảo vệ cố định:
`com.example.gameturbo`, `moe.shizuku.privileged.api`, và mọi gói bắt đầu bằng `com.google.android.gms`, `com.google.android.gsf`, `com.android.`, `android`. Giữ thứ tự, không trùng.

### 15. guardUpdate(tempC, source) và 16. guardReset
source 0 = nhiệt độ CPU/SoC (nóng ≥ 72, tới hạn ≥ 80, về bình thường khi < 70); source 1 = nhiệt độ pin (nóng ≥ 41, tới hạn ≥ 45, về bình thường khi < 40).
Trạng thái toàn cục `triggered: bool`. Trả:
- `0`: bình thường (và nếu nhiệt độ dưới ngưỡng "về bình thường" thì đặt `triggered = false`; nhiệt độ NaN hoặc < 0 → trả 0, không đổi trạng thái)
- `1`: nóng (≥ ngưỡng nóng, < ngưỡng tới hạn)
- `2`: tới hạn và `triggered` chưa bật → bật `triggered`, Kotlin phải hạ chế độ và phát cảnh báo
- `3`: tới hạn và `triggered` đã bật (không báo lại)
`guardReset` đặt `triggered = false`.

## Yêu cầu build
- `rust/Cargo.toml`: `name = "gtcore"`, `[lib] crate-type = ["cdylib", "rlib"]`, phụ thuộc `jni = "0.21"`. Không thêm crate khác trừ khi thật cần.
- `[profile.release]`: `opt-level = 3`, `lto = true`, `codegen-units = 1`, `strip = true` (KHÔNG dùng `panic = "abort"`).
- Build Android bằng `cargo ndk -t arm64-v8a -t armeabi-v7a -P 23 -o ../app/src/main/jniLibs build --release` (workflow lo phần này).
- Giữ `Cargo.lock` trong repo.
