package com.tcpchat.client.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

public class FrequencyTablePanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;

    public FrequencyTablePanel() {
        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(new Object[]{"Ký tự", "Số lượng"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(25); // Tăng chiều cao hàng để dễ nhìn hơn
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.getTableHeader().setBackground(new Color(240, 240, 240));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(250, 0)); // Rộng hơn một chút
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Phân tích Tần suất"));

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateFrequency(Map<Character, Integer> frequencyMap) {
        tableModel.setRowCount(0); // Xóa dữ liệu cũ
        if (frequencyMap != null) {
            for (Map.Entry<Character, Integer> entry : frequencyMap.entrySet()) {
                tableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }
    }
}
