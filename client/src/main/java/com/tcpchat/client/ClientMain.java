package com.tcpchat.client;

import com.tcpchat.client.gui.ChatWindow;
import com.tcpchat.client.gui.LoginDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class ClientMain {
    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        // Thiết lập Look and Feel của FlatLaf
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
            // Đặt phông chữ mặc định đẹp hơn (Inter hoặc Segoe UI)
            UIManager.put("defaultFont", new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
        } catch (Exception e) {
            logger.warn("Không thể thiết lập giao diện FlatLaf", e);
        }

        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDlg = new LoginDialog(null);
            loginDlg.setVisible(true);

            if (loginDlg.isSucceeded()) {
                String ip = loginDlg.getIpAddress();
                int port = loginDlg.getPort();

                ClientConnection connection = new ClientConnection();
                ChatWindow chatWindow = new ChatWindow(connection, ip, port);
                
                try {
                    connection.connect(ip, port, chatWindow);
                    chatWindow.setVisible(true);
                } catch (Exception e) {
                    logger.error("Không thể kết nối đến server", e);
                    JOptionPane.showMessageDialog(null, "Không thể kết nối tới server: " + e.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            } else {
                System.exit(0);
            }
        });
    }
}
