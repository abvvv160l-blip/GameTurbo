//! FPS từ `dumpsys SurfaceFlinger --latency` và tìm tên layer của game.

use crate::util::is_pkg_char;

const WINDOW_NS: i64 = 1_000_000_000;

/// Dòng 1 là chu kỳ làm tươi; các dòng sau `desired actual ready` (ns). Dùng cột `actual`.
pub fn fps_from_latency(text: &str) -> f32 {
    let mut stamps: Vec<i64> = Vec::with_capacity(128);
    for line in text.lines() {
        let mut it = line.split_whitespace();
        let (a, b, c) = match (it.next(), it.next(), it.next()) {
            (Some(a), Some(b), Some(c)) => (a, b, c),
            _ => continue,
        };
        if a.parse::<i64>().is_err() || c.parse::<i64>().is_err() {
            continue;
        }
        match b.parse::<i64>() {
            Ok(t) if t > 0 && t != i64::MAX => stamps.push(t),
            _ => {}
        }
    }
    let t_last = match stamps.iter().max() {
        Some(t) => *t,
        None => return -1.0,
    };
    let lo = t_last - WINDOW_NS;
    let mut n = 0u32;
    let mut t_min = i64::MAX;
    for &t in &stamps {
        if t >= lo {
            n += 1;
            if t < t_min {
                t_min = t;
            }
        }
    }
    let span = t_last - t_min;
    if n < 2 || span <= 0 {
        return -1.0;
    }
    let fps = (n - 1) as f64 / (span as f64 / 1e9);
    fps.min(240.0) as f32
}

/// `pkg` xuất hiện như một tên gói trọn vẹn (ký tự trước/sau không phải ký tự tên gói).
fn contains_pkg(line: &str, pkg: &str) -> bool {
    let mut from = 0;
    while let Some(pos) = line[from..].find(pkg) {
        let start = from + pos;
        let end = start + pkg.len();
        let before_ok = line[..start].chars().next_back().map_or(true, |c| !is_pkg_char(c));
        let after_ok = line[end..].chars().next().map_or(true, |c| !is_pkg_char(c));
        if before_ok && after_ok {
            return true;
        }
        from = start + 1;
        while from < line.len() && !line.is_char_boundary(from) {
            from += 1;
        }
    }
    false
}

/// Chọn layer của game trong `dumpsys SurfaceFlinger --list`.
pub fn find_layer(list: &str, pkg: &str) -> String {
    if pkg.is_empty() {
        return String::new();
    }
    let slash = format!("{}/", pkg);
    let mut best: Option<(i32, &str)> = None;
    for raw in list.lines() {
        let line = raw.trim();
        if line.is_empty() || !contains_pkg(line, pkg) {
            continue;
        }
        if ["Background for", "Bounds for", "Splash", "Letterbox"].iter().any(|x| line.contains(x)) {
            continue;
        }
        let mut score = 0;
        if line.contains("SurfaceView") {
            score += 3;
        }
        if line.contains("(BLAST)") {
            score += 1;
        }
        if line.contains(&slash) {
            score += 1;
        }
        if best.map_or(true, |(s, _)| score > s) {
            best = Some((score, line));
        }
    }
    best.map(|(_, l)| l.to_string()).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make(period: i64, count: usize, step: i64, junk: usize) -> String {
        let base: i64 = 5_000_000_000_000;
        let mut s = format!("{}\n", period);
        for k in 0..count as i64 {
            let t = base + k * step;
            s.push_str(&format!("{}\t{}\t{}\n", t - 1000, t, t + 2000));
        }
        for _ in 0..junk {
            s.push_str("9223372036854775807\t9223372036854775807\t9223372036854775807\n");
        }
        s
    }

    #[test]
    fn sixty_fps_with_invalid_rows() {
        let f = fps_from_latency(&make(16_666_666, 60, 16_666_667, 40));
        assert!((f - 60.0).abs() < 0.5, "{f}");
    }

    #[test]
    fn thirty_fps_full_buffer() {
        let f = fps_from_latency(&make(16_666_666, 127, 33_333_333, 0));
        assert!((f - 30.0).abs() < 0.5, "{f}");
    }

    #[test]
    fn irregular_gap_uses_last_second_only() {
        // 20 khung 60 fps cũ, im lặng 5 giây, rồi 10 khung 120 fps
        let base: i64 = 1_000_000_000_000;
        let mut s = String::from("8333333\n");
        for k in 0..20i64 {
            let t = base + k * 16_666_667;
            s.push_str(&format!("{} {} {}\n", t, t, t));
        }
        let b2 = base + 5_000_000_000;
        for k in 0..10i64 {
            let t = b2 + k * 8_333_333;
            s.push_str(&format!("{} {} {}\n", t, t, t));
        }
        let f = fps_from_latency(&s);
        assert!((f - 120.0).abs() < 1.0, "{f}");
    }

    #[test]
    fn degenerate_inputs() {
        assert_eq!(fps_from_latency(""), -1.0);
        assert_eq!(fps_from_latency("16666666\n"), -1.0);
        assert_eq!(fps_from_latency("16666666\n1 2 3\n"), -1.0); // 1 mốc
        assert_eq!(fps_from_latency("16666666\n0 0 0\n0 0 0\n"), -1.0);
        assert_eq!(fps_from_latency("16666666\n1 5000 3\n1 5000 3\n"), -1.0); // span = 0
        assert_eq!(fps_from_latency("16666666\n1 2\n3 4\n"), -1.0); // thiếu cột
        assert_eq!(fps_from_latency("abc\nx y z\n"), -1.0);
        assert!(fps_from_latency("16666666\r\n1 1000000000 3\r\n1 1500000000 3\r\n") > 0.0);
    }

    #[test]
    fn fps_capped_at_240() {
        let f = fps_from_latency(&make(1_000_000, 100, 1_000_000, 0));
        assert_eq!(f, 240.0);
    }

    const LIST: &str = "Display 0 HWC layers
SurfaceView[com.game.fun/com.game.fun.MainActivity](BLAST)#0
com.game.fun/com.game.fun.MainActivity#0
Background for SurfaceView[com.game.fun/com.game.fun.MainActivity](BLAST)#0
Bounds for - com.game.fun/com.game.fun.MainActivity#0
Splash Screen com.game.fun#0
com.android.systemui.ImageWallpaper#0
SurfaceView[com.game.funny/com.game.funny.Main](BLAST)#0
";

    #[test]
    fn find_layer_prefers_surfaceview_blast() {
        assert_eq!(
            find_layer(LIST, "com.game.fun"),
            "SurfaceView[com.game.fun/com.game.fun.MainActivity](BLAST)#0"
        );
        assert_eq!(
            find_layer(LIST, "com.game.funny"),
            "SurfaceView[com.game.funny/com.game.funny.Main](BLAST)#0"
        );
    }

    #[test]
    fn find_layer_old_style_and_fallback() {
        let old = "SurfaceView - com.game.fun/com.game.fun.MainActivity@2a4f#0\ncom.game.fun/com.game.fun.MainActivity#0\n";
        assert_eq!(
            find_layer(old, "com.game.fun"),
            "SurfaceView - com.game.fun/com.game.fun.MainActivity@2a4f#0"
        );
        let only_activity = "StatusBar#0\ncom.game.fun/com.game.fun.MainActivity#0\n";
        assert_eq!(find_layer(only_activity, "com.game.fun"), "com.game.fun/com.game.fun.MainActivity#0");
    }

    #[test]
    fn find_layer_no_match_and_boundaries() {
        assert_eq!(find_layer(LIST, "com.other"), "");
        assert_eq!(find_layer(LIST, ""), "");
        // com.game không được khớp com.game.fun
        assert_eq!(find_layer("com.game.fun/com.game.fun.Main#0\n", "com.game"), "");
        // tie: dòng đầu thắng
        assert_eq!(find_layer("a com.x#0\nb com.x#0\n", "com.x"), "a com.x#0");
    }
}
