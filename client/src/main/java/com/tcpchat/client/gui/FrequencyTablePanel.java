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

        tableModel = new DefaultTableModel(new Object[]{"Character", "Count"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(25); 
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.getTableHeader().setBackground(new Color(240, 240, 240));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(250, 0)); 
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Frequency Analysis"));

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateFrequency(Map<Character, Integer> frequencyMap) {
        tableModel.setRowCount(0); 
        if (frequencyMap != null) {
            for (Map.Entry<Character, Integer> entry : frequencyMap.entrySet()) {
                tableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }
    }
}
