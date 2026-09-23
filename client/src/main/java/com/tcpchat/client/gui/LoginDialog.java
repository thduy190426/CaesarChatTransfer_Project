package com.tcpchat.client.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class LoginDialog extends JDialog {
    private JTextField txtIp;
    private JTextField txtPort;
    private boolean succeeded;

    public LoginDialog(JFrame parent) {
        super(parent, "Kết nối tới Server", true);
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20)); // Thêm padding xung quanh
        GridBagConstraints cs = new GridBagConstraints();
        cs.fill = GridBagConstraints.HORIZONTAL;
        cs.insets = new Insets(10, 10, 10, 10); // Khoảng cách giữa các phần tử

        JLabel lbTitle = new JLabel("Kết nối Máy Chủ Caesar", SwingConstants.CENTER);
        lbTitle.setFont(lbTitle.getFont().deriveFont(Font.BOLD, 18f));
        cs.gridx = 0; cs.gridy = 0; cs.gridwidth = 3;
        panel.add(lbTitle, cs);

        JLabel lbIp = new JLabel("IP Server: ");
        cs.gridx = 0; cs.gridy = 1; cs.gridwidth = 1;
        panel.add(lbIp, cs);

        txtIp = new JTextField("127.0.0.1", 15);
        cs.gridx = 1; cs.gridy = 1; cs.gridwidth = 2;
        panel.add(txtIp, cs);

        JLabel lbPort = new JLabel("Cổng (Port): ");
        cs.gridx = 0; cs.gridy = 2; cs.gridwidth = 1;
        panel.add(lbPort, cs);

        txtPort = new JTextField("9999", 15);
        cs.gridx = 1; cs.gridy = 2; cs.gridwidth = 2;
        panel.add(txtPort, cs);

        JButton btnLogin = new JButton("Kết nối");
        btnLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogin.addActionListener(this::onConnect);

        JButton btnCancel = new JButton("Hủy");
        btnCancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCancel.addActionListener(e -> {
            succeeded = false;
            dispose();
        });

        JPanel bp = new JPanel();
        bp.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20)); // Padding cho nút
        bp.add(btnLogin);
        bp.add(btnCancel);

        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(bp, BorderLayout.PAGE_END);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void onConnect(ActionEvent e) {
        String ip = txtIp.getText().trim();
        String portStr = txtPort.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ IP và Port.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Integer.parseInt(portStr);
            succeeded = true;
            dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là số nguyên.", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String getIpAddress() {
        return txtIp.getText().trim();
    }

    public int getPort() {
        return Integer.parseInt(txtPort.getText().trim());
    }

    public boolean isSucceeded() {
        return succeeded;
    }
}
