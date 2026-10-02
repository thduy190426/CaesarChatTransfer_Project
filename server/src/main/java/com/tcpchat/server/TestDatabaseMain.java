package com.tcpchat.server;

import com.tcpchat.server.db.DatabaseManager;
import com.tcpchat.server.db.MessageRepository;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;


public class TestDatabaseMain {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  KIỂM THỬ KẾT NỐI CSDL & REPOSITORY (NGUYỄN QUANG ANH)");
        System.out.println("==================================================");

        try {
            
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initPool();

            System.out.println("-> Đã khởi tạo Connection Pool thành công.");
            System.out.println("-> Kích thước Pool: " + dbManager.getPoolSize());
            System.out.println("-> Số kết nối sẵn sàng (Available): " + dbManager.getAvailableConnections());

            
            Connection conn = dbManager.getConnection();
            System.out.println("-> Lấy thành công Connection bằng take(): " + conn.getMetaData().getURL());
            dbManager.releaseConnection(conn);
            System.out.println("-> Đã hoàn trả Connection về Pool thành công.");

            
            MessageRepository msgRepo = new MessageRepository(dbManager);
            Map<Character, Integer> freqs = new HashMap<>();
            freqs.put('H', 1);
            freqs.put('E', 1);
            freqs.put('L', 2);
            freqs.put('O', 1);

            System.out.println("\nĐang thử lưu tin nhắn mẫu vào CSDL bằng PreparedStatement...");
            long msgId = msgRepo.saveMessage("127.0.0.1", "KHOOR", 3, "HELLO", freqs);

            if (msgId > 0) {
                System.out.println("-> LƯU THÀNH CÔNG! Message ID được cấp: " + msgId);
                var record = msgRepo.getMessageById(msgId);
                if (record != null) {
                    System.out.println("-> Đọc dữ liệu từ DB (PreparedStatement):");
                    System.out.println("   + IP Client : " + record.getClientIp());
                    System.out.println("   + Mã hóa    : " + record.getCipherText());
                    System.out.println("   + Khóa Caesar: " + record.getShiftKey());
                    System.out.println("   + Giải mã   : " + record.getPlainText());
                    System.out.println("   + Thời gian : " + record.getCreatedAt());
                }
            } else {
                System.out.println("-> Lưu tin nhắn thất bại (Vui lòng kiểm tra dịch vụ MySQL trên máy).");
            }

            
            dbManager.closePool();
            System.out.println("\n=== KIỂM THỬ HOÀN TẤT NGUYÊN VẸN ===");

        } catch (Exception e) {
            System.err.println("\n[LỖI]: " + e.getMessage());
            System.err.println("Gợi ý: Nếu dùng MySQL thật, hãy đảm bảo MySQL service đang chạy và mật khẩu trong db.properties đúng.");
        }
    }
}
