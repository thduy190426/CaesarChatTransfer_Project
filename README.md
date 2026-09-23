# CaesarChatTransfer

CaesarChatTransfer là một hệ thống mạnh mẽ theo kiến trúc Client-Server (Khách-Chủ) đa phân hệ (multi-module) được phát triển bằng ngôn ngữ Java 17. Hệ thống cho phép trao đổi văn bản bảo mật qua thuật toán mã hóa Caesar Cipher và truyền tải tập tin bằng giao thức nhị phân phân mảnh (chunked binary protocol) đảm bảo độ tin cậy. Dự án kết hợp giao diện đồ họa người dùng (GUI) bằng Swing cho ứng dụng Client và một TCP Server hỗ trợ xử lý đồng thời cường độ cao kết hợp lưu trữ cơ sở dữ liệu MySQL.

## Kiến Trúc Hệ Thống

Dự án được chia thành ba phân hệ (module) chính, quản lý hoàn toàn bằng Maven:

1. **Phân hệ Common (`common`)**
   Chứa các tài nguyên và tiện ích dùng chung cho cả Client và Server.
   - **Định nghĩa Giao thức**: Các hằng số cấu hình hệ thống và chuẩn hóa loại thông điệp.
   - **Mô hình Dữ liệu (Models)**: Các lớp POJO (Plain Old Java Objects) như `Message`, `FilePacket`, và `TextResult`.
   - **Mã hóa (Cryptography)**: Triển khai thuật toán Caesar Cipher cho phép mã hóa và giải mã văn bản.
   - **Công cụ Phân tích**: Tiện ích phân tích tần suất xuất hiện của ký tự.
   - **Phân tích Thông điệp**: Xử lý (Serialize/Deserialize) các gói tin JSON thông qua thư viện Gson.

2. **Phân hệ Server (`server`)**
   Hạt nhân của hệ thống chịu trách nhiệm xử lý kết nối, dữ liệu và lưu trữ.
   - **Giao tiếp Mạng (TCP Networking)**: Sử dụng `ServerSocket` và `ThreadPoolExecutor` (hỗ trợ tối đa 50 luồng chạy song song) nhằm phục vụ số lượng lớn Client.
   - **Cơ chế Heartbeat**: Tích hợp luồng kiểm tra PING/PONG nhằm tự động phát hiện và ngắt kết nối an toàn với các Client bị mất tín hiệu.
   - **Connection Pool**: Quản lý kết nối Cơ sở dữ liệu Thread-safe tự xây dựng thông qua `ArrayBlockingQueue`, ngăn chặn tình trạng thắt cổ chai.
   - **Repositories**: Ứng dụng `PreparedStatement` phòng ngừa tận gốc tấn công SQL Injection khi lưu trữ lịch sử tin nhắn, tần suất ký tự và nhật ký truyền file.
   - **Xử lý File**: Tiếp nhận luồng dữ liệu nhị phân (binary stream), lưu trữ file xuống bộ nhớ vật lý cục bộ và tự động thu gom dọn dẹp các tệp bị lỗi hoặc đứt quãng.

3. **Phân hệ Client (`client`)**
   Ứng dụng cung cấp giao diện tương tác dành cho người dùng cuối xây dựng bằng Java Swing.
   - **Giao diện Người dùng**: Cung cấp hộp thoại Đăng nhập, cửa sổ Chat chính và Bảng thống kê tần suất ký tự.
   - **Xử lý Bất đồng bộ**: Sử dụng `SwingWorker` cho các tác vụ nặng (như chuyển file) nhằm đảm bảo giao diện luôn mượt mà. Thanh tiến trình `JProgressBar` giám sát quá trình tải lên.
   - **Luồng Lắng nghe**: Thiết lập luồng ngầm (`MessageReceiver`) liên tục đón nhận phản hồi từ Server mà không gây gián đoạn luồng xử lý giao diện (EDT).

## Tính Năng Nổi Bật

- **Mã Hóa Caesar Cipher**: Văn bản được mã hóa phía Client với một khóa (từ 1 đến 25) người dùng chỉ định trước khi truyền tải và được giải mã phía Server phục vụ công tác phân tích.
- **Phân Tích Tần Suất Ký Tự**: Server xử lý văn bản gốc, thống kê tỷ lệ xuất hiện của các ký tự và trả về dữ liệu phân tích chi tiết cho Client.
- **Truyền Tập Tin Dung Lượng Lớn**: Hỗ trợ chuyển phát nhanh tệp tin (hình ảnh, âm thanh, video) thông qua kỹ thuật băm nhỏ (chunk) 8KB, ngăn chặn tuyệt đối lỗi tràn bộ nhớ (OutOfMemory).
- **Kết Nối Bền Bỉ**: Hệ thống có khả năng theo dõi sức khỏe kết nối qua tín hiệu Heartbeat theo chu kỳ thời gian thực.
- **Bảo Toàn Dữ Liệu**: Mọi tương tác trong mạng lưới từ lịch sử đăng nhập, tin nhắn, dữ liệu phân tích đến hồ sơ truyền tải file đều được lưu vết đầy đủ trong CSDL MySQL.

## BƯỚC 1: CHUẨN BỊ MÔI TRƯỜNG (PREREQUISITES)

Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã được cài đặt các phần mềm sau:
1. **Java Development Kit (JDK) 17**: Bạn có thể tải từ Oracle hoặc Eclipse Temurin. Đảm bảo biến môi trường `JAVA_HOME` đã được cấu hình.
2. **Apache Maven (3.9.x trở lên)**: Dùng để tải thư viện và build project. (Lệnh `mvn -version` trên terminal phải chạy thành công).
3. **MySQL Server (8.x)**: Hệ quản trị cơ sở dữ liệu để lưu trữ tin nhắn và nhật ký truyền file. Cần có công cụ như MySQL Workbench hoặc DBeaver để quản lý.

---

## BƯỚC 2: THIẾT LẬP CƠ SỞ DỮ LIỆU (DATABASE SETUP)

Hệ thống bắt buộc phải có Database để lưu trữ dữ liệu.
1. Mở MySQL Workbench (hoặc công cụ tương đương) và kết nối vào MySQL Server của bạn.
2. Chạy câu lệnh tạo Database:
   ```sql
   CREATE DATABASE caesar_chat_db;
   USE caesar_chat_db;
   ```
3. Khởi tạo cấu trúc bảng: 
   Mở file `schema.sql` nằm ở đường dẫn `server/src/main/resources/schema.sql` và chạy toàn bộ mã SQL trong đó. Quá trình này sẽ tạo ra 4 bảng: `clients`, `messages`, `char_frequencies`, và `file_transfers`.
4. Cấu hình thông tin đăng nhập:
   Mở file `server/src/main/resources/db.properties`.
   Sửa lại `db.username` và `db.password` cho trùng khớp với tài khoản MySQL trên máy bạn.
   *Lưu ý: Không thay đổi tham số `db.url` trừ khi MySQL của bạn chạy ở một port khác ngoài 3306.*

---

## BƯỚC 3: BIÊN DỊCH VÀ CHẠY KIỂM THỬ (BUILD & TEST)

Hệ thống được trang bị bộ Unit Test chuẩn chỉ để đảm bảo logic mạng và mã hóa không có sai sót. Hãy chạy test trước khi khởi động ứng dụng.

1. Mở Terminal / PowerShell / Command Prompt.
2. Di chuyển (cd) vào thư mục gốc của dự án (Thư mục chứa file `pom.xml` ngoài cùng).
   ```bash
   cd đường_dẫn_tới_thư_mục_CaesarChatTransfer
   ```
3. Chạy lệnh sau để tải thư viện (Gson, SLF4J, FlatLaf, MySQL Connector) và thực thi kiểm thử toàn bộ hệ thống:
   ```bash
   mvn clean test
   ```
   **Dấu hiệu thành công:** Terminal in ra thông báo `BUILD SUCCESS` màu xanh lá, kèm theo thông số cho thấy không có bài Test nào bị FAILED hay SKIPPED.

4. (Tùy chọn) Để đóng gói toàn bộ dự án thành file chạy `.jar`, sử dụng lệnh:
   ```bash
   mvn clean install -DskipTests
   ```

---

## BƯỚC 4: CHẠY HỆ THỐNG THỰC TẾ

### 4.1. Khởi động Server (Bắt buộc chạy trước)
1. Mở dự án bằng IDE của bạn (IntelliJ IDEA, Eclipse, hoặc VSCode).
2. Tìm đến file **`com.tcpchat.server.ServerMain`** nằm trong phân hệ `server`.
3. Bấm **Run**.
4. Theo dõi Console của IDE. Nếu Server khởi động thành công, bạn sẽ thấy log:
   `Server đã khởi động tại port 9999...`
   `Đã khởi tạo Database Connection Pool thành công.`

### 4.2. Khởi động Client
1. Trong cùng IDE, tìm đến file **`com.tcpchat.client.ClientMain`** nằm trong phân hệ `client`.
2. Bấm **Run**.
3. Một hộp thoại Đăng nhập phẳng (FlatLaf) sẽ hiện ra yêu cầu nhập IP và Port:
   - IP: `127.0.0.1` (nếu chạy Server trên cùng 1 máy).
   - Port: `9999` (Port mặc định của Server).
4. Bấm **Kết nối**.

*Mẹo: Bạn có thể chạy file `ClientMain` nhiều lần để tạo ra nhiều Client độc lập kết nối cùng lúc tới Server!*

---

## BƯỚC 5: HƯỚNG DẪN SỬ DỤNG (USER GUIDE)

1. **Nhắn tin bảo mật (Caesar Cipher):**
   - Ở cửa sổ Chat, nhìn góc dưới bên trái có ô "Khóa Caesar". Hãy nhập một con số từ 1 đến 25 (Ví dụ: `7`).
   - Nhập một đoạn văn bản vào ô Text trống dài bên cạnh (Ví dụ: `HELLO WORLD`).
   - Bấm nút **Gửi Text**.
   - Client sẽ mã hóa văn bản theo khóa 7 và gửi lên Server. Server giải mã, lưu DB, đếm tần suất và trả kết quả về. Bảng Tần suất (bên phải) sẽ lập tức được cập nhật!

2. **Truyền File:**
   - Bấm nút **Gửi File**.
   - Trình duyệt file sẽ mở ra. Hãy chọn một file bất kỳ (ảnh, video, hoặc pdf) trên máy tính của bạn. Khuyến khích chọn file dưới 50MB để test.
   - Thanh tiến trình (JProgressBar) phía dưới cùng sẽ xuất hiện và trượt từ 0% đến 100%. Màn hình Chat vẫn có thể cuộn bình thường mà không bị đơ.
   - Khi chạy xong 100%, có thông báo: `Server: Đã lưu file tại uploads/...`
   
3. **Kiểm tra dữ liệu (Database Check):**
   - Mở lại MySQL Workbench và truy vấn:
     `SELECT * FROM messages;`
     `SELECT * FROM file_transfers;`
   - Bạn sẽ thấy toàn bộ lịch sử các giao dịch vừa rồi đã được lưu vết lại chính xác từng thời điểm!
   - Thư mục `uploads/` sẽ được tự động tạo ra trong thư mục chứa mã nguồn Server, chứa bản sao của các file bạn vừa tải lên.

## Công Nghệ Sử Dụng

- **Ngôn ngữ**: Java 17 LTS
- **Kết nối Mạng**: `java.net.Socket` và `ServerSocket`
- **Xử lý Đa luồng**: `java.util.concurrent` (ThreadPoolExecutor, ArrayBlockingQueue)
- **Thiết kế Giao diện**: Java Swing (JFrame, JDialog, JProgressBar, SwingWorker)
- **Thao tác JSON**: Google Gson 2.10.1
- **Truy xuất CSDL**: JDBC thuần với MySQL Connector/J 8.0.33
- **Lưu Vết (Logging)**: SLF4J đi kèm Logback
- **Kiểm Thử (Testing)**: JUnit 5

## Cấu Trúc Mã Nguồn (Project Structure)

Dưới đây là sơ đồ kiến trúc thư mục chi tiết, thể hiện rõ ràng các thành phần chức năng, công cụ mã hóa và phân tích của hệ thống:

```text
tcp-caesar-chat/
├── pom.xml                                  (Tệp cấu hình Maven Root)
├── README.md                                (Tài liệu hướng dẫn hiện tại)
│
├── common/                                  (Phân hệ các lớp nền tảng chia sẻ)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/tcpchat/common/
│       │   ├── MessageParser.java           (Xử lý Serializing/Deserializing JSON)
│       │   ├── Protocol.java                (Chứa hằng số giao thức chung)
│       │   ├── analysis/
│       │   │   └── CharFrequencyAnalyzer.java (Đếm và phân tích tần suất ký tự)
│       │   ├── crypto/
│       │   │   └── CaesarCipher.java        (Thuật toán dịch chuyển Caesar)
│       │   └── model/
│       │       ├── FilePacket.java          (POJO: Chứa thông tin cấu trúc tệp tin)
│       │       ├── Message.java             (POJO: Chứa nội dung text và khóa mã hóa)
│       │       └── TextResult.java          (POJO: Chứa kết quả giải mã và mảng tần suất)
│       └── test/java/com/tcpchat/common/
│           └── crypto/
│               └── CaesarCipherTest.java    (Kiểm thử Unit Test cho thuật toán mã hóa)
│
├── client/                                  (Phân hệ giao diện đồ họa và kết nối khách)
│   ├── pom.xml
│   └── src/
│       └── main/java/com/tcpchat/client/
│           ├── ClientMain.java              (Điểm chạy chính của Client, khởi tạo GUI)
│           ├── ClientConnection.java        (Quản lý trạng thái vòng đời Socket)
│           ├── MessageListener.java         (Giao diện callback khi nhận dữ liệu từ Server)
│           ├── MessageReceiver.java         (Luồng nền đọc phản hồi liên tục)
│           ├── ProgressListener.java        (Giao diện callback theo dõi % tải file)
│           ├── gui/
│           │   ├── ChatWindow.java          (Cửa sổ làm việc chính, chứa SwingWorker)
│           │   ├── FrequencyTablePanel.java (Bảng thống kê tỷ lệ ký tự trực quan)
│           │   └── LoginDialog.java         (Form nhập liệu cấu hình IP/Port ban đầu)
│           └── transfer/
│               └── FileTransferClient.java  (Luồng gửi byte nhị phân chia chunk từ file)
│
└── server/                                  (Phân hệ xử lý dữ liệu trung tâm)
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/tcpchat/server/
        │   │   ├── ServerMain.java          (Điểm chạy chính của Server, nạp DB)
        │   │   ├── TCPServer.java           (Lớp chứa ThreadPoolExecutor, Heartbeat)
        │   │   ├── ClientHandlerThread.java (Worker xử lý chu kỳ sống của một Client)
        │   │   ├── TestDatabaseMain.java    (Tệp tin kiểm thử kết nối nhanh)
        │   │   ├── db/
        │   │   │   ├── ClientRepository.java(Quản trị bảng clients)
        │   │   │   ├── DatabaseManager.java (Pool quản lý ArrayBlockingQueue connections)
        │   │   │   ├── FileRepository.java  (Quản trị bảng file_transfers)
        │   │   │   └── MessageRepository.java(Quản trị bảng messages và char_frequencies)
        │   │   └── transfer/
        │   │       └── FileReceiverHandler.java (Luồng tiếp nhận byte nhị phân, ghi đĩa)
        │   └── resources/
        │       ├── db.properties            (Lưu trữ các thông số cấu hình kết nối DB)
        │       └── schema.sql               (Kịch bản DDL tạo CSDL)
        └── test/java/com/tcpchat/server/
            └── db/                          (Tập hợp Unit Test cho lớp Data Access Object)
                ├── DatabaseManagerTest.java
                ├── FileRepositoryTest.java
                └── MessageRepositoryTest.java
```
