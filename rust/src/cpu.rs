//! Đo %CPU từ /proc/stat và thống kê tần số từ scaling_cur_freq.

use std::sync::Mutex;

/// Mẫu /proc/stat lần trước: (total, idleAll).
pub struct CpuState {
    prev: Option<(u64, u64)>,
}

impl CpuState {
    pub const fn new() -> Self {
        CpuState { prev: None }
    }

    pub fn reset(&mut self) {
        self.prev = None;
    }

    /// Trả %CPU bận từ mẫu trước tới mẫu này, hoặc -1.
    pub fn update(&mut self, text: &str) -> f32 {
        let cur = match parse_stat(text) {
            Some(c) => c,
            None => return -1.0, // không parse được: không lưu
        };
        let prev = self.prev.replace(cur);
        let p = match prev {
            Some(p) => p,
            None => return -1.0, // chưa có mẫu trước
        };
        // Δtotal <= 0 hoặc bộ đếm giảm
        if cur.0 <= p.0 || cur.1 < p.1 {
            return -1.0;
        }
        let dt = (cur.0 - p.0) as f64;
        let di = (cur.1 - p.1) as f64;
        let pct = 100.0 * (dt - di) / dt;
        pct.clamp(0.0, 100.0) as f32
    }
}

impl Default for CpuState {
    fn default() -> Self {
        Self::new()
    }
}

/// Lấy (total, idleAll) từ dòng `cpu ` đầu tiên của /proc/stat.
pub fn parse_stat(text: &str) -> Option<(u64, u64)> {
    for line in text.lines() {
        let l = line.trim_start();
        if let Some(rest) = l.strip_prefix("cpu") {
            if !rest.starts_with(|c: char| c.is_whitespace()) {
                continue; // cpu0, cpu1...
            }
            let mut v = [0u64; 8];
            let mut n = 0;
            for tok in rest.split_whitespace().take(8) {
                v[n] = tok.parse().ok()?;
                n += 1;
            }
            if n < 4 {
                return None;
            }
            let total = v.iter().fold(0u64, |a, b| a.saturating_add(*b));
            let idle = v[3].saturating_add(v[4]);
            return Some((total, idle));
        }
    }
    None
}

static GLOBAL: Mutex<CpuState> = Mutex::new(CpuState::new());

pub fn cpu_usage(text: &str) -> f32 {
    GLOBAL.lock().unwrap_or_else(|e| e.into_inner()).update(text)
}

pub fn cpu_usage_reset() {
    GLOBAL.lock().unwrap_or_else(|e| e.into_inner()).reset();
}

/// `max=<MHz>;avg=<MHz>;n=<số lõi hợp lệ>`; mỗi dòng `cpuN <kHz>` hoặc chỉ `<kHz>`.
pub fn freq_stats(lines: &str) -> String {
    let mut n = 0u32;
    let mut sum = 0f64;
    let mut max = 0f64;
    for line in lines.lines() {
        let tok = match line.split_whitespace().last() {
            Some(t) => t,
            None => continue,
        };
        let khz: f64 = match tok.parse() {
            Ok(v) => v,
            Err(_) => continue,
        };
        if !(khz > 0.0) || !khz.is_finite() {
            continue;
        }
        let mhz = khz / 1000.0;
        n += 1;
        sum += mhz;
        if mhz > max {
            max = mhz;
        }
    }
    if n == 0 {
        return "max=-1;avg=-1;n=0".to_string();
    }
    format!("max={:.1};avg={:.1};n={}", max, sum / n as f64, n)
}

#[cfg(test)]
mod tests {
    use super::*;

    const A: &str = "cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 50 0 25 400 25 0 0 0 0 0\nintr 12345 0 0\n";
    const B: &str = "cpu  160 0 70 860 60 0 10 0 0 0\ncpu0 80 0 35 430 30 0 5 0 0 0\nintr 12999 0 0\n";

    #[test]
    fn parse_stat_basic() {
        assert_eq!(parse_stat(A), Some((1000, 850)));
        assert_eq!(parse_stat(B), Some((1160, 920)));
    }

    #[test]
    fn guest_fields_are_not_double_counted() {
        // guest=7 guest_nice=3 nằm sau 8 trường đầu, không được cộng thêm
        let t = "cpu  10 0 10 70 10 0 0 0 7 3\n";
        assert_eq!(parse_stat(t), Some((100, 80)));
    }

    #[test]
    fn missing_trailing_fields_are_zero() {
        let t = "cpu  10 0 10 70 10\n";
        assert_eq!(parse_stat(t), Some((100, 80)));
        assert_eq!(parse_stat("cpu  1 2 3\n"), None);
        assert_eq!(parse_stat("cpu  a b c d e\n"), None);
        assert_eq!(parse_stat("cpu0 1 2 3 4\n"), None);
        assert_eq!(parse_stat(""), None);
    }

    #[test]
    fn usage_between_samples() {
        let mut s = CpuState::new();
        assert_eq!(s.update(A), -1.0); // mẫu đầu
        let p = s.update(B);
        assert!((p - 56.25).abs() < 0.01, "{p}");
        // mẫu y hệt: Δtotal = 0
        assert_eq!(s.update(B), -1.0);
    }

    #[test]
    fn counter_decrease_and_garbage() {
        let mut s = CpuState::new();
        s.update(B);
        assert_eq!(s.update(A), -1.0); // giảm, nhưng lưu A
        let p = s.update(B);
        assert!(p > 0.0);
        // rác không làm mất mẫu đã lưu
        assert_eq!(s.update("rác"), -1.0);
        assert!(s.update("cpu  200 0 80 900 60 0 10 0 0 0\n") >= 0.0);
        s.reset();
        assert_eq!(s.update(B), -1.0);
    }

    #[test]
    fn crlf_input() {
        let a = A.replace('\n', "\r\n");
        let b = B.replace('\n', "\r\n");
        let mut s = CpuState::new();
        s.update(&a);
        assert!((s.update(&b) - 56.25).abs() < 0.01);
    }

    #[test]
    fn global_wrapper() {
        cpu_usage_reset();
        assert_eq!(cpu_usage(A), -1.0);
        assert!((cpu_usage(B) - 56.25).abs() < 0.01);
        cpu_usage_reset();
        assert_eq!(cpu_usage(B), -1.0);
    }

    #[test]
    fn freq_stats_mixed() {
        let t = "cpu0 1804800\ncpu1 1804800\ncpu2 -1\ncpu3 2419200\ncpu4 \ncpu5 300000\n";
        assert_eq!(freq_stats(t), "max=2419.2;avg=1582.2;n=4");
        assert_eq!(freq_stats("1804800\n2419200"), "max=2419.2;avg=2112.0;n=2");
        assert_eq!(freq_stats(""), "max=-1;avg=-1;n=0");
        assert_eq!(freq_stats("cpu0 -1\ncpu1 0\ncpu2 abc\n"), "max=-1;avg=-1;n=0");
        assert_eq!(freq_stats("cpu0 1804800\r\ncpu1 900000\r\n"), "max=1804.8;avg=1352.4;n=2");
    }
}
