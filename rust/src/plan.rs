//! Lập kế hoạch lệnh cho 4 chế độ và lệnh khôi phục.
//!
//! Đầu ra là các dòng `X|lệnh`: `S` = shell (Shizuku/root), `R` = chỉ root,
//! `F` = dự phòng, chạy nếu dòng `S` ngay trước thất bại.

use crate::util::is_valid_pkg;

#[derive(Debug, Clone, PartialEq)]
pub struct Params {
    pub mode: i32,
    pub sdk: i32,
    pub pkg: String,
    pub res: i32,
    pub fps: i32,
    pub refresh: i32,
    pub maxrefresh: i32,
    pub fixed: bool,
    pub gm: String,
    pub root: bool,
    pub boost: bool,
}

impl Default for Params {
    fn default() -> Self {
        Params {
            mode: 1,
            sdk: 0,
            pkg: String::new(),
            res: 100,
            fps: 0,
            refresh: 0,
            maxrefresh: 0,
            fixed: false,
            gm: "performance".to_string(),
            root: false,
            boost: false,
        }
    }
}

fn flag(v: &str) -> bool {
    matches!(v.trim(), "1" | "true" | "TRUE" | "True")
}

fn int(v: &str) -> Option<i32> {
    v.trim().parse::<i32>().ok()
}

pub fn parse_params(text: &str) -> Params {
    let mut p = Params::default();
    for line in text.lines() {
        let line = line.trim();
        let (k, v) = match line.split_once('=') {
            Some(kv) => kv,
            None => continue,
        };
        let v = v.trim();
        match k.trim() {
            "mode" => {
                if let Some(x) = int(v) {
                    if (0..=3).contains(&x) {
                        p.mode = x;
                    }
                }
            }
            "sdk" => p.sdk = int(v).unwrap_or(0).max(0),
            // Tên gói sẽ nằm trong lệnh shell: chỉ nhận tên hợp lệ.
            "pkg" => p.pkg = if is_valid_pkg(v) { v.to_string() } else { String::new() },
            "res" => p.res = int(v).unwrap_or(100).clamp(50, 100),
            "fps" => p.fps = int(v).unwrap_or(0).clamp(0, 1000),
            "refresh" => p.refresh = int(v).unwrap_or(0).clamp(0, 1000),
            "maxrefresh" => p.maxrefresh = int(v).unwrap_or(0).clamp(0, 1000),
            "fixed" => p.fixed = flag(v),
            "gm" => {
                p.gm = match v {
                    "none" | "battery" | "standard" | "performance" => v.to_string(),
                    _ => "performance".to_string(),
                }
            }
            "root" => p.root = flag(v),
            "boost" => p.boost = flag(v),
            _ => {}
        }
    }
    p
}

/// Giá trị thật sự áp dụng sau khi chế độ chồng lên thiết lập của người dùng.
#[derive(Debug, PartialEq)]
pub struct Effective {
    pub gm: String,
    pub res: i32,
    pub fps: i32,
    pub refresh: i32,
    pub fixed: bool,
    pub boost: bool,
}

pub fn effective(p: &Params) -> Effective {
    match p.mode {
        0 => Effective {
            gm: "battery".into(),
            res: p.res.min(70),
            fps: if p.fps == 0 || p.fps > 30 { 30 } else { p.fps },
            refresh: 60,
            fixed: false,
            boost: false,
        },
        2 => Effective {
            gm: "performance".into(),
            res: p.res,
            fps: p.fps,
            refresh: if p.maxrefresh > 0 { p.maxrefresh } else { 0 },
            fixed: true,
            boost: p.root,
        },
        3 => Effective {
            gm: p.gm.clone(),
            res: p.res,
            fps: p.fps,
            refresh: p.refresh,
            fixed: p.fixed,
            boost: p.boost && p.root,
        },
        _ => Effective {
            gm: "standard".into(),
            res: p.res.min(90),
            fps: if p.fps == 0 || p.fps > 60 { 60 } else { p.fps },
            refresh: 0,
            fixed: false,
            boost: false,
        },
    }
}

const CPU_STATE: &str = "/data/local/tmp/gt_cpu_prev";

const ROOT_SAVE: &str = r#"[ -f /data/local/tmp/gt_cpu_prev ] || { for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d "$p" ] && echo "$p $(cat $p/scaling_governor 2>/dev/null) $(cat $p/scaling_min_freq 2>/dev/null)"; done > /data/local/tmp/gt_cpu_prev; }; true"#;

const ROOT_CPU_BOOST: &str = r#"for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d "$p" ] || continue; cat $p/cpuinfo_max_freq > $p/scaling_max_freq 2>/dev/null; cat $p/cpuinfo_max_freq > $p/scaling_min_freq 2>/dev/null; echo performance > $p/scaling_governor 2>/dev/null; done; true"#;

const ROOT_GPU_BOOST: &str = r#"d=/sys/class/kgsl/kgsl-3d0; if [ -d $d ]; then for f in force_clk_on force_bus_on force_rail_on; do echo 1 > $d/$f 2>/dev/null; done; if [ -r $d/devfreq/max_freq ]; then cat $d/devfreq/max_freq > $d/devfreq/min_freq 2>/dev/null; fi; fi; true"#;

const ROOT_CPU_RESTORE: &str = r#"if [ -f /data/local/tmp/gt_cpu_prev ]; then while read p g m; do [ -n "$p" ] || continue; [ -n "$g" ] && echo $g > $p/scaling_governor 2>/dev/null; [ -n "$m" ] && echo $m > $p/scaling_min_freq 2>/dev/null; done < /data/local/tmp/gt_cpu_prev; rm -f /data/local/tmp/gt_cpu_prev; fi; true"#;

const ROOT_GPU_RESTORE: &str = r#"d=/sys/class/kgsl/kgsl-3d0; if [ -d $d ]; then for f in force_clk_on force_bus_on force_rail_on; do echo 0 > $d/$f 2>/dev/null; done; fi; true"#;

/// Đường dẫn file lưu trạng thái CPU (để Kotlin hiển thị trong nhật ký nếu cần).
pub fn cpu_state_path() -> &'static str {
    CPU_STATE
}

pub fn plan_mode(text: &str) -> String {
    let p = parse_params(text);
    let e = effective(&p);
    let mut out: Vec<String> = Vec::new();

    if p.sdk >= 31 && !p.pkg.is_empty() && e.gm != "none" {
        let mut cmd = format!("S|cmd game set --mode {}", e.gm);
        if e.res < 100 {
            cmd.push_str(&format!(" --downscale {:.1}", e.res as f64 / 100.0));
        }
        if e.fps > 0 {
            cmd.push_str(&format!(" --fps {}", e.fps));
        }
        cmd.push(' ');
        cmd.push_str(&p.pkg);
        out.push(cmd);
        out.push(format!("F|cmd game mode {} {}", e.gm, p.pkg));
    }

    if e.refresh > 0 {
        out.push(format!("S|settings put system min_refresh_rate {}", e.refresh));
        out.push(format!("S|settings put system peak_refresh_rate {}", e.refresh));
    }

    if e.fixed {
        out.push("S|cmd power set-fixed-performance-mode-enabled true".to_string());
    }

    if e.boost {
        out.push(format!("R|{}", ROOT_SAVE));
        out.push(format!("R|{}", ROOT_CPU_BOOST));
        out.push(format!("R|{}", ROOT_GPU_BOOST));
    }

    out.join("\n")
}

pub fn plan_restore(text: &str) -> String {
    let p = parse_params(text);
    let mut out: Vec<String> = Vec::new();
    if p.sdk >= 31 && !p.pkg.is_empty() {
        out.push(format!("S|cmd game reset {}", p.pkg));
    }
    out.push("S|settings delete system min_refresh_rate".to_string());
    out.push("S|settings delete system peak_refresh_rate".to_string());
    out.push("S|cmd power set-fixed-performance-mode-enabled false".to_string());
    if p.root {
        out.push(format!("R|{}", ROOT_CPU_RESTORE));
        out.push(format!("R|{}", ROOT_GPU_RESTORE));
    }
    out.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lines(s: &str) -> Vec<&str> {
        s.lines().collect()
    }

    #[test]
    fn saver_mode() {
        let out = plan_mode("mode=0\nsdk=34\npkg=com.game.fun\nres=100\nfps=0\nroot=1\n");
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game set --mode battery --downscale 0.7 --fps 30 com.game.fun");
        assert_eq!(l[1], "F|cmd game mode battery com.game.fun");
        assert_eq!(l[2], "S|settings put system min_refresh_rate 60");
        assert_eq!(l[3], "S|settings put system peak_refresh_rate 60");
        assert_eq!(l.len(), 4, "tiết kiệm pin không bật fixed/boost: {out}");
    }

    #[test]
    fn saver_respects_lower_user_values() {
        let out = plan_mode("mode=0\nsdk=33\npkg=a.b\nres=60\nfps=24\n");
        assert!(out.starts_with("S|cmd game set --mode battery --downscale 0.6 --fps 24 a.b"), "{out}");
    }

    #[test]
    fn stable_mode() {
        let out = plan_mode("mode=1\nsdk=34\npkg=com.game.fun\nres=100\nfps=0\n");
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game set --mode standard --downscale 0.9 --fps 60 com.game.fun");
        assert_eq!(l[1], "F|cmd game mode standard com.game.fun");
        assert_eq!(l.len(), 2, "{out}");
        let out = plan_mode("mode=1\nsdk=34\npkg=a.b\nres=80\nfps=45\n");
        assert!(out.starts_with("S|cmd game set --mode standard --downscale 0.8 --fps 45 a.b"));
    }

    #[test]
    fn ultimate_without_root() {
        let out = plan_mode("mode=2\nsdk=34\npkg=com.game.fun\nres=100\nfps=0\nmaxrefresh=120\nroot=0\n");
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game set --mode performance com.game.fun");
        assert_eq!(l[1], "F|cmd game mode performance com.game.fun");
        assert_eq!(l[2], "S|settings put system min_refresh_rate 120");
        assert_eq!(l[3], "S|settings put system peak_refresh_rate 120");
        assert_eq!(l[4], "S|cmd power set-fixed-performance-mode-enabled true");
        assert_eq!(l.len(), 5, "{out}");
    }

    #[test]
    fn ultimate_with_root_has_boost() {
        let out = plan_mode("mode=2\nsdk=34\npkg=com.game.fun\nres=90\nfps=90\nmaxrefresh=0\nroot=1\n");
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game set --mode performance --downscale 0.9 --fps 90 com.game.fun");
        assert_eq!(l[2], "S|cmd power set-fixed-performance-mode-enabled true");
        assert!(l[3].starts_with("R|[ -f /data/local/tmp/gt_cpu_prev ]"));
        assert!(l[4].contains("scaling_max_freq") && l[4].contains("scaling_governor"));
        assert!(l[5].contains("kgsl-3d0"));
        assert_eq!(l.len(), 6);
    }

    #[test]
    fn custom_mode_uses_given_values() {
        let out = plan_mode(
            "mode=3\nsdk=35\npkg=a.b\nres=85\nfps=72\ngm=standard\nrefresh=90\nfixed=1\nroot=1\nboost=1\n",
        );
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game set --mode standard --downscale 0.8 --fps 72 a.b");
        assert!(out.contains("S|settings put system min_refresh_rate 90"));
        assert!(out.contains("S|cmd power set-fixed-performance-mode-enabled true"));
        assert!(out.contains("R|"));
        // boost cần root
        let nr = plan_mode("mode=3\nsdk=35\npkg=a.b\nboost=1\nroot=0\n");
        assert!(!nr.contains("R|"));
        // gm=none: không có lệnh game
        let none = plan_mode("mode=3\nsdk=35\npkg=a.b\ngm=none\nrefresh=60\n");
        assert!(!none.contains("cmd game"));
        assert!(none.contains("min_refresh_rate 60"));
    }

    #[test]
    fn no_game_commands_when_old_sdk_or_no_pkg() {
        assert!(!plan_mode("mode=1\nsdk=30\npkg=a.b\n").contains("cmd game"));
        assert!(!plan_mode("mode=1\nsdk=34\npkg=\n").contains("cmd game"));
        assert!(!plan_mode("mode=1\nsdk=34\n").contains("cmd game"));
    }

    #[test]
    fn rejects_dangerous_package_names() {
        let out = plan_mode("mode=1\nsdk=34\npkg=a.b; rm -rf /\n");
        assert!(!out.contains("rm"), "{out}");
        assert!(!out.contains("cmd game"));
    }

    #[test]
    fn defaults_and_clamps() {
        let p = parse_params("");
        assert_eq!(p, Params::default());
        let p = parse_params("res=10\nfps=-5\nrefresh=99999\nmode=9\ngm=weird\n");
        assert_eq!(p.res, 50);
        assert_eq!(p.fps, 0);
        assert_eq!(p.refresh, 1000);
        assert_eq!(p.mode, 1);
        assert_eq!(p.gm, "performance");
        let p = parse_params("sdk=abc\nroot=true\nnoise\n=\n");
        assert_eq!(p.sdk, 0);
        assert!(p.root);
    }

    #[test]
    fn restore_plan() {
        let out = plan_restore("sdk=34\npkg=a.b\nroot=0\n");
        let l = lines(&out);
        assert_eq!(l[0], "S|cmd game reset a.b");
        assert_eq!(l[1], "S|settings delete system min_refresh_rate");
        assert_eq!(l[2], "S|settings delete system peak_refresh_rate");
        assert_eq!(l[3], "S|cmd power set-fixed-performance-mode-enabled false");
        assert_eq!(l.len(), 4);
        let out = plan_restore("sdk=34\nroot=1\n");
        let l = lines(&out);
        assert!(!out.contains("cmd game"));
        assert!(l[l.len() - 2].starts_with("R|if [ -f /data/local/tmp/gt_cpu_prev ]"));
        assert!(l[l.len() - 1].contains("force_clk_on"));
    }

    /// Mọi dòng sinh ra phải là một dòng sh hợp lệ (kiểm tra cú pháp bằng `sh -n`).
    #[test]
    fn every_line_is_valid_sh() {
        let sh_ok = std::process::Command::new("sh").arg("-c").arg("true").status().map(|s| s.success()).unwrap_or(false);
        if !sh_ok {
            eprintln!("không có sh, bỏ qua");
            return;
        }
        let mut all = String::new();
        for mode in 0..=3 {
            all.push_str(&plan_mode(&format!(
                "mode={mode}\nsdk=35\npkg=com.game.fun\nres=80\nfps=60\nrefresh=90\nmaxrefresh=120\nfixed=1\nroot=1\nboost=1\n"
            )));
            all.push('\n');
        }
        all.push_str(&plan_restore("sdk=35\npkg=com.game.fun\nroot=1\n"));
        let mut checked = 0;
        for l in all.lines() {
            let (_, cmd) = l.split_once('|').expect("X|lệnh");
            let st = std::process::Command::new("sh").arg("-n").arg("-c").arg(cmd).status().unwrap();
            assert!(st.success(), "sh -n lỗi: {cmd}");
            checked += 1;
        }
        assert!(checked >= 20);
    }

    /// Chạy thử các đoạn root trên thư mục giả để chắc logic ghi/khôi phục đúng.
    #[test]
    fn root_scripts_work_on_fake_sysfs() {
        use std::fs;
        use std::process::Command;
        if !Command::new("sh").arg("-c").arg("true").status().map(|s| s.success()).unwrap_or(false) {
            return;
        }
        let base = std::env::temp_dir().join(format!("gt_fake_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        let pol = base.join("sys/devices/system/cpu/cpufreq/policy0");
        fs::create_dir_all(&pol).unwrap();
        fs::write(pol.join("cpuinfo_max_freq"), "2800000\n").unwrap();
        fs::write(pol.join("scaling_max_freq"), "2000000\n").unwrap();
        fs::write(pol.join("scaling_min_freq"), "300000\n").unwrap();
        fs::write(pol.join("scaling_governor"), "walt\n").unwrap();
        let tmp = base.join("data/local/tmp");
        fs::create_dir_all(&tmp).unwrap();
        // đổi các đường dẫn tuyệt đối sang thư mục giả
        let fix = |s: &str| {
            s.replace("/sys/devices", &format!("{}/sys/devices", base.display()))
                .replace("/data/local/tmp", &format!("{}/data/local/tmp", base.display()))
        };
        let run = |s: &str| {
            let st = Command::new("sh").arg("-c").arg(fix(s)).status().unwrap();
            assert!(st.success());
        };
        run(ROOT_SAVE);
        run(ROOT_CPU_BOOST);
        assert_eq!(fs::read_to_string(pol.join("scaling_governor")).unwrap().trim(), "performance");
        assert_eq!(fs::read_to_string(pol.join("scaling_min_freq")).unwrap().trim(), "2800000");
        assert_eq!(fs::read_to_string(pol.join("scaling_max_freq")).unwrap().trim(), "2800000");
        // gọi lần 2 không được ghi đè trạng thái đã lưu
        run(ROOT_SAVE);
        run(ROOT_CPU_RESTORE);
        assert_eq!(fs::read_to_string(pol.join("scaling_governor")).unwrap().trim(), "walt");
        assert_eq!(fs::read_to_string(pol.join("scaling_min_freq")).unwrap().trim(), "300000");
        assert!(!tmp.join("gt_cpu_prev").exists());
        let _ = fs::remove_dir_all(&base);
    }
}
