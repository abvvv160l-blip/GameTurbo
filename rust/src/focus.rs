//! Tìm gói đang ở tiền cảnh từ `dumpsys window` / `dumpsys activity activities`.

use crate::util::is_pkg_char;

/// Thứ tự ưu tiên theo đặc tả.
const MARKERS: [&str; 5] = [
    "mCurrentFocus=",
    "mFocusedApp=",
    "topResumedActivity=",
    "mResumedActivity:",
    "ResumedActivity",
];

/// Tên gói ngay trước dấu `/` đầu tiên sau `marker` trong cùng một dòng.
fn pkg_in_line(line: &str, marker_end: usize) -> Option<&str> {
    let rest = &line[marker_end..];
    let slash = rest.find('/')?;
    let head = &rest[..slash];
    // Token cuối (cách nhau bằng khoảng trắng) ngay trước dấu '/'; phải là tên gói hợp lệ.
    let pkg = head.rsplit(|c: char| c.is_whitespace()).next().unwrap_or("");
    if !pkg.chars().all(is_pkg_char) {
        return None;
    }
    if pkg.is_empty() || pkg == "null" {
        None
    } else {
        Some(pkg)
    }
}

pub fn focused_package(dump: &str) -> String {
    for marker in MARKERS {
        for line in dump.lines() {
            let mut from = 0;
            while let Some(pos) = line[from..].find(marker) {
                let end = from + pos + marker.len();
                if let Some(p) = pkg_in_line(line, end) {
                    return p.to_string();
                }
                from = end;
            }
        }
    }
    String::new()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn android10_current_focus() {
        let d = "  mCurrentFocus=Window{3f2a1c u0 com.game.fun/com.game.fun.MainActivity}\n  mFocusedApp=ActivityRecord{1a2b3c u0 com.game.fun/.MainActivity t123}\n";
        assert_eq!(focused_package(d), "com.game.fun");
    }

    #[test]
    fn android14_null_then_display_lines() {
        let d = "WINDOW MANAGER WINDOWS (dumpsys window windows)\n  mCurrentFocus=null\n  mFocusedApp=null\nDisplay: mDisplayId=0\n  mCurrentFocus=Window{c4d5e6f u0 com.garena.game/com.garena.game.UnityActivity}\n";
        assert_eq!(focused_package(d), "com.garena.game");
    }

    #[test]
    fn systemui_focus_without_slash_falls_back_to_focused_app() {
        let d = "  mCurrentFocus=Window{abc123 u0 NotificationShade}\n  mFocusedApp=ActivityRecord{1a u0 com.game.fun/.Main t9}\n";
        assert_eq!(focused_package(d), "com.game.fun");
    }

    #[test]
    fn focused_app_token_wrapper_android12() {
        let d = "  mFocusedApp=Token{d4e5f6 ActivityRecord{a1b2c3 u0 com.tencent.ig/com.epicgames.ue4.SplashActivity t77}}\n";
        assert_eq!(focused_package(d), "com.tencent.ig");
    }

    #[test]
    fn activity_activities_dump() {
        let d = "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n  topResumedActivity=ActivityRecord{9a8b7c u0 com.supercell.clashofclans/.GameApp t55}\n  mResumedActivity: ActivityRecord{9a8b7c u0 com.supercell.clashofclans/.GameApp t55}\n";
        assert_eq!(focused_package(d), "com.supercell.clashofclans");
        let d2 = "  mResumedActivity: ActivityRecord{1 u0 com.a.b/.C t1}\n";
        assert_eq!(focused_package(d2), "com.a.b");
        let d3 = "  ResumedActivity: ActivityRecord{5e6f u10 org.x.y/.Z t4}\n";
        assert_eq!(focused_package(d3), "org.x.y");
    }

    #[test]
    fn priority_current_focus_beats_resumed() {
        let d = "  mResumedActivity: ActivityRecord{1 u0 com.old.app/.A t1}\n  mCurrentFocus=Window{2 u0 com.new.app/com.new.app.B}\n";
        assert_eq!(focused_package(d), "com.new.app");
    }

    #[test]
    fn none_found() {
        assert_eq!(focused_package(""), "");
        assert_eq!(focused_package("mCurrentFocus=null\nmFocusedApp=null\n"), "");
        assert_eq!(focused_package("mCurrentFocus=Window{1 u0 StatusBar}\n"), "");
        assert_eq!(focused_package("hello world"), "");
    }

    #[test]
    fn crlf_and_unicode_safe() {
        let d = "  mCurrentFocus=Window{3f u0 com.game.fun/.Main}\r\n";
        assert_eq!(focused_package(d), "com.game.fun");
        let u = "mCurrentFocus=Window{1 u0 Ứng dụng/ x}\n";
        assert_eq!(focused_package(u), ""); // "Ứng dụng" không có phần tên gói hợp lệ ngay trước '/'
    }
}
