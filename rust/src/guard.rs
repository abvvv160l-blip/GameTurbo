//! Bảo vệ nhiệt: hạ chế độ khi quá nóng, có trễ để không dao động.

use std::sync::atomic::{AtomicBool, Ordering};

static TRIGGERED: AtomicBool = AtomicBool::new(false);

/// (nóng, tới hạn, về bình thường) theo nguồn: 0 = CPU/SoC, 1 = pin.
fn thresholds(source: i32) -> (f32, f32, f32) {
    if source == 1 {
        (41.0, 45.0, 40.0)
    } else {
        (72.0, 80.0, 70.0)
    }
}

/// 0 bình thường, 1 nóng, 2 tới hạn lần đầu (phải hạ chế độ), 3 tới hạn đã báo.
pub fn update(temp_c: f32, source: i32) -> i32 {
    if temp_c.is_nan() || temp_c < 0.0 {
        return 0;
    }
    let (hot, crit, reset) = thresholds(source);
    if temp_c < reset {
        TRIGGERED.store(false, Ordering::SeqCst);
        return 0;
    }
    if temp_c >= crit {
        // swap trả giá trị cũ: false → lần đầu
        return if TRIGGERED.swap(true, Ordering::SeqCst) { 3 } else { 2 };
    }
    if temp_c >= hot {
        return 1;
    }
    0
}

pub fn reset() {
    TRIGGERED.store(false, Ordering::SeqCst);
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Mutex;

    // trạng thái toàn cục: các test phải chạy lần lượt
    static LOCK: Mutex<()> = Mutex::new(());

    #[test]
    fn cpu_sequence() {
        let _g = LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset();
        assert_eq!(update(50.0, 0), 0);
        assert_eq!(update(72.0, 0), 1);
        assert_eq!(update(79.9, 0), 1);
        assert_eq!(update(80.0, 0), 2);
        assert_eq!(update(85.0, 0), 3);
        // dưới tới hạn nhưng chưa xuống 70: vẫn coi là đã báo
        assert_eq!(update(75.0, 0), 1);
        assert_eq!(update(81.0, 0), 3);
        assert_eq!(update(69.9, 0), 0);
        // đã nguội: lần tới hạn tiếp theo báo lại
        assert_eq!(update(80.5, 0), 2);
        reset();
    }

    #[test]
    fn battery_thresholds() {
        let _g = LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset();
        assert_eq!(update(40.5, 1), 0);
        assert_eq!(update(41.0, 1), 1);
        assert_eq!(update(45.0, 1), 2);
        assert_eq!(update(46.0, 1), 3);
        assert_eq!(update(39.9, 1), 0);
        reset();
    }

    #[test]
    fn invalid_values_do_not_change_state() {
        let _g = LOCK.lock().unwrap_or_else(|e| e.into_inner());
        reset();
        assert_eq!(update(f32::NAN, 0), 0);
        assert_eq!(update(-1.0, 0), 0);
        assert_eq!(update(85.0, 0), 2);
        assert_eq!(update(-1.0, 0), 0);
        assert_eq!(update(85.0, 0), 3, "giá trị lỗi không được xoá trạng thái");
        reset();
        assert_eq!(update(85.0, 0), 2);
        reset();
    }
}
