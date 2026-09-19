# Chat & File Transfer Client–Server (Java TCP)

Dự án ứng dụng Chat và Truyền nhận file (audio, video, hình ảnh) thông qua giao thức TCP/IP bằng Java.
Ứng dụng sử dụng thuật toán **Caesar Cipher** để mã hóa văn bản, kèm theo cơ chế Heartbeat (Ping/Pong) để duy trì kết nối và quản lý Connection Pool cơ sở dữ liệu để tối ưu hiệu năng.

## Tech Stack

- **Ngôn ngữ:** Java 17+ (LTS)
- **Giao thức:** TCP/IP (java.net.Socket)
- **Mã hóa:** Caesar Cipher (Shift Cipher)
- **Database:** MySQL 8.x (JDBC thuần) / H2 Database (cho Unit Testing)
- **Giao diện:** Java Swing
- **Build Tool:** Maven (Multi-module)

---

## Tiến độ dự án

### Phase 1: Foundation & Core Protocol (Trần Hoàng Duy)

Phần lõi của dự án (Common Module) bao gồm các thành phần dùng chung cho cả Client và Server:

- **Khởi tạo kiến trúc Maven Multi-module:** Thiết lập `pom.xml` gốc và cấu trúc cho `common`, `client`, `server`.
- **Giao thức Protocol (`Protocol.java`):** Định nghĩa cấu hình cổng `PORT = 9999`, `CHUNK_SIZE = 8192` và các cờ loại Message (`TEXT`, `FILE`, `KEY_EXCHANGE`, `PING`, `PONG`, v.v).
- **Models (POJO):** Khởi tạo cấu trúc dữ liệu cho `Message`, `FilePacket`, và `TextResult`.
- **Mã hóa Caesar (`CaesarCipher.java`):** Thuật toán mã hóa và giải mã theo cơ chế xoay vòng bảng chữ cái (chỉ xoay a-z, A-Z) và giữ nguyên các ký tự đặc biệt, số. Xác thực khóa bắt buộc (1-25).
- **Phân tích tần suất (`CharFrequencyAnalyzer.java`):** Hỗ trợ đếm tần suất xuất hiện của các ký tự trong bản rõ, chuẩn bị sẵn dữ liệu để trả về phía Client.
- **Unit Test (JUnit 5):** Chạy kiểm thử tự động xác minh tính chính xác tuyệt đối của thuật toán mã hóa (CaesarCipherTest).

---

### Phase 2: Database & Storage Layer (Nguyễn Quang Anh)

Phần cơ sở dữ liệu và lưu trữ dữ liệu phía Server (Server Database Module):

- **Thiết kế CSDL & Seed Data:**
  - `schema.sql`: Định nghĩa các bảng `clients`, `messages`, `char_frequencies`, `file_transfers`.
  - `seed.sql`: Chèn dữ liệu mẫu thử nghiệm ban đầu.
  - `db.properties`: Quản lý cấu hình tham số JDBC URL, username, password, driver class và pool size.
- **Connection Pool Thread-safe (`DatabaseManager.java`):**
  - Quản lý tập hợp 5 kết nối CSDL sử dụng `ArrayBlockingQueue<Connection>`.
  - **Cấp phát an toàn:** Sử dụng `ArrayBlockingQueue.take()` để cấp phát connection (chặn luồng gọi an toàn nếu pool bận).
  - **Giải phóng tự động:** Thu hồi connection về pool và thay thế kết nối mới nếu kết nối bị đóng/hỏng qua `releaseConnection()`.
- **Data Access Objects (Repositories / DAOs):**
  - **Bảo mật SQL Injection:** 100% sử dụng `PreparedStatement` cho tất cả truy vấn SQL (`?`).
  - `MessageRepository.java`: Thực hiện Transaction nguyên tố (Atomic Transaction) lưu tin nhắn và bảng tần suất ký tự (`char_frequencies`).
  - `FileRepository.java`: Ghi nhật ký truyền nhận tập tin và cập nhật trạng thái file (`SUCCESS`, `DELETED`, v.v.).
  - `ClientRepository.java`: Quản lý nhật ký kết nối và trạng thái của Client (`CONNECTED`, `DISCONNECTED`).
- **File khởi chạy thử nghiệm & Unit Testing:**
  - `TestDatabaseMain.java`: Điểm chạy độc lập chứa phương thức `main()` hỗ trợ kiểm thử kết nối MySQL trực tiếp trong IDE.
  - **Unit Tests:** `DatabaseManagerTest.java`, `MessageRepositoryTest.java`, `FileRepositoryTest.java` sử dụng H2 Database in-memory, kết quả kiểm thử **BUILD SUCCESS 100%**.

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
└── server/ (Database & TCP Server)
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/tcpchat/server/
        │   │   ├── TestDatabaseMain.java (Chạy thử nghiệm DB)
        │   │   └── db/ (DatabaseManager, MessageRepository, FileRepository, ClientRepository)
        │   └── resources/ (schema.sql, seed.sql, db.properties)
        └── test/
            └── java/com/tcpchat/server/db/ (DatabaseManagerTest, MessageRepositoryTest, FileRepositoryTest)
```

---

## Hướng dẫn kiểm thử

### 1. Kiểm thử tự động (Unit Test với Maven)
Chạy lệnh kiểm thử tự động toàn bộ dự án (sử dụng CSDL in-memory H2):
```bash
mvn clean test
```

### 2. Kiểm thử chạy kết nối MySQL trực tiếp trong IDE
1. Đảm bảo dịch vụ MySQL đang chạy và nhập thông tin kết nối đúng trong `server/src/main/resources/db.properties`.
2. Mở tập tin `server/src/main/java/com/tcpchat/server/TestDatabaseMain.java`.
3. Nhấn nút **Run** để chạy trực tiếp phương thức `main()`.
