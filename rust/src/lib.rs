//! gtcore: lõi đo đạc và lập kế hoạch lệnh của Game Turbo.
//!
//! Toàn bộ logic nằm trong các module thuần (test được trên máy chủ). File này chỉ là
//! lớp JNI mỏng: lấy chuỗi từ Java, gọi hàm thuần, bọc `catch_unwind` để không bao giờ
//! panic xuyên qua ranh giới JNI. Hợp đồng chi tiết: NATIVE_API.md.
#![allow(non_snake_case)]

pub mod cpu;
pub mod focus;
pub mod fps;
pub mod gpu;
pub mod guard;
pub mod killlist;
pub mod plan;
pub mod thermal;
pub mod util;

use jni::objects::{JClass, JString};
use jni::sys::{jfloat, jint, jstring};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

fn guarded<T>(default: T, f: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or(default)
}

fn get(env: &mut JNIEnv, s: &JString) -> String {
    match env.get_string(s) {
        Ok(js) => String::from(js),
        Err(_) => String::new(),
    }
}

fn put(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_version<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
) -> jstring {
    put(&mut env, concat!("gtcore ", env!("CARGO_PKG_VERSION")))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_cpuUsage<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    text: JString<'l>,
) -> jfloat {
    let t = get(&mut env, &text);
    guarded(-1.0, || cpu::cpu_usage(&t))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_cpuUsageReset<'l>(_env: JNIEnv<'l>, _c: JClass<'l>) {
    guarded((), cpu::cpu_usage_reset);
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_freqStats<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    lines: JString<'l>,
) -> jstring {
    let t = get(&mut env, &lines);
    let r = guarded(String::new(), || cpu::freq_stats(&t));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_gpuNodes<'l>(mut env: JNIEnv<'l>, _c: JClass<'l>) -> jstring {
    let r = guarded(String::new(), gpu::nodes);
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_gpuParse<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    kind: JString<'l>,
    path: JString<'l>,
    content: JString<'l>,
) -> jfloat {
    let k = get(&mut env, &kind);
    let p = get(&mut env, &path);
    let c = get(&mut env, &content);
    guarded(-1.0, || gpu::parse(&k, &p, &c))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_fpsFromLatency<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    text: JString<'l>,
) -> jfloat {
    let t = get(&mut env, &text);
    guarded(-1.0, || fps::fps_from_latency(&t))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_findLayer<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    list: JString<'l>,
    pkg: JString<'l>,
) -> jstring {
    let l = get(&mut env, &list);
    let p = get(&mut env, &pkg);
    let r = guarded(String::new(), || fps::find_layer(&l, &p));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_focusedPackage<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    dump: JString<'l>,
) -> jstring {
    let d = get(&mut env, &dump);
    let r = guarded(String::new(), || focus::focused_package(&d));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_cpuTemp<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    dump: JString<'l>,
) -> jfloat {
    let d = get(&mut env, &dump);
    guarded(-1.0, || thermal::cpu_temp(&d))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_planMode<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    params: JString<'l>,
) -> jstring {
    let p = get(&mut env, &params);
    let r = guarded(String::new(), || plan::plan_mode(&p));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_planRestore<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    params: JString<'l>,
) -> jstring {
    let p = get(&mut env, &params);
    let r = guarded(String::new(), || plan::plan_restore(&p));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_runningThirdParty<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    pm_list: JString<'l>,
    ps: JString<'l>,
) -> jstring {
    let a = get(&mut env, &pm_list);
    let b = get(&mut env, &ps);
    let r = guarded(String::new(), || killlist::running_third_party(&a, &b));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_killList<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    running: JString<'l>,
    keep: JString<'l>,
    game: JString<'l>,
) -> jstring {
    let r_ = get(&mut env, &running);
    let k = get(&mut env, &keep);
    let g = get(&mut env, &game);
    let r = guarded(String::new(), || killlist::kill_list(&r_, &k, &g));
    put(&mut env, &r)
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_guardUpdate<'l>(
    _env: JNIEnv<'l>,
    _c: JClass<'l>,
    temp_c: jfloat,
    source: jint,
) -> jint {
    guarded(0, || guard::update(temp_c, source))
}

#[no_mangle]
pub extern "system" fn Java_com_example_gameturbo_core_Native_guardReset<'l>(_env: JNIEnv<'l>, _c: JClass<'l>) {
    guarded((), guard::reset);
}

#[cfg(test)]
mod tests {
    /// Mỗi hàm parse chạy rất nhanh (chỉ in thời gian, không so chặt).
    #[test]
    fn rough_timing() {
        let stat = "cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 50 0 25 400 25 0 0 0 0 0\n";
        let mut lat = String::from("16666666\n");
        for k in 0..127i64 {
            let t = 5_000_000_000_000i64 + k * 16_666_667;
            lat.push_str(&format!("{}\t{}\t{}\n", t - 1000, t, t + 2000));
        }
        let freq = "cpu0 1804800\ncpu1 1804800\ncpu2 2419200\ncpu3 2419200\ncpu4 2841600\ncpu5 2841600\ncpu6 2841600\ncpu7 3205000\n";
        let therm = "cpu-1-0-usr\t61000\nbattery\t38200\nsoc_therm\t54000\n";
        let n = 2000u32;
        let t0 = std::time::Instant::now();
        let mut sink = 0f32;
        let mut st = crate::cpu::CpuState::new();
        for _ in 0..n {
            sink += st.update(stat);
            sink += crate::fps::fps_from_latency(&lat);
            sink += crate::cpu::freq_stats(freq).len() as f32;
            sink += crate::thermal::cpu_temp(therm);
        }
        let per = t0.elapsed().as_secs_f64() * 1e6 / n as f64;
        eprintln!("trung bình {per:.2} µs cho 4 lần parse (sink={sink})");
        assert!(per < 1000.0);
    }
}
