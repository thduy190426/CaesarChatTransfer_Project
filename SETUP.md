# HƯỚNG DẪN CÀI ĐẶT VÀ VẬN HÀNH DỰ ÁN CAESARCHATTRANSFER
Tài liệu này cung cấp các bước chi tiết từ A-Z để bạn có thể biên dịch, kiểm thử (chạy Unit Test) và vận hành ứng dụng Client-Server này trên máy tính của bạn.

---

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
   Sửa lại `db.user` và `db.password` cho trùng khớp với tài khoản MySQL trên máy bạn (mặc định đang là `root` / `root`).
   *Lưu ý: Không thay đổi tham số `db.url` trừ khi MySQL của bạn chạy ở một port khác ngoài 3306.*

---

## BƯỚC 3: BIÊN DỊCH VÀ CHẠY KIỂM THỬ (BUILD & TEST)

Hệ thống được trang bị bộ Unit Test chuẩn chỉ để đảm bảo logic mạng và mã hóa không có sai sót. Hãy chạy test trước khi khởi động ứng dụng.

1. Mở Terminal / PowerShell / Command Prompt.
2. Di chuyển (cd) vào thư mục gốc của dự án (Thư mục chứa file `pom.xml` ngoài cùng).
   ```bash
   cd C:\Users\duyho\Downloads\CaesarChatTransfer
   ```
3. Chạy lệnh sau để tải thư viện (Gson, SLF4J, MySQL Connector) và thực thi kiểm thử toàn bộ hệ thống:
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
   `Server khởi động tại port 9999...`
   `Đã khởi tạo Database Connection Pool thành công.`

### 4.2. Khởi động Client
1. Trong cùng IDE, tìm đến file **`com.tcpchat.client.ClientMain`** nằm trong phân hệ `client`.
2. Bấm **Run**.
3. Một hộp thoại Swing sẽ hiện ra yêu cầu nhập IP và Port:
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
