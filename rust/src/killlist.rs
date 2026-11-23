//! Danh sách app bên thứ ba đang chạy và danh sách cần dọn.

use crate::util::{is_valid_pkg, pkg_lines};
use std::collections::HashSet;

/// Lấy tên gói từ một dòng `pm list packages`.
fn pkg_from_pm_line(line: &str) -> Option<&str> {
    let l = line.trim();
    let rest = l.strip_prefix("package:")?;
    let name = match rest.rsplit_once('=') {
        Some((_, n)) => n,
        None => rest,
    };
    let name = name.trim();
    if is_valid_pkg(name) {
        Some(name)
    } else {
        None
    }
}

pub fn running_third_party(pm_list: &str, ps: &str) -> String {
    let installed: HashSet<&str> = pm_list.lines().filter_map(pkg_from_pm_line).collect();
    let mut seen: HashSet<String> = HashSet::new();
    let mut out: Vec<String> = Vec::new();
    for line in ps.lines() {
        let tok = match line.split_whitespace().last() {
            Some(t) => t,
            None => continue,
        };
        let name = tok.split(':').next().unwrap_or("");
        if name.is_empty() || !installed.contains(name) {
            continue;
        }
        if seen.insert(name.to_string()) {
            out.push(name.to_string());
        }
    }
    out.join("\n")
}

fn protected(pkg: &str) -> bool {
    pkg == "com.example.gameturbo"
        || pkg == "moe.shizuku.privileged.api"
        || pkg.starts_with("com.google.android.gms")
        || pkg.starts_with("com.google.android.gsf")
        || pkg.starts_with("com.android.")
        || pkg == "android"
        || pkg.starts_with("android.")
}

pub fn kill_list(running: &str, keep: &str, game: &str) -> String {
    let keep: HashSet<&str> = pkg_lines(keep).collect();
    let game = game.trim();
    let mut seen: HashSet<&str> = HashSet::new();
    let mut out: Vec<&str> = Vec::new();
    for p in pkg_lines(running) {
        if !is_valid_pkg(p) || p == game || keep.contains(p) || protected(p) {
            continue;
        }
        if seen.insert(p) {
            out.push(p);
        }
    }
    out.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    const PM: &str = "package:com.zing.zalo\npackage:com.facebook.orca\npackage:com.game.fun\npackage:/data/app/~~x/base.apk=com.spotify.music\npackage:org.telegram.messenger\n";
    const PS: &str = "NAME\ninit\nzygote64\nsystem_server\ncom.zing.zalo\ncom.zing.zalo:push\ncom.facebook.orca:service\ncom.android.systemui\ncom.game.fun\ncom.spotify.music\nsh\nps\n";

    #[test]
    fn running_filters_and_dedups() {
        let r = running_third_party(PM, PS);
        assert_eq!(r, "com.zing.zalo\ncom.facebook.orca\ncom.game.fun\ncom.spotify.music");
    }

    #[test]
    fn running_handles_empty_and_crlf() {
        assert_eq!(running_third_party("", PS), "");
        assert_eq!(running_third_party(PM, ""), "");
        let pm = PM.replace('\n', "\r\n");
        let ps = PS.replace('\n', "\r\n");
        assert_eq!(running_third_party(&pm, &ps), "com.zing.zalo\ncom.facebook.orca\ncom.game.fun\ncom.spotify.music");
    }

    #[test]
    fn kill_list_removes_keep_game_and_protected() {
        let running = "com.zing.zalo\ncom.facebook.orca\ncom.game.fun\ncom.spotify.music\ncom.example.gameturbo\nmoe.shizuku.privileged.api\ncom.google.android.gms.persistent\ncom.android.chrome\nandroid\ncom.zing.zalo\n";
        let keep = "com.zing.zalo\n\n  com.sec.android.inputmethod  \n";
        assert_eq!(kill_list(running, keep, "com.game.fun"), "com.facebook.orca\ncom.spotify.music");
    }

    #[test]
    fn kill_list_rejects_bad_names() {
        assert_eq!(kill_list("a.b\nbad name\nx;y\nc.d\n", "", ""), "a.b\nc.d");
        assert_eq!(kill_list("", "a.b", "c.d"), "");
        assert_eq!(kill_list("androidx.foo\nandroid.zhibo\n", "", ""), "androidx.foo");
    }
}
