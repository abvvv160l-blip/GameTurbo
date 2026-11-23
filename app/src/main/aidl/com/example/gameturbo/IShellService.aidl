package com.example.gameturbo;

interface IShellService {
    // Shizuku yêu cầu phương thức này (mã 16777114) để huỷ service
    void destroy() = 16777114;

    // Chạy lệnh shell, trả về "<mã thoát>\n<kết quả>"
    String exec(String command) = 1;

    // Đọc nhiều file hệ thống trong một lần (không mở tiến trình con), xem core/SysReader.kt
    String readMany(String keys) = 2;
}
