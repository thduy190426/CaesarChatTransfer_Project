package com.tcpchat.client.gui;

import com.tcpchat.client.ClientConnection;
import com.tcpchat.client.MessageListener;
import com.tcpchat.client.ProgressListener;
import com.tcpchat.common.crypto.CaesarCipher;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

public class ChatWindow extends JFrame implements MessageListener {
    private JTextPane chatPane;
    private StringBuilder chatHtml;
    private JTextField txtInput;
    private JTextField txtKey;
    private JProgressBar progressBar;
    private FrequencyTablePanel freqPanel;

    private ClientConnection connection;
    private String serverIp;
    private int serverPort;

    public ChatWindow(ClientConnection connection, String ip, int port) {
        this.connection = connection;
        this.serverIp = ip;
        this.serverPort = port;

        setTitle("Caesar Chat Client - " + ip + ":" + port);
        setSize(850, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setContentPane(contentPane);

        initComponents();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                connection.close();
            }
        });
    }

    private void initComponents() {
        // Chat HTML builder
        chatHtml = new StringBuilder();
        chatHtml.append("<html><body style='font-family:\"Segoe UI\",Inter,sans-serif; font-size: 13px; padding: 5px; margin: 0;'>");

        // Chat Area
        chatPane = new JTextPane();
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setText(chatHtml.toString() + "</body></html>");
        
        JScrollPane scrollChat = new JScrollPane(chatPane);
        scrollChat.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        add(scrollChat, BorderLayout.CENTER);

        // Frequency Panel (Bên phải)
        freqPanel = new FrequencyTablePanel();
        add(freqPanel, BorderLayout.EAST);

        // Input Panel (Bên dưới)
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel inputPanel = new JPanel(new BorderLayout(10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        
        JPanel keyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        keyPanel.add(new JLabel("Khóa Caesar (1-25): "));
        txtKey = new JTextField("7", 5);
        txtKey.setHorizontalAlignment(JTextField.CENTER);
        keyPanel.add(txtKey);
        inputPanel.add(keyPanel, BorderLayout.WEST);

        txtInput = new JTextField();
        txtInput.addActionListener(e -> sendText());
        inputPanel.add(txtInput, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JButton btnSendFile = new JButton("Gửi File");
        btnSendFile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSendFile.addActionListener(e -> sendFile());

        JButton btnSend = new JButton("Gửi Text");
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.setBackground(new Color(0, 122, 204));
        btnSend.setForeground(Color.WHITE);
        btnSend.addActionListener(e -> sendText());
        
        btnPanel.add(btnSendFile);
        btnPanel.add(btnSend);
        inputPanel.add(btnPanel, BorderLayout.EAST);

        bottomPanel.add(inputPanel, BorderLayout.NORTH);

        // Progress Bar cho việc gửi file
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendText() {
        String text = txtInput.getText().trim();
        String keyStr = txtKey.getText().trim();

        if (text.isEmpty()) return;

        try {
            int key = Integer.parseInt(keyStr);
            if (key < 1 || key > 25) {
                throw new NumberFormatException();
            }

            // Gửi key exchange trước
            connection.sendKeyExchange(key);

            // Mã hóa và gửi text
            String cipher = CaesarCipher.encrypt(text, key);
            connection.sendText(cipher);

            appendHtmlChat("<b>Me (Plain):</b>", text, "#005a9e"); // Xanh đậm
            appendHtmlChat("<b>Me (Cipher):</b>", cipher, "#0078d4"); // Xanh nhạt
            txtInput.setText("");

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Khóa Caesar phải là số nguyên từ 1 đến 25", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            appendHtmlChat("<b>Lỗi hệ thống:</b>", e.getMessage(), "#d13438"); // Đỏ
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            progressBar.setVisible(true);
            progressBar.setValue(0);

            // Sử dụng SwingWorker để gửi file ở background thread, không làm đơ UI
            SwingWorker<Void, Integer> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    connection.sendFile(file, percent -> {
                        publish(percent);
                    });
                    return null;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    int lastPercent = chunks.get(chunks.size() - 1);
                    progressBar.setValue(lastPercent);
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    try {
                        get(); // Lấy kết quả để ném exception nếu có
                        appendHtmlChat("<b>Hệ thống:</b>", "Đã gửi file " + file.getName() + " xong, chờ phản hồi...", "#107c10"); // Xanh lá
                    } catch (Exception e) {
                        appendHtmlChat("<b>Lỗi gửi file:</b>", e.getMessage(), "#d13438"); // Đỏ
                    }
                }
            };
            worker.execute();
        }
    }

    private void appendHtmlChat(String prefix, String message, String color) {
        String safeMsg = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        chatHtml.append("<div style='margin-bottom: 5px; color: ").append(color).append(";'>")
                .append(prefix).append(" ").append(safeMsg)
                .append("</div>");
        
        chatPane.setText(chatHtml.toString() + "</body></html>");
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    // --- MessageListener methods ---

    @Override
    public void onTextResultReceived(TextResult result) {
        appendHtmlChat("<b>Server (Plain):</b>", result.getPlainText(), "#107c10"); // Xanh lá cây
        freqPanel.updateFrequency(result.getFrequency());
    }

    @Override
    public void onFileAckReceived(FilePacket ack) {
        if ("OK".equals(ack.getStatus())) {
            appendHtmlChat("<b>Server:</b>", "Đã lưu file tại " + ack.getSavedPath(), "#107c10"); // Xanh lá
        } else {
            appendHtmlChat("<b>Server (Lỗi File):</b>", ack.getStatus(), "#d13438"); // Đỏ
        }
    }

    @Override
    public void onErrorReceived(Message error) {
        appendHtmlChat("<b>Server (Lỗi):</b>", error.getMessage(), "#d13438"); // Đỏ
        JOptionPane.showMessageDialog(this, error.getMessage(), "Lỗi từ Server", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onDisconnected() {
        appendHtmlChat("<b>Hệ thống:</b>", "--- Đã ngắt kết nối khỏi server ---", "#a80000"); // Đỏ sậm
        txtInput.setEnabled(false);
    }
}
