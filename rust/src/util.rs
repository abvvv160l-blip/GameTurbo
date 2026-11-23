//! Tiện ích dùng chung cho các module parse.

/// Ký tự hợp lệ trong tên gói Android.
pub fn is_pkg_char(c: char) -> bool {
    c.is_ascii_alphanumeric() || c == '_' || c == '.'
}

/// Tên gói hợp lệ: không rỗng, chỉ gồm `[A-Za-z0-9_.]`, dài tối đa 255.
pub fn is_valid_pkg(s: &str) -> bool {
    !s.is_empty() && s.len() <= 255 && s.chars().all(is_pkg_char)
}

/// Số đầu tiên trong chuỗi (`45`, `45 %`, `45@800000000Hz`, `45.5`).
/// Nếu ngay trước chữ số là dấu `-` thì trả số âm.
pub fn first_number(s: &str) -> Option<f64> {
    let b = s.as_bytes();
    let mut i = 0;
    while i < b.len() && !b[i].is_ascii_digit() {
        i += 1;
    }
    if i >= b.len() {
        return None;
    }
    let neg = i > 0 && b[i - 1] == b'-';
    let start = i;
    while i < b.len() && b[i].is_ascii_digit() {
        i += 1;
    }
    if i + 1 < b.len() && b[i] == b'.' && b[i + 1].is_ascii_digit() {
        i += 1;
        while i < b.len() && b[i].is_ascii_digit() {
            i += 1;
        }
    }
    let v: f64 = s[start..i].parse().ok()?;
    Some(if neg { -v } else { v })
}

/// Dòng danh sách tên gói: bỏ dòng rỗng, cắt khoảng trắng.
pub fn pkg_lines(s: &str) -> impl Iterator<Item = &str> {
    s.lines().map(str::trim).filter(|l| !l.is_empty())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn first_number_forms() {
        assert_eq!(first_number("45"), Some(45.0));
        assert_eq!(first_number("45 %"), Some(45.0));
        assert_eq!(first_number("45@800000000Hz"), Some(45.0));
        assert_eq!(first_number("45.5"), Some(45.5));
        assert_eq!(first_number("-1"), Some(-1.0));
        assert_eq!(first_number("abc"), None);
        assert_eq!(first_number(""), None);
        assert_eq!(first_number("x 7."), Some(7.0));
    }

    #[test]
    fn pkg_validation() {
        assert!(is_valid_pkg("com.game.fun_2"));
        assert!(!is_valid_pkg(""));
        assert!(!is_valid_pkg("a b"));
        assert!(!is_valid_pkg("a;rm -rf /"));
        assert!(!is_valid_pkg("com.a/b"));
    }
}
