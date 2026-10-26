# CaesarChatTransfer

Hệ thống Client-Server đa phân hệ (Multi-module) phát triển bằng **Java 17**, hỗ trợ trao đổi văn bản bảo mật qua thuật toán **Mã hóa Caesar** (kèm phân tích tần suất ký tự) và **Truyền tải tập tin nhị phân đa luồng** (Chunked Streaming). Hệ thống tích hợp giao diện người dùng đồ họa (Swing FlatLaf), TCP Socket Server chịu tải cao và lưu trữ dữ liệu tập trung với **MySQL**.

---

## Kiến Trúc Hệ Thống

Dự án được tổ chức thành 3 phân hệ Maven độc lập:

* **`common`**: Tài nguyên và logic dùng chung cho cả Client và Server:
  * **Protocol**: Hằng số cấu hình hệ thống, chuẩn hóa thông điệp JSON (`Protocol.java`).
  * **Crypto**: Thuật toán Caesar Cipher mã hóa/giải mã toàn diện ký tự ASCII, an toàn với Unicode/Tiếng Việt.
  * **Analysis**: Bộ phân tích tần suất ký tự (`CharFrequencyAnalyzer`).
  * **Models**: Các đối tượng dữ liệu `Message`, `FilePacket`, `TextResult`.
  * **Parser**: Tuần tự hóa và giải mã JSON thông qua Google Gson.

* **`server`**: Hạt nhân trung tâm xử lý dữ liệu và kết nối mạng:
  * **TCP Server & ThreadPool**: Lắng nghe tại cổng TCP `9999`, quản lý kết nối đồng thời thông qua `ThreadPoolExecutor`.
  * **Heartbeat Mechanism**: Luồng ngầm kiểm tra PING/PONG định kỳ để phát hiện và thu hồi kết nối zombie/mất mạng.
  * **Connection Pool**: Quản lý kết nối CSDL MySQL an toàn luồng (Thread-safe) bằng `ArrayBlockingQueue`.
  * **Dynamic Config**: Tự động nhận diện và tải cấu hình từ file `db.properties` đặt cùng thư mục (tiện lợi khi triển khai VPS/Production).
  * **File Storage**: Tiếp nhận luồng byte nhị phân (chunk 8KB), lưu trữ vào thư mục `uploads/` và tự động dọn dẹp file tạm rác khi client ngắt kết nối đột ngột.

* **`client`**: Ứng dụng người dùng với giao diện đồ họa trực quan (GUI Swing & FlatLaf):
  * **GUI**: Cửa sổ đăng nhập (`LoginDialog`), màn hình chat chính (`ChatWindow`) và bảng tần suất ký tự (`FrequencyTablePanel`).
  * **Bất đồng bộ (Async)**: Xử lý truyền file qua `SwingWorker` kèm thanh tiến trình `JProgressBar`, không gây đơ giao diện.
  * **MessageReceiver**: Luồng đọc dữ liệu nền độc lập, phân tách luồng mạng khỏi luồng giao diện (EDT).

---

## 🚀 Tính Năng Chính

* **Mã hóa Caesar Cipher**: Mã hóa văn bản với khóa K (1 - 25) trước khi gửi qua mạng. Server giải mã, lưu DB và thống kê tần suất ký tự trả về Client.
* **Thống kê tần suất**: Phân tích trực quan tỷ lệ xuất hiện của các ký tự trong chuỗi văn bản.
* **Truyền tệp tin nhị phân lớn**: Hỗ trợ gửi file (hình ảnh, tài liệu, video) bằng kỹ thuật chia nhỏ 8KB (chunked streaming), chống tràn bộ nhớ (OOM).
* **Bảo toàn dữ liệu & Chống SQL Injection**: Toàn bộ thao tác truy vấn CSDL đều dùng `PreparedStatement`.
* **Tự động thu hồi file rác**: Server phát hiện sự cố ngắt kết nối khi đang gửi file, hủy file lỗi và cập nhật trạng thái `FAILED`.
* **Sẵn sàng triển khai đa môi trường**: Chạy được trên cùng 1 máy (Localhost), mạng nội bộ (LAN / Máy ảo) hoặc máy chủ đám mây (Cloud VPS).

---

## 🛠️ Hướng Dẫn Cài Đặt & Vận Hành

### 1. Chuẩn bị môi trường
* **Java Development Kit (JDK) 17** trở lên.
* **Apache Maven 3.9+** (hoặc dùng Maven tích hợp trong IntelliJ IDEA / Eclipse).
* **MySQL Server 8.x**.

### 2. Thiết lập Cơ sở dữ liệu (Database)
1. Mở MySQL Client (MySQL Workbench, DBeaver, hoặc Terminal).
2. Tạo CSDL và import cấu trúc bảng từ file `server/src/main/resources/schema.sql`:
   ```sql
   CREATE DATABASE IF NOT EXISTS caesar_chat_db;
   USE caesar_chat_db;
   -- Chạy toàn bộ nội dung trong file schema.sql để tạo 4 bảng:
   -- clients, messages, char_frequencies, file_transfers
   ```
3. Cấu hình thông tin đăng nhập trong file `server/src/main/resources/db.properties` (hoặc tạo file `db.properties` ngoài):
   ```properties
   db.driver=com.mysql.cj.jdbc.Driver
   db.url=jdbc:mysql://localhost:3306/caesar_chat_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   db.username=root
   db.password=MatKhauCuaBan
   db.pool.size=5
   ```

### 3. Đóng gói dự án (Build Fat JAR)
Dự án đã được tích hợp `maven-shade-plugin` để đóng gói toàn bộ thư viện phụ thuộc vào 2 file `.jar` độc lập:

```bash
mvn clean package -DskipTests
```

Sau khi build thành công:
* File chạy Server: `server/target/server-1.0.0-SNAPSHOT.jar`
* File chạy Client: `client/target/client-1.0.0-SNAPSHOT.jar`

---

## Chạy Hệ Thống

### 1. Khởi động Server
* **Cách 1 (Từ terminal/dòng lệnh):**
  ```bash
  java -jar server/target/server-1.0.0-SNAPSHOT.jar
  ```
* **Cách 2 (Từ IDE):** Mở và chạy file `com.tcpchat.server.ServerMain`.

Server sẽ lắng nghe tại cổng TCP `9999` và kết nối tới MySQL.

### 2. Khởi động Client
* **Cách 1 (Từ terminal/dòng lệnh):**
  ```bash
  java -jar client/target/client-1.0.0-SNAPSHOT.jar
  ```
* **Cách 2 (Từ IDE):** Mở và chạy file `com.tcpchat.client.ClientMain`.

Tại hộp thoại kết nối:
* **IP Server:** 
  * `127.0.0.1` (nếu chạy Server trên cùng máy).
  * `192.168.1.x` (nếu chạy qua mạng LAN / Máy ảo).
  * `IP_VPS` (nếu chạy Server trên Cloud VPS).
* **Port:** `9999`.

*(Mẹo: Bạn có thể mở đồng thời nhiều Client để kiểm thử tính năng đa người dùng).*

---

## ☁️ Triển Khai Trên Máy Ảo & Cloud VPS (Điểm Cộng)

### Triển khai Mạng LAN / Máy ảo (VMware / VirtualBox):
1. Cấu hình card mạng của máy ảo sang chế độ **Bridged Network** để máy ảo cùng dải IP nội bộ với máy thật.
2. Trên máy chạy Server (Windows), mở inbound port `9999` trong **Windows Defender Firewall**.
3. Client chỉ cần nhập địa chỉ IPv4 nội bộ của Server để kết nối.

### Triển khai lên Cloud VPS (Ubuntu / Linux):
1. Cài đặt Java 17 và MySQL trên VPS:
   ```bash
   sudo apt update && sudo apt install -y openjdk-17-jre-headless mysql-server
   ```
2. Mở cổng `9999/tcp` trên Firewall (UFW) và trên Security Group / Ingress Rules của nhà cung cấp VPS:
   ```bash
   sudo ufw allow 9999/tcp
   ```
3. Đẩy file `server-1.0.0-SNAPSHOT.jar`, `schema.sql` và `db.properties` lên VPS.
4. Chạy nền 24/7 với Systemd hoặc lệnh `nohup`:
   ```bash
   nohup java -jar server-1.0.0-SNAPSHOT.jar > server.log 2>&1 &
   ```
5. Mọi Client từ bất kỳ máy tính nào có kết nối Internet đều có thể kết nối thẳng tới `IP_VPS:9999`.

---

## Cấu Trúc Thư Mục

```text
CaesarChatTransfer/
├── pom.xml                                  (Maven Root quản lý đa phân hệ)
├── README.md                                (Tài liệu dự án)
├── SETUP.md                                 (Hướng dẫn cài đặt chi tiết)
│
├── common/                                  (Phân hệ dùng chung)
│   ├── pom.xml
│   └── src/main/java/com/tcpchat/common/    (Giao thức, CaesarCipher, FrequencyAnalyzer, Models)
│
├── client/                                  (Phân hệ Client)
│   ├── pom.xml                              (Đóng gói Client Fat JAR)
│   └── src/main/java/com/tcpchat/client/    (GUI FlatLaf, Socket Client, File Transfer)
│
└── server/                                  (Phân hệ Server)
    ├── pom.xml                              (Đóng gói Server Fat JAR)
    └── src/main/
        ├── java/com/tcpchat/server/         (TCP Server, ThreadPool, Heartbeat, DAO)
        └── resources/
            ├── db.properties                (Cấu hình kết nối MySQL)
            └── schema.sql                   (Kịch bản DDL tạo CSDL)
```
