//! Nhiệt độ CPU/SoC từ các thermal_zone.

const INCLUDE: [&str; 14] = [
    "cpu", "soc", "tsens", "mtktscpu", "cluster", "big", "little", "prime", "gold", "silver", "apc", "ap_therm",
    "ap-therm", "ssoc",
];

const EXCLUDE: [&str; 16] = [
    "battery", "batt", "skin", "charger", "wifi", "modem", "gpu", "ddr", "usb", "xo", "pa-", "quiet", "flash",
    "camera", "display", "bcl",
];

fn normalize(v: f64) -> f64 {
    if v >= 1000.0 {
        v / 1000.0
    } else if v >= 150.0 {
        v / 10.0
    } else {
        v
    }
}

fn cpu_like(ty: &str) -> bool {
    let t = ty.to_ascii_lowercase();
    INCLUDE.iter().any(|k| t.contains(k)) && !EXCLUDE.iter().any(|k| t.contains(k))
}

/// Mỗi dòng `type<TAB>value` (hoặc cách nhau bởi khoảng trắng; value là token cuối).
pub fn cpu_temp(dump: &str) -> f32 {
    let mut best: Option<f64> = None;
    for line in dump.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let (ty, val) = if let Some((a, b)) = line.rsplit_once('\t') {
            (a.trim(), b.trim())
        } else if let Some((a, b)) = line.rsplit_once(char::is_whitespace) {
            (a.trim(), b.trim())
        } else {
            continue;
        };
        if ty.is_empty() || !cpu_like(ty) {
            continue;
        }
        let v: f64 = match val.parse() {
            Ok(v) => v,
            Err(_) => continue,
        };
        let c = normalize(v);
        if !(10.0..=130.0).contains(&c) {
            continue;
        }
        if best.map_or(true, |b| c > b) {
            best = Some(c);
        }
    }
    match best {
        Some(c) => ((c * 10.0).round() / 10.0) as f32,
        None => -1.0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn picks_hottest_cpu_like_zone() {
        let d = "battery\t38200\ncpu-1-0-usr\t61000\ncpuss-0\t58500\ngpuss-0\t55000\nskin-msm-therm\t41000\nsoc_therm\t54000\nxo-therm\t35000\nmodem-0\t40000\n";
        assert_eq!(cpu_temp(d), 61.0);
    }

    #[test]
    fn unit_normalization() {
        assert_eq!(cpu_temp("mtktscpu\t452\n"), 45.2); // decicelsius
        assert_eq!(cpu_temp("cpu\t47\n"), 47.0); // độ C
        assert_eq!(cpu_temp("cpu-thermal\t48123\n"), 48.1); // milli
        assert_eq!(cpu_temp("tsens_tz_sensor5 76000\n"), 76.0); // cách nhau bằng khoảng trắng
    }

    #[test]
    fn implausible_values_ignored() {
        assert_eq!(cpu_temp("cpu\t0\n"), -1.0);
        assert_eq!(cpu_temp("cpu-x\t-274000\n"), -1.0);
        assert_eq!(cpu_temp("cpu\t200000\n"), -1.0); // 200 °C
        assert_eq!(cpu_temp("cpu\tabc\n"), -1.0);
        assert_eq!(cpu_temp("cpu\n"), -1.0);
        assert_eq!(cpu_temp(""), -1.0);
        // vùng hợp lệ + vùng rác: chỉ lấy vùng hợp lệ
        assert_eq!(cpu_temp("cpu-a\t-1\ncpu-b\t55000\n"), 55.0);
    }

    #[test]
    fn exclusions() {
        assert_eq!(cpu_temp("battery\t40000\nskin-therm\t39000\ngpuss-1\t50000\nbcl-soc\t60\nusb-therm\t30000\n"), -1.0);
        assert_eq!(cpu_temp("cpu-battery-model\t40000\n"), -1.0); // chứa "batt"
        assert_eq!(cpu_temp("Cpu-0\t51000\n"), 51.0); // không phân biệt hoa thường
        assert_eq!(cpu_temp("cpuss-1-usr\t66000\nBIG_core\t70000\nlittle0\t52000\n"), 70.0);
    }

    #[test]
    fn crlf() {
        assert_eq!(cpu_temp("cpu\t50000\r\nsoc_therm\t52000\r\n"), 52.0);
    }
}
