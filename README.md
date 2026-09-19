# Chat & File Transfer Client–Server (Java TCP)

Dự án ứng dụng Chat và Truyền nhận file (audio, video, hình ảnh) thông qua giao thức TCP/IP bằng Java.
Ứng dụng sử dụng thuật toán **Caesar Cipher** để mã hóa văn bản, kèm theo cơ chế Heartbeat (Ping/Pong) để duy trì kết nối và quản lý Connection Pool cơ sở dữ liệu để tối ưu hiệu năng.

## 🛠 Tech Stack

- **Ngôn ngữ:** Java 17+ (LTS)
- **Giao thức:** TCP/IP (java.net.Socket)
- **Mã hóa:** Caesar Cipher (Shift Cipher)
- **Database:** MySQL 8.x (JDBC thuần)
- **Giao diện:** Java Swing
- **Build Tool:** Maven (Multi-module)

---

## Tiến độ dự án

### Phase 1: Foundation & Core Protocol

Phần lõi của dự án (Common Module) đã được khởi tạo thành công, bao gồm các thành phần dùng chung cho cả Client và Server:

- **Khởi tạo kiến trúc Maven Multi-module:** Thiết lập `pom.xml` gốc và cấu trúc cho `common`, `client`, `server`.
- **Giao thức Protocol (`Protocol.java`):** Định nghĩa cấu hình cổng `PORT = 9999`, `CHUNK_SIZE = 8192` và các cờ loại Message (`TEXT`, `FILE`, `KEY_EXCHANGE`, `PING`, `PONG`, v.v).
- **Models (POJO):** Khởi tạo cấu trúc dữ liệu cho `Message`, `FilePacket`, và `TextResult`.
- **Mã hóa Caesar (`CaesarCipher.java`):** Thuật toán mã hóa và giải mã theo cơ chế xoay vòng bảng chữ cái (chỉ xoay a-z, A-Z) và giữ nguyên các ký tự đặc biệt, số. Xác thực khóa bắt buộc (1-25).
- **Phân tích tần suất (`CharFrequencyAnalyzer.java`):** Hỗ trợ đếm tần suất xuất hiện của các ký tự trong bản rõ, chuẩn bị sẵn dữ liệu để trả về phía Client.
- **Unit Test (JUnit 5):** Chạy kiểm thử tự động xác minh tính chính xác tuyệt đối của thuật toán mã hóa (CaesarCipherTest).

---

## Cấu trúc thư mục hiện tại

```text
tcp-caesar-chat/
├── pom.xml
├── common/ (Lõi giao thức và thuật toán)
│   ├── pom.xml
│   └── src/main/java/com/tcpchat/common/
│       ├── Protocol.java
│       ├── analysis/CharFrequencyAnalyzer.java
│       ├── crypto/CaesarCipher.java
│       └── model/ (Message, FilePacket, TextResult)
├── client/ (Giao diện UI & TCP Client - Đang phát triển)
│   └── pom.xml
└── server/ (Database & TCP Server - Đang phát triển)
    └── pom.xml
```

## Hướng dẫn kiểm thử (Phase 1)

1. Mở dự án trong **IntelliJ IDEA**.
2. Đợi Maven đồng bộ thư viện (nếu cần, bấm **Reload All Maven Projects** ở tab bên phải).
3. Truy cập đường dẫn: `common/src/test/java/com/tcpchat/common/crypto/CaesarCipherTest.java`.
4. Bấm nút Play màu xanh lá cây bên cạnh tên class để chạy kiểm thử Unit Test.
