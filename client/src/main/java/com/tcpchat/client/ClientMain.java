package com.tcpchat.client;

import com.tcpchat.client.gui.ChatWindow;
import com.tcpchat.client.gui.LoginDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class ClientMain {
    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
            
            UIManager.put("defaultFont", new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
        } catch (Exception e) {
            logger.warn("Failed to set FlatLaf look and feel", e);
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
                    logger.error("Failed to connect to server", e);
                    JOptionPane.showMessageDialog(null, "Could not connect to server: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            } else {
                System.exit(0);
            }
        });
    }
}
