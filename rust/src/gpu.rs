//! Đọc GPU: danh sách node ứng viên và parse nội dung.

use crate::util::first_number;

const NODES: &str = "busy|/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
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
busy|/proc/mali/utilization";

pub fn nodes() -> String {
    NODES.to_string()
}

/// `kind` = "busy" (trả %) hoặc "freq" (trả MHz). Lỗi: -1.
pub fn parse(kind: &str, path: &str, content: &str) -> f32 {
    match kind {
        "busy" => parse_busy(path, content),
        "freq" => parse_freq(content),
        _ => -1.0,
    }
}

fn parse_busy(path: &str, content: &str) -> f32 {
    if path.ends_with("gpubusy") {
        let mut it = content.split_whitespace();
        let busy: f64 = match it.next().and_then(|t| t.parse().ok()) {
            Some(v) => v,
            None => return -1.0,
        };
        let total: f64 = match it.next().and_then(|t| t.parse().ok()) {
            Some(v) => v,
            None => return -1.0,
        };
        if total <= 0.0 {
            return 0.0;
        }
        return (busy / total * 100.0).clamp(0.0, 100.0) as f32;
    }
    match first_number(content) {
        Some(v) if v >= 0.0 => v.clamp(0.0, 100.0) as f32,
        _ => -1.0,
    }
}

fn parse_freq(content: &str) -> f32 {
    match first_number(content) {
        Some(v) if v >= 0.0 => {
            let mhz = if v >= 1e7 {
                v / 1e6
            } else if v >= 1e4 {
                v / 1e3
            } else {
                v
            };
            mhz as f32
        }
        _ => -1.0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nodes_list_is_well_formed() {
        let n = nodes();
        let lines: Vec<&str> = n.lines().collect();
        assert!(lines.len() >= 13);
        for l in &lines {
            let (k, p) = l.split_once('|').expect("kind|path");
            assert!(k == "busy" || k == "freq");
            assert!(p.starts_with('/'));
        }
        assert_eq!(lines[0], "busy|/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
        assert!(lines.contains(&"busy|/proc/mali/utilization"));
    }

    #[test]
    fn kgsl_gpubusy_pair() {
        let p = "/sys/class/kgsl/kgsl-3d0/gpubusy";
        let v = parse("busy", p, "12345 100000\n");
        assert!((v - 12.345).abs() < 0.01, "{v}");
        assert_eq!(parse("busy", p, "0 0\n"), 0.0);
        assert_eq!(parse("busy", p, "5\n"), -1.0);
        assert_eq!(parse("busy", p, "x y"), -1.0);
        assert_eq!(parse("busy", p, "200 100"), 100.0);
    }

    #[test]
    fn busy_single_number_forms() {
        let p = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage";
        assert_eq!(parse("busy", p, "45 %\n"), 45.0);
        assert_eq!(parse("busy", p, "45"), 45.0);
        assert_eq!(parse("busy", "/sys/class/devfreq/x.mali/load", "45@800000000Hz\n"), 45.0);
        assert!((parse("busy", "/proc/mali/utilization", "45.5") - 45.5).abs() < 1e-4);
        assert_eq!(parse("busy", p, "250"), 100.0);
        assert_eq!(parse("busy", p, "-1"), -1.0);
        assert_eq!(parse("busy", p, "n/a"), -1.0);
    }

    #[test]
    fn freq_units() {
        let p = "/sys/class/kgsl/kgsl-3d0/gpuclk";
        assert_eq!(parse("freq", p, "840000000\n"), 840.0); // Hz
        assert_eq!(parse("freq", p, "650000"), 650.0); // kHz
        assert_eq!(parse("freq", p, "587"), 587.0); // MHz
        assert_eq!(parse("freq", p, "-1"), -1.0);
        assert_eq!(parse("freq", p, "abc"), -1.0);
    }

    #[test]
    fn unknown_kind() {
        assert_eq!(parse("temp", "/x", "45"), -1.0);
    }
}
